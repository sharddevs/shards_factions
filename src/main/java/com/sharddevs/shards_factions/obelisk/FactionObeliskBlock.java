package com.sharddevs.shards_factions.obelisk;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import com.sharddevs.shards_factions.Claim;
import com.sharddevs.shards_factions.ClaimResult;
import com.sharddevs.shards_factions.Faction;
import com.sharddevs.shards_factions.FactionManager;
import com.sharddevs.shards_factions.FactionSavedData;

// ===========================================================================
// FactionObeliskBlock — the obelisk block + its lifecycle (design §7,
// Addendum 6).
//
//   setPlacedBy — on placement: validate legality, auto-claim the chunk if
//                 unclaimed, remove any prior obelisk (one per faction),
//                 bind this one, and flip the faction's claims protected.
//   onRemove    — on destruction (break/explosion/replacement): flip the
//                 faction's claims unprotected, clear its obeliskPos, notify.
//
// Anything that mutates faction/claim state here MUST setDirty(), or the
// change is memory-only (Addendum 8 §51 — the persistence bug this fixed).
// ===========================================================================
public class FactionObeliskBlock extends Block implements EntityBlock {

    public FactionObeliskBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FactionObeliskBlockEntity(pos, state);
    }

    // -----------------------------------------------------------------------
    // PLACEMENT — bind the obelisk to the placer's faction.
    // -----------------------------------------------------------------------
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        // Guards — server-only, real player, our block entity present.
        if (level.isClientSide()) return;
        if (!(placer instanceof Player player)) return;
        if (!(level.getBlockEntity(pos) instanceof FactionObeliskBlockEntity obelisk)) return;

        FactionManager manager = FactionSavedData.get(level.getServer()).getManager();
        Faction faction = manager.getFactionByMember(player.getUUID());
        if (faction == null) return;   // no faction to bind to

        ChunkPos chunkPos = new ChunkPos(pos);
        Claim claim = manager.getClaim(chunkPos);

        // Legality: illegal only if the chunk is claimed by a DIFFERENT faction.
        // (unclaimed → legal; our own → legal; enemy/system → reject.)
        if (claim != null && !claim.getClaimedBy().equals(faction.getId())) {
            player.sendSystemMessage(Component.literal(
                    "You cannot place an Obelisk on another faction's claim."));
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());           // undo placement
            player.getInventory().add(
                    new ItemStack(ObeliskRegistration.FACTION_OBELISK_ITEM.get()));  // refund item
            return;
        }

        // On unclaimed land, auto-claim the chunk (claimChunk does the budget
        // check + usedClaims bump). No budget → reject the whole placement.
        if (claim == null) {
            ClaimResult result = manager.claimChunk(chunkPos, faction);
            if (result != ClaimResult.SUCCESS) {
                player.sendSystemMessage(Component.literal(
                        "Your faction has no claim budget remaining."));
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                player.getInventory().add(
                        new ItemStack(ObeliskRegistration.FACTION_OBELISK_ITEM.get()));
                return;
            }
        }

        // One obelisk per faction — air out the old one if it exists elsewhere.
        BlockPos oldObelisk = faction.getObeliskPos();
        if (oldObelisk != null && !oldObelisk.equals(pos)) {
            level.setBlockAndUpdate(oldObelisk, Blocks.AIR.defaultBlockState());
        }

        // Bind: BE remembers the faction, faction remembers the position, and
        // all the faction's claims flip protected.
        obelisk.setFactionId(faction.getId());
        faction.setObeliskPos(pos);
        for (Claim c : manager.getClaimsByFaction(faction.getId())) {
            c.setProtected(true);
        }
        FactionSavedData.get(level.getServer()).setDirty();
    }

    // -----------------------------------------------------------------------
    // DESTRUCTION — fires on any state-change away from the obelisk. On a real
    // removal (not a same-block state change, not client-side), flip claims
    // unprotected, clear obeliskPos, notify. Read the BE BEFORE super (super
    // clears it). (Addendum 6 §40, Addendum 8 §51.)
    // -----------------------------------------------------------------------
    @Override
    public void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // same-block state change — not a removal.
        if (oldState.is(newState.getBlock())) {
            super.onRemove(oldState, level, pos, newState, isMoving);
            return;
        }
        if (level.isClientSide()) {
            super.onRemove(oldState, level, pos, newState, isMoving);
            return;
        }

        if (level.getBlockEntity(pos) instanceof FactionObeliskBlockEntity obelisk) {
            UUID factionId = obelisk.getFactionId();
            if (factionId != null) {
                FactionManager manager = FactionSavedData.get(level.getServer()).getManager();
                Faction faction = manager.getFaction(factionId);
                if (faction != null) {   // null = disbanded; silently skip
                    for (Claim c : manager.getClaimsByFaction(faction.getId())) {
                        c.setProtected(false);
                    }
                    faction.setObeliskPos(null);
                    manager.notifyFaction(faction, Component.literal(
                                    "Your faction base is under attack! Your obelisk has been destroyed!"),
                            level.getServer());
                }
            }
        }

        super.onRemove(oldState, level, pos, newState, isMoving);   // parent bookkeeping (clears the BE)
        FactionSavedData.get(level.getServer()).setDirty();
    }
}