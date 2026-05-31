package com.sharddevs.shards_factions;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
// ChatFormatting — needed for the faction-colour assignment below.
import net.minecraft.ChatFormatting;

public class FactionManager {
    private final Map<UUID, Faction> factions = new HashMap<>();
    private final Map<ChunkPos, Claim> claims = new HashMap<>();

    public Faction getFaction(UUID id) {
        return this.factions.get(id);
    }
    public Claim getClaim(ChunkPos chunk) {
        return this.claims.get(chunk);
    }

    // -----------------------------------------------------------------------
    // COLOUR ASSIGNMENT  —  picks a display colour for a brand-new faction.
    //
    // Rule (HANDOFF_6 §6 / §5): "auto-assigned at creation, least-used
    // colour". Count how many existing factions already use each colour;
    // pick a colour with the lowest count. With <=16 factions every colour
    // chosen is unused (count 0); past 16, the count tie-breaks so usage
    // stays as even as possible.
    //
    // CRITICAL: ChatFormatting holds BOTH the 16 colours AND style codes
    // (BOLD, ITALIC, UNDERLINE, OBFUSCATED, STRIKETHROUGH, RESET). A faction
    // colour must be an actual COLOUR — .isColor() filters the styles out.
    // Without that filter a faction could be assigned "BOLD" as its colour,
    // which has no .getColor() RGB and would render wrong on the /f map grid.
    //
    // private — this is createFaction's internal helper, nothing else calls
    // it. static — it needs no instance state; it only reads the factions
    // map passed in via the loop below (it walks this.factions directly, so
    // it is an instance method after all — kept non-static for that access).
    // -----------------------------------------------------------------------
    private ChatFormatting pickLeastUsedColor() {
        // Tally: colour -> how many existing factions already use it.
        Map<ChatFormatting, Integer> usage = new HashMap<>();

        // Seed every real colour at count 0, so a never-used colour is still
        // a candidate (a colour absent from the map would be skipped).
        for (ChatFormatting cf : ChatFormatting.values()) {
            if (cf.isColor()) {
                usage.put(cf, 0);
            }
        }

        // Count actual usage across all existing factions.
        for (Faction faction : this.factions.values()) {
            ChatFormatting used = faction.getColor();
            // getOrDefault: if 'used' somehow isn't a key (shouldn't happen,
            // every colour was seeded) treat it as 0 rather than NPE.
            usage.put(used, usage.getOrDefault(used, 0) + 1);
        }

        // Walk the tally, keep the colour with the smallest count seen so far.
        ChatFormatting best = ChatFormatting.WHITE; // safe fallback
        int bestCount = Integer.MAX_VALUE;
        for (Map.Entry<ChatFormatting, Integer> entry : usage.entrySet()) {
            if (entry.getValue() < bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    public CreateResult createFaction(String name, UUID owner, FactionType type) {
        for (Faction faction : this.factions.values()) {
            if (faction.getName().equalsIgnoreCase(name)) {
                return CreateResult.NAME_TAKEN;
            }
        }
        // Pick the colour BEFORE constructing — the 4-arg constructor now
        // requires it. pickLeastUsedColor reads the current factions map,
        // so it must run while this new faction is NOT yet in the map
        // (otherwise it would count a faction with no colour assigned).
        ChatFormatting color = pickLeastUsedColor();
        Faction faction = new Faction(name, owner, type, color);
        this.factions.put(faction.getId(), faction);
        return CreateResult.SUCCESS;

    }
    public void addFaction(Faction faction) {
        this.factions.put(faction.getId(), faction);
    }
    public Faction getFactionByMember(UUID player) {
        for (Faction faction : this.factions.values()) {
            // if this faction has `player` as a member, return it
            if (faction.isMember(player)) {
                return faction;
            }
        }
        return null;  // not in any faction
    }
    public void disbandFaction(Faction faction) {
        UUID id = faction.getId();

        // Sweep claims belonging to this faction. removeIf walks the
        // collection and removes matching entries safely — a plain for-loop
        // calling claims.remove(...) mid-iteration throws.
        claims.values().removeIf(claim -> claim.getClaimedBy().equals(id));

        // TODO: remove the faction's Obelisk from the world (Addendum 1 §13.7).
        //       Obelisk not built yet — no-op stub for now.

        factions.remove(id);
    }
    public Faction getFactionByName(String name) {
        for (Faction faction : this.factions.values()) {
            if (faction.getName().equalsIgnoreCase(name)) {
                return faction;
            }
        }
        return null;
    }
    private final Map<UUID, Long> pendingDisband = new HashMap<>();
    public Map<UUID, Long> getPendingDisband() { return pendingDisband; }
    public ClaimResult claimChunk(ChunkPos chunk, Faction faction) {
        if (getClaim(chunk) != null) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        if (faction.getAvailableBudget() <= 0) {
            return ClaimResult.NO_BUDGET;
        }
        Claim newClaim = new Claim(chunk, faction.getId());
        claims.put(chunk, newClaim);
        faction.incrementUsedClaims();
        return ClaimResult.SUCCESS;

    }
    public void addClaim(Claim claim) {
        this.claims.put(claim.getChunk(), claim);
    }
    public void removeClaim(ChunkPos chunk) {
        this.claims.remove(chunk);
    }
    public void notifyFaction(Faction faction, Component message, MinecraftServer server) {
        for (UUID memberId : faction.getMembers()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) {
                member.sendSystemMessage(message);
            }
        }
    }
    public Collection<Faction> getAllFactions() {
        return this.factions.values();
    }
    public Collection<Claim> getAllClaims() {
        return this.claims.values();
    }
}