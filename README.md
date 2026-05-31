# shards_factions

A native [NeoForge](https://neoforged.net/) factions mod for **Minecraft 1.21.1**, built for the Aeronautic Warfare PvP modpack.

Bases can't be *sneaked* down — they must be *assaulted*. A protected claim is immune to hand tools and automated miners; the only way through claimed blocks is **explosives**. Ground troops can't crack a base — only launchers and airships delivering ordnance can breach it.

> **Status:** v1 feature-complete. Standalone — depends only on NeoForge (no hard dependency on a permissions plugin).

---

## What it does

- **Factions** with owners, officers, members, and a ranked role system.
- **Chunk claims** backed by a power/budget system that scales with membership.
- **The Obelisk** — a placeable block that switches a faction's protection on. One per faction.
- **Siege-based raiding** — explosives bypass claim protection; everything else doesn't.
- **Overclaiming** — a stronger faction can peel chunks from one that's overextended.
- **System zones** — server-owned SafeZones (no PvP, no block edits) and WarZones (PvP on, blocks protected).
- **Server-authoritative** — all faction state lives on the server; the client needs the mod installed (it adds a custom block), but no client-side configuration.

---

## How it works

### Claims and power

Every faction has a **power** value derived from its size and combat record:

```
power      = (members × 10) + bonusBudget
available  = power − claimsUsed
```

A faction can claim wilderness chunks while it has available budget. `bonusBudget` moves with combat — winning a PvP fight credits power, losing one drains it — so a faction's reach grows and shrinks with how it fares in the field.

### The Obelisk

Claiming land doesn't protect it. **Protection comes from the Obelisk.**

- The faction owner requests the Obelisk item with `/f obelisk give` (subject to a cooldown).
- Placing it on the faction's own (or unclaimed) land binds it and switches **protection on** for all the faction's claims.
- While the Obelisk stands, non-members can't break or place blocks in the faction's claims — by hand, by tool, or by automated miner.
- **Explosives still work.** Missiles and charges break through. This is the intended siege path.
- Breaking the Obelisk (any player, by hand, tool, or explosion) switches **protection off**. The owning faction is notified, and a new Obelisk must be placed to restore it.

There is one Obelisk per faction. Requesting a new one is gated by a configurable cooldown (default one week), which is the mechanism that keeps Obelisks from leaking into circulation.

### Overclaiming

When a faction is **overextended** — holding more claims than its power supports (`claimsUsed > power`) — a healthier enemy faction can take its chunks one at a time with `/f claim`, until the victim is no longer overextended. Combat losses are what push a faction into that exposed state, so winning fights and holding ground are directly linked.

A faction's Obelisk chunk is shielded from overclaim while the Obelisk stands.

### System zones

Server admins can create playerless, permanently protected zones:

- **SafeZone** — no block break/place by anyone, PvP disabled, explosion-immune. (Spawn is a SafeZone.)
- **WarZone** — same block protection and explosion immunity, but **PvP enabled** — a combat buffer around protected areas.

---

## Commands

All commands work under `/faction`, `/factions`, and `/f`.

| Command | Who | What |
|---|---|---|
| `/f new <name>` | not in a faction | Create a faction (`/f create` is an alias). |
| `/f join <faction>` | not in a faction | Accept an invite. |
| `/f leave` | any member | Leave (owners are redirected to disband). |
| `/f invite <player>` | officer+ | Invite a player (invites expire after 5 min). |
| `/f kick <player>` | officer+ | Remove a member you out-rank. |
| `/f disband` | owner | Disband the faction (two-step confirm). |
| `/f claim` | officer+ | Claim the current chunk — also the overclaim path. |
| `/f unclaim` | officer+ | Release the current chunk. |
| `/f autoclaim` | officer+ | Toggle: walk into chunks to claim them. |
| `/f map` | member | Chat-grid map of nearby claims. |
| `/f info <name>` | any | Show a faction's details. |
| `/f promote <role> <player>` | owner | Raise a member's rank (`owner` transfers ownership). |
| `/f demote <role> <player>` | owner | Lower a member's rank. |
| `/f obelisk give` | owner | Receive the Obelisk item (cooldown-gated). |
| `/f bypass` | admin | Toggle protection bypass (permission-gated). |
| `/f admin createsystem <SAFEZONE\|WARZONE> <name>` | OP | Create a system zone. |
| `/f admin join <faction>` | OP | Join any faction (for system-zone setup). |

---

## Permissions

`shards_factions` uses NeoForge's native `PermissionAPI`. It works standalone with no permission plugin, and integrates automatically with a permission manager (e.g. LuckPerms) if one is installed — no API dependency required.

Two commands are gated by permission nodes (`shards_factions.map`, `shards_factions.bypass`), both defaulting to explicit-grant-only. Faction-role requirements (officer/owner) are enforced separately and always apply.

---

## Configuration

Server config lives in `config/shards_factions-server.toml`:

| Key | Default | Description |
|---|---|---|
| `obelisk.obeliskGiveCooldownMs` | `604800000` (1 week) | Cooldown between `/f obelisk give` uses, in milliseconds. |

---

## Building

Requires **JDK 21**.

```bash
./gradlew clean build
```

The built jar lands in `build/libs/`. Run a test client with `./gradlew runClient`.

---

## Not in v1

The following are deliberately deferred: a power cap on combat gains, automated-miner break protection for the Obelisk, full damage immunity in SafeZones (currently PvP-only), war declarations, `/f home` fallback behavior, and a client-rendered map. See the design docs for details.

---

*Built by [sharddevs](https://github.com/sharddevs).*
