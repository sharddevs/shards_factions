package com.sharddevs.shards_factions;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

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
    public static final UUID SERVER_OWNER = new UUID(0L, 0L);
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
        // One lookup, stored once. Tells us claimed vs. unclaimed for the
        // whole method — no need to call getClaim(chunk) again below.
        Claim existing = getClaim(chunk);

        if (existing != null) {
            // ── chunk is already owned by someone ──

            // Own-faction guard. If WE already own it there's nothing to claim
            // or overclaim — bail before running overclaim logic against
            // ourselves (which would compare the faction to itself).
            if (existing.getClaimedBy().equals(faction.getId())) {
                return ClaimResult.ALREADY_CLAIMED;
            }

            // The current owner B (victim). getFaction is the real lookup
            // (not getFactionById). Owner id comes from getClaimedBy().
            Faction victim = getFaction(existing.getClaimedBy());

            // The four §17.2 overclaim conditions, all expressed as "allowed":
            //   1. victim overextended      — B.usedClaims > B.budget   (cond 2)
            //   2. attacker has headroom    — A.usedClaims < A.budget   (cond 1)
            //   3. attacker budget positive — A.budget > 0              (cond 3)
            //   4. NOT B's obelisk chunk while B's obelisk stands       (cond 4)
            // "power" = getAvailableBudget() (Addendum 4 — unified value).
            // Obelisk null-check is FIRST inside the !( ) so a null pos
            // short-circuits before new ChunkPos(null) can NPE.

            if (victim.getType() != FactionType.PLAYER) {
                return ClaimResult.ALREADY_CLAIMED;
            }
            if (victim.getUsedClaims() > victim.getBaseBudget() + victim.getBonusBudget()
                    && faction.getUsedClaims() < faction.getBaseBudget() + faction.getBonusBudget()
                    && faction.getBaseBudget() + faction.getBonusBudget() > 0
                    && !(victim.getObeliskPos() != null
                    && chunk.equals(new ChunkPos(victim.getObeliskPos())))) {

                // ── transfer (§17.4): two-sided, but the Claim object already
                // lives in the manager's claims map keyed by this chunk, so we
                // just flip its owner in place — no remove/re-add needed. ──
                victim.decrementUsedClaims();          // B loses a claim
                existing.setClaimedBy(faction.getId()); // chunk now owned by A
                existing.setProtected(faction.getObeliskPos() != null);
                faction.incrementUsedClaims();          // A gains a claim
                return ClaimResult.OVERCLAIM_SUCCESS;
            }

            // Conditions failed — overclaim not allowed, fall back to reject.
            return ClaimResult.ALREADY_CLAIMED;
        }

        // ── unclaimed chunk — original normal-claim path, unchanged ──
        if (faction.getAvailableBudget() <= 0 && faction.getType() == FactionType.PLAYER) {
            return ClaimResult.NO_BUDGET;
        }
        Claim newClaim = new Claim(chunk, faction.getId());
        claims.put(chunk, newClaim);
        faction.incrementUsedClaims();
        return ClaimResult.SUCCESS;
    }
    private final Set<UUID> bypassingPlayers = new HashSet<>();
    public boolean toggleBypass(UUID uuid) {
        if (bypassingPlayers.contains(uuid)) {
            bypassingPlayers.remove(uuid);
            return false;
        } else {
            bypassingPlayers.add(uuid);
            return true;
        }
    }
    public boolean isBypassing(UUID uuid) {
        return bypassingPlayers.contains(uuid);
    }
    public void addClaim(Claim claim) {
        this.claims.put(claim.getChunk(), claim);
    }
    public void removeClaim(ChunkPos chunk) {
        this.claims.remove(chunk);
    }
    public void addOverclaim(Claim claim) {
        this.claims.put(claim.getChunk(), claim);
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
    public Collection<Claim> getClaimsByFaction(UUID factionId) {
        List<Claim> result = new ArrayList<>();
        for (Claim claim : this.claims.values()) {
            if (claim.getClaimedBy().equals(factionId)) {
                result.add(claim);
            }
        }
        return result;
    }
}