package com.sharddevs.shards_factions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

// ===========================================================================
// FactionManager — the live, in-memory home for all factions and claims, and
// the logic that operates on them. FactionSavedData wraps one of these and
// persists its contents; everything stateful (membership, claims, budget,
// overclaim, bypass) lives here.
//
// NOTE: mutations here are memory-only. The caller (command / event layer)
// is responsible for FactionSavedData.setDirty() after a persisted change
// (Addendum 8 §51.3).
// ===========================================================================
public class FactionManager {

    // -----------------------------------------------------------------------
    // STATE
    // -----------------------------------------------------------------------
    private final Map<UUID, Faction> factions = new HashMap<>();
    private final Map<ChunkPos, Claim> claims = new HashMap<>();
    private final Map<UUID, Long> pendingDisband = new HashMap<>();   // /f disband two-step confirm (ephemeral)
    private final Set<UUID> bypassingPlayers = new HashSet<>();        // /f bypass toggles (ephemeral, §47)

    // Synthetic owner of all system factions (SAFEZONE / WARZONE). No real
    // player has the all-zeros UUID — a safe "owned by the server" sentinel.
    public static final UUID SERVER_OWNER = new UUID(0L, 0L);

    // -----------------------------------------------------------------------
    // LOOKUPS
    // -----------------------------------------------------------------------
    public Faction getFaction(UUID id) {
        return this.factions.get(id);
    }

    public Claim getClaim(ChunkPos chunk) {
        return this.claims.get(chunk);
    }

    public Faction getFactionByMember(UUID player) {
        for (Faction faction : this.factions.values()) {
            if (faction.isMember(player)) return faction;
        }
        return null;   // not in any faction
    }

