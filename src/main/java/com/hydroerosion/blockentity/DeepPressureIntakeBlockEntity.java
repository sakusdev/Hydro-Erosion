package com.hydroerosion.blockentity;

import com.hydroerosion.compat.CompatManager;
import com.hydroerosion.pressure.WaterPressureCalculator;
import com.hydroerosion.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Buffers and pushes pressurized cooling water into adjacent fluid networks.
 * At water depth >= 40 blocks, multiplies coolant throughput and triggers Mekanism Heavy Water bonuses.
 */
public class DeepPressureIntakeBlockEntity extends BlockEntity {

    public static final int HIGH_PRESSURE_DEPTH_THRESHOLD = 40;
    private static final int BASE_TRANSFER_PER_CYCLE_MB = 1000;

    private final FluidTank coolantTank = new FluidTank(16000, fluid -> fluid.getFluid() == Fluids.WATER);
    private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> coolantTank);

    private boolean upgraded = false;
    private int lastWaterHead = 0;

    public DeepPressureIntakeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DEEP_PRESSURE_INTAKE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DeepPressureIntakeBlockEntity be) {
        if ((level.getGameTime() % 10L) != 0L) {
            return;
        }
        int head = WaterPressureCalculator.getMaxAdjacentWaterHead(level, pos);
        be.lastWaterHead = head;
        if (head <= 0) {
            return;
        }

        // Standard water supply below 40m; high-pressure coolant multiplier at >= 40m.
        float multiplier = head >= HIGH_PRESSURE_DEPTH_THRESHOLD
                ? (2.0f + (head - HIGH_PRESSURE_DEPTH_THRESHOLD) / 20.0f) * (be.upgraded ? 2.0f : 1.0f)
                : (be.upgraded ? 1.5f : 1.0f);

        int generatedMb = Math.round(BASE_TRANSFER_PER_CYCLE_MB * multiplier);
        be.coolantTank.fill(new FluidStack(Fluids.WATER, generatedMb), IFluidHandler.FluidAction.EXECUTE);

        // Push pressurized cooling water into adjacent pipes, boilers, reactors, or tanks.
        pushFluidToNeighbors(level, pos, be.coolantTank);

        // Notify optional Mekanism compatibility hook for Heavy Water & machine bonuses at >= 40m.
        CompatManager.onDeepPressureIntakeTick(level, pos, head, be.upgraded);
        be.setChanged();
    }

    private static void pushFluidToNeighbors(Level level, BlockPos pos, FluidTank tank) {
        if (tank.isEmpty()) {
            return;
        }
        for (Direction dir : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
            if (neighbor == null || neighbor instanceof DeepPressureIntakeBlockEntity) {
                continue;
            }
            neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, dir.getOpposite()).ifPresent(handler -> {
                FluidStack available = tank.getFluid();
                if (!available.isEmpty()) {
                    int accepted = handler.fill(available.copy(), IFluidHandler.FluidAction.EXECUTE);
                    if (accepted > 0) {
                        tank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
                    }
                }
            });
            if (tank.isEmpty()) {
                break;
            }
        }
    }

    public boolean isUpgraded() {
        return upgraded;
    }

    public void setUpgraded(boolean upgraded) {
        this.upgraded = upgraded;
        setChanged();
    }

    public int getLastWaterHead() {
        return lastWaterHead;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("HighPressureUpgraded", upgraded);
        tag.putInt("LastWaterHead", lastWaterHead);
        coolantTank.writeToNBT(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        upgraded = tag.getBoolean("HighPressureUpgraded");
        lastWaterHead = tag.getInt("LastWaterHead");
        coolantTank.readFromNBT(tag);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCap.invalidate();
    }
}
