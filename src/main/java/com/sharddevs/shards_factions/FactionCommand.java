// ===========================================================================
// FactionCommand.java  —  the /faction | /factions | /f command tree.
//
// Builds ONE Brigadier command tree and registers it under three aliases.
// A command is a tree of nodes: literal (a fixed word), argument (a typed
// slot), and executes(...) (the lambda that runs when a branch is hit).
//
// Every executes body follows the same recipe:
//   1. resolve the player + FactionManager.
//   2. GATES — `if (bad) { sendFailure; return 0; }`, all before any mutation.
//   3. ACT — mutate via the manager / faction.
//   4. setDirty() — only when persisted data changed (members, claims,
//      obelisk-give time). NOT for ephemeral state (invites, bypass,
//      disband-pending, autoclaim) and NOT for pure reads.
//   5. respond — sendSuccess to the runner, notifyFaction to the faction.
//   6. return 1 (success) or 0 (failure).
//
// Command groups, in tree order:
//   membership : new, join, leave, invite, kick, disband
//   territory  : claim, unclaim, autoclaim, map
//   roles      : promote, demote  (shared handleRoleChange)
//   special    : obelisk give, bypass
//   admin      : createsystem, join   (OP-gated)
// ===========================================================================

package com.sharddevs.shards_factions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.server.permission.PermissionAPI;

import com.sharddevs.shards_factions.obelisk.ObeliskRegistration;

public class FactionCommand {

