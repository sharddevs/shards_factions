// The package declaration. This MUST mirror the folder path:
// src/main/java/com/sharddevs/shards_factions/  ->  com.sharddevs.shards_factions
// The Java compiler enforces this — file location and package must agree.
package com.sharddevs.shards_factions;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import java.util.UUID;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.level.ChunkPos;

/**
 * Main entry point for the shards_factions mod.
 *
 * The @Mod annotation is the handshake with NeoForge: it says "this class is
 * the entry point for the mod whose id is 'shards_factions'". That string MUST
 * match the modId in neoforge.mods.toml exactly, or NeoForge loads the
 * metadata but never finds this code.
 *
 * NeoForge discovers this class at startup and instantiates it — the
 * constructor below is what runs. For now the constructor only logs a line,
 * proving the mod loaded. Later, block/item/event registration will be wired
 * up here.
 */
@Mod("shards_factions")
public class ShardsFactions {

    // The mod id, kept as a constant in one place so other classes can refer
    // to MOD_ID instead of re-typing the literal string (and risking typos).
    public static final String MOD_ID = "shards_factions";

    // A logger for this mod. SLF4J is the logging API NeoForge/Minecraft use;
    // LogUtils.getLogger() hands back a logger tagged to this class.
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[shards_factions] Test Faction should be created after this line");
        FactionManager manager = new FactionManager();
        Faction faction = manager.createFaction("TestFaction", UUID.randomUUID(), FactionType.PLAYER);
        ChunkPos chunk = new ChunkPos(0, 0);
        ClaimResult first = manager.claimChunk(chunk, faction);
        LOGGER.info("[shards_factions] claim attempt 1: {}", first);
        ClaimResult second = manager.claimChunk(chunk, faction);
        LOGGER.info("[shards_factions] (This one should fail) claim attempt 2: {}", second);
        // THROWAWAY persistence test — delete when /f new exists.
        FactionSavedData data = FactionSavedData.get(event.getServer());
        LOGGER.info("[shards_factions] loaded factions: {}",
                data.getManager().getAllFactions().size());

        data.getManager().createFaction("PersistTest", UUID.randomUUID(), FactionType.PLAYER);
        data.setDirty();
        LOGGER.info("[shards_factions] created PersistTest, marked dirty");
    }

    /**
     * NeoForge calls this constructor during mod loading. Right now it does
     * one thing: log that the mod is alive. If you see this line in the
     * server console, the mod loaded successfully.
     */
    public ShardsFactions() {
        LOGGER.info("shards_factions loaded.");
        NeoForge.EVENT_BUS.register(this);
    }
}
