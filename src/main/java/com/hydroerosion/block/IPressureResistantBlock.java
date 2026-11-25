package com.hydroerosion.block;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Implemented by engineered blocks that specify a custom hydrostatic pressure resistance limit.
 */
public interface IPressureResistantBlock {

    /**
     * Returns the maximum hydrostatic pressure in kPa that this block can withstand before cracking.
     */
    float getMaxPressureKpa(BlockState state);
}
