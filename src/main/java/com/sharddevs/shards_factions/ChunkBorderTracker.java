// ===========================================================================
// ChunkBorderTracker.java  —  detects when a player crosses a chunk border.
//
// Annotated for learning. Read against JAVA_CRASH_COURSE.md.
//
// WHY THIS CLASS EXISTS
// Every command you have written so far is REACTIVE: a player types something,
// your code runs once, returns, and goes inert. This class is different — it
// is CONTINUOUS. It hooks the game loop and runs every tick (~20x/second) for
// every online player, whether or not anything happened.
//
// Its ONE job: notice the moment a player moves from one chunk into another,
// and announce that crossing. It does NOT decide what happens on a crossing —
// claiming a chunk (/f autoclaim) or showing a faction label (Addendum 2
// section 19.4) is the CALLER's job.
//
// HOW IT IS WIRED
// Same event pattern as ShardsFactions: instance methods marked
// @SubscribeEvent, instance registered to NeoForge.EVENT_BUS from the mod
// constructor. PlayerTickEvent.Post and PlayerLoggedOutEvent fire on the
// GAME bus — the same bus RegisterCommandsEvent uses.
// ===========================================================================

package com.sharddevs.shards_factions;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.network.chat.Component;

/**
 * [§13] Detects chunk-border crossings and reports them to onChunkCrossed.
 *
 * Design reference: Addendum 2 section 19.4 (chunk-enter display) and
 * section 18 (/f autoclaim) — both consumers of this detector.
 */
public class ChunkBorderTracker {

    // -----------------------------------------------------------------------
    // FIELDS — per-player tracking state.
    //
    // lastChunk: Map<UUID, ChunkPos> — for each online player, the chunk they
    // were in as of the LAST tick. To know a player MOVED, we must remember
    // where they were; this map is that memory.
    //
    // autoclaimEnabled: the set of players with /f autoclaim on. A Set, not a
    // Map — there is no value to store, only membership.
    //
    // lastLabel: Map<UUID, String> — the action-bar label last shown to each
    // player ("Wilderness" or a faction name). Used so the action bar only
    // redraws when the label actually CHANGES, not on every chunk crossing.
    //
    // LIFECYCLE — all three are mutable state we own, so we own their cleanup:
    // entries are created/updated as a player plays, REMOVED on logout (see
    // onPlayerLogout). Skipping logout cleanup leaks, and a stale lastChunk
    // entry would fake a crossing if the player rejoins.
    // -----------------------------------------------------------------------
    private final Set<UUID> autoclaimEnabled = new HashSet<>();
    private final Map<UUID, ChunkPos> lastChunk = new HashMap<>();
    private final Map<UUID, String> lastLabel = new HashMap<>();

    // -----------------------------------------------------------------------
    // AUTOCLAIM STATE — controlled access to the autoclaimEnabled set.
    // -----------------------------------------------------------------------
    public boolean isAutoclaimEnabled(UUID id) {
        return this.autoclaimEnabled.contains(id);
    }

    /**
     * Flips autoclaim for a player. Returns the NEW state (true = now on).
     */
    public boolean toggleAutoclaim(UUID id) {
        if (this.autoclaimEnabled.contains(id)) {
            this.autoclaimEnabled.remove(id);
            return false;
        } else {
            this.autoclaimEnabled.add(id);
            return true;
        }
    }

    /**
     * Force autoclaim off — used by onChunkCrossed's stop cases.
     */
    public void disableAutoclaim(UUID id) {
        this.autoclaimEnabled.remove(id);
    }

    // -----------------------------------------------------------------------
    // THE TICK HANDLER — runs every tick, for every player.
    //
    // PlayerTickEvent.Post fires once per player per tick (~20x/second). The
    // ".Post" variant fires AFTER the player's tick logic, so the player's
    // position is fully up to date when we read it.
    //
    // PERFORMANCE NOTE: this method runs a LOT. It must stay cheap — a map
    // lookup, an equals, maybe a put. Heavy work belongs in the caller, and
    // only on the rare ticks where a crossing actually happened.
    // -----------------------------------------------------------------------
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {

        // The event hands us a Player, but it may be the CLIENT-side player.
        // Faction logic is server-side only. 'instanceof' checks the type AND,
        // on success, gives us a correctly-typed 'player' to use.
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID id = player.getUUID();

        // The chunk the player is in RIGHT NOW, this tick.
        ChunkPos current = player.chunkPosition();

        // The chunk we recorded for them LAST tick. null if no entry yet —
        // i.e. the first tick we have seen them.
        ChunkPos previous = this.lastChunk.get(id);

        // --- CASE 1: first sighting (previous == null) ---
        // No "before" to compare against — record and wait for next tick.
        if (previous == null) {
            this.lastChunk.put(id, current);
            return;
        }

        // --- CASE 2: same chunk as last tick ---
        // ChunkPos is an object — compare with .equals, NOT ==. No crossing.
        // This is the common case, so it is the cheap path: equals, return.
        if (previous.equals(current)) {
            return;
        }

        // --- CASE 3: a border was crossed ---
        // Update the memory FIRST, so onChunkCrossed cannot re-fire for the
        // same crossing.
        this.lastChunk.put(id, current);

        // Hand off to the seam.
        onChunkCrossed(player, previous, current);
    }

