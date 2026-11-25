package com.hydroerosion.blockentity;

import com.hydroerosion.block.WaterPressureSensorBlock;
import com.hydroerosion.config.HydroErosionConfig;
import com.hydroerosion.pressure.WaterPressureCalculator;
import com.hydroerosion.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Periodically measures adjacent water head and updates the sensor's redstone output (0-15).
 */
public class WaterPressureSensorBlockEntity extends BlockEntity {

    private int lastWaterHead = 0;

    public WaterPressureSensorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WATER_PRESSURE_SENSOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WaterPressureSensorBlockEntity be) {
        if ((level.getGameTime() % 10L) != 0L) {
            return;
        }
        int head = WaterPressureCalculator.getMaxAdjacentWaterHead(level, pos);
        int maxDepth = Math.max(1, HydroErosionConfig.SENSOR_MAX_DEPTH_BLOCKS.get());
        int targetPower = head <= 0
                ? 0
                : Mth.clamp(Math.max(1, Math.round(15.0f * head / (float) maxDepth)), 1, 15);

        if (be.lastWaterHead != head) {
            be.lastWaterHead = head;
            be.setChanged();
        }

        if (state.getValue(WaterPressureSensorBlock.POWER) != targetPower) {
            level.setBlock(pos, state.setValue(WaterPressureSensorBlock.POWER, targetPower), Block.UPDATE_ALL);
            level.updateNeighborsAt(pos, state.getBlock());
            for (Direction dir : Direction.values()) {
                level.updateNeighborsAt(pos.relative(dir), state.getBlock());
            }
        }
    }

    public int getLastWaterHead() {
        return lastWaterHead;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("LastWaterHead", lastWaterHead);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        lastWaterHead = tag.getInt("LastWaterHead");
    }
}
