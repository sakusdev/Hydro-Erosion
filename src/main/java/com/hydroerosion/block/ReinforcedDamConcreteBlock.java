package com.hydroerosion.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * High-strength reinforced concrete block engineered to withstand extreme dam water pressure.
 */
public class ReinforcedDamConcreteBlock extends Block implements IPressureResistantBlock {

    public static final float MAX_PRESSURE_KPA = 2500.0f;

    public ReinforcedDamConcreteBlock(Properties properties) {
        super(properties);
    }

    @Override
    public float getMaxPressureKpa(BlockState state) {
        return MAX_PRESSURE_KPA;
    }
}
