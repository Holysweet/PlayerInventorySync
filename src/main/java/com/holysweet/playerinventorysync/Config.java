package com.holysweet.playerinventorysync;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.ModContainer;

public class Config {
    public static final ModConfigSpec COMMON_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_WATCHDOG_RESYNC;
    public static final ModConfigSpec.IntValue WATCHDOG_TICKS;
    public static final ModConfigSpec.IntValue MAX_RESYNCS_PER_TICK;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        ENABLE_WATCHDOG_RESYNC = b
                .comment("After a rejected click/use, perform a micro resync on the next tick as a safeguard.")
                .define("watchdogEnabled", true);

        WATCHDOG_TICKS = b
                .comment("How many ticks to keep a player marked 'suspect' for a follow-up resync.")
                .defineInRange("watchdogTicks", 2, 1, 10);

        MAX_RESYNCS_PER_TICK = b
                .comment("Rate limiter per player per tick to avoid packet spam.")
                .defineInRange("maxResyncsPerTick", 3, 1, 10);

        COMMON_SPEC = b.build();
    }

    public static void init() {
        // In NeoForge 21.x, register via the ModContainer (not ModLoadingContext#registerConfig)
        ModContainer container = ModLoadingContext.get().getActiveContainer();
        container.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "playerinventorysync-common.toml");
    }
}
