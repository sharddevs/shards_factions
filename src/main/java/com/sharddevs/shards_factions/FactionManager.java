package com.sharddevs.shards_factions;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;

import net.minecraft.world.level.ChunkPos;

public class FactionManager {
    private final Map<UUID, Faction> factions = new HashMap<>();
    private final Map<ChunkPos, Claim> claims = new HashMap<>();

    public Faction getFaction(UUID id) {
        return this.factions.get(id);
    }
    public Claim getClaim(ChunkPos chunk) {
        return this.claims.get(chunk);
    }
    public Faction createFaction(String name, UUID owner, FactionType type) {
        Faction faction = new Faction(name, owner, type);
        this.factions.put(faction.getId(), faction);
        return faction;
    }
    public void addFaction(Faction faction) {
        this.factions.put(faction.getId(), faction);
    }
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
    public Collection<Faction> getAllFactions() {
        return this.factions.values();
    }
    public Collection<Claim> getAllClaims() {
        return this.claims.values();
    }
}