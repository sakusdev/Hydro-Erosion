package com.hydroerosion.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common configuration options for Hydro-Erosion hydrology and structural mechanics.
 */
public final class HydroErosionConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue DISABLE_INFINITE_WATER;
    public static final ForgeConfigSpec.BooleanValue ENABLE_FINITE_WATER_PHYSICS;
    public static final ForgeConfigSpec.IntValue FINITE_WATER_MAX_UPDATES_PER_TICK;
    public static final ForgeConfigSpec.DoubleValue FINITE_WATER_EVAPORATE_CHANCE;

    public static final ForgeConfigSpec.BooleanValue ENABLE_RAIN_ACCUMULATION;
    public static final ForgeConfigSpec.IntValue RAIN_SAMPLES_PER_CHUNK;
    public static final ForgeConfigSpec.DoubleValue RAIN_ACCUMULATION_CHANCE;

    public static final ForgeConfigSpec.BooleanValue ENABLE_PRESSURE_BREACH;
    public static final ForgeConfigSpec.IntValue BREACH_SAMPLES_PER_CHUNK;
    public static final ForgeConfigSpec.DoubleValue EROSION_SPEED_MULTIPLIER;

    public static final ForgeConfigSpec.IntValue SENSOR_MAX_DEPTH_BLOCKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("finite_water");
        DISABLE_INFINITE_WATER = builder
                .comment("Prevent vanilla 2x2 water pools from creating infinite water source blocks.")
                .define("disableInfiniteWaterSource", true);
        ENABLE_FINITE_WATER_PHYSICS = builder
                .comment("Enable volume-conserving cellular automaton finite water flow and leveling physics.")
                .define("enableFiniteWaterPhysics", true);
        FINITE_WATER_MAX_UPDATES_PER_TICK = builder
                .comment("Maximum active water cells simulated per server tick to prevent TPS drops.")
                .defineInRange("finiteWaterMaxUpdatesPerTick", 600, 10, 10000);
        FINITE_WATER_EVAPORATE_CHANCE = builder
                .comment("Probability (0.0 - 1.0) per step for isolated minimum-level (1/8) water films to evaporate.")
                .defineInRange("finiteWaterEvaporateChance", 0.15, 0.0, 1.0);
        builder.pop();

        builder.push("rain_accumulation");
        ENABLE_RAIN_ACCUMULATION = builder
                .comment("Enable bottom-up rainwater accumulation during rainfall.")
                .define("enableRainAccumulation", true);
        RAIN_SAMPLES_PER_CHUNK = builder
                .comment("Number of random columns sampled per selected chunk every rain accumulation cycle (20 ticks).")
                .defineInRange("rainSamplesPerChunk", 3, 1, 32);
        RAIN_ACCUMULATION_CHANCE = builder
                .comment("Probability (0.0 - 1.0) for a valid rain cell to accumulate water per sample.")
                .defineInRange("rainAccumulationChance", 0.15, 0.0, 1.0);
        builder.pop();

        builder.push("hydrostatic_breach");
        ENABLE_PRESSURE_BREACH = builder
                .comment("Enable progressive cracking and structural breach when water pressure exceeds block limits.")
                .define("enablePressureBreach", true);
        BREACH_SAMPLES_PER_CHUNK = builder
                .comment("Number of random positions sampled per selected chunk every breach scan cycle (10 ticks).")
                .defineInRange("breachSamplesPerChunk", 6, 1, 64);
        EROSION_SPEED_MULTIPLIER = builder
                .comment("Multiplier for how fast over-pressurized blocks accumulate cracks (higher = faster breach).")
                .defineInRange("erosionSpeedMultiplier", 1.0, 0.1, 10.0);
        builder.pop();

        builder.push("automation");
        SENSOR_MAX_DEPTH_BLOCKS = builder
                .comment("Water depth (blocks) that yields maximum redstone signal (15) on the Water Pressure Sensor.")
                .defineInRange("sensorMaxDepthBlocks", 30, 5, 128);
        builder.pop();

        SPEC = builder.build();
    }

    private HydroErosionConfig() {
    }
}
