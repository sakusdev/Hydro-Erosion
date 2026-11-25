package com.hydroerosion.compat.mekanism;

import com.hydroerosion.HydroErosionMod;
import com.hydroerosion.blockentity.DeepPressureIntakeBlockEntity;
import com.hydroerosion.pressure.WaterPressureCalculator;
import com.hydroerosion.water.LoadedChunkTracker;
import mekanism.api.Upgrade;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mekanism soft-dependency compatibility module.
 * Grants Heavy Water synthesis & pressurized cooling water bonuses when operating at >= 40m water depth.
 */
public final class MekanismCompatModule {

    private static final ResourceLocation HEAVY_WATER_ID = new ResourceLocation("mekanism", "heavy_water");
    private static final ResourceLocation ELECTRIC_PUMP_ID = new ResourceLocation("mekanism", "electric_pump");

    private static final Map<net.minecraft.resources.ResourceKey<Level>, Set<Long>> BOOSTED_MEKANISM_MACHINES =
            new ConcurrentHashMap<>();

    private MekanismCompatModule() {
    }

    public static void register(IEventBus modEventBus) {
        MinecraftForge.EVENT_BUS.register(MekanismCompatModule.class);
        HydroErosionMod.LOGGER.info("Mekanism deep-pressure Heavy Water & Coolant hook enabled (>= 40m depth).");
    }

    /**
     * Called every 10 ticks by {@link DeepPressureIntakeBlockEntity} when Mekanism is loaded.
     * At water depth >= 40 blocks, produces Heavy Water and pushes it to adjacent fluid pipes/tanks.
     */
    public static void onDeepPressureIntakeTick(Level level, BlockPos pos, int waterHead, boolean upgraded) {
        if (level.isClientSide || waterHead < DeepPressureIntakeBlockEntity.HIGH_PRESSURE_DEPTH_THRESHOLD) {
            return;
        }
        Fluid heavyWater = ForgeRegistries.FLUIDS.getValue(HEAVY_WATER_ID);
        if (heavyWater == null || heavyWater == Fluids.EMPTY) {
            return;
        }

        int bonusMb = (25 + (waterHead - DeepPressureIntakeBlockEntity.HIGH_PRESSURE_DEPTH_THRESHOLD) * 2)
                * (upgraded ? 2 : 1);
        FluidStack stack = new FluidStack(heavyWater, bonusMb);

        for (Direction dir : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
            if (neighbor == null || neighbor instanceof DeepPressureIntakeBlockEntity) {
                continue;
            }
            neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, dir.getOpposite()).ifPresent(handler -> {
                if (stack.getAmount() > 0) {
                    int filled = handler.fill(stack.copy(), IFluidHandler.FluidAction.EXECUTE);
                    stack.shrink(filled);
                }
            });
            if (stack.isEmpty()) {
                break;
            }
        }
    }

    /**
     * Allows players to apply a High-Pressure Upgrade directly onto a Mekanism machine (e.g. Electric Pump).
     */
    public static boolean onHighPressureUpgradeUsed(Level level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null || !"mekanism".equals(blockId.getNamespace())) {
            return false;
        }
        BOOSTED_MEKANISM_MACHINES
                .computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet())
                .add(pos.asLong());
        player.displayClientMessage(Component.literal(
                "[Hydro-Erosion x Mekanism] High-Pressure link active on " + blockId + " (bonus at >= 40m depth).")
                .withStyle(ChatFormatting.AQUA), false);
        return true;
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(event.getPlacedBlock().getBlock());
        if (ELECTRIC_PUMP_ID.equals(id)) {
            BOOSTED_MEKANISM_MACHINES
                    .computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet())
                    .add(event.getPos().asLong());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if ((level.getGameTime() % 10L) != 0L) {
            return;
        }

        // Periodically discover any pre-existing Mekanism electric pumps in loaded chunks.
        RandomSource random = level.random;
        List<ChunkPos> chunks = LoadedChunkTracker.sampleLoadedChunks(level, 16, random);
        for (ChunkPos chunk : chunks) {
            int x = chunk.getMinBlockX() + random.nextInt(16);
            int z = chunk.getMinBlockZ() + random.nextInt(16);
            BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).below();
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(level.getBlockState(top).getBlock());
            if (ELECTRIC_PUMP_ID.equals(id)) {
                BOOSTED_MEKANISM_MACHINES
                        .computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet())
                        .add(top.asLong());
            }
        }

        Set<Long> tracked = BOOSTED_MEKANISM_MACHINES.get(level.dimension());
        if (tracked == null || tracked.isEmpty()) {
            return;
        }

        Fluid heavyWater = ForgeRegistries.FLUIDS.getValue(HEAVY_WATER_ID);
        Iterator<Long> it = tracked.iterator();
        while (it.hasNext()) {
            BlockPos pos = BlockPos.of(it.next());
            if (!level.isLoaded(pos)) {
                continue;
            }
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
            if (id == null || !"mekanism".equals(id.getNamespace())) {
                it.remove();
                continue;
            }

            int head = Math.max(
                    WaterPressureCalculator.getMaxAdjacentWaterHead(level, pos),
                    WaterPressureCalculator.getMaxAdjacentWaterHead(level, pos.below())
            );
            if (head < DeepPressureIntakeBlockEntity.HIGH_PRESSURE_DEPTH_THRESHOLD) {
                continue;
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) {
                continue;
            }

            boolean filterMode = hasMekanismFilterUpgrade(be);
            if (filterMode && heavyWater != null && heavyWater != Fluids.EMPTY) {
                int hwBonus = 20 + (head - DeepPressureIntakeBlockEntity.HIGH_PRESSURE_DEPTH_THRESHOLD) * 2;
                pushBonusFluid(level, pos, new FluidStack(heavyWater, hwBonus));
            } else {
                int coolantBonus = 150 + (head - DeepPressureIntakeBlockEntity.HIGH_PRESSURE_DEPTH_THRESHOLD) * 15;
                pushBonusFluid(level, pos, new FluidStack(Fluids.WATER, coolantBonus));
            }
        }
    }

    private static boolean hasMekanismFilterUpgrade(BlockEntity be) {
        CompoundTag nbt = be.saveWithoutMetadata();
        if (nbt.contains("componentUpgrade")) {
            Map<Upgrade, Integer> map = Upgrade.buildMap(nbt.getCompound("componentUpgrade"));
            return map.getOrDefault(Upgrade.FILTER, 0) > 0;
        }
        return false;
    }

    private static void pushBonusFluid(ServerLevel level, BlockPos sourcePos, FluidStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        for (Direction dir : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(sourcePos.relative(dir));
            if (neighbor == null) {
                continue;
            }
            neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, dir.getOpposite()).ifPresent(handler -> {
                if (stack.getAmount() > 0) {
                    int filled = handler.fill(stack.copy(), IFluidHandler.FluidAction.EXECUTE);
                    stack.shrink(filled);
                }
            });
            if (stack.isEmpty()) {
                break;
            }
        }
    }
}
