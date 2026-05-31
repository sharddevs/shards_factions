package com.sharddevs.shards_factions;

import java.util.UUID;

import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
// ChatFormatting — the faction colour type, persisted by name() / valueOf().
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
/**
 * Persistence layer for faction data (design §10).
 *
 * Extends Minecraft's SavedData: Minecraft handles WHEN to save/load;
 * this class provides the WHAT. It wraps a FactionManager — the manager
 * holds the live data and logic; this class only moves that data to and
 * from NBT on disk.
 */
public class FactionSavedData extends SavedData {

    // The one FactionManager this save-data wraps. final — the slot never
    // re-points; the manager's CONTENTS change as factions come and go.
    private final FactionManager manager;

    // Constructor. Runs when a fresh (empty) FactionSavedData is created.
    public FactionSavedData() {
        // LOGIC LINE — give 'manager' a brand-new FactionManager.
        this.manager = new FactionManager();/* ??? */
    }

    // A read method so the rest of the mod can reach the wrapped manager.
    public FactionManager getManager() {
        return this.manager;
    }
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag factionList = new ListTag();

        for (Faction  faction : this.manager.getAllFactions()) {
            CompoundTag factionTag = new CompoundTag();

            // LOGIC LINES — write this faction's scalar fields into factionTag.
            factionTag.putUUID("id", faction.getId());
            // factionTag.putUUID("id", ??? );
            factionTag.putString("name", faction.getName());
            // factionTag.putString("name", ??? );
            factionTag.putUUID("owner", faction.getOwner());
            // factionTag.putUUID("owner", ??? );
            if (faction.getObeliskPos() != null) {
                factionTag.putLong("obeliskPos", faction.getObeliskPos().asLong());
            }
            factionTag.putInt("bonusBudget", faction.getBonusBudget());
            // factionTag.putInt("bonusBudget", ??? );
            factionTag.putInt("usedClaims", faction.getUsedClaims());
            // factionTag.putInt("usedClaims", ??? );
            factionTag.putString("type", faction.getType().name());
            // An enum is persisted as its NAME — .name() gives the String
            // "RED", "AQUA", etc.; load() turns it back with valueOf().
            // Same pattern as "type" above.
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
    public static FactionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FactionSavedData data = new FactionSavedData();
        ListTag factionList = tag.getList("factions", Tag.TAG_COMPOUND);
        ListTag claimList = tag.getList("claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < factionList.size(); i++) {
            CompoundTag factionTag = factionList.getCompound(i);

            // --- Restore the faction's colour from NBT. ---
            // getString returns "" for a MISSING key — and an old save file
            // written before factions had colours has no "color" tag.
            // ChatFormatting.valueOf("") THROWS, which would crash the load.
            // So: if the tag is missing/blank, fall back to WHITE; otherwise
            // valueOf the saved name. (This is why ADDING a saved field is
            // not the mirror of REMOVING one — a removed tag is harmlessly
            // ignored, a missing required tag must be defended.)
            String colorName = factionTag.getString("color");
            ChatFormatting color;
            if (colorName.isEmpty()) {
                color = ChatFormatting.WHITE;
            } else {
                color = ChatFormatting.valueOf(colorName);
            }

            Faction faction = new Faction (
                    factionTag.getUUID("id"),
                    factionTag.getString("name"),
                    factionTag.getUUID("owner"),
                    FactionType.valueOf(factionTag.getString("type")),
                    color,
                    factionTag.getInt("bonusBudget"),
                    factionTag.getInt("usedClaims")
            );
            if (factionTag.contains("obeliskPos")) {
                faction.setObeliskPos(
                        BlockPos.of(factionTag.getLong("obeliskPos")));
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
    // --- write all factions ---
}