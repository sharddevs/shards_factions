// ===========================================================================
// Faction.java  —  the blueprint for a single faction.
//
// This file is annotated for learning. Read it line by line against
// JAVA_CRASH_COURSE.md — each annotation names the section ([§N]) that
// explains what that line is doing.
//
// This is a PLAIN Java class: no Minecraft API, no persistence. It just
// describes what a faction IS and what it can DO. Saving it to disk is the
// next file's job (SavedData).
//
// LAYOUT — methods are grouped by the SUBSYSTEM they belong to, not by
// "getter vs setter". A method lives with the data it operates on:
//   GETTERS        — simple identity reads (name, owner, id, type, members).
//   MEMBER METHODS — everything that touches the members map.
//   POWER METHODS  — everything that touches the power field.
//   BUDGET METHODS — everything that touches the claim budget.
// ===========================================================================

// [§12] package — must match this file's folder path:
// src/main/java/com/sharddevs/shards_factions/
package com.sharddevs.shards_factions;

// [§12] imports — pull in classes that live outside this file's package.
import java.util.ArrayList;   // a concrete kind of List we can create with 'new'
import java.util.List;        // [§9] the List type itself
import java.util.UUID;        // [§3] a player's permanent unique id
import java.util.Map;         // [§10] the Map type itself
import java.util.HashMap;     // a concrete kind of Map we can create with 'new'

/**
 * [§13] Javadoc comment describing the class below.
 *
 * A Faction is one faction in the game. The mod will hold many Faction
 * objects — one per faction that exists — all stamped from this one blueprint.
 *
 * Design reference: shards_factions_design.md §6.
 */
// [§1][§4] 'public class' — Door 1: other files may see and use this type.
public class Faction {

    // -----------------------------------------------------------------------
    // FIELDS — [§2] the data each Faction instance holds.
    // [§4] all 'private' — Door 2: only code inside this class may touch them.
    // -----------------------------------------------------------------------

    // [§3] String = text. [§11] final = set once in the constructor, never
    // re-pointed afterward. A faction's name does not change for its lifetime.
    private final String name;

    // [§3] UUID = the owner's permanent player id. NOT final — ownership can
    // transfer to another member via /f promote owner, which reassigns this.
    private final UUID owner;

    // [§10] Map<UUID, FactionRole> — each member id mapped to their role.
    // [§11] final fixes the slot; the map's CONTENTS still change as members
    // join, leave, or have their role changed.
    private final Map<UUID, FactionRole> members;

    // [§3] int — the faction's live power pool (design §6). NOT final: power
    // changes constantly (drops on death, regens on tick).
    private int power;

    // [§3][§11] the faction's permanent unique id, generated at creation.
    private final UUID id;

    // [§11] the faction's type (PLAYER / SAFEZONE / WARZONE). final — a
    // faction never changes category for its lifetime.
    private final FactionType type;

    // [§3] int — the "bonus" budget term (design §6). A spare capacity source
    // for future features (events, upgrades). 0 for now. NOT final — a future
    // feature could change it at runtime.
    private final int bonusBudget;

    // [§3] int — how many chunks this faction currently owns. Goes UP on
    // claim, DOWN on unclaim or losing a chunk to an enemy overclaim. NOT
    // final. With baseBudget + bonusBudget, this is the third budget term.
    private int usedClaims;

    // -----------------------------------------------------------------------
    // CONSTRUCTOR — [§6] runs once, when a new Faction is created with 'new'.
    // -----------------------------------------------------------------------

    /**
     * Creates a new faction.
     *
     * @param name  the faction's name
     * @param owner the UUID of the player who owns it
     * @param type  the faction's type (PLAYER / SAFEZONE / WARZONE)
     *
     * The owner is automatically added to the members map with role OWNER —
     * a faction always contains its owner (design addendum §13.7, §16.3).
     */
    public Faction(String name, UUID owner, FactionType type) {
        // [§7] 'this.name' = this object's FIELD; 'name' = the parameter.
        this.name = name;
        this.owner = owner;

        // [§8] new empty map, then [§10] put() inserts the owner as the first
        // member with role OWNER — a faction always has its owner.
        this.members = new HashMap<>();
        this.members.put(owner, FactionRole.OWNER);

        // Starting power. 10 is a placeholder — real value is configurable
        // (design §12), wired up later.
        this.power = 10;

        // [§8] every faction generates its own permanent id at creation.
        this.id = UUID.randomUUID();
        this.type = type;

        // Budget terms both start empty (design §6). Set explicitly even
        // though int defaults to 0 — keeps every field's start value in one
        // visible place.
        this.bonusBudget = 0;
        this.usedClaims = 0;
    }
    public Faction(UUID id, String name, UUID owner, FactionType type, int power, int bonusBudget, int usedClaims) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.type = type;
        this.power = power;
        this.bonusBudget = bonusBudget;
        this.usedClaims = usedClaims;