    /**
     * Builds the whole /faction tree and registers it under all three
     * aliases. Called once, from ShardsFactions.onRegisterCommands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        LiteralArgumentBuilder<CommandSourceStack> faction = Commands.literal("faction")

                // ===================== MEMBERSHIP =====================

                // /f new <name> — create a brand-new PLAYER faction.
                .then(Commands.literal("new")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String name = StringArgumentType.getString(ctx, "name");
                                    FactionManager manager = FactionSavedData.get(player.server).getManager();

                                    if (manager.getFactionByMember(player.getUUID()) != null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("You are already in a faction!"));
                                        return 0;
                                    }

                                    CreateResult result = manager.createFaction(
                                            name, player.getUUID(), FactionType.PLAYER);
                                    if (result == CreateResult.NAME_TAKEN) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("A faction named '" + name + "' already exists!"));
                                        return 0;
                                    }

                                    FactionSavedData.get(player.server).setDirty();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Faction '" + name + "' created successfully!"), false);
                                    player.server.getPlayerList().broadcastSystemMessage(
                                            Component.literal("[Shard's Factions] Faction '" + name + "' has been founded!"), false);
                                    return 1;
                                })))

                // /f join <faction> — accept an invite and join by name.
                .then(Commands.literal("join")
                        .then(Commands.argument("faction", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String factionName = StringArgumentType.getString(ctx, "faction");
                                    FactionManager manager = FactionSavedData.get(player.server).getManager();

                                    if (manager.getFactionByMember(player.getUUID()) != null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("You are already in a faction!"));
                                        return 0;
                                    }

                                    Faction target = manager.getFactionByName(factionName);
                                    if (target == null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal(factionName + " faction does not exist!"));
                                        return 0;
                                    }
                                    // hasValidInvite does the 300s expiry check internally.
                                    if (!target.hasValidInvite(player.getUUID())) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("You were either not invited to " + factionName + " or your invite has expired!"));
                                        return 0;
                                    }

                                    target.addMember(player.getUUID());
                                    target.removeInvite(player.getUUID()); // single-use
                                    FactionSavedData.get(player.server).setDirty();

                                    manager.notifyFaction(target,
                                            Component.literal(player.getName().getString() + " joined the faction."),
                                            player.server);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("You have successfully joined " + factionName + "!"), false);
                                    return 1;
                                })))

                // /f leave — leave your faction. The owner is redirected to
                // /f disband (this is where removeMember's missing owner-guard
                // is enforced — the command layer catches the owner first).
                .then(Commands.literal("leave")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            FactionManager manager = FactionSavedData.get(player.server).getManager();

                            Faction playerFaction = manager.getFactionByMember(player.getUUID());
                            if (playerFaction == null) {
                                ctx.getSource().sendFailure(
                                        Component.literal("You are not in a faction!"));
                                return 0;
                            }
                            if (playerFaction.getOwner().equals(player.getUUID())) {
                                ctx.getSource().sendFailure(
                                        Component.literal("You are the owner of '" + playerFaction.getName()
                                                + "'. To leave you must /f promote someone to leader or /f dis nd!"));
                                return 0;
                            }

                            FactionSavedData.get(player.server).setDirty();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("You left '" + playerFaction.getName() + "'."), false);
                            // notify before removing keeps the leaver in reach for this message.
                            playerFaction.removeMember(player.getUUID());
                            manager.notifyFaction(playerFaction,
                                    Component.literal(player.getName().getString() + " has left the faction."),
                                    player.server);
                            return 1;
                        }))

                // /f invite <player> — issue a pending invite (OFFICER+).
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    FactionManager manager = FactionSavedData.get(player.server).getManager();

                                    Faction playerFaction = manager.getFactionByMember(player.getUUID());
                                    if (playerFaction == null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("You are not in a faction!"));
                                        return 0;
                                    }
                                    if (!playerFaction.isAtLeastOfficer(player.getUUID())) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("You must be an officer or owner to invite players."));
                                        return 0;
                                    }

                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    playerFaction.addInvite(target.getUUID()); // ephemeral — no setDirty

                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Invited " + target.getName().getString()
                                                    + " to '" + playerFaction.getName() + "'."), false);
                                    manager.notifyFaction(playerFaction,
                                            Component.literal(target.getName().getString()
                                                    + " was invited to the faction by " + player.getName().getString() + "."),
                                            player.server);
                                    // the invited player isn't a member yet, so they're not in
                                    // notifyFaction's loop — message them directly with how to accept.
                                    target.sendSystemMessage(
                                            Component.literal("You've been invited to '" + playerFaction.getName()
                                                    + "'. Type /f join " + playerFaction.getName() + " to accept."));
                                    return 1;
                                })))

                // /f kick <player> — remove a member. The kicker must STRICTLY
                // out-rank the target (FactionRole ordinals: OWNER 0, OFFICER 1,
                // MEMBER 2 — lower = higher rank). Protects the owner for free.
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer kicker = ctx.getSource().getPlayerOrException();
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    FactionManager manager = FactionSavedData.get(kicker.server).getManager();

                                    Faction playerFaction = manager.getFactionByMember(kicker.getUUID());
                                    if (playerFaction == null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("You are not in a faction!"));
                                        return 0;
                                    }
                                    if (!playerFaction.isAtLeastOfficer(kicker.getUUID())) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("You must be an Officer or Owner to kick players."));
                                        return 0;
                                    }
                                    // must run before getRole/ordinal — getRole on a non-member is null.
                                    if (!playerFaction.isMember(target.getUUID())) {
                                        ctx.getSource().sendFailure(
                                                Component.literal(target.getName().getString()
                                                        + " is not in your faction."));
                                        return 0;
                                    }
                                    FactionRole kickerRole = playerFaction.getRole(kicker.getUUID());
                                    FactionRole targetRole = playerFaction.getRole(target.getUUID());
                                    if (!(kickerRole.ordinal() < targetRole.ordinal())) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("You can't kick someone of equal or higher rank."));
                                        return 0;
                                    }

                                    playerFaction.removeMember(target.getUUID());
                                    FactionSavedData.get(kicker.server).setDirty();

                                    // target is no longer a member, so notifyFaction won't reach them...
                                    manager.notifyFaction(playerFaction,
                                            Component.literal(kicker.getName().getString() + " has kicked "
                                                    + target.getName().getString() + " from the faction!"),
                                            kicker.server);
                                    // ...message them directly.
                                    target.sendSystemMessage(
                                            Component.literal("You were kicked from '"
                                                    + playerFaction.getName() + "'."));
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Kicked " + target.getName().getString()
                                                    + " from '" + playerFaction.getName() + "'."), false);
                                    return 1;
                                })))

                // /f disband — destroy your own faction (OWNER only). Two-step:
                // first call warns, a second call within 10s confirms. The
                // "was warned" state lives in the manager's pendingDisband map.
                .then(Commands.literal("disband")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            FactionManager manager = FactionSavedData.get(player.server).getManager();

                            Faction playerFaction = manager.getFactionByMember(player.getUUID());
                            if (playerFaction == null) {
                                ctx.getSource().sendFailure(
                                        Component.literal("You are not in a faction!"));
                                return 0;
                            }
                            if (!playerFaction.getOwner().equals(player.getUUID())) {
                                ctx.getSource().sendFailure(
                                        Component.literal("Only the owner of '" + playerFaction.getName() + "' can disband it!"));
                                return 0;
                            }

                            long now = System.currentTimeMillis();
                            Long warnedAt = manager.getPendingDisband().get(player.getUUID());

                            if (warnedAt != null && now - warnedAt < 10_000) {
                                // CONFIRM — notify BEFORE disbandFaction, or the member
                                // list is gone and notifyFaction reaches nobody.
                                player.server.getPlayerList().broadcastSystemMessage(
                                        Component.literal("[Shards Factions] Faction '" + playerFaction.getName() + "' has been disband!"), false);
                                manager.notifyFaction(playerFaction,
                                        Component.literal(player.getName().getString() + " has disbanded the faction!"),
                                        player.server);
                                manager.getPendingDisband().remove(player.getUUID());
                                BlockPos obeliskPos = playerFaction.getObeliskPos();
                                if (obeliskPos != null) {
                                    player.serverLevel().setBlockAndUpdate(obeliskPos, Blocks.AIR.defaultBlockState());
                                }
                                manager.disbandFaction(playerFaction);
                                FactionSavedData.get(player.server).setDirty();
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("Faction Disbanded."), false);
                                return 1;
                            } else {
                                // WARN — record the time, ask for a second call.
                                manager.getPendingDisband().put(player.getUUID(), now);
                                ctx.getSource().sendFailure(
                                        Component.literal("This will disband your faction. Type /f disband again to confirm."));
                                return 1;
                            }
                        }))

                // ===================== TERRITORY =====================

                // /f claim — claim the chunk you're standing in (OFFICER+).
                // Also the overclaim path (handled inside claimChunk).
                .then(Commands.literal("claim")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            FactionManager manager = FactionSavedData.get(player.server).getManager();
                            ChunkPos chunk = player.chunkPosition();

                            Faction playerFaction = manager.getFactionByMember(player.getUUID());
                            if (playerFaction == null) {
                                ctx.getSource().sendFailure(
                                        Component.literal("You are not in a faction!"));
                                return 0;
                            }
                            if (!playerFaction.isAtLeastOfficer(player.getUUID())) {
                                ctx.getSource().sendFailure(
                                        Component.literal("You must be an Officer or Owner to do this!"));
                                return 0;
                            }

                            // capture the former owner BEFORE the claim call — on an
                            // overclaim the transfer reassigns the claim's owner.
                            Claim before = manager.getClaim(chunk);
                            Faction formerOwner = before != null ? manager.getFaction(before.getClaimedBy()) : null;
                            String formerName = formerOwner != null ? formerOwner.getName() : "";
                            ClaimResult result = manager.claimChunk(chunk, playerFaction);

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
                            if (result == ClaimResult.OVERCLAIM_SUCCESS) {
                                manager.notifyFaction(playerFaction,
                                        Component.literal(player.getName().getString()
                                                + " has overclaimed a chunk for the faction from " + formerName), player.server);
                                FactionSavedData.get(player.server).setDirty();
                                return 1;
                            }
                            // normal claim success
                            FactionSavedData.get(player.server).setDirty();
                            manager.notifyFaction(playerFaction,
                                    Component.literal(player.getName().getString() + " has claimed a chunk for the faction!"),
                                    player.server);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Chunk claimed for '" + playerFaction.getName() + "'!"), false);
                            return 1;
                        }))

                // /f unclaim — release the chunk you're standing in (OFFICER+).
                // Also the escape hatch out of the over-budget-frozen state.
                .then(Commands.literal("unclaim")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            FactionManager manager = FactionSavedData.get(player.server).getManager();
                            ChunkPos chunk = player.chunkPosition();

                            Faction playerFaction = manager.getFactionByMember(player.getUUID());
                            if (playerFaction == null) {
                                ctx.getSource().sendFailure(
                                        Component.literal("You are not in a faction!"));
                                return 0;
                            }
                            if (!playerFaction.isAtLeastOfficer(player.getUUID())) {
                                ctx.getSource().sendFailure(
                                        Component.literal("You must be an Officer or Owner to do this!"));
                                return 0;
                            }

                            Claim claim = manager.getClaim(chunk);
                            if (claim == null) {
                                ctx.getSource().sendFailure(
                                        Component.literal("This chunk is not claimed!"));
                                return 0;
                            }
                            // getClaimedBy() is a faction id — compare with .equals.
                            if (!claim.getClaimedBy().equals(playerFaction.getId())) {
                                ctx.getSource().sendFailure(
                                        Component.literal("This chunk is owned by another faction!"));
                                return 0;
                            }

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

                // /f autoclaim — toggle autoclaim (OFFICER+). The claiming itself
                // happens in ChunkBorderTracker.onChunkCrossed; this only flips
                // the per-player flag. Ephemeral — no setDirty.
                .then(Commands.literal("autoclaim")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            FactionManager manager = FactionSavedData.get(player.server).getManager();

                            Faction playerFaction = manager.getFactionByMember(player.getUUID());
                            if (playerFaction == null) {
                                ctx.getSource().sendFailure(
                                        Component.literal("You are not in a faction!"));
                                return 0;
                            }
                            if (!playerFaction.isAtLeastOfficer(player.getUUID())) {
                                ctx.getSource().sendFailure(
                                        Component.literal("You must be an Officer or Owner to do this!"));
                                return 0;
                            }

                            boolean nowOn = ShardsFactions.chunkBorderTracker
                                    .toggleAutoclaim(player.getUUID());
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal(nowOn
                                            ? "Autoclaim ON — walk into chunks to claim them."
                                            : "Autoclaim OFF."), false);
                            return 1;
                        }))

                // /f map — chat-grid map of nearby claims (server-side only).
                // 9x9 chunks centred on the player. +Z is south, -Z is north,
                // so dz runs -4..+4 top-to-bottom to draw north-up. Read-only.
                // Cells: + own chunk (yellow), # claimed (faction colour),
                // . wilderness (dark gray). Node-gated (FactionPermissions.MAP).
                .then(Commands.literal("map")
                        .requires(src -> {
                            ServerPlayer p = src.getPlayer();
                            return p != null && PermissionAPI.getPermission(p, FactionPermissions.MAP);
                        })
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            FactionManager manager = FactionSavedData.get(player.server).getManager();

                            Faction playerFaction = manager.getFactionByMember(player.getUUID());
                            if (playerFaction == null) {
                                ctx.getSource().sendFailure(
                                        Component.literal("You are not in a faction!"));
                                return 0;
                            }

                            ChunkPos centre = player.chunkPosition();
                            int px = centre.x;
                            int pz = centre.z;

                            MutableComponent map = Component.literal("Faction Map — centred on you")
                                    .withStyle(ChatFormatting.GOLD);

                            for (int dz = -4; dz <= 4; dz++) {
                                MutableComponent row = Component.literal("\n");
                                for (int dx = -4; dx <= 4; dx++) {
                                    ChunkPos cell = new ChunkPos(px + dx, pz + dz);

                                    String symbol;
                                    ChatFormatting colour;
                                    if (dx == 0 && dz == 0) {
                                        symbol = "+";
                                        colour = ChatFormatting.YELLOW;
                                    } else {
                                        Claim claim = manager.getClaim(cell);
                                        if (claim == null) {
                                            symbol = ".";
                                            colour = ChatFormatting.DARK_GRAY;
                                        } else {
                                            Faction owner = manager.getFaction(claim.getClaimedBy());
                                            symbol = "#";
                                            // defensive: fall back to wilderness styling if unresolved.
                                            colour = (owner != null) ? owner.getColor() : ChatFormatting.DARK_GRAY;
                                        }
                                    }
                                    row.append(Component.literal(symbol + " ").withStyle(colour));
                                }
                                map.append(row);
                            }

                            // lambda captures must be effectively final.
                            final MutableComponent finalMap = map;
                            ctx.getSource().sendSuccess(() -> finalMap, false);
                            return 1;
                        }))

                // ===================== ROLES =====================

                // /f promote <role> <name> / /f demote <role> <name> — owner-only
                // role management. Shared body (handleRoleChange); isPromote
                // selects the direction guard. /f promote owner <name> transfers.
                .then(Commands.literal("promote")
                        .then(Commands.argument("role", StringArgumentType.word())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> handleRoleChange(ctx, true)))))
                .then(Commands.literal("demote")
                        .then(Commands.argument("role", StringArgumentType.word())
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> handleRoleChange(ctx, false)))))

                // ===================== SPECIAL =====================

                // /f obelisk give — owner-only; hands over the Obelisk item.
                // Continuous cooldown (§39.3), config-driven, first give free.
                .then(Commands.literal("obelisk")
                        .then(Commands.literal("give")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    FactionManager manager = FactionSavedData.get(player.server).getManager();

                                    Faction playerFaction = manager.getFactionByMember(player.getUUID());
                                    if (playerFaction == null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("You are not in a faction!"));
                                        return 0;
                                    }
                                    if (!playerFaction.getOwner().equals(player.getUUID())) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("Only the owner of '"
                                                        + playerFaction.getName()
                                                        + "' can request an obelisk!"));
                                        return 0;
                                    }

                                    // cooldown (§39.3): continuous; first give free (last == 0).
                                    long now = System.currentTimeMillis();
                                    long last = playerFaction.getLastObeliskGive();
                                    long cooldown = ShardsFactionsConfig.OBELISK_GIVE_COOLDOWN_MS.get();
                                    long elapsed = now - last;
                                    if (last > 0 && elapsed < cooldown) {
                                        long remainingMin = (cooldown - elapsed) / 60_000L;
                                        ctx.getSource().sendFailure(
                                                Component.literal("Your faction must wait "
                                                        + remainingMin
                                                        + " more minute(s) before requesting another obelisk."));
                                        return 0;
                                    }

                                    ItemStack obelisk = new ItemStack(ObeliskRegistration.FACTION_OBELISK_ITEM.get());
                                    if (!player.getInventory().add(obelisk)) {
                                        player.drop(obelisk, false); // inventory full — drop so it's never lost
                                    }

                                    playerFaction.setLastObeliskGive(now);
                                    FactionSavedData.get(player.server).setDirty();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Obelisk granted to '"
                                                    + playerFaction.getName() + "'."), false);
                                    return 1;
                                })))

                // /f bypass — toggle admin protection-bypass for this player.
                // Node-gated (FactionPermissions.BYPASS, explicit grant only).
                // Ephemeral — no setDirty.
                .then(Commands.literal("bypass")
                        .requires(src -> {
                            ServerPlayer p = src.getPlayer();
                            return p != null && PermissionAPI.getPermission(p, FactionPermissions.BYPASS);
                        })
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            FactionManager manager = FactionSavedData.get(player.server).getManager();

                            boolean nowOn = manager.toggleBypass(player.getUUID());
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal(nowOn
                                            ? "Bypass is ON - You are bypassing Faction Protection!"
                                            : "Bypass is OFF - You no longer bypass Faction Protections!"), false);
                            return 1;
                        }))

                // ===================== ADMIN (OP level 2) =====================
                // The .requires on the `admin` literal gates ALL its children.

                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))

                        // /f admin createsystem <SAFEZONE|WARZONE> <name> —
                        // create a playerless system faction owned by the
                        // server sentinel. PLAYER is rejected (use /f new).
                        .then(Commands.literal("createsystem")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    FactionManager manager = FactionSavedData.get(player.server).getManager();

                                                    String typeArg = StringArgumentType.getString(ctx, "type").toUpperCase();
                                                    String name = StringArgumentType.getString(ctx, "name");

                                                    FactionType type;
                                                    if (typeArg.equals("SAFEZONE")) {
                                                        type = FactionType.SAFEZONE;
                                                    } else if (typeArg.equals("WARZONE")) {
                                                        type = FactionType.WARZONE;
                                                    } else {
                                                        ctx.getSource().sendFailure(
                                                                Component.literal("Type must be SAFEZONE or WARZONE."));
                                                        return 0;
                                                    }

                                                    CreateResult result = manager.createFaction(
                                                            name, FactionManager.SERVER_OWNER, type);
                                                    if (result == CreateResult.NAME_TAKEN) {
                                                        ctx.getSource().sendFailure(
                                                                Component.literal("A faction named '" + name + "' already exists!"));
                                                        return 0;
                                                    }

                                                    FactionSavedData.get(player.server).setDirty();
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("Created " + type.name()
                                                                    + " faction '" + name + "'. Use /f admin join "
                                                                    + name + " then /f claim to paint its chunks."), false);
                                                    return 1;
                                                }))))

                        // /f admin join <faction> — join any faction with no
                        // invite, as OFFICER (so /f claim works for setup).
                        // The system-faction setup path (§13.4 OP join).
                        .then(Commands.literal("join")
                                .then(Commands.argument("faction", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String factionName = StringArgumentType.getString(ctx, "faction");
                                            FactionManager manager = FactionSavedData.get(player.server).getManager();

                                            if (manager.getFactionByMember(player.getUUID()) != null) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("You are already in a faction!"));
                                                return 0;
                                            }
                                            Faction target = manager.getFactionByName(factionName);
                                            if (target == null) {
                                                ctx.getSource().sendFailure(
                                                        Component.literal(factionName + " faction does not exist!"));
                                                return 0;
                                            }

                                            target.addMemberWithRole(player.getUUID(), FactionRole.OFFICER);
                                            FactionSavedData.get(player.server).setDirty();
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Admin-joined '" + factionName + "' as officer."), false);
                                            return 1;
                                        }))))

                // /f info <name> — read-only readout of a faction.
                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String name = StringArgumentType.getString(ctx, "name");
                                    FactionManager manager = FactionSavedData.get(player.server).getManager();

                                    Faction target = manager.getFactionByName(name);
                                    if (target == null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("No faction named '" + name + "' exists!"));
                                        return 0;
                                    }

                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal(target.getName() + " Owned by " + target.getOwner() + "\n" +
                                                    "Member Count: " + target.getMemberCount() + "\n" +
                                                    "Power: " + target.getUsedClaims() + " / " + (target.getBaseBudget() + target.getBonusBudget()) + "\n" +
                                                    "Claims: " + target.getUsedClaims() + "\n" +
                                                    "Claim Budget Available: " + target.getAvailableBudget()), false);
                                    return 1;
                                })))
                ;

        // Register the tree once; point both other aliases at the same node.
        LiteralCommandNode<CommandSourceStack> built = dispatcher.register(faction);
        dispatcher.register(Commands.literal("factions").redirect(built));
        dispatcher.register(Commands.literal("f").redirect(built));
    }

    // ===========================================================================
    // Shared body for /f promote and /f demote (Addendum 1 §16.4).
    // Both set a member's role; isPromote only selects which direction guard
    // applies. FactionRole ordinals: OWNER 0, OFFICER 1, MEMBER 2 — lower
    // ordinal = higher rank (same convention /f kick uses).
    // ===========================================================================
    private static int handleRoleChange(CommandContext<CommandSourceStack> ctx,
                                        boolean isPromote) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String roleArg = StringArgumentType.getString(ctx, "role");
        FactionManager manager = FactionSavedData.get(player.server).getManager();

        // GATE 1: runner in a faction.
        Faction playerFaction = manager.getFactionByMember(player.getUUID());
        if (playerFaction == null) {
            ctx.getSource().sendFailure(
                    Component.literal("You are not in a faction!"));
            return 0;
        }
        // GATE 2: owner-only — §16.4 makes role management owner-exclusive,
        // so this is getOwner(), NOT isAtLeastOfficer.
        if (!playerFaction.getOwner().equals(player.getUUID())) {
            ctx.getSource().sendFailure(
                    Component.literal("Only the owner can manage roles."));
            return 0;
        }
        // GATE 3: target must be a member (before any getRole/ordinal on it).
        if (!playerFaction.isMember(target.getUUID())) {
            ctx.getSource().sendFailure(
                    Component.literal(target.getName().getString() + " is not in your faction."));
            return 0;
        }
        // GATE 4: parse the role safely — valueOf throws on a bad string.
        FactionRole newRole;
        try {
            newRole = FactionRole.valueOf(roleArg.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(
                    Component.literal("'" + roleArg + "' is not a valid role. Use OWNER, OFFICER or MEMBER."));
            return 0;
        }
        // GATE 5: can't target the current owner directly — the only exit from
        // OWNER is the transfer branch below (blocks "/f demote MEMBER <owner>").
        if (target.getUUID().equals(playerFaction.getOwner())) {
            ctx.getSource().sendFailure(
                    Component.literal("You can't change the owner's role directly. Use /f promote owner <player> to transfer."));
            return 0;
        }

        // TRANSFER: /f promote owner <name> — setting OWNER is a transfer, not a set.
        if (newRole == FactionRole.OWNER) {
            if (!isPromote) {
                ctx.getSource().sendFailure(
                        Component.literal("You can't demote someone to owner. Use /f promote owner <player>."));
                return 0;
            }
            playerFaction.transferOwnership(target.getUUID());
            FactionSavedData.get(player.server).setDirty();
            manager.notifyFaction(playerFaction,
                    Component.literal(target.getName().getString() + " is now the owner of the faction."),
                    player.server);
            ctx.getSource().sendSuccess(
                    () -> Component.literal("Ownership transferred to " + target.getName().getString() + "."), false);
            return 1;
        }

        // DIRECTION GUARD (§16.4): verb must match effect, vs the target's current rank.
        FactionRole currentRole = playerFaction.getRole(target.getUUID());
        if (isPromote && newRole.ordinal() >= currentRole.ordinal()) {
            ctx.getSource().sendFailure(
                    Component.literal("/f promote can only raise a rank."));
            return 0;
        }
        if (!isPromote && newRole.ordinal() <= currentRole.ordinal()) {
            ctx.getSource().sendFailure(
                    Component.literal("/f demote can only lower a rank."));
            return 0;
        }

        // ACT: plain role-set.
        playerFaction.addMemberWithRole(target.getUUID(), newRole);
        FactionSavedData.get(player.server).setDirty();
        manager.notifyFaction(playerFaction,
                Component.literal(target.getName().getString() + " is now " + newRole.name() + "."),
                player.server);
        ctx.getSource().sendSuccess(
                () -> Component.literal("Set " + target.getName().getString() + " to " + newRole.name() + "."), false);
        return 1;
    }
}