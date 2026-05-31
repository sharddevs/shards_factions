// The package declaration. This MUST mirror the folder path:
// src/main/java/com/sharddevs/shards_factions/  ->  com.sharddevs.shards_factions
// The Java compiler enforces this — file location and package must agree.
package com.sharddevs.shards_factions;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;

import com.sharddevs.shards_factions.obelisk.ObeliskRegistration;
/**
 * Main entry point for the shards_factions mod.
 *
 * The @Mod annotation is the handshake with NeoForge: it says "this class is
 * the entry point for the mod whose id is 'shards_factions'". That string MUST
 * match the modId in neoforge.mods.toml exactly, or NeoForge loads the
 * metadata but never finds this code.
 *
 * NeoForge discovers this class at startup and instantiates it — the
 * constructor below is what runs.
 */
@Mod("shards_factions")
public class ShardsFactions {

    // The mod id, kept as a constant in one place so other classes can refer
    // to MOD_ID instead of re-typing the literal string (and risking typos).
    public static final String MOD_ID = "shards_factions";

    // A logger for this mod. SLF4J is the logging API NeoForge/Minecraft use;
    // LogUtils.getLogger() hands back a logger tagged to this class.
    private static final Logger LOGGER = LogUtils.getLogger();

    // The mod's single ChunkBorderTracker. Held here so FactionCommand can
    // reach it (/f autoclaim). Created and bus-registered in the constructor.
    public static ChunkBorderTracker chunkBorderTracker;

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[Shards Factions] Server is Starting and I'm loaded!");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FactionCommand.register(event.getDispatcher());
    }

    /**
     * NeoForge calls this constructor during mod loading. It logs that the
     * mod is alive, then creates and bus-registers the event handlers.
     */
    public ShardsFactions(IEventBus modBus) {
        LOGGER.info("shards_factions loaded.");
        ObeliskRegistration.BLOCKS.register(modBus);
        ObeliskRegistration.ITEMS.register(modBus);
        ObeliskRegistration.BLOCK_ENTITIES.register(modBus);
        NeoForge.EVENT_BUS.register(this);

        chunkBorderTracker = new ChunkBorderTracker();
        NeoForge.EVENT_BUS.register(chunkBorderTracker);

    }
}