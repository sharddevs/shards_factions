package com.sharddevs.shards_factions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;

// ===========================================================================
// Faction — the data + behaviour of a single faction (design §6).
//
// Plain Java: no Minecraft world API, no persistence (FactionSavedData does
// that). Methods are grouped by subsystem, not getter/setter:
//   GETTERS  — identity reads (name, owner, id, type, colour, members)
//   MEMBERS  — the members map (join, leave, role, count)
//   OBELISK  — placed-obelisk position + give-cooldown timestamp
//   BUDGET   — the claim-budget / power system
//   INVITE   — the pending-invites map (300s expiry)
//   OWNERSHIP— transferOwnership
// ===========================================================================
public class Faction {

    // -----------------------------------------------------------------------
    // FIELDS — all private; only this class mutates them.
    // -----------------------------------------------------------------------

    private final String name;                       // never changes for the faction's life
    private UUID owner;                               // NOT final — /f promote owner reassigns it
    private final Map<UUID, FactionRole> members;     // member id -> role
    private final UUID id;                            // permanent, generated at creation
    private final FactionType type;                   // PLAYER / SAFEZONE / WARZONE, fixed for life
    private final ChatFormatting color;               // display colour, auto-assigned at creation (HANDOFF_6 §5)

    private int bonusBudget;                          // budget/power term; combat-driven (Addendum 8 §52), negative-capable
    private int usedClaims;                           // chunks currently owned

    // pending invites: invited id -> issue timestamp (ms). 300s expiry lives
    // in hasValidInvite. Ephemeral — NOT persisted.
    private final Map<UUID, Long> invites = new HashMap<>();

    // placed obelisk position; null = no obelisk placed.
    private BlockPos obeliskPos = null;

    // wall-clock ms of the last /f obelisk give; 0 = never (first give free).
    // Continuous cooldown (§39.3). Persisted.
    private long lastObeliskGive = 0L;

    // -----------------------------------------------------------------------
    // CONSTRUCTORS
    //   4-arg — BRAND NEW faction (/f new): generates a fresh id, seeds the
    //           owner into the members map as OWNER (§13.7 — owner is always
    //           a member).
    //   7-arg — REBUILT FROM DISK (FactionSavedData.load): every value from
    //           NBT; members map starts empty and load() refills it via
    //           addMemberWithRole at each saved role.
    // -----------------------------------------------------------------------

    public Faction(String name, UUID owner, FactionType type, ChatFormatting color) {
        this.name = name;
        this.owner = owner;
        this.members = new HashMap<>();
        this.members.put(owner, FactionRole.OWNER);   // owner is always a member
        this.id = UUID.randomUUID();
        this.type = type;
        this.color = color;
        this.bonusBudget = 0;
        this.usedClaims = 0;
    }

