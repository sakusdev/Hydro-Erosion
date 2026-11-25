package com.hydroerosion.mixin;

import com.hydroerosion.config.HydroErosionConfig;
import com.hydroerosion.water.FiniteWaterPhysicsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts vanilla water fluid ticks when finite water physics is enabled,
 * delegating active flow cells to {@link FiniteWaterPhysicsManager} without flooding the queue with flat ocean tiles.
 */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void hydroerosion$onFluidTick(Level level, BlockPos pos, FluidState state, CallbackInfo ci) {
        if (level.isClientSide || !HydroErosionConfig.ENABLE_FINITE_WATER_PHYSICS.get()) {
            return;
        }
        if (state.is(Fluids.WATER) || state.is(Fluids.FLOWING_WATER)) {
            if (level instanceof ServerLevel serverLevel) {
                FiniteWaterPhysicsManager.enqueueFromFluidTick(serverLevel, pos);
            }
            ci.cancel();
        }
    }
}
