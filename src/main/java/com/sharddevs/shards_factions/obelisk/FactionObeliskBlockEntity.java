package com.sharddevs.shards_factions.obelisk;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// ===========================================================================
// FactionObeliskBlockEntity — stores the owning faction id on the obelisk
// block itself (Addendum 6 §38). Persisting the binding here is what lets it
// survive restarts and chunk unloads, and lets onRemove know whose obelisk
// was destroyed without scanning the world.
// ===========================================================================
public class FactionObeliskBlockEntity extends BlockEntity {

    private UUID factionId = null;

    public FactionObeliskBlockEntity(BlockPos pos, BlockState state) {
        super(ObeliskRegistration.FACTION_OBELISK_BLOCK_ENTITY.get(), pos, state);
    }

    public UUID getFactionId() {
        return this.factionId;
    }

    public void setFactionId(UUID factionId) {
        this.factionId = factionId;
        setChanged();   // marks the BE dirty so the binding persists
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