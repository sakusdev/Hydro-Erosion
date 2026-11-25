package com.hydroerosion.pressure;

import com.hydroerosion.HydroErosionMod;
import com.hydroerosion.config.HydroErosionConfig;
import com.hydroerosion.water.FiniteWaterPhysicsManager;
import com.hydroerosion.water.LoadedChunkTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Monitors hydrostatic pressure on retaining blocks, renders progressive cracking,
 * and triggers dam breaches when pressure exceeds structural limits.
 */
@Mod.EventBusSubscriber(modid = HydroErosionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BreachManager {

    private static final int TICK_INTERVAL = 10;
    private static final int MAX_CHUNKS_PER_CYCLE = 32;
    private static final int MAX_PRIORITY_PER_CYCLE = 64;
    private static final int BREAKER_ID_BASE = 0x48594400; // Deterministic virtual breaker id prefix

    private static final Map<ResourceKey<Level>, Queue<BlockPos>> PRIORITY_QUEUES = new ConcurrentHashMap<>();

    private BreachManager() {
    }

    /**
     * Queues a block position for immediate hydrostatic stress inspection on the next cycle.
     */
    public static void queueForInspection(ServerLevel level, BlockPos pos) {
        PRIORITY_QUEUES
                .computeIfAbsent(level.dimension(), k -> new ConcurrentLinkedQueue<>())
                .add(pos.immutable());
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            queueForInspection(level, event.getPos());
            for (Direction dir : Direction.values()) {
                queueForInspection(level, event.getPos().relative(dir));
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (!HydroErosionConfig.ENABLE_PRESSURE_BREACH.get()) {
            return;
        }
        if ((level.getGameTime() % TICK_INTERVAL) != 0) {
            return;
        }

        ErosionSavedData data = ErosionSavedData.get(level);
        RandomSource random = level.random;

        // 1. Process priority queue (newly placed blocks, cascading breach neighbors, testdam plugs).
        Queue<BlockPos> queue = PRIORITY_QUEUES.get(level.dimension());
        if (queue != null) {
            for (int i = 0; i < MAX_PRIORITY_PER_CYCLE; i++) {
                BlockPos queued = queue.poll();
                if (queued == null) {
                    break;
                }
                inspectAndTrack(level, data, queued);
            }
        }

        // 2. Sample loaded chunks around water bodies to discover newly stressed retaining walls.
        List<ChunkPos> chunks = LoadedChunkTracker.sampleLoadedChunks(level, MAX_CHUNKS_PER_CYCLE, random);
        int samplesPerChunk = HydroErosionConfig.BREACH_SAMPLES_PER_CHUNK.get();
        for (ChunkPos chunkPos : chunks) {
            for (int i = 0; i < samplesPerChunk; i++) {
                int x = chunkPos.getMinBlockX() + random.nextInt(16);
                int z = chunkPos.getMinBlockZ() + random.nextInt(16);
                BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
                int depthBias = random.nextInt(24);
                BlockPos samplePos = new BlockPos(x, Math.max(level.getMinBuildHeight() + 1, top.getY() - depthBias), z);
                if (WaterPressureCalculator.isWater(level, samplePos)) {
                    for (Direction dir : Direction.values()) {
                        inspectAndTrack(level, data, samplePos.relative(dir));
                    }
                } else {
                    inspectAndTrack(level, data, samplePos);
                }
            }
        }

        // 3. Advance fatigue on all actively stressed blocks.
        List<Long> keys = new ArrayList<>(data.getEntriesView().keySet());
        double speedMultiplier = HydroErosionConfig.EROSION_SPEED_MULTIPLIER.get();

        for (long packed : keys) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            float limitKpa = WaterPressureCalculator.getBlockPressureLimitKpa(state);
            if (state.isAir() || !state.getFluidState().isEmpty() || Float.isInfinite(limitKpa) || limitKpa <= 0.0f) {
                clearCrackProgress(level, pos);
                data.remove(packed);
                continue;
            }

            int head = WaterPressureCalculator.getMaxAdjacentWaterHead(level, pos);
            float pressureKpa = WaterPressureCalculator.calculatePressureKpa(head);

            if (pressureKpa <= limitKpa || !hasLowPressureOutlet(level, pos, head)) {
                float currentDmg = data.getDamage(pos) - 5.0f;
                if (currentDmg <= 0.0f) {
                    clearCrackProgress(level, pos);
                    data.remove(packed);
                } else {
                    data.setDamage(pos, currentDmg);
                    level.destroyBlockProgress(breakerId(pos), pos, Mth.clamp((int) (currentDmg / 10.0f), 0, 9));
                }
                continue;
            }

            float ratio = pressureKpa / limitKpa;
            float delta = (float) (Math.min(25.0f, ratio * 5.0f) * speedMultiplier);
            float nextDamage = data.getDamage(pos) + delta;

            if (nextDamage >= 100.0f) {
                triggerBreach(level, data, pos, state);
            } else {
                data.setDamage(pos, nextDamage);
                int stage = Mth.clamp((int) (nextDamage / 10.0f), 0, 9);
                level.destroyBlockProgress(breakerId(pos), pos, stage);
                spawnLeakEffects(level, pos, state, random);
            }
        }
    }

    private static void inspectAndTrack(ServerLevel level, ErosionSavedData data, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        float limitKpa = WaterPressureCalculator.getBlockPressureLimitKpa(state);
        if (state.isAir() || !state.getFluidState().isEmpty() || Float.isInfinite(limitKpa) || limitKpa <= 0.0f) {
            return;
        }
        int head = WaterPressureCalculator.getMaxAdjacentWaterHead(level, pos);
        float pressureKpa = WaterPressureCalculator.calculatePressureKpa(head);
        if (pressureKpa > limitKpa && hasLowPressureOutlet(level, pos, head) && data.getDamage(pos) <= 0.0f) {
            data.setDamage(pos, 1.0f);
        }
    }

    /**
     * Prevents natural seabed/riverbed dirt and sand resting on solid stone from falsely breaching;
     * only blocks holding water against an open dry side or a significantly lower water level can breach.
     */
    private static boolean hasLowPressureOutlet(ServerLevel level, BlockPos pos, int actingHead) {
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) {
                continue;
            }
            BlockPos adj = pos.relative(dir);
            if (!level.isLoaded(adj)) {
                continue;
            }
            BlockState adjState = level.getBlockState(adj);
            if (adjState.getFluidState().isEmpty()) {
                if (!adjState.blocksMotion()) {
                    return true;
                }
            } else if (WaterPressureCalculator.isWater(level, adj)) {
                int adjHead = WaterPressureCalculator.calculateWaterHeadAtWater(level, adj);
                if (adjHead <= actingHead - 3) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void triggerBreach(ServerLevel level, ErosionSavedData data, BlockPos pos, BlockState oldState) {
        clearCrackProgress(level, pos);
        data.remove(pos.asLong());

        // Destroy the weakened block without dropping items and let retained water surge out.
        level.destroyBlock(pos, false);
        if (HydroErosionConfig.ENABLE_FINITE_WATER_PHYSICS.get()) {
            FiniteWaterPhysicsManager.enqueueRadius(level, pos, 2);
        } else {
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            level.scheduleTick(pos, Fluids.WATER, 1);
        }

        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, oldState),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                20, 0.35, 0.35, 0.35, 0.12);
        level.sendParticles(ParticleTypes.SPLASH,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                24, 0.4, 0.4, 0.4, 0.2);

        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.65f, 1.25f);
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.9f, 0.85f);

        // Cascade stress inspection to immediate neighbors.
        for (Direction dir : Direction.values()) {
            queueForInspection(level, pos.relative(dir));
        }
    }

    private static void spawnLeakEffects(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        for (Direction dir : Direction.values()) {
            BlockPos drySide = pos.relative(dir);
            if (level.isLoaded(drySide) && level.getBlockState(drySide).isAir()) {
                double px = pos.getX() + 0.5 + dir.getStepX() * 0.54;
                double py = pos.getY() + 0.3 + random.nextDouble() * 0.4;
                double pz = pos.getZ() + 0.5 + dir.getStepZ() * 0.54;
                level.sendParticles(ParticleTypes.DRIPPING_WATER, px, py, pz, 2, 0.15, 0.15, 0.15, 0.02);
                if (random.nextFloat() < 0.35f) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                            px, py, pz, 3, 0.1, 0.1, 0.1, 0.05);
                }
            }
        }
        if (random.nextFloat() < 0.3f) {
            level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.35f, 0.7f + random.nextFloat() * 0.25f);
        }
    }

    private static void clearCrackProgress(ServerLevel level, BlockPos pos) {
        level.destroyBlockProgress(breakerId(pos), pos, -1);
    }

    private static int breakerId(BlockPos pos) {
        return BREAKER_ID_BASE ^ (pos.hashCode() & 0x00ffffff);
    }
}
