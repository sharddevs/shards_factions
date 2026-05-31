package com.sharddevs.shards_factions.obelisk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;          // NEW — NBT compound type
import net.minecraft.core.HolderLookup;         // NEW — needed by save/loadAdditional
import java.util.UUID;

public class FactionObeliskBlockEntity extends BlockEntity {

    // Field moved ABOVE the constructor (convention — fields first).
    private UUID factionId = null;

    public FactionObeliskBlockEntity(BlockPos pos, BlockState state) {
        super(ObeliskRegistration.FACTION_OBELISK_BLOCK_ENTITY.get(), pos, state);
    }

    public UUID getFactionId() {
        return this.factionId;
    }

    public void setFactionId(UUID factionId) {
        this.factionId = factionId;
        setChanged();                            // NEW — marks the BE dirty so the change persists
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.factionId != null) {
            tag.putUUID("factionId", this.factionId);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("factionId")) {
            this.factionId = tag.getUUID("factionId");
        }
    }
}