package com.sharddevs.shards_factions.obelisk;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import javax.annotation.Nullable;
import com.sharddevs.shards_factions.FactionManager;
import com.sharddevs.shards_factions.FactionSavedData;
import com.sharddevs.shards_factions.Faction;
import com.sharddevs.shards_factions.Claim;
import com.sharddevs.shards_factions.ClaimResult;

import java.util.UUID;

public class FactionObeliskBlock extends Block implements EntityBlock {

    public FactionObeliskBlock(BlockBehaviour.Properties properties) {
        super(properties);



    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FactionObeliskBlockEntity(pos, state);
    }
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        // --- guard clauses: bail on the bad cases up front ---

        // Block code runs on client AND server. Game logic is server-only.
        if (level.isClientSide()) return;

        // The placer might not be a player (dispenser, etc.). Need a player.
        if (!(placer instanceof Player player)) return;

        // Grab our block entity at this position, as our specific type.
        if (!(level.getBlockEntity(pos) instanceof FactionObeliskBlockEntity obelisk)) return;

        // --- resolve the placer's faction ---
        FactionManager manager =
                FactionSavedData.get(level.getServer()).getManager();
        Faction faction = manager.getFactionByMember(player.getUUID());

        // No faction → nothing to bind to. Bail.
        if (faction == null) return;

        // --- 4b: placement legality ---
        // Which chunk is this obelisk in, and who claims that chunk?
        ChunkPos chunkPos = new ChunkPos(pos);
        Claim claim = manager.getClaim(chunkPos);

        // Illegal ONLY if the chunk is claimed by a DIFFERENT faction.
        // claim == null  → unclaimed land → legal.
        // claim by us    → our own territory → legal.
        // claim by other → enemy or system faction → illegal, reject.
        if (claim != null && !claim.getClaimedBy().equals(faction.getId())) {
            // Reject: the block is ALREADY placed, so we undo it.

            // 1. Tell the player why.
            player.sendSystemMessage(Component.literal(
                    "You cannot place an Obelisk on another faction's claim."));

            // 2. Undo the placement — set the position back to air.
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());

            // 3. Return the consumed obelisk item to the player.
            player.getInventory().add(
                    new ItemStack(ObeliskRegistration.FACTION_OBELISK_ITEM.get()));

            return; // bail before binding
        }
            // --- 4c: claim the obelisk's chunk if it isn't already ours ---
            // --- 4c: claim the obelisk's chunk if it isn't already ours ---
        if (claim == null) {
            // Unclaimed land. Ask claimChunk to claim it — it does the
            // budget check and the usedClaims bump internally.
            ClaimResult result = manager.claimChunk(chunkPos, faction);

            // If it couldn't be claimed (no budget), reject the placement.
            if (result != ClaimResult.SUCCESS) {
                player.sendSystemMessage(Component.literal(
                        "Your faction has no claim budget remaining."));
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                player.getInventory().add(
                        new ItemStack(ObeliskRegistration.FACTION_OBELISK_ITEM.get()));
                return;
            }
        }
            // --- 4e: one obelisk per faction ---
            // If the faction already had an obelisk, remove that old one.
        BlockPos oldObelisk = faction.getObeliskPos();
        if (oldObelisk != null && !oldObelisk.equals(pos)) {
            // Old obelisk exists and isn't this same spot — air it out.
            level.setBlockAndUpdate(oldObelisk, Blocks.AIR.defaultBlockState());
        }

        // --- bind the new obelisk ---
        obelisk.setFactionId(faction.getId());          // block entity remembers faction
        faction.setObeliskPos(pos);                      // faction remembers obelisk location
        for (Claim c: manager.getClaimsByFaction(faction.getId())) {
            c.setProtected(true);
        }
    FactionSavedData.get(level.getServer()).setDirty();
    }
    public void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
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
                if (faction != null) {
                    for (Claim c: manager.getClaimsByFaction(faction.getId())) {
                        c.setProtected(false);
                    }
                    faction.setObeliskPos(null);
                    manager.notifyFaction(faction, Component.literal("Your faction base is under attack! Your obelisk has been destroyed!"), level.getServer());
                }
            }
        }
        super.onRemove(oldState, level, pos, newState, isMoving);
        FactionSavedData.get(level.getServer()).setDirty();
    }
}