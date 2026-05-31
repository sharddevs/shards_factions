package com.sharddevs.shards_factions;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import com.sharddevs.shards_factions.obelisk.FactionObeliskBlock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.ArrayList;

@EventBusSubscriber(modid = ShardsFactions.MOD_ID)
public class ProtectionEvents {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        ChunkPos chunkPos = new ChunkPos(event.getPos());
        ServerLevel level = (ServerLevel) event.getLevel();
        FactionManager manager = FactionSavedData.get(level.getServer()).getManager();
        Claim claim = manager.getClaim(chunkPos);
        if (claim == null) return;
        if (!claim.isProtected()) return;
        if (event.getState().getBlock() instanceof FactionObeliskBlock) return;
        Player player = event.getPlayer();
        Faction breakerFaction = manager.getFactionByMember(player.getUUID());
        if (breakerFaction != null && breakerFaction.getId().equals(claim.getClaimedBy())) return;
        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("Cannot break! " + manager.getFaction(claim.getClaimedBy()).getName() + "'s land is protected by their obelisk!"));

    }
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent place) {
        if (place.getLevel().isClientSide()) return;
        if (!(place.getEntity() instanceof Player player)) return;
        ChunkPos chunkPos = new ChunkPos(place.getPos());
        ServerLevel level = (ServerLevel) place.getLevel();
        FactionManager manager = FactionSavedData.get(level.getServer()).getManager();
        Claim claim = manager.getClaim(chunkPos);
        if (claim == null) return;
        if (!claim.isProtected()) return;
        //Player playerManager = player.getPlayer();
        Faction placerFaction = manager.getFactionByMember(player.getUUID());
        if (placerFaction != null && placerFaction.getId().equals(claim.getClaimedBy())) return;
        place.setCanceled(true);
        player.sendSystemMessage(Component.literal("Cannot place in " + manager.getFaction(claim.getClaimedBy()).getName() + "'s  land while it is protected by their obelisk!"));
    }
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        ServerLevel level = (ServerLevel) event.getLevel();
        FactionManager manager = FactionSavedData.get(level.getServer()).getManager();
        List<BlockPos> toRemove = new ArrayList<>();
        for (BlockPos pos : event.getAffectedBlocks()) {
            ChunkPos chunkPos = new ChunkPos(pos);
            Claim claim = manager.getClaim(chunkPos);
            if (claim == null) continue;
            Faction owner = manager.getFaction(claim.getClaimedBy());
            if (owner.getType() != FactionType.PLAYER) {
                toRemove.add(pos);

            }
        }
        event.getAffectedBlocks().removeAll(toRemove);
    }

}
