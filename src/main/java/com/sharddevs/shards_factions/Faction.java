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
    private UUID owner;

    // [§10] Map<UUID, FactionRole> — each member id mapped to their role.
    // [§11] final fixes the slot; the map's CONTENTS still change as members
    // join, leave, or have their role changed.
    private final Map<UUID, FactionRole> members;

    // [§3] int = a whole number — the faction's live power pool (design §6).
    // NOT final: power changes constantly (drops on death, regens on tick),
    // so its slot must be reassignable.
    private int power;

    // [§3][§11] the faction's permanent unique id, generated at creation.
    private final UUID id;

    // [§11] the faction's type (PLAYER / SAFEZONE / WARZONE). final — a
    // faction never changes category for its lifetime.
    private final FactionType type;

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
    // [§6] same name as the class, no return type.
    // [§5] 'name', 'owner', 'type' in the parentheses are PARAMETERS — values
    // the caller must supply.
    public Faction(String name, UUID owner, FactionType type) {
        // [§7] 'this.name' = this object's name FIELD; 'name' = the parameter.
        // The line copies the incoming value into this object's slot.
        this.name = name;
        this.owner = owner;

        // [§8] 'new HashMap<>()' creates a fresh, empty map and the members
        // slot is set to point at it. Then [§10] put() inserts the owner as
        // the first member with role OWNER — a faction always has its owner.
        this.members = new HashMap<>();
        this.members.put(owner, FactionRole.OWNER);

        // Starting power. 10 is a placeholder default — the real starting
        // value is configurable (design §12) and will be wired up later.
        this.power = 10;

        // [§8] every faction generates its own permanent id at creation.
        this.id = UUID.randomUUID();
        this.type = type;
    }

    // -----------------------------------------------------------------------
    // GETTERS — [§5] methods that hand a private field's value back out.
    // Outside code can't read the fields directly (they're private), so it
    // reads them through these.
    // -----------------------------------------------------------------------

    // [§5] return type String; no parameters; body hands back the name.
    public String getName() {
        return this.name;
    }

    public UUID getOwner() {
        return this.owner;
    }

    // [§5] returns the int power value.
    public int getPower() {
        return this.power;
    }

    // [§10] returns a fresh List of all member UUIDs (the map's keys). A NEW
    // list is built so callers can read the members without being able to
    // modify the faction's real members map. getMemberCount() gives the
    // count — the basis for the design's base budget (members * 10).
    public List<UUID> getMembers() {
        return new ArrayList<>(this.members.keySet());
    }

    public UUID getId() {
        return this.id;
    }

    public FactionType getType() {
        return this.type;
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
        // twice. 'containsKey' returns true/false; '!' flips it, so this reads
        // "if the map does NOT already contain this player".
        if (!this.members.containsKey(playerId)) {
            this.members.put(playerId, FactionRole.MEMBER);
        }
    }

    /**
     * Removes a player from this faction.
     *
     * NOTE: this does NOT currently guard the owner — passing the owner here
     * would leave the faction ownerless. The owner guard is deferred to the
     * command layer (/f leave / /f disband). See design addendum §13.7.
     */
    public void removeMember(UUID playerId) {
        // [§10] remove does nothing if the id isn't a key, so this is
        // safe to call unconditionally.
        this.members.remove(playerId);
    }

    /**
     * How many members the faction has.
     * [§10] members.size() is a whole number, so the return type is int.
     */
    public int getMemberCount() {
        return this.members.size();
    }

    // -----------------------------------------------------------------------
    // POWER METHODS — [§5] controlled changes to the power field.
    // Because 'power' is private, THIS is the only way outside code can change
    // it — and these methods are where the design's power RULES get enforced.
    // -----------------------------------------------------------------------

    /**
     * Increases power by the given amount (used by the regen tick later).
     */
    public void addPower(int amount) {
        // [§7] read this object's current power, add amount, store it back.
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
    public int getBaseBudget(){
        return getMemberCount() * 10;
    }

}