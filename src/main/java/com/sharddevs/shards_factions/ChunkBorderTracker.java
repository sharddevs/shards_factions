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
// §19.4) is the CALLER's job. This class only answers "did a border get
// crossed, and into which chunk?".
//
// Keeping detection separate from reaction is deliberate: /f autoclaim AND the
// §19.4 chunk-enter display both need the SAME detector. Build it once here,
// both features plug into the onChunkCrossed seam below.
//
// HOW IT IS WIRED
// This class follows the same event pattern as ShardsFactions: instance
// methods marked @SubscribeEvent, and an instance of this class registered to
// NeoForge.EVENT_BUS from the mod constructor. Both events used here
// (PlayerTickEvent.Post, PlayerLoggedOutEvent) fire on the GAME bus — the same
// bus RegisterCommandsEvent uses.
// ===========================================================================

package com.sharddevs.shards_factions;

// [§3] UUID — a player's permanent id. Used as the map key.
import java.util.UUID;
// [§10] Map / HashMap — the per-player last-chunk memory.
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
// Minecraft / NeoForge types.
import net.minecraft.world.level.ChunkPos;          // an (x, z) chunk coordinate
import net.minecraft.server.level.ServerPlayer;     // a player on the server side
import net.neoforged.bus.api.SubscribeEvent;        // marks a method as an event handler
import net.neoforged.neoforge.event.tick.PlayerTickEvent;       // fires every tick, per player
import net.neoforged.neoforge.event.entity.player.PlayerEvent;  // PlayerLoggedOutEvent lives here

/**
 * [§13] Detects chunk-border crossings and reports them to onChunkCrossed.
 *
 * Design reference: Addendum 2 §19.4 (chunk-enter display) and §18
 * (/f autoclaim) — both consumers of this detector.
 */
public class ChunkBorderTracker {

    // -----------------------------------------------------------------------
    // FIELD — the per-player last-chunk memory.
    //
    // [§10] Map<UUID, ChunkPos> — for each online player, the chunk they were
    // in as of the LAST tick we checked. To know a player MOVED, we must
    // remember where they were; this map is that memory.
    //
    // [§11] final fixes the slot — the map object never changes. Its CONTENTS
    // change constantly: an entry is updated every tick, and removed on logout.
    //
    // LIFECYCLE — this map is mutable state we own, so we own its cleanup:
    //   - an entry is CREATED the first tick we see a player (get returns null).
    //   - an entry is UPDATED every tick afterward to the player's new chunk.
    //   - an entry is REMOVED when the player logs out (see onPlayerLogout).
    // Skipping the logout removal would be a slow leak — and worse, a stale
    // entry would make a rejoining player look like they "crossed" from their
    // old chunk to their spawn chunk on their first tick back.
    // -----------------------------------------------------------------------
    // Players with /f autoclaim enabled. A Set, not a Map — there is no value
    // to store, only membership: "is this player in autoclaim mode?".
    // Same lifecycle concern as lastChunk — cleared on logout below.
    private final Set<UUID> autoclaimEnabled = new HashSet<>();
    private final Map<UUID, ChunkPos> lastChunk = new HashMap<>();

    // -----------------------------------------------------------------------
    // AUTOCLAIM STATE — controlled access to the autoclaimEnabled set.
    // The command layer (/f autoclaim) and onChunkCrossed are the only
    // callers; the set itself stays private.
    // -----------------------------------------------------------------------
    public boolean isAutoclaimEnabled(UUID id) {
        return this.autoclaimEnabled.contains(id);
    }

    /** Flips autoclaim for a player. Returns the NEW state (true = now on). */
    public boolean toggleAutoclaim(UUID id) {
        if (this.autoclaimEnabled.contains(id)) {
            this.autoclaimEnabled.remove(id);
            return false;
        } else {
            this.autoclaimEnabled.add(id);
            return true;
        }
    }

    /** Force autoclaim off — used by onChunkCrossed's stop cases. */
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
    // lookup, an equals, maybe a put. No loops, no allocation in the common
    // case. That is why detection is all this does; heavy work belongs in the
    // caller, and only on the rare ticks where a crossing actually happened.
    // -----------------------------------------------------------------------
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {

        // The event hands us a Player, but it may be the CLIENT-side player
        // object. Faction logic is server-side only. 'instanceof' both checks
        // the type AND, on success, gives us a correctly-typed variable
        // 'player' to use — so if this is not a ServerPlayer, we return.
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID id = player.getUUID();

        // The chunk the player is in RIGHT NOW, this tick.
        ChunkPos current = player.chunkPosition();

        // The chunk we recorded for them LAST tick. [§10] map.get returns null
        // if this player has no entry yet — i.e. this is the first tick we
        // have seen them (they just logged in, or the server just started).
        ChunkPos previous = this.lastChunk.get(id);

        // --- CASE 1: first sighting (previous == null) ---
        // We have no "before" to compare against, so we cannot call this a
        // crossing. Just record where they are and wait for the next tick.
        if (previous == null) {
            this.lastChunk.put(id, current);
            return;
        }

        // --- CASE 2: same chunk as last tick ---
        // [§10] ChunkPos is an object — compare with .equals, NOT ==. They
        // have not moved across a border; nothing to do. (This is the common
        // case — most ticks, a player has not changed chunk — so it must be
        // the cheap path: one equals, then return.)
        if (previous.equals(current)) {
            return;
        }

        // --- CASE 3: a border was crossed ---
        // current differs from previous. Update the memory FIRST, so that
        // whatever onChunkCrossed does, the map already reflects the new
        // position — no chance of a re-fire for the same crossing.
        this.lastChunk.put(id, current);

        // Announce the crossing. This is the SEAM: onChunkCrossed is where
        // /f autoclaim and the §19.4 display will hook in. Detection ends
        // here; reaction is that method's concern.
        onChunkCrossed(player, previous, current);
    }

    // -----------------------------------------------------------------------
    // THE LOGOUT HANDLER — cleans up the map when a player leaves.
    //
    // PlayerLoggedOutEvent fires once, when a player disconnects. Removing
    // their entry here is the lifecycle cleanup the field comment describes:
    // it stops a slow leak, and prevents a stale entry from faking a crossing
    // if that player later rejoins.
    // -----------------------------------------------------------------------
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // [§10] remove does nothing if the key is absent — safe to call
        // unconditionally, even for a player who never got a map entry.
        this.lastChunk.remove(event.getEntity().getUUID());
    }

    // -----------------------------------------------------------------------
    // THE SEAM — what to do when a crossing is detected.
    //
    // Right now this is intentionally EMPTY. The detector above is complete
    // and correct on its own; this method is the single, obvious place the
    // next features plug into:
    //   - /f autoclaim: if the player has autoclaim enabled, try to claim
    //     'to' for their faction (and switch autoclaim off when budget runs
    //     out — Addendum 2 §18).
    //   - §19.4 chunk-enter display: show the owning faction's name (or
    //     "Wilderness") on the action bar.
    //
    // 'from' and 'to' are passed in case a consumer wants both — e.g. "you
    // left faction X, you entered faction Y". Most will only need 'to'.
    //
    // @param player the player who crossed a chunk border
    // @param from   the chunk they were in last tick
    // @param to     the chunk they are in now
    // -----------------------------------------------------------------------
    private void onChunkCrossed(ServerPlayer player, ChunkPos from, ChunkPos to) {
        // Empty for now — filled when /f autoclaim is built (next slice).
    }
}