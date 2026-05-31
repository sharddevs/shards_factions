package com.sharddevs.shards_factions;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import com.sharddevs.shards_factions.obelisk.FactionObeliskBlock;

// ===========================================================================
// ProtectionEvents — claim protection handlers (game bus, server-side).
//
// Three handlers enforce who may break/place/explode where (design §6, §13,
// Addenda 1 §13.5–13.6, 7). Each follows the bail-out skeleton: cheap-first
// returns, the cancel/filter is the verdict if nothing bailed.
//
// Two protection regimes:
//   SYSTEM zones (SAFEZONE/WARZONE) — permanently protected by TYPE (§13.4),
//       not by the obelisk flag. No member/bypass exemption. Checked first.
//   PLAYER claims — protected only while their obelisk stands; members and
//       bypassing admins are exempt; the obelisk block itself is breakable.
//
// NOTE: owner lookups (manager.getFaction(...)) are unguarded for null, to
// match the original. Claims and factions stay in sync (disband sweeps
// claims), so it hasn't NPE'd; add a null guard here if that ever changes.
// ===========================================================================
@EventBusSubscriber(modid = ShardsFactions.MOD_ID)
public class ProtectionEvents {

    // -----------------------------------------------------------------------
    // BREAK
    // -----------------------------------------------------------------------
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;

        ChunkPos chunkPos = new ChunkPos(event.getPos());
        ServerLevel level = (ServerLevel) event.getLevel();
        FactionManager manager = FactionSavedData.get(level.getServer()).getManager();

        Claim claim = manager.getClaim(chunkPos);
        if (claim == null) return;                       // wilderness — allowed
        Faction owner = manager.getFaction(claim.getClaimedBy());

        // SYSTEM zone — permanently protected, no break by ANYONE (§13.4).
        // Keyed off type, before the obelisk-flag / member / bypass checks.
        if (owner.getType() != FactionType.PLAYER) {
            event.setCanceled(true);
            event.getPlayer().sendSystemMessage(
                    Component.literal("Cannot break! " + owner.getName() + " is a protected zone."));
            return;
        }

        // PLAYER land below.
        if (!claim.isProtected()) return;                                  // obelisk down — allowed
        if (event.getState().getBlock() instanceof FactionObeliskBlock) return; // obelisk itself is breakable (§7)
        Player player = event.getPlayer();
        Faction breakerFaction = manager.getFactionByMember(player.getUUID());
        if (breakerFaction != null && breakerFaction.getId().equals(claim.getClaimedBy())) return; // own member
        if (manager.isBypassing(player.getUUID())) return;                 // admin bypass

        event.setCanceled(true);
        player.sendSystemMessage(Component.literal("Cannot break! " + owner.getName()
                + "'s land is protected by their obelisk!"));
    }

    // -----------------------------------------------------------------------
    // PLACE — only PLAYER placement is gated (§45.2); non-player placers
    // (pistons, missiles delivering blocks) fall through.
    // -----------------------------------------------------------------------
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent place) {
        if (place.getLevel().isClientSide()) return;
        if (!(place.getEntity() instanceof Player player)) return;

        ChunkPos chunkPos = new ChunkPos(place.getPos());
        ServerLevel level = (ServerLevel) place.getLevel();
        FactionManager manager = FactionSavedData.get(level.getServer()).getManager();

        Claim claim = manager.getClaim(chunkPos);
        if (claim == null) return;
        Faction owner = manager.getFaction(claim.getClaimedBy());

        // SYSTEM zone — permanently protected, no place by anyone (§13.4).
        if (owner.getType() != FactionType.PLAYER) {
            place.setCanceled(true);
            player.sendSystemMessage(
                    Component.literal("Cannot place in " + owner.getName() + " — it is a protected zone."));
            return;
        }

        // PLAYER land below.
        if (!claim.isProtected()) return;
        Faction placerFaction = manager.getFactionByMember(player.getUUID());
        if (placerFaction != null && placerFaction.getId().equals(claim.getClaimedBy())) return; // own member
        if (manager.isBypassing(player.getUUID())) return;                 // admin bypass

        place.setCanceled(true);
        player.sendSystemMessage(Component.literal("Cannot place in " + owner.getName()
                + "'s land while it is protected by their obelisk!"));
    }

    // -----------------------------------------------------------------------
    // EXPLOSION — per-block FILTER, not whole-event cancel (Addendum 7 §46).
    // System-zone blocks are removed from the affected list (explosion-immune);
    // PLAYER-land blocks stay (explosives are the intended siege path, §6).
    // Bypass does NOT apply here — system-zone explosion immunity is absolute.
    // -----------------------------------------------------------------------
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;

        ServerLevel level = (ServerLevel) event.getLevel();
        FactionManager manager = FactionSavedData.get(level.getServer()).getManager();

        // build a remove-list during iteration, then removeAll once after —
        // in-place removal mid-iteration would throw.
        List<BlockPos> toRemove = new ArrayList<>();
        for (BlockPos pos : event.getAffectedBlocks()) {
            ChunkPos chunkPos = new ChunkPos(pos);
            Claim claim = manager.getClaim(chunkPos);
            if (claim == null) continue;                 // wilderness — leave it in
            Faction owner = manager.getFaction(claim.getClaimedBy());
            if (owner.getType() != FactionType.PLAYER) {
                toRemove.add(pos);                       // system zone — protect it
            }
        }
        event.getAffectedBlocks().removeAll(toRemove);
    }
}