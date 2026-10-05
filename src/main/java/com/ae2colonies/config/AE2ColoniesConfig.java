package com.ae2colonies.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class AE2ColoniesConfig {
    public static final ModConfigSpec SPEC;
    
    // Crafting
    public static final ModConfigSpec.IntValue CRAFTING_TIMEOUT_TICKS;
    public static final ModConfigSpec.IntValue CRAFTING_FAILURE_COOLDOWN_MS;
    public static final ModConfigSpec.IntValue MAX_CONCURRENT_CRAFTING_JOBS;
    public static final ModConfigSpec.IntValue MAX_CRAFT_DEPTH;
    
    // Performance
    public static final ModConfigSpec.IntValue ITEM_CACHE_DURATION_TICKS;
    public static final ModConfigSpec.IntValue TERMINAL_RECHECK_INTERVAL_TICKS;
    
    // Features
    public static final ModConfigSpec.BooleanValue ENABLE_REDSTONE_CONTROL;
    public static final ModConfigSpec.BooleanValue ENABLE_DO_BLOCK_SYNTHESIS;
    public static final ModConfigSpec.BooleanValue ENABLE_SUB_REQUEST_DELEGATION;
    public static final ModConfigSpec.IntValue SUB_REQUEST_TIMEOUT_TICKS;
    public static final ModConfigSpec.IntValue MAX_CONCURRENT_DELEGATED_JOBS;
    
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        
        builder.comment("Crafting Settings").push("crafting");
        CRAFTING_TIMEOUT_TICKS = builder
            .comment("Ticks before a pending crafting calculation times out (default: 200 = 10 seconds)")
            .defineInRange("craftingTimeoutTicks", 200, 20, 6000);
        CRAFTING_FAILURE_COOLDOWN_MS = builder
            .comment("Milliseconds before a failed crafting item can be retried (default: 30000 = 30 seconds)")
            .defineInRange("craftingFailureCooldownMs", 30000, 1000, 300000);
        MAX_CONCURRENT_CRAFTING_JOBS = builder
            .comment("Maximum concurrent AE2 crafting jobs per terminal (default: 8)")
            .defineInRange("maxConcurrentCraftingJobs", 8, 1, 64);
        MAX_CRAFT_DEPTH = builder
            .comment("Maximum recursion depth when checking if items can be crafted (default: 6)")
            .defineInRange("maxCraftDepth", 6, 1, 20);
        builder.pop();
        
        builder.comment("Performance Settings").push("performance");
        ITEM_CACHE_DURATION_TICKS = builder
            .comment("Ticks to cache the ME network item list (default: 20 = 1 second)")
            .defineInRange("itemCacheDurationTicks", 20, 1, 200);
        TERMINAL_RECHECK_INTERVAL_TICKS = builder
            .comment("Ticks between warehouse link re-checks for linked terminals (default: 200 = 10 seconds)")
            .defineInRange("terminalRecheckIntervalTicks", 200, 40, 2000);
        builder.pop();
        
        builder.comment("Feature Toggles").push("features");
        ENABLE_REDSTONE_CONTROL = builder
            .comment("Allow redstone signal to disable Colony Terminal (default: true)")
            .define("enableRedstoneControl", true);
        ENABLE_DO_BLOCK_SYNTHESIS = builder
            .comment("Allow on-the-fly Domum Ornamentum block synthesis (default: true)")
            .define("enableDOBlockSynthesis", true);
        ENABLE_SUB_REQUEST_DELEGATION = builder
            .comment("Allow AE2 to delegate missing crafting ingredients to colony workers (default: true)")
            .define("enableSubRequestDelegation", true);
        SUB_REQUEST_TIMEOUT_TICKS = builder
            .comment("Ticks before an unfulfilled colony sub-request times out (default: 6000 = 5 minutes)")
            .defineInRange("subRequestTimeoutTicks", 6000, 600, 72000);
        MAX_CONCURRENT_DELEGATED_JOBS = builder
            .comment("Maximum concurrent delegated jobs per terminal (default: 4)")
            .defineInRange("maxConcurrentDelegatedJobs", 4, 1, 16);
        builder.pop();
        
        SPEC = builder.build();
    }
}