    // -----------------------------------------------------------------------
    // THE LOGOUT HANDLER — cleans up the maps when a player leaves.
    // -----------------------------------------------------------------------
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        this.lastChunk.remove(id);
        this.autoclaimEnabled.remove(id);
        this.lastLabel.remove(id);
    }

    // -----------------------------------------------------------------------
    // THE SEAM — what to do when a crossing is detected.
    //
    // Runs in two stages:
    //   STAGE 1 — action-bar display (section 19.4): runs for EVERY player on
    //             every crossing; shows the owning faction's name, or
    //             "Wilderness" — but only when that label CHANGED.
    //   STAGE 2 — autoclaim (section 18): only for players with autoclaim on;
    //             claims the chunk, or switches autoclaim off on a stop case.
    //
    // 'from' is kept for future use; stages 1 and 2 use only 'to'.
    // -----------------------------------------------------------------------
    private void onChunkCrossed(ServerPlayer player, ChunkPos from, ChunkPos to) {
        UUID id = player.getUUID();

        // === STAGE 1: action-bar display (Addendum 2 section 19.4) ===
        FactionManager manager = FactionSavedData.get(player.server).getManager();

        Claim crossedInto = manager.getClaim(to);
        String label;
        if (crossedInto == null) {
            // Unclaimed. "Wilderness" is a LABEL, not a Faction object.
            label = "Wilderness";
        } else {
            // Claimed: resolve the faction id on the claim to its name.
            Faction owner = manager.getFaction(crossedInto.getClaimedBy());
            // Defensive: a claim with no resolvable faction shouldn't happen
            // (disbandFaction sweeps claims), but don't NPE on .getName().
            label = (owner != null) ? owner.getName() : "Wilderness";
        }

        // Only redraw the action bar when the label actually CHANGED. Walking
        // 20 chunks of Wilderness builds the string "Wilderness" 20 times but
        // displays it once — the first crossing into it.
        //
        // label.equals(previousLabel), not the reverse: 'label' is never null
        // (always at least "Wilderness"), 'previousLabel' CAN be null on the
        // first crossing. Calling equals on the non-null side is NPE-safe;
        // "Wilderness".equals(null) is simply false, so the first label shows.
        //
        // displayClientMessage(component, true) -> action bar (above hotbar).
        // The 'true' is what makes it the action bar and not chat.
        String previousLabel = this.lastLabel.get(id);
        if (!label.equals(previousLabel)) {
            player.displayClientMessage(Component.literal(label), true);
            this.lastLabel.put(id, label);
        }

        // === STAGE 2: autoclaim (Addendum 2 section 18) ===
        // Common case: most players are not autoclaiming. Bail immediately.
        if (!this.autoclaimEnabled.contains(id)) {
            return;
        }

        // Safety: the player may have left/disbanded their faction while
        // autoclaim was still on. No faction -> nothing to claim for.
        Faction faction = manager.getFactionByMember(id);
        if (faction == null) {
            disableAutoclaim(id);
            return;
        }

        // --- Is the chunk they walked into already owned? ---
        Claim existing = manager.getClaim(to);
        if (existing != null) {
            // CASE: own faction's chunk -> skip, message, stay ON.
            if (existing.getClaimedBy().equals(faction.getId())) {
                player.sendSystemMessage(
                        Component.literal("Already your faction's land — skipping."));
                return;
            }
            // CASE: an enemy faction's chunk -> switch OFF, notify.
            disableAutoclaim(id);
            player.sendSystemMessage(
                    Component.literal("You entered another faction's territory. Autoclaim OFF."));
            return;
        }

        // --- Unclaimed: attempt the claim. ---
        ClaimResult result = manager.claimChunk(to, faction);

        if (result == ClaimResult.NO_BUDGET) {
            // CASE: budget exhausted -> switch OFF (Addendum 2 section 18).
            disableAutoclaim(id);
            player.sendSystemMessage(
                    Component.literal("Out of claim budget. Autoclaim OFF."));
            return;
        }

        if (result == ClaimResult.SUCCESS) {
            // A claim is persisted data -> setDirty.
            FactionSavedData.get(player.server).setDirty();
            player.sendSystemMessage(
                    Component.literal("Chunk claimed for " + faction.getName() + "."));
        }
        // ALREADY_CLAIMED can't occur — getClaim(to) was null above.
    }
}