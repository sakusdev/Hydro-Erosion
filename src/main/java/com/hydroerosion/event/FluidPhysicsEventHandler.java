package com.hydroerosion.event;

import com.hydroerosion.HydroErosionMod;
import com.hydroerosion.config.HydroErosionConfig;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Enforces finite water physics by preventing 2x2 infinite water source creation.
 */
@Mod.EventBusSubscriber(modid = HydroErosionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FluidPhysicsEventHandler {

    private FluidPhysicsEventHandler() {
    }

    @SubscribeEvent
    public static void onCreateFluidSource(BlockEvent.CreateFluidSourceEvent event) {
        if (!HydroErosionConfig.DISABLE_INFINITE_WATER.get()) {
            return;
        }
        FluidState fluidState = event.getState().getFluidState();
        if (fluidState.is(Fluids.WATER) || fluidState.is(Fluids.FLOWING_WATER)) {
            event.setResult(Event.Result.DENY);
        }
    }
}
