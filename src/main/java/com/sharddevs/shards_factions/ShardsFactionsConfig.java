package com.sharddevs.shards_factions;

import net.neoforged.neoforge.common.ModConfigSpec;

// ===========================================================================
// ShardsFactionsConfig — server-side mod config (NeoForge ModConfigSpec).
//
// Registered as ModConfig.Type.SERVER in ShardsFactions' constructor, so it
// lives in config/shards_factions-server.toml — server-authoritative, never
// client-synced. Edit the .toml and reload; no recompile to change values.
// ===========================================================================
public class ShardsFactionsConfig {

    // The built spec, handed to modContainer.registerConfig(...).
    public static final ModConfigSpec SPEC;

    // -----------------------------------------------------------------------
    // OBELISK
    // -----------------------------------------------------------------------

    // The /f obelisk give cooldown, in milliseconds (§39.3). Continuous —
    // runs whether or not an obelisk is placed. Default 1 week; playtest
    // value is set in the .toml, not here.
    public static final ModConfigSpec.LongValue OBELISK_GIVE_COOLDOWN_MS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("obelisk");

        OBELISK_GIVE_COOLDOWN_MS = builder
                .comment("Cooldown between `/f obelisk give` uses, in milliseconds.",
                        "Runs continuously regardless of whether an obelisk is placed.",
                        "Default 604800000 = 1 week. Playtest: 3600000 = 1 hour.")
                .defineInRange("obeliskGiveCooldownMs", 604_800_000L, 0L, Long.MAX_VALUE);

        builder.pop();

        SPEC = builder.build();
    }
}