    public Faction getFactionByName(String name) {
        for (Faction faction : this.factions.values()) {
            if (faction.getName().equalsIgnoreCase(name)) return faction;
        }
        return null;
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
            if (claim.getClaimedBy().equals(factionId)) result.add(claim);
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // COLOUR ASSIGNMENT — least-used colour for a new faction (HANDOFF_6 §5/§6).
    //
    // Seed every real colour at 0, count usage across existing factions, pick
    // the lowest. <=16 factions: every pick is unused; past 16, ties keep
    // usage even. ChatFormatting holds styles (BOLD, ITALIC…) too, so
    // .isColor() filters to actual colours — a style has no RGB and would
    // render wrong on the /f map grid.
    // -----------------------------------------------------------------------
    private ChatFormatting pickLeastUsedColor() {
        Map<ChatFormatting, Integer> usage = new HashMap<>();

        for (ChatFormatting cf : ChatFormatting.values()) {
            if (cf.isColor()) usage.put(cf, 0);
        }
        for (Faction faction : this.factions.values()) {
            ChatFormatting used = faction.getColor();
            usage.put(used, usage.getOrDefault(used, 0) + 1);
        }

        ChatFormatting best = ChatFormatting.WHITE;   // safe fallback
        int bestCount = Integer.MAX_VALUE;
        for (Map.Entry<ChatFormatting, Integer> entry : usage.entrySet()) {
            if (entry.getValue() < bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    // -----------------------------------------------------------------------
    // FACTION LIFECYCLE
    // -----------------------------------------------------------------------
    public CreateResult createFaction(String name, UUID owner, FactionType type) {
        for (Faction faction : this.factions.values()) {
            if (faction.getName().equalsIgnoreCase(name)) return CreateResult.NAME_TAKEN;
        }
        // Pick the colour BEFORE the new faction is in the map, or it would
        // count itself (with no colour yet assigned).
        ChatFormatting color = pickLeastUsedColor();
        Faction faction = new Faction(name, owner, type, color);
        this.factions.put(faction.getId(), faction);
        return CreateResult.SUCCESS;
    }

    public void addFaction(Faction faction) {
        this.factions.put(faction.getId(), faction);
    }

    public void disbandFaction(Faction faction) {
        UUID id = faction.getId();

        // removeIf is the safe sweep — a plain loop calling claims.remove(...)
        // mid-iteration would throw.
        claims.values().removeIf(claim -> claim.getClaimedBy().equals(id));

        // The faction's placed Obelisk (if any) is aired out command-side in
        // /f disband BEFORE this call, so the block's onRemove resolves against
        // a still-existing faction (Addendum 1 §13.7). Nothing to do here.

        factions.remove(id);
    }

    public Map<UUID, Long> getPendingDisband() {
        return pendingDisband;
    }

    // -----------------------------------------------------------------------
    // CLAIMS + OVERCLAIM (Addendum 2 §17, condition corrected this session)
    // -----------------------------------------------------------------------
    public ClaimResult claimChunk(ChunkPos chunk, Faction faction) {
        // One lookup: claimed vs unclaimed for the whole method.
        Claim existing = getClaim(chunk);

        if (existing != null) {
            // ── already owned by someone ──

            // Own-faction guard — nothing to claim/overclaim against ourselves.
            if (existing.getClaimedBy().equals(faction.getId())) {
                return ClaimResult.ALREADY_CLAIMED;
            }

            Faction victim = getFaction(existing.getClaimedBy());

            // System-faction land is permanently protected, never overclaimable
            // (§13.4). Reject before the power comparison.
            if (victim.getType() != FactionType.PLAYER) {
                return ClaimResult.ALREADY_CLAIMED;
            }

            // Overclaim conditions (§17.2). "power" = baseBudget + bonusBudget
            // — the TOTAL the faction has, NOT getAvailableBudget() (which
            // already subtracts usedClaims and would double-count it; that was
            // a real bug fixed this session). A faction is overextended when
            // its claims exceed its power:
            //   1. victim overextended      — B.used > B.power
            //   2. attacker has headroom    — A.used < A.power
            //   3. attacker power positive  — A.power > 0
            //   4. NOT B's obelisk chunk while B's obelisk stands (null-check
            //      first so new ChunkPos(null) can't NPE)
            if (victim.getUsedClaims() > victim.getBaseBudget() + victim.getBonusBudget()
                    && faction.getUsedClaims() < faction.getBaseBudget() + faction.getBonusBudget()
                    && faction.getBaseBudget() + faction.getBonusBudget() > 0
                    && !(victim.getObeliskPos() != null
                    && chunk.equals(new ChunkPos(victim.getObeliskPos())))) {

                // Transfer (§17.4): the Claim already lives in the map keyed by
                // this chunk, so flip its owner in place — no remove/re-add.
                victim.decrementUsedClaims();            // B loses a claim
                existing.setClaimedBy(faction.getId());  // chunk now owned by A
                existing.setProtected(faction.getObeliskPos() != null); // protection follows NEW owner
                faction.incrementUsedClaims();           // A gains a claim
                return ClaimResult.OVERCLAIM_SUCCESS;
            }

            // Not overclaimable — reject.
            return ClaimResult.ALREADY_CLAIMED;
        }

        // ── unclaimed — normal claim ──
        // System factions are budget-exempt (§13.4); only PLAYER land hits
        // the budget wall.
        if (faction.getAvailableBudget() <= 0 && faction.getType() == FactionType.PLAYER) {
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

    // NOTE: addOverclaim is identical to addClaim and appears unused (the
    // overclaim path mutates the Claim in place, it doesn't re-add). Left in
    // pending a confirm — safe to delete if nothing calls it.
    public void addOverclaim(Claim claim) {
        this.claims.put(claim.getChunk(), claim);
    }

    // -----------------------------------------------------------------------
    // BYPASS (§47) — ephemeral admin protection-override toggles.
    // -----------------------------------------------------------------------
    public boolean toggleBypass(UUID uuid) {
        if (bypassingPlayers.contains(uuid)) {
            bypassingPlayers.remove(uuid);
            return false;
        }
        bypassingPlayers.add(uuid);
        return true;
    }

    public boolean isBypassing(UUID uuid) {
        return bypassingPlayers.contains(uuid);
    }

    // -----------------------------------------------------------------------
    // MESSAGING
    // -----------------------------------------------------------------------
    public void notifyFaction(Faction faction, Component message, MinecraftServer server) {
        for (UUID memberId : faction.getMembers()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) member.sendSystemMessage(message);
        }
    }
}