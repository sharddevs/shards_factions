// ===========================================================================
// FactionCommand.java  —  the /faction | /factions | /f command tree.
//
// Annotated for learning. This file builds ONE Brigadier command tree and
// registers it under three aliases. Brigadier is Minecraft's command
// framework; a command is a TREE of nodes:
//   - literal node  — a fixed word the player types ("faction", "claim").
//   - argument node — a typed slot the player fills (a name, a player).
//   - executes(...) — the lambda that actually runs when that branch is hit.
//
// SHARED COMMAND SHAPE — every executes body below follows the same recipe:
//   1. get the player + the FactionManager.
//   2. GATES — a stack of `if (bad) { sendFailure; return 0; }` checks.
//      RULE: every gate runs BEFORE any state is changed. Nothing that
//      mutates data may run until all gates have passed.
//   3. ACT — call the manager / faction to change state.
//   4. setDirty() — ONLY if persisted data changed (members, claims).
//      NOT for ephemeral data (invites, disband-pending) and NOT for reads.
//   5. respond — sendSuccess to the runner, notifyFaction to the faction.
//   6. return 1 (success) or 0 (failure).
// ===========================================================================

package com.sharddevs.shards_factions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

public class FactionCommand {

    /**
     * Builds the whole /faction tree and registers it. Called once, from
     * ShardsFactions.onRegisterCommands (wired to RegisterCommandsEvent).
     *
     * The tree is assembled into `faction` first; the three dispatcher.register
     * calls at the bottom hand it to the game under all three aliases.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        LiteralArgumentBuilder<CommandSourceStack> faction = Commands.literal("faction")

                // ===========================================================
                // /f new <name>  —  create a brand-new PLAYER faction.
                // ===========================================================
                .then(Commands.literal("new")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    // getPlayerOrException: the command source
                                    // as a player, or throw if it isn't one
                                    // (console, command block). Guarantees a
                                    // non-null player for the rest of the body.
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String name = StringArgumentType.getString(ctx, "name");
                                    FactionManager manager = FactionSavedData.get(player.server).getManager();

                                    // GATE: can't create a faction while in one.
                                    if (manager.getFactionByMember(player.getUUID()) != null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("You are already in a faction!"));
                                        return 0;
                                    }

                                    // ACT: createFaction returns a CreateResult
                                    // enum, not the Faction itself.
                                    CreateResult result = manager.createFaction(
                                            name, player.getUUID(), FactionType.PLAYER);

                                    // GATE on the result: name collision.
                                    if (result == CreateResult.NAME_TAKEN) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("A faction named '" + name + "' already exists!"));
                                        return 0;
                                    }

                                    // persisted data (a new faction) changed.
                                    FactionSavedData.get(player.server).setDirty();

                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Faction '" + name + "' created successfully!"), false);

                                    // server-wide announcement: a new faction
                                    // is news for everyone on a PvP server.
                                    player.server.getPlayerList().broadcastSystemMessage(
                                            Component.literal("[Shard's Factions] Faction '" + name + "' has been founded!"), false);
                                    return 1;
                                })))

                // ===========================================================
                // /f claim  —  claim the chunk the player is standing in.
                // ===========================================================
                .then(Commands.literal("claim")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            FactionManager manager = FactionSavedData.get(player.server).getManager();
                            ChunkPos chunk = player.chunkPosition();

                            // GATE 1: must be in a faction.
                            Faction playerFaction = manager.getFactionByMember(player.getUUID());
                            if (playerFaction == null) {
                                ctx.getSource().sendFailure(
                                        Component.literal("You are not in a faction!"));
                                return 0;
                            }
                            // GATE 2: must be OFFICER+ (Addendum 2 section 18).
                            // isAtLeastOfficer is the shared rank helper —
                            // the rule lives in Faction, not copied here.
                            if (!playerFaction.isAtLeastOfficer(player.getUUID())) {
                                ctx.getSource().sendFailure(
                                        Component.literal("You must be an Officer or Owner to do this!"));
                                return 0;
                            }

                            // ACT: claimChunk returns a ClaimResult enum.
                            ClaimResult result = manager.claimChunk(chunk, playerFaction);

                            // GATEs on the result. These come AFTER the act
                            // because they branch on what it returned — unlike
                            // GATEs 1/2, which decide whether to act at all.
                            if (result == ClaimResult.ALREADY_CLAIMED) {
                                ctx.getSource().sendFailure(
                                        Component.literal("This chunk is already claimed."));
                                return 0;
                            }
                            if (result == ClaimResult.NO_BUDGET) {
                                ctx.getSource().sendFailure(
                                        Component.literal("Your faction has no power left to claim land!"));
                                return 0;
                            }

                            FactionSavedData.get(player.server).setDirty();
                            manager.notifyFaction(playerFaction,
                                    Component.literal(player.getName().getString() + " has claimed a chunk for the faction!"),
                                    player.server);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Chunk claimed for '" + playerFaction.getName() + "'!"), false);
                            return 1;
                        }))

                // ===========================================================
                // /f info <name>  —  read-only readout of a faction.
                // ===========================================================
                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String name = StringArgumentType.getString(ctx, "name");
                                    FactionManager manager = FactionSavedData.get(player.server).getManager();

                                    // here `target` is a Faction — looked up
                                    // by name. (Other commands use `target`
                                    // for a player; the name is reused, the
                                    // type is not — watch which is which.)
                                    Faction target = manager.getFactionByName(name);
                                    if (target == null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("No faction named '" + name + "' exists!"));
                                        return 0;
                                    }

                                    // pure read — no setDirty, no notify.
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal(target.getName() + " Owned by " + target.getOwner() + "\n" +
                                                    "Member Count: " + target.getMemberCount() + "\n" +
                                                    "Power: " + target.getUsedClaims() + " / " + (target.getBaseBudget() + target.getBonusBudget()) + "\n" +
                                                    "Claims: " + target.getUsedClaims() + "\n" +
                                                    "Claim Budget Available: " + target.getAvailableBudget()), false);
                                    return 1;
                                })))

                // ===========================================================
                // /f disband  —  destroy the player's own faction.
                // Two-step: first call WARNS, a second call within 10s
                // CONFIRMS. The "was warned" state lives in the manager's
                // pendingDisband map (UUID -> warn timestamp).
                // ===========================================================
                .then(Commands.literal("disband")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            FactionManager manager = FactionSavedData.get(player.server).getManager();

                            // GATE 1: must be in a faction.
                            Faction playerFaction = manager.getFactionByMember(player.getUUID());
                            if (playerFaction == null) {
                                ctx.getSource().sendFailure(
                                        Component.literal("You are not in a faction!"));
                                return 0;
                            }
                            // GATE 2: must be the OWNER (not just OFFICER+).
                            if (!playerFaction.getOwner().equals(player.getUUID())) {
                                ctx.getSource().sendFailure(
                                        Component.literal("Only the owner of '" + playerFaction.getName() + "' can disband it!"));
                                return 0;
                            }

                            long now = System.currentTimeMillis();
                            Long warnedAt = manager.getPendingDisband().get(player.getUUID());

                            if (warnedAt != null && now - warnedAt < 10_000) {
                                // ---- CONFIRM branch (warned < 10s ago) ----
                                // notify BEFORE disbandFaction — once the
                                // faction is disbanded its member list is
                                // gone, so notifyFaction would reach nobody.
                                player.server.getPlayerList().broadcastSystemMessage(
                                        Component.literal("[Shards Factions] Faction '" + playerFaction.getName() + "' has been disband!"), false);
                                manager.notifyFaction(playerFaction,
                                        Component.literal(player.getName().getString() + " has disbanded the faction!"),
                                        player.server);

                                manager.getPendingDisband().remove(player.getUUID());
                                manager.disbandFaction(playerFaction);
                                FactionSavedData.get(player.server).setDirty();
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("Faction Disbanded."), false);
                                return 1;
                            } else {
                                // ---- WARN branch (no warning, or stale) ----
                                // record the warn time; ask for a second call.
                                manager.getPendingDisband().put(player.getUUID(), now);
                                ctx.getSource().sendFailure(
                                        Component.literal("This will disband your faction. Type /f disband again to confirm."));
                                return 1;
                            }
                        }))

                // ===========================================================
                // /f leave  —  leave the player's faction.
                // The OWNER cannot leave — they are redirected to /f disband.
                // This is where removeMember's missing owner-guard is
                // enforced: the command layer catches the owner first.
                // ===========================================================
                .then(Commands.literal("leave")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            FactionManager manager = FactionSavedData.get(player.server).getManager();

                            // GATE 1: must be in a faction.
                            Faction playerFaction = manager.getFactionByMember(player.getUUID());
                            if (playerFaction == null) {
                                ctx.getSource().sendFailure(
                                        Component.literal("You are not in a faction!"));
                                return 0;
                            }
                            // GATE 2: the owner cannot simply leave.
                            if (playerFaction.getOwner().equals(player.getUUID())) {
                                ctx.getSource().sendFailure(
                                        Component.literal("You are the owner of '" + playerFaction.getName()
                                                + "'. To leave you must /f promote someone to leader or /f disband!"));
                                return 0;
                            }

                            FactionSavedData.get(player.server).setDirty();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("You left '" + playerFaction.getName() + "'."), false);
                            // notify BEFORE removing keeps the leaver in the
                            // loop's reach for this one message. (Either
                            // order is fine; this includes the leaver.)
                            playerFaction.removeMember(player.getUUID());
                            manager.notifyFaction(playerFaction,
                                    Component.literal(player.getName().getString() + " has left the faction."),
                                    player.server);
                            return 1;
                        }))

                // ===========================================================
                // /f invite <player>  —  issue a pending invite.
                // EntityArgument.player() gives real online-player tab
                // completion and rejects unknown names before this body runs.
                // ===========================================================
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    FactionManager manager = FactionSavedData.get(player.server).getManager();

                                    // GATE 1: must be in a faction.
                                    Faction playerFaction = manager.getFactionByMember(player.getUUID());
                                    if (playerFaction == null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("You are not in a faction!"));
                                        return 0;
                                    }
                                    // GATE 2: must be OFFICER+.
                                    if (!playerFaction.isAtLeastOfficer(player.getUUID())) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("You must be an officer or owner to invite players."));
                                        return 0;
                                    }

                                    // `target` here is the invited PLAYER.
                                    // No null-check needed — EntityArgument
                                    // guaranteed an online player exists.
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

                                    // ACT: record the invite (ephemeral — no
                                    // setDirty; the invites map is not saved).
                                    playerFaction.addInvite(target.getUUID());

                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Invited " + target.getName().getString()
                                                    + " to '" + playerFaction.getName() + "'."), false);

                                    // audience 1: the faction.
                                    manager.notifyFaction(playerFaction,
                                            Component.literal(target.getName().getString()
                                                    + " was invited to the faction by " + player.getName().getString() + "."),
                                            player.server);
                                    // audience 2: the invited player directly
                                    // (they are NOT a member yet, so they are
                                    // not in notifyFaction's loop — they need
                                    // their own message, with how to accept).
                                    target.sendSystemMessage(
                                            Component.literal("You've been invited to '" + playerFaction.getName()
                                                    + "'. Type /f join " + playerFaction.getName() + " to accept."));
                                    return 1;
                                })))

                // ===========================================================
                // /f join <faction>  —  accept an invite and join by name.
                // ===========================================================
                .then(Commands.literal("join")
                        .then(Commands.argument("faction", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String factionName = StringArgumentType.getString(ctx, "faction");
                                    FactionManager manager = FactionSavedData.get(player.server).getManager();

                                    // GATE 1: cannot join while already in one.
                                    if (manager.getFactionByMember(player.getUUID()) != null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("You are already in a faction!"));
                                        return 0;
                                    }

                                    // `target` is the Faction being joined.
                                    Faction target = manager.getFactionByName(factionName);
                                    // GATE 2: that faction must exist. Use the
                                    // `target` already fetched — no second
                                    // getFactionByName call.
                                    if (target == null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal(factionName + " faction does not exist!"));
                                        return 0;
                                    }
                                    // GATE 3: must hold a valid (un-expired)
                                    // invite. hasValidInvite does the 300s
                                    // expiry check internally.
                                    if (!target.hasValidInvite(player.getUUID())) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("You were either not invited to " + factionName + " or your invite has expired!"));
                                        return 0;
                                    }

                                    // ACT: join, then consume the invite so
                                    // it cannot be reused.
                                    target.addMember(player.getUUID());
                                    target.removeInvite(player.getUUID());
                                    // membership is persisted -> setDirty.
                                    FactionSavedData.get(player.server).setDirty();

                                    manager.notifyFaction(target,
                                            Component.literal(player.getName().getString() + " joined the faction."),
                                            player.server);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("You have successfully joined " + factionName + "!"), false);
                                    return 1;
                                })))

                // ===========================================================
                // /f unclaim  —  release the chunk the player is standing in.
                // Also the escape hatch out of the over-budget-frozen state.
                // ===========================================================
                .then(Commands.literal("unclaim")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            FactionManager manager = FactionSavedData.get(player.server).getManager();
                            ChunkPos chunk = player.chunkPosition();

                            // GATE 1: must be in a faction.
                            Faction playerFaction = manager.getFactionByMember(player.getUUID());
                            if (playerFaction == null) {
                                ctx.getSource().sendFailure(
                                        Component.literal("You are not in a faction!"));
                                return 0;
                            }
                            // GATE 2: must be OFFICER+.
                            if (!playerFaction.isAtLeastOfficer(player.getUUID())) {
                                ctx.getSource().sendFailure(
                                        Component.literal("You must be an Officer or Owner to do this!"));
                                return 0;
                            }

                            Claim claim = manager.getClaim(chunk);
                            // GATE 3a: this chunk must actually be claimed.
                            if (claim == null) {
                                ctx.getSource().sendFailure(
                                        Component.literal("This chunk is not claimed!"));
                                return 0;
                            }
                            // GATE 3b: and it must be THIS faction's claim.
                            // getClaimedBy() is a faction id (UUID) — compare
                            // with .equals, not == (two UUID objects).
                            if (!claim.getClaimedBy().equals(playerFaction.getId())) {
                                ctx.getSource().sendFailure(
                                        Component.literal("This chunk is owned by another faction!"));
                                return 0;
                            }

                            // ACT: drop the claim, keep usedClaims honest.
                            manager.removeClaim(chunk);
                            playerFaction.decrementUsedClaims();
                            FactionSavedData.get(player.server).setDirty();

                            manager.notifyFaction(playerFaction,
                                    Component.literal(player.getName().getString() + " has unclaimed a chunk for the faction!"),
                                    player.server);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("You have unclaimed this chunk!"), false);
                            return 1;
                        }))

                // ===========================================================
                // /f kick <player>  —  remove a member from your faction.
                // Rank rule: the kicker must out-rank the target STRICTLY.
                // An OFFICER cannot kick another OFFICER or the OWNER.
                // ===========================================================
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    // `kicker` runs the command; `target` is
                                    // the player being kicked.
                                    ServerPlayer kicker = ctx.getSource().getPlayerOrException();
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    FactionManager manager = FactionSavedData.get(kicker.server).getManager();

                                    // GATE 1: kicker must be in a faction.
                                    Faction playerFaction = manager.getFactionByMember(kicker.getUUID());
                                    if (playerFaction == null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("You are not in a faction!"));
                                        return 0;
                                    }
                                    // GATE 2: kicker must be OFFICER+ to kick
                                    // at all (the shared rank helper).
                                    if (!playerFaction.isAtLeastOfficer(kicker.getUUID())) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("You must be an Officer or Owner to kick players."));
                                        return 0;
                                    }
                                    // GATE 3: target must be in this faction.
                                    // Must run BEFORE GATE 4 — getRole on a
                                    // non-member returns null, and null has
                                    // no .ordinal().
                                    if (!playerFaction.isMember(target.getUUID())) {
                                        ctx.getSource().sendFailure(
                                                Component.literal(target.getName().getString()
                                                        + " is not in your faction."));
                                        return 0;
                                    }
                                    // GATE 4: kicker must STRICTLY out-rank
                                    // the target. FactionRole is declared
                                    // OWNER, OFFICER, MEMBER, so ordinal() is
                                    // 0,1,2 — LOWER ordinal = HIGHER rank.
                                    // "out-ranks" is kicker.ordinal < target.
                                    // This also protects the OWNER for free:
                                    // owner is ordinal 0, nothing is below 0.
                                    FactionRole kickerRole = playerFaction.getRole(kicker.getUUID());
                                    FactionRole targetRole = playerFaction.getRole(target.getUUID());
                                    if (!(kickerRole.ordinal() < targetRole.ordinal())) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("You can't kick someone of equal or higher rank."));
                                        return 0;
                                    }

                                    // ACT: remove the target.
                                    playerFaction.removeMember(target.getUUID());
                                    FactionSavedData.get(kicker.server).setDirty();

                                    // notify the faction (the kicked player is
                                    // NO LONGER a member, so notifyFaction's
                                    // loop will not reach them)...
                                    manager.notifyFaction(playerFaction,
                                            Component.literal(kicker.getName().getString() + " has kicked "
                                                    + target.getName().getString() + " from the faction!"),
                                            kicker.server);
                                    // ...so the kicked player gets told
                                    // directly. Without this they would never
                                    // learn they were kicked.
                                    target.sendSystemMessage(
                                            Component.literal("You were kicked from '"
                                                    + playerFaction.getName() + "'."));

                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Kicked " + target.getName().getString()
                                                    + " from '" + playerFaction.getName() + "'."), false);
                                    return 1;
                                })))
                ;

        // Register the tree ONCE and capture the node it returns. The two
        // aliases redirect to that same node — build the tree once, point
        // three names at it.
        LiteralCommandNode<CommandSourceStack> built = dispatcher.register(faction);
        dispatcher.register(Commands.literal("factions").redirect(built));
        dispatcher.register(Commands.literal("f").redirect(built));
    }
}