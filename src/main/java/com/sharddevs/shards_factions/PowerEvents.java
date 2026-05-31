package com.sharddevs.shards_factions;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

@EventBusSubscriber
public class PowerEvents {
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;        // victim is a player
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return; // killed BY a player — suicide/fall/lava bail here
        if (victim.getUUID().equals(killer.getUUID())) return;                  // self-kill guard

        FactionManager manager = FactionSavedData.get(victim.server).getManager();
        Faction victimFaction = manager.getFactionByMember(victim.getUUID());
        Faction killerFaction = manager.getFactionByMember(killer.getUUID());

        if (victimFaction != null && victimFaction == killerFaction) return;    // same-faction: no farm, skip both

        if (victimFaction != null) victimFaction.decrementBonusBudget();
        if (killerFaction != null) killerFaction.incrementBonusBudget();

        FactionSavedData.get(victim.server).setDirty();
    }
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        // server-side only
        if (event.getEntity().level().isClientSide()) return;

        // victim must be a player — PvP is player-vs-player (§13.2)
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;

        // attacker must be a player — a mob/fall/lava killing you in a safezone
        // is NOT PvP and is allowed (full immunity is v2). This is the line that
        // scopes us to PvP-only.
        if (!(event.getSource().getEntity() instanceof ServerPlayer)) return;

        // where is the VICTIM standing? PvP protection keys off the victim's
        // location — you're safe because YOU are in the safezone.
        ServerLevel level = (ServerLevel) event.getEntity().level();
        FactionManager manager = FactionSavedData.get(level.getServer()).getManager();
        ChunkPos chunk = new ChunkPos(event.getEntity().blockPosition());
        Claim claim = manager.getClaim(chunk);
        if (claim == null) return;                       // wilderness — PvP allowed

        Faction owner = manager.getFaction(claim.getClaimedBy());
        // SAFEZONE cancels PvP; WARZONE explicitly does NOT (§13.3 — PvP enabled
        // is warzone's whole point); PLAYER land — PvP allowed (siege gameplay).
        if (owner.getType() == FactionType.SAFEZONE) {
            event.setCanceled(true);
        }
    }
}