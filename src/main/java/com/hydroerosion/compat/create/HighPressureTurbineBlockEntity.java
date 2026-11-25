package com.hydroerosion.compat.create;

import com.hydroerosion.pressure.WaterPressureCalculator;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * Generates Create rotational speed and hydrostatic-scaled stress capacity (SU)
 * proportional to the water head acting on the turbine housing.
 */
public class HighPressureTurbineBlockEntity extends GeneratingKineticBlockEntity {

    private int waterHead = 0;

    public HighPressureTurbineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        if ((level.getGameTime() % 20L) == 0L) {
            int measured = measureIntakeHead();
            if (measured != waterHead) {
                waterHead = measured;
                updateGeneratedRotation();
                notifyStressCapacityChange(calculateAddedStressCapacity());
                sendData();
            }
        }
    }

    private int measureIntakeHead() {
        if (level == null) {
            return 0;
        }
        Direction shaftDir = getBlockState().getValue(HighPressureTurbineBlock.FACING);
        int maxHead = 0;
        for (Direction dir : Direction.values()) {
            if (dir == shaftDir) {
                continue;
            }
            BlockPos neighbor = worldPosition.relative(dir);
            if (WaterPressureCalculator.isWater(level, neighbor)) {
                int head = WaterPressureCalculator.calculateWaterHeadAtWater(level, neighbor);
                if (head > maxHead) {
                    maxHead = head;
                }
            }
        }
        return maxHead;
    }

    @Override
    public float getGeneratedSpeed() {
        if (waterHead <= 0) {
            return 0.0f;
        }
        return Mth.clamp(16 + (waterHead / 2) * 4, 16, 128);
    }

    @Override
    public float calculateAddedStressCapacity() {
        if (waterHead <= 0) {
            this.lastCapacityProvided = 0.0f;
            return 0.0f;
        }
        float capacityPerRpm = 32.0f + (waterHead * 12.0f);
        this.lastCapacityProvided = capacityPerRpm;
        return capacityPerRpm;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        float pressureKpa = WaterPressureCalculator.calculatePressureKpa(waterHead);
        tooltip.add(Component.literal("    Hydrostatic Head: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format(Locale.ROOT, "%d m (%.1f kPa)", waterHead, pressureKpa))
                        .withStyle(ChatFormatting.AQUA)));
        return true || added;
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        compound.putInt("WaterHead", waterHead);
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        waterHead = compound.getInt("WaterHead");
    }
}
