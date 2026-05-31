package com.sharddevs.shards_factions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// ===========================================================================
// ChunkBorderTracker — detects chunk-border crossings (game bus, server-side).
//
// Unlike commands (reactive, run-once), this is CONTINUOUS: it hooks
// PlayerTickEvent.Post and runs every tick for every online player. Its one
// job is to notice when a player moves from one chunk to another and hand
// that crossing to onChunkCrossed — the shared seam for the two consumers:
//   - action-bar faction label (Addendum 2 §19.4)
//   - /f autoclaim (§18)
//
// Wired like ShardsFactions: instance @SubscribeEvent methods, instance
// registered to NeoForge.EVENT_BUS in the mod constructor.
// ===========================================================================
public class ChunkBorderTracker {

    // -----------------------------------------------------------------------
    // PER-PLAYER STATE — all ephemeral, all cleared on logout (or a stale
    // lastChunk would fake a crossing on rejoin, and the maps would leak).
    //   autoclaimEnabled — players with /f autoclaim on (Set: membership only)
    //   lastChunk        — each player's chunk as of last tick (crossing memory)
    //   lastLabel        — last action-bar label shown, so we only redraw on change
    // -----------------------------------------------------------------------
    private final Set<UUID> autoclaimEnabled = new HashSet<>();
    private final Map<UUID, ChunkPos> lastChunk = new HashMap<>();
    private final Map<UUID, String> lastLabel = new HashMap<>();

    // -----------------------------------------------------------------------
    // AUTOCLAIM STATE
    // -----------------------------------------------------------------------
    public boolean isAutoclaimEnabled(UUID id) {
        return this.autoclaimEnabled.contains(id);
    }

    // Flips autoclaim; returns the NEW state (true = now on).
    public boolean toggleAutoclaim(UUID id) {
        if (this.autoclaimEnabled.contains(id)) {
            this.autoclaimEnabled.remove(id);
            return false;
        }
        this.autoclaimEnabled.add(id);
        return true;
    }

    // Force off — used by onChunkCrossed's stop cases.
    public void disableAutoclaim(UUID id) {
        this.autoclaimEnabled.remove(id);
    }

    // -----------------------------------------------------------------------
    // TICK HANDLER — runs ~20x/sec per player; must stay cheap (a lookup, an
    // equals, maybe a put). .Post fires after the player's tick, so position
    // is up to date.
    // -----------------------------------------------------------------------
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        // server-side only.
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID id = player.getUUID();
        ChunkPos current = player.chunkPosition();
        ChunkPos previous = this.lastChunk.get(id);   // null = first sighting

        // first sighting — record and wait for next tick.
        if (previous == null) {
            this.lastChunk.put(id, current);
            return;
        }
        // same chunk — common case, cheap path. (ChunkPos: .equals, not ==.)
        if (previous.equals(current)) return;

        // crossing — update memory FIRST so it can't re-fire, then hand off.
        this.lastChunk.put(id, current);
        onChunkCrossed(player, previous, current);
    }

    // -----------------------------------------------------------------------
    // LOGOUT HANDLER — clears all per-player state.
    // -----------------------------------------------------------------------
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        this.lastChunk.remove(id);
        this.autoclaimEnabled.remove(id);
        this.lastLabel.remove(id);
    }

    // -----------------------------------------------------------------------
    // THE SEAM — two stages on each crossing:
    //   STAGE 1 — action-bar label (§19.4): every player, but only redrawn
    //             when the label CHANGED.
    //   STAGE 2 — autoclaim (§18): only autoclaiming players; claim, or stop
    //             on a stop case.
    // 'from' is unused for now; both stages use only 'to'.
    // -----------------------------------------------------------------------
    private void onChunkCrossed(ServerPlayer player, ChunkPos from, ChunkPos to) {
        UUID id = player.getUUID();
        FactionManager manager = FactionSavedData.get(player.server).getManager();

        // === STAGE 1: action-bar label ===
        Claim crossedInto = manager.getClaim(to);
        String label;
        if (crossedInto == null) {
            label = "Wilderness";                       // a LABEL, not a Faction
        } else {
            Faction owner = manager.getFaction(crossedInto.getClaimedBy());
            label = (owner != null) ? owner.getName() : "Wilderness";   // defensive
        }

        // Redraw only when the label changed (walking 20 wilderness chunks
        // shows "Wilderness" once). Order matters: 'label' is never null,
        // 'previousLabel' CAN be null on first crossing — calling equals on
        // the non-null side is NPE-safe. displayClientMessage(..., true) =
        // action bar (the 'true'), not chat.
        String previousLabel = this.lastLabel.get(id);
        if (!label.equals(previousLabel)) {
            player.displayClientMessage(Component.literal(label), true);
            this.lastLabel.put(id, label);
        }

        // === STAGE 2: autoclaim ===
        if (!this.autoclaimEnabled.contains(id)) return;   // common case — bail

        // may have left/disbanded their faction while autoclaim was on.
        Faction faction = manager.getFactionByMember(id);
        if (faction == null) {
            disableAutoclaim(id);
            return;
        }

        Claim existing = manager.getClaim(to);
        if (existing != null) {
            if (existing.getClaimedBy().equals(faction.getId())) {
                // own land — skip, stay ON.
                player.sendSystemMessage(Component.literal("Already your faction's land — skipping."));
                return;
            }
            // enemy land — stop.
            disableAutoclaim(id);
            player.sendSystemMessage(Component.literal("You entered another faction's territory. Autoclaim OFF."));
            return;
        }

        // unclaimed — attempt the claim.
        ClaimResult result = manager.claimChunk(to, faction);
        if (result == ClaimResult.NO_BUDGET) {
            disableAutoclaim(id);
            player.sendSystemMessage(Component.literal("Out of claim budget. Autoclaim OFF."));
            return;
        }
        if (result == ClaimResult.SUCCESS) {
            FactionSavedData.get(player.server).setDirty();   // claim is persisted
            player.sendSystemMessage(Component.literal("Chunk claimed for " + faction.getName() + "."));
        }
        // ALREADY_CLAIMED can't occur here — getClaim(to) was null above.
    }
}