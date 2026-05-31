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
//   BUDGET METHODS — everything that touches the claim budget.
//   INVITE METHODS — everything that touches the invites map.
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
import net.minecraft.core.BlockPos;

// ChatFormatting is a VANILLA Minecraft enum — the 16 colours (RED, AQUA,
// GOLD, ...) plus style codes (BOLD, ITALIC, ...). It is the faction's
// display colour, used by /f map's coloured grid and the action-bar label.
// It lives outside our package, so it must be imported like List or UUID.
import net.minecraft.ChatFormatting;

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

    // [§3][§11] the faction's permanent unique id, generated at creation.
    private final UUID id;

    // [§11] the faction's type (PLAYER / SAFEZONE / WARZONE). final — a
    // faction never changes category for its lifetime.
    private final FactionType type;

    // [§11] the faction's display colour — a vanilla ChatFormatting value.
    // final: assigned ONCE at creation and never changed. There is no
    // /f color command in v1 (HANDOFF_6 §5) — players do not pick it; it is
    // auto-assigned by FactionManager.createFaction (least-used colour).
    // Used by /f map's coloured grid cells and the action-bar faction label.
    private final ChatFormatting color;

    // [§3] int — the "bonus" budget term (design §6). A spare capacity source
    // for future features (events, upgrades). 0 for now. NOT final — a future
    // feature could change it at runtime.
    private int bonusBudget;

    // [§3] int — how many chunks this faction currently owns. Goes UP on
    // claim, DOWN on unclaim or losing a chunk to an enemy overclaim. NOT
    // final. With baseBudget + bonusBudget, this is the third budget term.
    private int usedClaims;

    // [§10] Map<UUID, Long> — pending invites: invited player id -> the
    // millisecond timestamp the invite was issued. [§11] final fixes the
    // slot; the map's CONTENTS change as invites are issued, accepted, or
    // expire. Addendum 2 §19.3: an invite is valid for 300s after issue.
    // NOT persisted — invites are ephemeral, like the disband-pending map.
    private final Map<UUID, Long> invites = new HashMap<>();



    // -----------------------------------------------------------------------
    // CONSTRUCTORS — [§6] run once, when a new Faction object is created.
    //
    // There are TWO, for two different situations:
    //   3-arg — a BRAND NEW faction (/f new). Generates a fresh id and seeds
    //           the owner into the members map.
    //   6-arg — a faction REBUILT FROM DISK by FactionSavedData.load. Every
    //           value comes from saved NBT; the members map is created empty
    //           here and load() re-fills it afterward with addMemberWithRole,
    //           restoring each member at their saved role.
    // -----------------------------------------------------------------------

    /**
     * Creates a BRAND NEW faction (used by /f new).
     *
     * @param name  the faction's name
     * @param owner the UUID of the player who owns it
     * @param type  the faction's type (PLAYER / SAFEZONE / WARZONE)
     * @param color the faction's display colour, chosen by the CALLER
     *              (FactionManager.createFaction picks the least-used one).
     *
     * The owner is automatically added to the members map with role OWNER —
     * a faction always contains its owner (design addendum §13.7, §16.3).
     */
    public Faction(String name, UUID owner, FactionType type, ChatFormatting color) {
        // [§7] 'this.name' = this object's FIELD; 'name' = the parameter.
        this.name = name;
        this.owner = owner;

        // [§8] new empty map, then [§10] put() inserts the owner as the first
        // member with role OWNER — a faction always has its owner.
        this.members = new HashMap<>();
        this.members.put(owner, FactionRole.OWNER);

        // [§8] every faction generates its own permanent id at creation.
        this.id = UUID.randomUUID();
        this.type = type;
        this.color = color;

        // Budget terms both start empty (design §6). Set explicitly even
        // though int defaults to 0 — keeps every field's start value in one
        // visible place.
        this.bonusBudget = 0;
        this.usedClaims = 0;
    }

    /**
     * Rebuilds a faction FROM SAVED DATA (used by FactionSavedData.load).
     * Every field value comes straight from NBT — nothing is generated.
     *
     * The members map is created EMPTY here. load() fills it immediately
     * afterward via addMemberWithRole, so each member (owner included) is
     * restored with the exact role that was saved.
     */
    public Faction(UUID id, String name, UUID owner, FactionType type, ChatFormatting color, int bonusBudget, int usedClaims) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.type = type;
        this.color = color;
        this.bonusBudget = bonusBudget;
        this.usedClaims = usedClaims;

        this.members = new HashMap<>();
    }

    // -----------------------------------------------------------------------
    // GETTERS — [§5] simple identity reads. These hand a private field
    // straight back. Subsystem-specific reads live in their own sections
    // below (getUsedClaims with BUDGET, getRole with MEMBER, etc.).
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

    // [§5] hands back the faction's display colour. /f map and the action-bar
    // label call this to colour a faction's name / grid cells.
    public ChatFormatting getColor() {
        return this.color;
    }

    // [§10] returns a NEW List of all member UUIDs (the map's keys). A fresh
    // list is built so callers can read members without being able to modify
    // the faction's real members map.
    public List<UUID> getMembers() {
        return new ArrayList<>(this.members.keySet());
    }

    // -----------------------------------------------------------------------
    // MEMBER METHODS — [§5][§10] controlled changes to, and reads of, the
    // members map. Anything that asks "who is in this faction / what is their
    // role" lives here, next to the map it reads.
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

    /**
     * Adds a player with an EXPLICIT role (not the default MEMBER).
     * Used by FactionSavedData.load to restore each saved member at the
     * exact role they were saved with.
     */
    public void addMemberWithRole(UUID playerId, FactionRole role) {
        this.members.put(playerId, role);
    }

    /**
     * True if this player is in the faction at all (any role).
     * [§10] containsKey returns true/false directly.
     */
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

    /**
     * The player's role in this faction, or null if they are not a member.
     * [§10] map.get returns null for an absent key.
     */
    public FactionRole getRole(UUID playerId) {
        return this.members.get(playerId);
    }

    // 1. The field — nullable. null = faction has no placed obelisk.
    private BlockPos obeliskPos = null;

    // 2. Getter.
    public BlockPos getObeliskPos() {
        return this.obeliskPos;
    }

    // 3. Setter.
    public void setObeliskPos(BlockPos obeliskPos) {
        this.obeliskPos = obeliskPos;
    }
    private long lastObeliskGive = 0L;
    public long getLastObeliskGive() { return this.lastObeliskGive; }
    public void setLastObeliskGive(long lastObeliskGive) {
        this.lastObeliskGive = lastObeliskGive;
    }

    /**
     * True if the player is OFFICER or OWNER — the "elevated rank" test.
     *
     * This is the shared permission gate behind /f claim, /f unclaim,
     * /f invite and /f kick (the "are you allowed to do faction-management
     * actions at all" check). Putting it in ONE method means the rule lives
     * in one place: if the rank model ever changes, only this line changes,
     * not four copies in the command file.
     *
     * Written as "== OWNER || == OFFICER" rather than "!= MEMBER" on purpose
     * — if FactionRole ever gains a fourth value, "!= MEMBER" would silently
     * let it through; the explicit form will not.
     *
     * [§5] returns boolean. getRole may return null (non-member); null
     * equals neither OWNER nor OFFICER, so a non-member correctly gets false.
     */
    public boolean isAtLeastOfficer(UUID playerId) {
        FactionRole role = getRole(playerId);
        return role == FactionRole.OWNER || role == FactionRole.OFFICER;
    }

    // -----------------------------------------------------------------------
    // BUDGET METHODS — [§5] the claim-budget system (design §6).
    //
    // Addendum 4: "power" and "claim budget" are ONE concept. There is no
    // separate stored power value — a faction's power IS its available
    // budget. This section is the whole system.
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
     *
     * Addendum 4: this value IS the faction's "power". There is no separate
     * getPower() — callers that want power ask for the available budget.
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
     * logically be negative.
     */
    public void decrementUsedClaims() {
        if (this.usedClaims > 0) {
            this.usedClaims = this.usedClaims - 1;
        }
    }

    // -----------------------------------------------------------------------
    // INVITE METHODS — [§5][§10] controlled access to the invites map.
    // Addendum 2 §19.3: an invite is valid for 300 seconds after it is
    // issued. The 300s rule lives INSIDE hasValidInvite — callers just ask
    // "valid invite?" and never touch the timestamp themselves.
    // -----------------------------------------------------------------------

    /**
     * Issues an invite to a player — stores the current time as the issue
     * timestamp. Re-inviting an already-invited player just refreshes the
     * timestamp, which restarts their 300s window.
     */
    public void addInvite(UUID playerId) {
        this.invites.put(playerId, System.currentTimeMillis());
    }

    /**
     * True only if this player has an invite AND it has not expired.
     * [§10] map.get returns null if the key is absent — checked first, or
     * the subtraction below would throw on a null Long.
     */
    public boolean hasValidInvite(UUID playerId) {
        Long issued = this.invites.get(playerId);
        if (issued == null) {
            return false;
        }
        // 300_000 ms = 300 s. Underscores are just visual digit grouping.
        return System.currentTimeMillis() - issued < 300_000;
    }

    /**
     * Removes a player's invite — called when they accept (/f join) so the
     * invite is single-use.
     */
    public void removeInvite(UUID playerId) {

        this.invites.remove(playerId);
    }
    /**
     * Transfers ownership to another member (design addendum §16.4).
     * The new owner is set to OWNER; the OLD owner is demoted to OFFICER.
     * Both the owner FIELD and the role-map entries are updated together —
     * that is the whole reason this is one method and not two setter calls.
     */
    public void transferOwnership(UUID newOwner) {
        this.members.put(this.owner, FactionRole.OFFICER);
        this.members.put(newOwner, FactionRole.OWNER);
        this.owner = newOwner;
    }
    public void decrementBonusBudget() {
        if(getAvailableBudget() > 0) {
            this.bonusBudget--;
        }
    }
    public void incrementBonusBudget() {
        this.bonusBudget++;
    }
}