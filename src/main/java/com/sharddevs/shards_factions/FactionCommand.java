package com.sharddevs.shards_factions;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

public class FactionCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // The /f command tree gets built and registered here.
        // Next step: build the literal/argument/executes tree
        // and hand it to the dispatcher.
    }
}