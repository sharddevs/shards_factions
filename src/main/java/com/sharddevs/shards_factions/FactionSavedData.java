package com.sharddevs.shards_factions;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

// ===========================================================================
// FactionSavedData — persistence layer (design §10).
//
// Extends Minecraft's SavedData: Minecraft decides WHEN to save/load, this
// class provides the WHAT. It wraps a FactionManager (which holds the live
// data) and moves that data to/from NBT. Attached to the overworld's data
// storage; no external database.
//
// Anything that mutates faction/claim state must call setDirty() somewhere,
// or the change is memory-only until something else marks dirty (Addendum 8
// §51.3). The command and block/event layers handle that, not this class.
// ===========================================================================
public class FactionSavedData extends SavedData {

    // The wrapped manager. final — the slot never re-points; its CONTENTS
    // change as factions/claims come and go.
    private final FactionManager manager;

    public FactionSavedData() {
        this.manager = new FactionManager();
    }

    public FactionManager getManager() {
        return this.manager;
    }

    // -----------------------------------------------------------------------
    // SAVE — walk live factions + claims, write each to NBT.
    // -----------------------------------------------------------------------
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag factionList = new ListTag();

        for (Faction faction : this.manager.getAllFactions()) {
            CompoundTag factionTag = new CompoundTag();

            factionTag.putUUID("id", faction.getId());
            factionTag.putString("name", faction.getName());
            factionTag.putUUID("owner", faction.getOwner());

            // Nullable/zero fields written only when present — keeps old saves
            // and unset state clean (load() defends the absent case).
            if (faction.getObeliskPos() != null) {
                factionTag.putLong("obeliskPos", faction.getObeliskPos().asLong());
            }
            if (faction.getLastObeliskGive() > 0) {
                factionTag.putLong("lastObeliskGive", faction.getLastObeliskGive());
            }

            factionTag.putInt("bonusBudget", faction.getBonusBudget());
            factionTag.putInt("usedClaims", faction.getUsedClaims());

            // Enums persist as their name(); load() restores via valueOf().
            factionTag.putString("type", faction.getType().name());
            factionTag.putString("color", faction.getColor().name());

            ListTag memberList = new ListTag();
            for (UUID memberId : faction.getMembers()) {
                CompoundTag memberTag = new CompoundTag();
                memberTag.putUUID("uuid", memberId);
                memberTag.putString("role", faction.getRole(memberId).name());
                memberList.add(memberTag);
            }
            factionTag.put("members", memberList);

            factionList.add(factionTag);
        }

        ListTag claimList = new ListTag();
        for (Claim claim : this.manager.getAllClaims()) {
            CompoundTag claimTag = new CompoundTag();
            claimTag.putInt("chunkX", claim.getChunk().x);
            claimTag.putInt("chunkZ", claim.getChunk().z);
            claimTag.putUUID("claimedBy", claim.getClaimedBy());
            claimTag.putBoolean("protected", claim.isProtected());
            claimList.add(claimTag);
        }

        tag.put("factions", factionList);
        tag.put("claims", claimList);
        return tag;
    }

    // -----------------------------------------------------------------------
    // LOAD — rebuild manager state from NBT.
    // -----------------------------------------------------------------------
    public static FactionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FactionSavedData data = new FactionSavedData();
        ListTag factionList = tag.getList("factions", Tag.TAG_COMPOUND);
        ListTag claimList = tag.getList("claims", Tag.TAG_COMPOUND);

        for (int i = 0; i < factionList.size(); i++) {
            CompoundTag factionTag = factionList.getCompound(i);

            // Colour: getString returns "" for a MISSING key. An old save with
            // no "color" tag would make ChatFormatting.valueOf("") THROW and
            // crash the load — so fall back to WHITE when blank. (Adding a
            // saved field is not the mirror of removing one: a removed tag is
            // ignored harmlessly, a missing required tag must be defended.)
            String colorName = factionTag.getString("color");
            ChatFormatting color = colorName.isEmpty()
                    ? ChatFormatting.WHITE
                    : ChatFormatting.valueOf(colorName);

            Faction faction = new Faction(
                    factionTag.getUUID("id"),
                    factionTag.getString("name"),
                    factionTag.getUUID("owner"),
                    FactionType.valueOf(factionTag.getString("type")),
                    color,
                    factionTag.getInt("bonusBudget"),
                    factionTag.getInt("usedClaims")
            );

            // contains-guarded — absent tag leaves the field at its default
            // (obeliskPos null, lastObeliskGive 0 = first give free).
            if (factionTag.contains("obeliskPos")) {
                faction.setObeliskPos(BlockPos.of(factionTag.getLong("obeliskPos")));
            }
            if (factionTag.contains("lastObeliskGive")) {
                faction.setLastObeliskGive(factionTag.getLong("lastObeliskGive"));
            }

            ListTag memberList = factionTag.getList("members", Tag.TAG_COMPOUND);
            for (int j = 0; j < memberList.size(); j++) {
                CompoundTag memberTag = memberList.getCompound(j);
                faction.addMemberWithRole(
                        memberTag.getUUID("uuid"),
                        FactionRole.valueOf(memberTag.getString("role"))
                );
            }

            data.getManager().addFaction(faction);
        }

        for (int i = 0; i < claimList.size(); i++) {
            CompoundTag claimTag = claimList.getCompound(i);
            ChunkPos chunk = new ChunkPos(
                    claimTag.getInt("chunkX"),
                    claimTag.getInt("chunkZ")
            );
            Claim claim = new Claim(chunk, claimTag.getUUID("claimedBy"));
            claim.setProtected(claimTag.getBoolean("protected"));
            data.getManager().addClaim(claim);
        }

        return data;
    }

    // -----------------------------------------------------------------------
    // PLUMBING — factory + accessor Minecraft uses to load/create this data.
    // -----------------------------------------------------------------------
    public static SavedData.Factory<FactionSavedData> factory() {
        return new SavedData.Factory<>(
                FactionSavedData::new,
                FactionSavedData::load
        );
    }

    public static final String DATA_NAME = "shards_factions";

    public static FactionSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }
}