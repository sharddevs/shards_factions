package com.sharddevs.shards_factions;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ShardsFactionsConfig {

    public static final ModConfigSpec SPEC;

    // The /f obelisk give cooldown, in milliseconds (§39.3).
    // Default 1 week. Playtest value set in the .toml, not here.
    public static final ModConfigSpec.LongValue OBELISK_GIVE_COOLDOWN_MS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("obelisk");

        OBELISK_GIVE_COOLDOWN_MS = builder
                .comment("Cooldown between `/f obelisk give` uses, in milliseconds.",
                        "Runs continuously regardless of whether an obelisk is placed.",
                        "Default 604800000 = 1 week. Playtest: 3600000 = 1 hour.")
                .defineInRange("obeliskGiveCooldownMs", 604_800_000L, 0L, Long.MAX_VALUE);
                builder.defineInRange("obeliskGiveCooldownMs", 604_800_000L, 0L, Long.MAX_VALUE);

        builder.pop();

        SPEC = builder.build();
    }
}