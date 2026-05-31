package com.sharddevs.shards_factions;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.Commands;
public class FactionCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // The /f command tree gets built and registered here.
        // Next step: build the literal/argument/executes tree
        // and hand it to the dispatcher.
        LiteralArgumentBuilder<CommandSourceStack> faction = Commands.literal("faction")
                .then(Commands.literal("new"));
        dispatcher.register(faction);
        dispatcher.register(Commands.literal("factions").redirect(faction.build()));
        dispatcher.register(Commands.literal("f").redirect(faction.build()));

    }
}