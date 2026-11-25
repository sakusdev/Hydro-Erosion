package com.hydroerosion.registry;

import com.hydroerosion.HydroErosionMod;
import com.hydroerosion.item.HighPressureUpgradeItem;
import com.hydroerosion.item.PressureGaugeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Core item registry for Hydro-Erosion.
 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, HydroErosionMod.MOD_ID);

    public static final RegistryObject<Item> PRESSURE_GAUGE = ITEMS.register("pressure_gauge",
            () -> new PressureGaugeItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> HIGH_PRESSURE_UPGRADE = ITEMS.register("high_pressure_upgrade",
            () -> new HighPressureUpgradeItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> REINFORCED_DAM_CONCRETE = ITEMS.register("reinforced_dam_concrete",
            () -> new BlockItem(ModBlocks.REINFORCED_DAM_CONCRETE.get(), new Item.Properties()));

    public static final RegistryObject<Item> WATER_PRESSURE_SENSOR = ITEMS.register("water_pressure_sensor",
            () -> new BlockItem(ModBlocks.WATER_PRESSURE_SENSOR.get(), new Item.Properties()));

    public static final RegistryObject<Item> DEEP_PRESSURE_INTAKE = ITEMS.register("deep_pressure_intake",
            () -> new BlockItem(ModBlocks.DEEP_PRESSURE_INTAKE.get(), new Item.Properties()));

    private ModItems() {
    }
}
