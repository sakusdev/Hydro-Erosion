package com.hydroerosion.water;

import com.hydroerosion.HydroErosionMod;
import com.hydroerosion.config.HydroErosionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Accumulates finite water sources inside enclosed sky-exposed basins during rainfall.
 * Strictly fills from bottom-up (only on top of solid blocks or existing water sources)
 * so water never spawns mid-air or creates floating cliff waterfalls.
 */
@Mod.EventBusSubscriber(modid = HydroErosionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RainAccumulationManager {

    private static final int TICK_INTERVAL = 20;
    private static final int MAX_CHUNKS_PER_CYCLE = 48;
    private static final int MAX_BASIN_SLICE_AREA = 196;
    private static final int MAX_DESCENT_STEPS = 16;
    private static final Direction[] CARDINALS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private RainAccumulationManager() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (!HydroErosionConfig.ENABLE_RAIN_ACCUMULATION.get() || !level.isRaining()) {
            return;
        }
        if ((level.getGameTime() % TICK_INTERVAL) != 0) {
            return;
        }

        RandomSource random = level.random;
        List<ChunkPos> chunks = LoadedChunkTracker.sampleLoadedChunks(level, MAX_CHUNKS_PER_CYCLE, random);
        if (chunks.isEmpty()) {
            return;
        }

        int samplesPerChunk = HydroErosionConfig.RAIN_SAMPLES_PER_CHUNK.get();
        double baseChance = HydroErosionConfig.RAIN_ACCUMULATION_CHANCE.get();
        double effectiveChance = level.isThundering() ? Math.min(1.0, baseChance * 1.5) : baseChance;

        for (ChunkPos chunkPos : chunks) {
            for (int i = 0; i < samplesPerChunk; i++) {
                int x = chunkPos.getMinBlockX() + random.nextInt(16);
                int z = chunkPos.getMinBlockZ() + random.nextInt(16);
                tryAccumulateColumn(level, x, z, effectiveChance, random);
            }
        }
    }

    private static void tryAccumulateColumn(ServerLevel level, int x, int z, double chance, RandomSource random) {
        BlockPos surfaceTop = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        BlockPos candidate = findBottomSupportedCell(level, surfaceTop);
        if (candidate == null) {
            return;
        }

        if (HydroErosionConfig.ENABLE_FINITE_WATER_PHYSICS.get()) {
            tryAccumulateFiniteRain(level, candidate, chance, random);
            return;
        }

        // Slide downhill toward the lowest bottom-supported cell in the local depression.
        BlockPos lowest = descendToLocalMinimum(level, candidate);
        if (lowest == null || isWaterSource(level, lowest) || !canReplaceWithWater(level, lowest)) {
            return;
        }
        if (!isBottomSupported(level, lowest.below())) {
            return;
        }
        if (!hasSkyRainAccess(level, lowest)) {
            return;
        }
        if (!isEnclosedBottomSupportedBasinSlice(level, lowest)) {
            return;
        }

        if (random.nextDouble() <= chance) {
            level.setBlock(lowest, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void tryAccumulateFiniteRain(ServerLevel level,
                                                BlockPos candidate,
                                                double chance,
                                                RandomSource random) {
        if (random.nextDouble() > chance) {
            return;
        }
        BlockPos lowest = descendToLocalMinimum(level, candidate);
        if (lowest != null
                && !isWaterSource(level, lowest)
                && isBottomSupported(level, lowest.below())
                && hasSkyRainAccess(level, lowest)
                && isEnclosedBottomSupportedBasinSlice(level, lowest)) {
            FiniteWaterPhysicsManager.addWaterVolume(level, lowest, 4);
            return;
        }

        // On sloped terrain, deposit rainwater directly on the solid floor so finite physics rolls it downhill.
        if (hasSkyRainAccess(level, candidate)
                && isBottomSupported(level, candidate.below())
                && FiniteWaterPhysicsManager.canAcceptWater(level, candidate)) {
            FiniteWaterPhysicsManager.addWaterVolume(level, candidate, 2);
        }
    }

    /**
     * Walks downward from {@code start} through air or non-source flowing water until reaching a cell
     * whose direct floor ({@code pos.below()}) is a sturdy solid block or an existing {@code Blocks.WATER} source.
     */
    private static BlockPos findBottomSupportedCell(ServerLevel level, BlockPos start) {
        BlockPos.MutableBlockPos cursor = start.mutable();
        int minY = level.getMinBuildHeight() + 1;

        for (int step = 0; step < 32 && cursor.getY() > minY; step++) {
            BlockPos below = cursor.below();
            if (canReplaceWithWater(level, cursor) && isBottomSupported(level, below)) {
                return cursor.immutable();
            }
            if (!canReplaceWithWater(level, cursor)) {
                // If we hit a water source column at the surface, check one block above it.
                if (isWaterSource(level, cursor)) {
                    BlockPos above = cursor.above();
                    if (canReplaceWithWater(level, above) && isBottomSupported(level, cursor)) {
                        return above;
                    }
                }
                return null;
            }
            cursor.move(Direction.DOWN);
        }
        return null;
    }

    private static BlockPos descendToLocalMinimum(ServerLevel level, BlockPos start) {
        BlockPos current = start;
        for (int step = 0; step < MAX_DESCENT_STEPS; step++) {
            BlockPos best = current;
            for (Direction dir : CARDINALS) {
                BlockPos adj = current.relative(dir);
                if (!level.isLoaded(adj)) {
                    return null;
                }
                if (canReplaceWithWater(level, adj)) {
                    BlockPos dropped = findBottomSupportedCell(level, adj);
                    if (dropped != null && dropped.getY() < best.getY()) {
                        best = dropped;
                    }
                }
            }
            if (best.equals(current)) {
                break;
            }
            current = best;
        }
        return current;
    }

    /**
     * Ensures the horizontal slice at {@code fillPos.getY()} is completely enclosed by solid walls
     * and every cell in the slice sits directly atop either a solid floor or an existing water source.
     * This guarantees strict bottom-up filling with zero spills or mid-air water generation.
     */
    private static boolean isEnclosedBottomSupportedBasinSlice(ServerLevel level, BlockPos seed) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();

        queue.add(seed);
        visited.add(seed.asLong());

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (!level.isLoaded(current) || !isBottomSupported(level, current.below())) {
                return false;
            }

            for (Direction dir : CARDINALS) {
                BlockPos neighbor = current.relative(dir);
                if (!level.isLoaded(neighbor)) {
                    return false;
                }
                long key = neighbor.asLong();
                if (visited.contains(key)) {
                    continue;
                }

                if (isSolidWall(level, neighbor, dir.getOpposite())) {
                    continue;
                }

                // Any non-wall neighbor must be part of the bottom-supported pool slice (air, flowing water, or water source).
                if (!canReplaceWithWater(level, neighbor) && !isWaterSource(level, neighbor)) {
                    return false;
                }

                if (visited.size() >= MAX_BASIN_SLICE_AREA) {
                    return false;
                }
                visited.add(key);
                queue.add(neighbor);
            }
        }
        return true;
    }

    private static boolean hasSkyRainAccess(ServerLevel level, BlockPos pos) {
        Biome biome = level.getBiome(pos).value();
        if (biome.getPrecipitationAt(pos) != Biome.Precipitation.RAIN) {
            return false;
        }
        if (level.canSeeSky(pos)) {
            return true;
        }
        // Allow submerged bottom layers as long as only water/air exists between pos and sky.
        BlockPos.MutableBlockPos cursor = pos.above().mutable();
        int maxY = level.getMaxBuildHeight();
        while (cursor.getY() < maxY) {
            if (level.canSeeSky(cursor)) {
                return canPassRainThrough(level, cursor);
            }
            if (!canPassRainThrough(level, cursor)) {
                return false;
            }
            cursor.move(Direction.UP);
        }
        return false;
    }

    private static boolean canPassRainThrough(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.getFluidState().is(Fluids.WATER) || state.getFluidState().is(Fluids.FLOWING_WATER);
    }

    private static boolean canReplaceWithWater(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        FluidState fluid = state.getFluidState();
        return (fluid.is(Fluids.WATER) || fluid.is(Fluids.FLOWING_WATER)) && !fluid.isSource();
    }

    private static boolean isWaterSource(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        FluidState fluid = level.getFluidState(pos);
        return fluid.is(Fluids.WATER) && fluid.isSource();
    }

    private static boolean isBottomSupported(ServerLevel level, BlockPos below) {
        return isWaterSource(level, below) || isSolidFloor(level, below);
    }

    private static boolean isSolidFloor(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty()
                && (state.isFaceSturdy(level, pos, Direction.UP) || state.isSolidRender(level, pos));
    }

    private static boolean isSolidWall(ServerLevel level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty()
                && (state.isFaceSturdy(level, pos, face) || state.isSolidRender(level, pos));
    }
}
