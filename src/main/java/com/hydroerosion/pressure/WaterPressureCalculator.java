package com.hydroerosion.pressure;

import com.hydroerosion.block.IPressureResistantBlock;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Utility for computing hydrostatic head (connected water column height),
 * fluid pressure in kPa, and structural pressure resistance limits of blocks.
 */
public final class WaterPressureCalculator {

    public static final float KPA_PER_BLOCK = 9.81f;
    private static final int MAX_ENCLOSED_BFS_NODES = 512;
    private static final int MAX_VERTICAL_SCAN = 128;

    private WaterPressureCalculator() {
    }

    /**
     * Computes the hydrostatic pressure (kPa) from a water head height in blocks.
     */
    public static float calculatePressureKpa(int waterHeadBlocks) {
        return Math.max(0, waterHeadBlocks) * KPA_PER_BLOCK;
    }

    /**
     * Checks whether the given position holds water.
     */
    public static boolean isWater(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getFluidState(pos).is(FluidTags.WATER);
    }

    /**
     * Checks whether {@code pos} holds sufficient water (>= 3/8 quanta) to transmit hydrostatic head upward.
     */
    private static boolean hasSignificantWater(Level level, BlockPos pos) {
        return isWater(level, pos) && level.getFluidState(pos).getAmount() >= 3;
    }

    /**
     * Measures the effective water head (in blocks) acting on {@code pos}.
     */
    public static int getMaxAdjacentWaterHead(Level level, BlockPos pos) {
        if (isWater(level, pos)) {
            return calculateWaterHeadAtWater(level, pos);
        }
        int maxHead = 0;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (isWater(level, neighbor)) {
                int head = calculateWaterHeadAtWater(level, neighbor);
                if (head > maxHead) {
                    maxHead = head;
                }
            }
        }
        return maxHead;
    }

    /**
     * Computes water head height above {@code waterPos}.
     * Uses an O(depth) vertical fast path when the water column has an open surface,
     * and only runs a bounded primitive BFS when capped by a solid ceiling (enclosed penstocks/tunnels).
     */
    public static int calculateWaterHeadAtWater(Level level, BlockPos waterPos) {
        if (!isWater(level, waterPos)) {
            return 0;
        }

        int baseY = waterPos.getY();
        int maxAllowedY = Math.min(level.getMaxBuildHeight() - 1, baseY + MAX_VERTICAL_SCAN);
        int maxSurfaceY = traceColumnTopY(level, waterPos, maxAllowedY);

        // Fast path: open-air water surface directly above this column (avoids BFS on rivers/oceans/open dams).
        if (maxSurfaceY < maxAllowedY) {
            BlockPos aboveTop = new BlockPos(waterPos.getX(), maxSurfaceY + 1, waterPos.getZ());
            if (level.isLoaded(aboveTop) && !level.getBlockState(aboveTop).blocksMotion()) {
                return Math.max(1, maxSurfaceY - baseY + 1);
            }
        }

        // Enclosed ceiling / pipe path (Pascal's principle).
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        LongOpenHashSet visited = new LongOpenHashSet();
        LongOpenHashSet tracedColumns = new LongOpenHashSet();

        queue.enqueue(waterPos.asLong());
        visited.add(waterPos.asLong());
        tracedColumns.add(packColumn(waterPos.getX(), waterPos.getZ()));

        while (!queue.isEmpty() && visited.size() < MAX_ENCLOSED_BFS_NODES) {
            BlockPos current = BlockPos.of(queue.dequeueLong());

            for (Direction dir : Direction.values()) {
                if (dir == Direction.DOWN && current.getY() <= baseY) {
                    continue;
                }
                BlockPos next = current.relative(dir);
                if (next.getY() < baseY || next.getY() > maxAllowedY) {
                    continue;
                }
                long key = next.asLong();
                boolean passableWater = next.getY() == baseY ? isWater(level, next) : hasSignificantWater(level, next);
                if (!visited.contains(key) && passableWater) {
                    visited.add(key);
                    queue.enqueue(key);

                    long colKey = packColumn(next.getX(), next.getZ());
                    if (tracedColumns.add(colKey)) {
                        int colTop = traceColumnTopY(level, next, maxAllowedY);
                        if (colTop > maxSurfaceY) {
                            maxSurfaceY = colTop;
                        }
                    } else if (next.getY() > maxSurfaceY) {
                        maxSurfaceY = next.getY();
                    }
                }
            }
        }

        return Math.max(1, maxSurfaceY - baseY + 1);
    }

    private static int traceColumnTopY(Level level, BlockPos start, int maxAllowedY) {
        BlockPos.MutableBlockPos cursor = start.mutable();
        int topY = start.getY();
        while (cursor.getY() < maxAllowedY) {
            cursor.move(Direction.UP);
            if (!hasSignificantWater(level, cursor)) {
                break;
            }
            topY = cursor.getY();
        }
        return topY;
    }

    private static long packColumn(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    /**
     * Returns the structural hydrostatic pressure limit (kPa) of a block.
     */
    public static float getBlockPressureLimitKpa(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return 0.0f;
        }
        if (state.getBlock() instanceof IPressureResistantBlock resistant) {
            return resistant.getMaxPressureKpa(state);
        }

        if (state.getBlock().defaultDestroyTime() < 0.0f
                || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.CRYING_OBSIDIAN)
                || state.is(Blocks.REINFORCED_DEEPSLATE)) {
            return Float.POSITIVE_INFINITY;
        }

        if (state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.MUD)
                || state.is(Blocks.SOUL_SAND)
                || state.is(Blocks.SOUL_SOIL)) {
            return 29.4f;
        }

        if (state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.COBBLED_DEEPSLATE)
                || state.is(BlockTags.PLANKS)
                || state.is(BlockTags.LOGS)
                || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE)
                || state.is(BlockTags.TERRACOTTA)
                || state.is(Blocks.GLASS)) {
            return 68.7f;
        }

        if (state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.STONE_BRICKS)
                || state.is(Blocks.BRICKS)
                || state.is(Blocks.NETHER_BRICKS)
                || state.getBlock().getDescriptionId().contains("concrete")) {
            return 157.0f;
        }

        if (state.is(Blocks.IRON_BLOCK) || state.is(Blocks.COPPER_BLOCK) || state.is(Blocks.NETHERITE_BLOCK)) {
            return 981.0f;
        }

        float hardness = state.getBlock().defaultDestroyTime();
        if (hardness <= 0.8f) {
            return 29.4f;
        }
        if (hardness <= 2.2f) {
            return 68.7f;
        }
        return 157.0f;
    }
}
