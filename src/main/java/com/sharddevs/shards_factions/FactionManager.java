package com.sharddevs.shards_factions;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

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
}