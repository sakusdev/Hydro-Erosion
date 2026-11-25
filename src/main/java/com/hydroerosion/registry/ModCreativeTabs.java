package com.hydroerosion.registry;

import com.hydroerosion.HydroErosionMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Creative mode tab registry for Hydro-Erosion.
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HydroErosionMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> HYDROEROSION_TAB = CREATIVE_MODE_TABS.register(
            "hydroerosion_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + HydroErosionMod.MOD_ID))
                    .icon(() -> new ItemStack(ModItems.PRESSURE_GAUGE.get()))
                    .displayItems((parameters, output) ->
                            ModItems.ITEMS.getEntries().forEach(entry -> output.accept(entry.get())))
                    .build()
    );

    private ModCreativeTabs() {
    }
}
