package com.hydroerosion.compat.create;

import com.hydroerosion.HydroErosionMod;
import com.hydroerosion.block.ReinforcedDamConcreteBlock;
import com.hydroerosion.registry.ModCreativeTabs;
import com.simibubi.create.api.stress.BlockStressValues;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Create soft-dependency compatibility module.
 * Loaded only when Create is present, invoked from HydroErosionMod constructor.
 */
public final class CreateCompatModule {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, HydroErosionMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, HydroErosionMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HydroErosionMod.MOD_ID);

    public static final RegistryObject<Block> HIGH_PRESSURE_TURBINE = BLOCKS.register(
            "high_pressure_turbine",
            () -> new HighPressureTurbineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(8.0f, ReinforcedDamConcreteBlock.MAX_PRESSURE_KPA)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .noLootTable()
                    .sound(SoundType.METAL))
    );

    public static final RegistryObject<Item> HIGH_PRESSURE_TURBINE_ITEM = ITEMS.register(
            "high_pressure_turbine",
            () -> new BlockItem(HIGH_PRESSURE_TURBINE.get(), new Item.Properties())
    );

    public static final RegistryObject<BlockEntityType<HighPressureTurbineBlockEntity>> HIGH_PRESSURE_TURBINE_BE =
            BLOCK_ENTITIES.register("high_pressure_turbine", () ->
                    BlockEntityType.Builder.of(
                            (pos, state) -> new HighPressureTurbineBlockEntity(
                                    CreateCompatModule.HIGH_PRESSURE_TURBINE_BE.get(), pos, state),
                            HIGH_PRESSURE_TURBINE.get()
                    ).build(null));

    private CreateCompatModule() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);

        modEventBus.addListener(CreateCompatModule::onCommonSetup);
        modEventBus.addListener(CreateCompatModule::onBuildCreativeTab);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.hydroerosion.compat.create.client.CreateClientCompat.register(modEventBus));
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() ->
                BlockStressValues.CAPACITIES.register(HIGH_PRESSURE_TURBINE.get(), () -> 64.0)
        );
    }

    private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModCreativeTabs.HYDROEROSION_TAB.getKey()) {
            event.accept(HIGH_PRESSURE_TURBINE_ITEM.get());
        }
    }
}