    public Faction(UUID id, String name, UUID owner, FactionType type, ChatFormatting color, int bonusBudget, int usedClaims) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.type = type;
        this.color = color;
        this.bonusBudget = bonusBudget;
        this.usedClaims = usedClaims;
        this.members = new HashMap<>();               // load() refills this
    }

    // -----------------------------------------------------------------------
    // GETTERS
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

    public ChatFormatting getColor() {
        return this.color;
    }

    // A fresh list of member ids, so callers can read members without being
    // able to mutate the real map.
    public List<UUID> getMembers() {
        return new ArrayList<>(this.members.keySet());
    }

    // -----------------------------------------------------------------------
    // MEMBER METHODS
    // -----------------------------------------------------------------------

    // Adds a player at base role MEMBER (no-op if already present).
    public void addMember(UUID playerId) {
        if (!this.members.containsKey(playerId)) {
            this.members.put(playerId, FactionRole.MEMBER);
        }
    }

    // Adds a player at an EXPLICIT role — used by load() to restore saved roles.
    public void addMemberWithRole(UUID playerId, FactionRole role) {
        this.members.put(playerId, role);
    }

    public boolean isMember(UUID playerId) {
        return this.members.containsKey(playerId);
    }

    // NOTE: does NOT guard the owner — removing the owner would leave the
    // faction ownerless. The owner guard is deferred to the command layer
    // (/f leave redirects the owner to /f disband). See §13.7.
    public void removeMember(UUID playerId) {
        this.members.remove(playerId);
    }

    public int getMemberCount() {
        return this.members.size();
    }

    // The player's role, or null if not a member.
    public FactionRole getRole(UUID playerId) {
        return this.members.get(playerId);
    }

    // True if OFFICER or OWNER — the shared "elevated rank" gate behind
    // /f claim, /f unclaim, /f invite, /f kick. Written "== OWNER || ==
    // OFFICER" not "!= MEMBER" on purpose: a future 4th role would slip
    // through "!= MEMBER" but not this. getRole may be null (non-member);
    // null matches neither, so a non-member correctly gets false.
    public boolean isAtLeastOfficer(UUID playerId) {
        FactionRole role = getRole(playerId);
        return role == FactionRole.OWNER || role == FactionRole.OFFICER;
    }

    // -----------------------------------------------------------------------
    // OBELISK — placed position + give-cooldown timestamp.
    // -----------------------------------------------------------------------

    public BlockPos getObeliskPos() {
        return this.obeliskPos;
    }

    public void setObeliskPos(BlockPos obeliskPos) {
        this.obeliskPos = obeliskPos;
    }

    public long getLastObeliskGive() {
        return this.lastObeliskGive;
    }

    public void setLastObeliskGive(long lastObeliskGive) {
        this.lastObeliskGive = lastObeliskGive;
    }

    // -----------------------------------------------------------------------
    // BUDGET / POWER (design §6, Addendum 4 unified, Addendum 8 combat-driven)
    //
    //   baseBudget  — DERIVED: memberCount * 10 (always current, never stored)
    //   bonusBudget — STORED: combat moves it (kill +1 / death -1), can go
    //                 negative (floored so total power stays >= 0)
    //   usedClaims  — STORED: chunks owned
    //
    //   available (getAvailableBudget) = base + bonus - used  → the wilderness
    //       claim check (available > 0).
    //   power (for overclaim) = base + bonus  → a faction is overextended when
    //       usedClaims > power. NOTE: overclaim compares against base+bonus,
    //       NOT available — available already subtracts used and would
    //       double-count it (the bug fixed this session). See claimChunk.
    // -----------------------------------------------------------------------

    public int getBaseBudget() {
        return getMemberCount() * 10;
    }

    public int getBonusBudget() {
        return this.bonusBudget;
    }

    public int getUsedClaims() {
        return this.usedClaims;
    }

    public int getAvailableBudget() {
        return getBaseBudget() + this.bonusBudget - this.usedClaims;
    }

    // Up on claim / overclaim gain. No guard — going up is always valid; the
    // budget check lives in the caller (claimChunk).
    public void incrementUsedClaims() {
        this.usedClaims = this.usedClaims + 1;
    }

    // Down on unclaim / overclaim loss. Floored at 0 — a count can't be negative.
    public void decrementUsedClaims() {
        if (this.usedClaims > 0) {
            this.usedClaims = this.usedClaims - 1;
        }
    }

    // Combat: death drains power, but only while available is still positive,
    // so a kill can't push a faction's available below 0 (Addendum 8 §52.4).
    public void decrementBonusBudget() {
        if (getBaseBudget() + this.bonusBudget > 0) {
            this.bonusBudget--;
        }
    }

    // Combat: kill credits power. UNCAPPED (Addendum 8 §52.4 — cap is an open
    // balance lever).
    public void incrementBonusBudget() {
        this.bonusBudget++;
    }

    // -----------------------------------------------------------------------
    // INVITE METHODS — 300s expiry lives in hasValidInvite; callers just ask
    // "valid invite?" and never touch timestamps. Ephemeral, not persisted.
    // -----------------------------------------------------------------------

    // Issues (or refreshes) an invite, stamping the current time.
    public void addInvite(UUID playerId) {
        this.invites.put(playerId, System.currentTimeMillis());
    }

    // True only if an invite exists AND is under 300s old. Null-checked first
    // so the subtraction can't throw on an absent Long.
    public boolean hasValidInvite(UUID playerId) {
        Long issued = this.invites.get(playerId);
        if (issued == null) return false;
        return System.currentTimeMillis() - issued < 300_000;   // 300s
    }

    // Consumes an invite (single-use, on /f join accept).
    public void removeInvite(UUID playerId) {
        this.invites.remove(playerId);
    }

    // -----------------------------------------------------------------------
    // OWNERSHIP
    // -----------------------------------------------------------------------

    // Transfers ownership (§16.4): new owner -> OWNER, old owner -> OFFICER,
    // and the owner field updated — all together, which is why it's one method.
    public void transferOwnership(UUID newOwner) {
        this.members.put(this.owner, FactionRole.OFFICER);
        this.members.put(newOwner, FactionRole.OWNER);
        this.owner = newOwner;
    }
}