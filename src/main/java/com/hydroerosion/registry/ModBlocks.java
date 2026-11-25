package com.hydroerosion.registry;

import com.hydroerosion.HydroErosionMod;
import com.hydroerosion.block.DeepPressureIntakeBlock;
import com.hydroerosion.block.ReinforcedDamConcreteBlock;
import com.hydroerosion.block.WaterPressureSensorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Core block registry for Hydro-Erosion.
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, HydroErosionMod.MOD_ID);

    public static final RegistryObject<Block> REINFORCED_DAM_CONCRETE = BLOCKS.register(
            "reinforced_dam_concrete",
            () -> new ReinforcedDamConcreteBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(12.0f, 1200.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE))
    );

    public static final RegistryObject<Block> WATER_PRESSURE_SENSOR = BLOCKS.register(
            "water_pressure_sensor",
            () -> new WaterPressureSensorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(6.0f, 600.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.METAL))
    );

    public static final RegistryObject<Block> DEEP_PRESSURE_INTAKE = BLOCKS.register(
            "deep_pressure_intake",
            () -> new DeepPressureIntakeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(8.0f, 900.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .sound(SoundType.METAL))
    );

    private ModBlocks() {
    }
}
