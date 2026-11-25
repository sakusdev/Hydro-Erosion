package com.hydroerosion;

import com.hydroerosion.compat.CompatManager;
import com.hydroerosion.config.HydroErosionConfig;
import com.hydroerosion.registry.ModBlockEntities;
import com.hydroerosion.registry.ModBlocks;
import com.hydroerosion.registry.ModCreativeTabs;
import com.hydroerosion.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Main entry point for the Hydro-Erosion mod.
 */
@Mod(HydroErosionMod.MOD_ID)
public class HydroErosionMod {

    public static final String MOD_ID = "hydroerosion";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HydroErosionMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, HydroErosionConfig.SPEC);

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        // Register soft-dependency modules inside the mod constructor so Forge registration events are never missed.
        if (ModList.get().isLoaded("create")) {
            CompatManager.initCreate(modEventBus);
        }
        if (ModList.get().isLoaded("mekanism")) {
            CompatManager.initMekanism(modEventBus);
        }

        LOGGER.info("Hydro-Erosion initialized (Create loaded: {}, Mekanism loaded: {})",
                CompatManager.isCreateLoaded(), CompatManager.isMekanismLoaded());
    }
}
