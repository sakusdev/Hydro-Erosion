package com.hydroerosion.registry;

import com.hydroerosion.HydroErosionMod;
import com.hydroerosion.blockentity.DeepPressureIntakeBlockEntity;
import com.hydroerosion.blockentity.WaterPressureSensorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Core block entity registry for Hydro-Erosion.
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HydroErosionMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<WaterPressureSensorBlockEntity>> WATER_PRESSURE_SENSOR =
            BLOCK_ENTITIES.register("water_pressure_sensor", () ->
                    BlockEntityType.Builder.of(
                            WaterPressureSensorBlockEntity::new,
                            ModBlocks.WATER_PRESSURE_SENSOR.get()
                    ).build(null));

    public static final RegistryObject<BlockEntityType<DeepPressureIntakeBlockEntity>> DEEP_PRESSURE_INTAKE =
            BLOCK_ENTITIES.register("deep_pressure_intake", () ->
                    BlockEntityType.Builder.of(
                            DeepPressureIntakeBlockEntity::new,
                            ModBlocks.DEEP_PRESSURE_INTAKE.get()
                    ).build(null));

    private ModBlockEntities() {
    }
}