        this.members = new HashMap<>();
    }
    // -----------------------------------------------------------------------
    // GETTERS — [§5] simple identity reads. These hand a private field
    // straight back. Subsystem-specific reads live in their own sections
    // below (getPower with POWER, getUsedClaims with BUDGET, etc.).
    // -----------------------------------------------------------------------

    public String getName() {
        return this.name;
    }

    public UUID getOwner() {
        return this.owner;
    }

    public UUID getId() {
        return this.id;
    }

    public FactionType getType() {
        return this.type;
    }

    // [§10] returns a NEW List of all member UUIDs (the map's keys). A fresh
    // list is built so callers can read members without being able to modify
    // the faction's real members map.
    public List<UUID> getMembers() {
        return new ArrayList<>(this.members.keySet());
    }

    // -----------------------------------------------------------------------
    // MEMBER METHODS — [§5][§10] controlled changes to the members map.
    // -----------------------------------------------------------------------

    /**
     * Adds a player to this faction with the base role MEMBER.
     * Return type [§5] void — it does something but hands nothing back.
     */
    public void addMember(UUID playerId) {
        // [§10] only add if not already a key, so a player can't be listed
        // twice. 'containsKey' returns true/false; '!' flips it.
        if (!this.members.containsKey(playerId)) {
            this.members.put(playerId, FactionRole.MEMBER);
        }
    }
    public void addMemberWithRole(UUID playerId, FactionRole role) {
        this.members.put(playerId, role);
    }
    public boolean isMember(UUID playerId) {
        return this.members.containsKey(playerId);
    }
    /**
     * Removes a player from this faction.
     *
     * NOTE: does NOT guard the owner — passing the owner here would leave the
     * faction ownerless. The owner guard is deferred to the command layer
     * (/f leave / /f disband). See design addendum §13.7.
     */
    public void removeMember(UUID playerId) {
        // [§10] remove does nothing if the id isn't a key — safe to call
        // unconditionally.
        this.members.remove(playerId);
    }

    /**
     * How many members the faction has.
     * [§10] members.size() is a whole number, so the return type is int.
     */
    public int getMemberCount() {
        return this.members.size();
    }
    public FactionRole getRole(UUID playerId) {
        return this.members.get(playerId);
    }

    // -----------------------------------------------------------------------
    // POWER METHODS — [§5] controlled access to the power field.
    // Because 'power' is private, these methods are the ONLY way outside code
    // touches it — so the design's power RULES get enforced in one place.
    // -----------------------------------------------------------------------

    /**
     * Reads the current power value.
     */
    public int getPower() {
        return this.power;
    }

    /**
     * Increases power by the given amount (used by the regen tick later).
     */
    public void addPower(int amount) {
        this.power = this.power + amount;
    }

    /**
     * Decreases power by the given amount (used when a member dies later),
     * but never below zero.
     */
    public void removePower(int amount) {
        this.power = this.power - amount;

        // Enforce the floor. This 'if' is exactly WHY power is private and
        // changes go through a method: the rule "power can't go negative"
        // lives in one place and cannot be bypassed from outside.
        if (this.power < 0) {
            this.power = 0;
        }
    }

    // -----------------------------------------------------------------------
    // BUDGET METHODS — [§5] the claim-budget system (design §6).
    //
    // Three terms decide whether a faction may claim another chunk:
    //   baseBudget  — DERIVED (member count * 10). A method, never a field,
    //                 so it's always current and can't fall out of sync.
    //   bonusBudget — STORED field. Spare capacity. 0 for now.
    //   usedClaims  — STORED field. Chunks currently owned.
    //
    // available = baseBudget + bonusBudget - usedClaims
    // The /f claim check is "available > 0" — enforced by the CALLER
    // (FactionManager.claimChunk), not here. These methods are dumb
    // mechanisms; the rule lives one layer up.
    // -----------------------------------------------------------------------

    /**
     * Base budget: member count * 10 (design §6). Derived — recomputed every
     * call from the live member count.
     */
    public int getBaseBudget() {
        return getMemberCount() * 10;
    }

    /**
     * Reads the bonusBudget FIELD. (Returns the field — a getter hands back
     * the field, it does not call itself.)
     */
    public int getBonusBudget() {
        return this.bonusBudget;
    }

    /**
     * Reads the usedClaims FIELD.
     */
    public int getUsedClaims() {
        return this.usedClaims;
    }

    /**
     * The available budget — the headroom /f claim checks (design §6).
     * Calls getBaseBudget() rather than recomputing member*10, so the "base"
     * formula lives in exactly one place. No stored field: derived, returned
     * on the spot.
     */
    public int getAvailableBudget() {
        return getBaseBudget() + this.bonusBudget - this.usedClaims;
    }

    /**
     * Raises usedClaims by one — called when a chunk is claimed (or gained via
     * overclaim). No guard: going up is always valid. The budget CHECK that
     * decides whether this should be called lives in the caller.
     */
    public void incrementUsedClaims() {
        this.usedClaims = this.usedClaims + 1;
    }

    /**
     * Lowers usedClaims by one — called on unclaim, or on losing a chunk to an
     * enemy overclaim. Floored at zero: usedClaims is a count and cannot
     * logically be negative. Same guard pattern as removePower().
     */
    public void decrementUsedClaims() {
        if (this.usedClaims > 0) {
            this.usedClaims = this.usedClaims - 1;
        }
    }
}