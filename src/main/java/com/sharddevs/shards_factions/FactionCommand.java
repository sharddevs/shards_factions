package com.sharddevs.shards_factions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
public class FactionCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // The /f command tree gets built and registered here.
        // Next step: build the literal/argument/executes tree
        // and hand it to the dispatcher.
        LiteralArgumentBuilder<CommandSourceStack> faction = Commands.literal("faction")
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
                                            ()  -> Component.literal("Faction '" + name + "' created successfully!"), false);
                                    return 1;
                                })))
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
                    FactionSavedData.get(player.server).setDirty();
                    ctx.getSource().sendSuccess(
                            () -> Component.literal("Chunk claimed for '" + playerFaction.getName() + "'!"), false);
                    return 1;
                }))
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
                        if (target != null) {
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("'" + name + "' exists! I havent figured out how to grab all the data yet but wanna see if this works!"), false);
                        }
                        return 1;
                            }
                    )));

        dispatcher.register(faction);
        dispatcher.register(Commands.literal("factions").redirect(faction.build()));
        dispatcher.register(Commands.literal("f").redirect(faction.build()));

    }
}