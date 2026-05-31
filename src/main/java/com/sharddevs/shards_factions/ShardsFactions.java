package com.sharddevs.shards_factions;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import com.sharddevs.shards_factions.obelisk.ObeliskRegistration;

// ===========================================================================
// ShardsFactions — the mod entry point.
//
// @Mod("shards_factions") is the handshake with NeoForge: this class is the
// entry point for the mod whose id is "shards_factions". That string MUST
// match the modId in neoforge.mods.toml exactly. NeoForge discovers this
// class at startup and runs the constructor below.
// ===========================================================================
@Mod("shards_factions")
public class ShardsFactions {

    // -----------------------------------------------------------------------
    // STATICS
    // -----------------------------------------------------------------------

    // Mod id kept in one place so other classes reference MOD_ID, not a literal.
    public static final String MOD_ID = "shards_factions";

    // SLF4J logger tagged to this class (the logging API Minecraft uses).
    private static final Logger LOGGER = LogUtils.getLogger();

    // The mod's single ChunkBorderTracker, held here so FactionCommand can
    // reach it (/f autoclaim). Created and bus-registered in the constructor.
    public static ChunkBorderTracker chunkBorderTracker;

    // -----------------------------------------------------------------------
    // GAME-BUS EVENT HANDLERS (this instance is registered to NeoForge.EVENT_BUS)
    // -----------------------------------------------------------------------

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[Shards Factions] Server is Starting and I'm loaded!");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FactionCommand.register(event.getDispatcher());
    }

    // -----------------------------------------------------------------------
    // CONSTRUCTOR — runs once during mod loading.
    //
    // NeoForge injects what we ask for by parameter type: modBus (this mod's
    // loading bus, for registries) and modContainer (owns config registration).
    // -----------------------------------------------------------------------

    public ShardsFactions(IEventBus modBus, ModContainer modContainer) {
        LOGGER.info("shards_factions loaded.");

        // config — SERVER type (see ShardsFactionsConfig).
        modContainer.registerConfig(ModConfig.Type.SERVER, ShardsFactionsConfig.SPEC);

        // registries — obelisk block/item/block-entity, on the mod bus.
        ObeliskRegistration.BLOCKS.register(modBus);
        ObeliskRegistration.ITEMS.register(modBus);
        ObeliskRegistration.BLOCK_ENTITIES.register(modBus);

        // game-bus handlers. FactionPermissions is registered as a class
        // (its @SubscribeEvent gather handler is static); permission nodes
        // gather on the game bus, NOT the mod bus.
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(FactionPermissions.class);

        chunkBorderTracker = new ChunkBorderTracker();
        NeoForge.EVENT_BUS.register(chunkBorderTracker);
    }
}