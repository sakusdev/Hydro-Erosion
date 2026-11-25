package com.hydroerosion.compat.create.client;

import com.hydroerosion.HydroErosionMod;
import com.hydroerosion.compat.create.CreateCompatModule;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Client-only registration for Create kinetic PartialModels and BlockEntityRenderers.
 */
public final class CreateClientCompat {

    public static final PartialModel TURBINE_ROTOR = PartialModel.of(
            new ResourceLocation(HydroErosionMod.MOD_ID, "block/high_pressure_turbine_rotor")
    );

    private CreateClientCompat() {
    }

    public static void register(IEventBus modEventBus) {
        // Ensure PartialModel class initializes early before model baking.
        TURBINE_ROTOR.modelLocation();
        modEventBus.addListener(CreateClientCompat::onRegisterRenderers);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                CreateCompatModule.HIGH_PRESSURE_TURBINE_BE.get(),
                HighPressureTurbineRenderer::new
        );
    }
}
