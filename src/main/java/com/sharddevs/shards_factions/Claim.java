package com.sharddevs.shards_factions;

import java.util.UUID;

import net.minecraft.world.level.ChunkPos;

public class Claim {
    private final ChunkPos chunk;
    private UUID claimedBy;
    private boolean isProtected;


    public Claim(ChunkPos chunk, UUID claimedBy) {
        this.chunk = chunk;
        this.claimedBy = claimedBy;
        this.isProtected = false;
    }
    public ChunkPos getChunk(){
        return this.chunk;
    }
    public UUID getClaimedBy(){
        return this.claimedBy;
    }
    public boolean isProtected(){
        return this.isProtected;
    }
    public void setClaimedBy(UUID newOwner){
        this.claimedBy = newOwner;
    }
    public void setProtected(boolean isProtected){
        this.isProtected = isProtected;
    }
}