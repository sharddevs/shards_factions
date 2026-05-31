package com.sharddevs.shards_factions;

import java.util.UUID;

import net.minecraft.world.level.ChunkPos;

// ===========================================================================
// Claim — one claimed chunk (design §6).
//
// Carries TWO independent facts:
//   claimedBy   — which faction owns the chunk.
//   isProtected — whether that faction's protection is currently active
//                 (PLAYER land: obelisk up; system zones: always, set at
//                 claim time). Claimed != protected — a faction can own a
//                 chunk that isn't currently protected.
//
// Plain data; no persistence (FactionSavedData handles that). claimedBy is
// mutable so overclaim can flip ownership in place.
// ===========================================================================
public class Claim {

    private final ChunkPos chunk;
    private UUID claimedBy;
    private boolean isProtected;

    public Claim(ChunkPos chunk, UUID claimedBy) {
        this.chunk = chunk;
        this.claimedBy = claimedBy;
        this.isProtected = false;
    }

    public ChunkPos getChunk() {
        return this.chunk;
    }

    public UUID getClaimedBy() {
        return this.claimedBy;
    }

    public boolean isProtected() {
        return this.isProtected;
    }

    public void setClaimedBy(UUID newOwner) {
        this.claimedBy = newOwner;
    }

    public void setProtected(boolean isProtected) {
        this.isProtected = isProtected;
    }
}