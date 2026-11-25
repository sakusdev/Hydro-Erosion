package com.hydroerosion.compat.create;

import com.hydroerosion.block.IPressureResistantBlock;
import com.hydroerosion.block.ReinforcedDamConcreteBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.Collections;
import java.util.List;

/**
 * Create kinetic generator powered by hydrostatic water head.
 * Outputs shaft rotation along its FACING direction.
 */
public class HighPressureTurbineBlock extends DirectionalKineticBlock
        implements IBE<HighPressureTurbineBlockEntity>, IPressureResistantBlock {

    public HighPressureTurbineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public float getMaxPressureKpa(BlockState state) {
        return ReinforcedDamConcreteBlock.MAX_PRESSURE_KPA;
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return Collections.singletonList(new ItemStack(this));
    }

    @Override
    public Class<HighPressureTurbineBlockEntity> getBlockEntityClass() {
        return HighPressureTurbineBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HighPressureTurbineBlockEntity> getBlockEntityType() {
        return CreateCompatModule.HIGH_PRESSURE_TURBINE_BE.get();
    }
}
