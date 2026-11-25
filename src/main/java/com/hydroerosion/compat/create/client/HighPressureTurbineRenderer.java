package com.hydroerosion.compat.create.client;

import com.hydroerosion.compat.create.HighPressureTurbineBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders the rotating 8-blade hydro turbine rotor inside the High-Pressure Turbine housing
 * synchronized to the block entity's generated RPM.
 */
public class HighPressureTurbineRenderer extends KineticBlockEntityRenderer<HighPressureTurbineBlockEntity> {

    public HighPressureTurbineRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(HighPressureTurbineBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(CreateClientCompat.TURBINE_ROTOR, state);
    }
}
