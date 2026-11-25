package com.hydroerosion.water;

import com.hydroerosion.HydroErosionMod;
import com.hydroerosion.config.HydroErosionConfig;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance cellular-automaton finite water simulator.
 * Uses dual deduplicated primitive queues (PRIORITY for player/breach/cascade actions,
 * BACKGROUND capped for worldgen fluid ticks) and O(1)/bounded plateau equalization
 * so dug shorelines fill immediately with zero idle server lag.
 */
@Mod.EventBusSubscriber(modid = HydroErosionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FiniteWaterPhysicsManager {

    private static final int MAX_BACKGROUND_QUEUE_SIZE = 8192;
    private static final int MAX_BACKGROUND_PER_TICK = 150;
    private static final int MAX_PLATEAU_BFS = 24;
    private static final Direction[] CARDINALS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private static final Map<ResourceKey<Level>, LongLinkedOpenHashSet> PRIORITY_QUEUES =
            new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, LongLinkedOpenHashSet> BACKGROUND_QUEUES =
            new ConcurrentHashMap<>();

    private static boolean updatingInternally = false;

    private FiniteWaterPhysicsManager() {
    }

    /**
     * Returns the conserved water volume (0..8 quanta) at {@code pos}.
     * Level 0 (source) and falling level >= 8 map to full volume 8; levels 1..7 map to 7..1.
     */
    public static int getWaterVolume(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return 0;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.WATER)) {
            return 0;
        }
        int lvl = state.getValue(LiquidBlock.LEVEL);
        if (lvl == 0 || lvl >= 8) {
            return 8;
        }
        return Math.max(1, 8 - lvl);
    }

    /**
     * Adds up to {@code delta} quanta of water at {@code pos} and enqueues neighboring cells.
     */
    public static int addWaterVolume(ServerLevel level, BlockPos pos, int delta) {
        if (delta <= 0 || !canAcceptWater(level, pos)) {
            return 0;
        }
        int current = getWaterVolume(level, pos);
        int next = Math.min(8, current + delta);
        int added = next - current;
        if (added > 0) {
            setWaterVolume(level, pos, next);
            enqueueWithNeighbors(level, pos);
        }
        return added;
    }

    /**
     * Sets the conserved water volume (0..8) at {@code pos}.
     */
    public static void setWaterVolume(ServerLevel level, BlockPos pos, int volume) {
        int clamped = Math.max(0, Math.min(8, volume));
        BlockState oldState = level.getBlockState(pos);

        boolean prevFlag = updatingInternally;
        updatingInternally = true;
        try {
            if (clamped == 0) {
                if (oldState.is(Blocks.WATER)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                }
                return;
            }

            if (!oldState.isAir() && !oldState.is(Blocks.WATER) && canReplaceNonSolid(level, pos, oldState)) {
                Block.dropResources(oldState, level, pos, null);
            }

            int propLevel = (clamped >= 8) ? 0 : (8 - clamped);
            BlockState target = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, propLevel);
            if (!oldState.equals(target)) {
                int flags = oldState.is(Blocks.WATER)
                        ? Block.UPDATE_CLIENTS
                        : (Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                level.setBlock(pos, target, flags);
            }
        } finally {
            updatingInternally = prevFlag;
        }
    }

    public static boolean canReplaceNonSolid(Level level, BlockPos pos, BlockState state) {
        if (state.isAir()
                || state.is(Blocks.WATER)
                || !state.getFluidState().isEmpty()
                || state.hasBlockEntity()
                || state.getDestroySpeed(level, pos) < 0.0f
                || state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return false;
        }
        return !state.blocksMotion() && (
                state.canBeReplaced(Fluids.WATER)
                        || state.is(BlockTags.FLOWERS)
                        || state.is(BlockTags.REPLACEABLE_BY_TREES)
                        || state.is(BlockTags.CROPS)
                        || state.is(Blocks.SNOW)
        );
    }

    public static boolean canAcceptWater(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)
                || pos.getY() < level.getMinBuildHeight()
                || pos.getY() >= level.getMaxBuildHeight()) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        if (state.is(Blocks.WATER)) {
            return getWaterVolume(level, pos) < 8;
        }
        return canReplaceNonSolid(level, pos, state);
    }

    private static boolean canPassHorizontally(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)
                || pos.getY() < level.getMinBuildHeight()
                || pos.getY() >= level.getMaxBuildHeight()) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(Blocks.WATER) || canReplaceNonSolid(level, pos, state);
    }

    /**
     * Quick check to avoid enqueueing stationary ocean/river interior water during worldgen fluid ticks.
     */
    private static boolean canMoveImmediately(ServerLevel level, BlockPos pos) {
        int vol = getWaterVolume(level, pos);
        if (vol <= 0) {
            return false;
        }
        if (canAcceptWater(level, pos.below())) {
            return true;
        }
        for (Direction dir : CARDINALS) {
            BlockPos side = pos.relative(dir);
            if (canPassHorizontally(level, side)) {
                if (canAcceptWater(level, side.below())) {
                    return true;
                }
                if (vol >= 2 && getWaterVolume(level, side) <= vol - 2 && canAcceptWater(level, side)) {
                    return true;
                }
            }
        }
        return vol == 1 && isIsolatedThinFilm(level, pos);
    }

    public static void enqueueFromFluidTick(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
            return;
        }
        if (!canMoveImmediately(level, pos)) {
            return;
        }
        LongLinkedOpenHashSet bg = BACKGROUND_QUEUES.computeIfAbsent(level.dimension(), k -> new LongLinkedOpenHashSet());
        synchronized (bg) {
            if (bg.size() < MAX_BACKGROUND_QUEUE_SIZE) {
                bg.add(pos.asLong());
            }
        }
    }

    public static void enqueue(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
            return;
        }
        LongLinkedOpenHashSet queue = PRIORITY_QUEUES.computeIfAbsent(level.dimension(), k -> new LongLinkedOpenHashSet());
        synchronized (queue) {
            queue.add(pos.asLong());
        }
    }

    public static void enqueueWithNeighbors(ServerLevel level, BlockPos pos) {
        LongLinkedOpenHashSet queue = PRIORITY_QUEUES.computeIfAbsent(level.dimension(), k -> new LongLinkedOpenHashSet());
        synchronized (queue) {
            addPosAndNeighbors(level, pos, queue);
        }
    }

    public static void enqueueRadius(ServerLevel level, BlockPos center, int radius) {
        LongLinkedOpenHashSet queue = PRIORITY_QUEUES.computeIfAbsent(level.dimension(), k -> new LongLinkedOpenHashSet());
        synchronized (queue) {
            for (int dy = -radius; dy <= radius; dy++) {
                int y = center.getY() + dy;
                if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                    continue;
                }
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        queue.add(BlockPos.asLong(center.getX() + dx, y, center.getZ() + dz));
                    }
                }
            }
        }
    }

    public static int getActiveQueueSize(ServerLevel level) {
        int total = 0;
        LongLinkedOpenHashSet pq = PRIORITY_QUEUES.get(level.dimension());
        if (pq != null) {
            synchronized (pq) {
                total += pq.size();
            }
        }
        LongLinkedOpenHashSet bg = BACKGROUND_QUEUES.get(level.dimension());
        if (bg != null) {
            synchronized (bg) {
                total += bg.size();
            }
        }
        return total;
    }

    private static void addPosAndNeighbors(ServerLevel level, BlockPos pos, LongOpenHashSet target) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        if (pos.getY() >= minY && pos.getY() < maxY) {
            target.add(pos.asLong());
        }
        for (Direction dir : Direction.values()) {
            BlockPos n = pos.relative(dir);
            if (n.getY() >= minY && n.getY() < maxY) {
                target.add(n.asLong());
            }
        }
        BlockPos above = pos.above();
        if (above.getY() >= minY && above.getY() < maxY) {
            for (Direction dir : CARDINALS) {
                target.add(above.relative(dir).asLong());
            }
        }
    }

    private static void addPosAndNeighbors(ServerLevel level, BlockPos pos, LongLinkedOpenHashSet target) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        if (pos.getY() >= minY && pos.getY() < maxY) {
            target.add(pos.asLong());
        }
        for (Direction dir : Direction.values()) {
            BlockPos n = pos.relative(dir);
            if (n.getY() >= minY && n.getY() < maxY) {
                target.add(n.asLong());
            }
        }
        BlockPos above = pos.above();
        if (above.getY() >= minY && above.getY() < maxY) {
            for (Direction dir : CARDINALS) {
                target.add(above.relative(dir).asLong());
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!updatingInternally && HydroErosionConfig.ENABLE_FINITE_WATER_PHYSICS.get()
                && event.getLevel() instanceof ServerLevel level) {
            enqueueRadius(level, event.getPos(), 1);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!updatingInternally && HydroErosionConfig.ENABLE_FINITE_WATER_PHYSICS.get()
                && event.getLevel() instanceof ServerLevel level) {
            enqueueWithNeighbors(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!updatingInternally && HydroErosionConfig.ENABLE_FINITE_WATER_PHYSICS.get()
                && event.getLevel() instanceof ServerLevel level) {
            enqueueWithNeighbors(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PRIORITY_QUEUES.remove(level.dimension());
            BACKGROUND_QUEUES.remove(level.dimension());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (!HydroErosionConfig.ENABLE_FINITE_WATER_PHYSICS.get()) {
            clearQueue(PRIORITY_QUEUES.get(level.dimension()));
            clearQueue(BACKGROUND_QUEUES.get(level.dimension()));
            return;
        }

        int totalBudget = HydroErosionConfig.FINITE_WATER_MAX_UPDATES_PER_TICK.get();
        long[] priorityBatch = popBatch(PRIORITY_QUEUES.get(level.dimension()), totalBudget);
        int remaining = Math.min(MAX_BACKGROUND_PER_TICK, totalBudget - priorityBatch.length);
        long[] bgBatch = remaining > 0
                ? popBatch(BACKGROUND_QUEUES.get(level.dimension()), remaining)
                : new long[0];

        if (priorityBatch.length == 0 && bgBatch.length == 0) {
            return;
        }

        LongOpenHashSet nextWakeups = new LongOpenHashSet();
        for (long packed : priorityBatch) {
            processCell(level, BlockPos.of(packed), nextWakeups);
        }
        for (long packed : bgBatch) {
            processCell(level, BlockPos.of(packed), nextWakeups);
        }

        if (!nextWakeups.isEmpty()) {
            LongLinkedOpenHashSet pq = PRIORITY_QUEUES.computeIfAbsent(level.dimension(), k -> new LongLinkedOpenHashSet());
            synchronized (pq) {
                pq.addAll(nextWakeups);
            }
        }
    }

    private static void clearQueue(LongLinkedOpenHashSet queue) {
        if (queue != null) {
            synchronized (queue) {
                queue.clear();
            }
        }
    }

    private static long[] popBatch(LongLinkedOpenHashSet queue, int limit) {
        if (queue == null || limit <= 0) {
            return new long[0];
        }
        synchronized (queue) {
            if (queue.isEmpty()) {
                return new long[0];
            }
            int count = Math.min(limit, queue.size());
            long[] batch = new long[count];
            for (int i = 0; i < count; i++) {
                batch[i] = queue.removeFirstLong();
            }
            return batch;
        }
    }

    private static void processCell(ServerLevel level, BlockPos pos, LongOpenHashSet nextWakeups) {
        if (!level.isLoaded(pos)) {
            return;
        }
        int vol = getWaterVolume(level, pos);
        if (vol <= 0) {
            return;
        }

        // Rule 1: Vertical fall (gravity)
        BlockPos below = pos.below();
        if (canAcceptWater(level, below)) {
            int belowVol = getWaterVolume(level, below);
            int move = Math.min(vol, 8 - belowVol);
            if (move > 0) {
                setWaterVolume(level, below, belowVol + move);
                setWaterVolume(level, pos, vol - move);
                addPosAndNeighbors(level, pos, nextWakeups);
                addPosAndNeighbors(level, below, nextWakeups);
                return;
            }
        }

        // Rule 2: Diagonal downward flow over ledges
        int rot = Math.floorMod(pos.hashCode() + (int) level.getGameTime(), 4);
        for (int i = 0; i < 4; i++) {
            Direction dir = CARDINALS[(rot + i) & 3];
            BlockPos side = pos.relative(dir);
            if (!canPassHorizontally(level, side)) {
                continue;
            }
            BlockPos sideBelow = side.below();
            if (canAcceptWater(level, sideBelow)) {
                int sbVol = getWaterVolume(level, sideBelow);
                int move = Math.min(vol, 8 - sbVol);
                if (move > 0) {
                    setWaterVolume(level, sideBelow, sbVol + move);
                    setWaterVolume(level, pos, vol - move);
                    addPosAndNeighbors(level, pos, nextWakeups);
                    addPosAndNeighbors(level, side, nextWakeups);
                    addPosAndNeighbors(level, sideBelow, nextWakeups);
                    return;
                }
            }
        }

        // Rule 3: Horizontal equalization (direct diff >= 2, then zero-allocation plateau bridging)
        if (vol >= 2) {
            if (tryHorizontalEqualize(level, pos, vol, rot, nextWakeups)) {
                return;
            }
            if (tryPlateauEqualize(level, pos, vol, rot, nextWakeups)) {
                return;
            }
        }

        // Rule 3-B: O(1) local overhang pressure equalization (only when adjacent full column is deeper above)
        if (vol == 8 && tryLocalOverhangUpflow(level, pos, nextWakeups)) {
            return;
        }

        // Rule 4: Throttled evaporation of strictly isolated 1/8 water films on solid ground
        if (vol == 1 && isIsolatedThinFilm(level, pos)) {
            int phase = Math.floorMod(pos.hashCode(), 20);
            if ((level.getGameTime() % 20L) == phase) {
                if (level.random.nextDouble() < HydroErosionConfig.FINITE_WATER_EVAPORATE_CHANCE.get()) {
                    setWaterVolume(level, pos, 0);
                    addPosAndNeighbors(level, pos, nextWakeups);
                    return;
                }
            }
            nextWakeups.add(pos.asLong());
        }
    }

    private static boolean tryHorizontalEqualize(ServerLevel level,
                                                   BlockPos pos,
                                                   int startVol,
                                                   int rot,
                                                   LongOpenHashSet nextWakeups) {
        BlockPos[] neighbors = new BlockPos[4];
        int[] nVols = new int[4];
        boolean[] eligible = new boolean[4];
        int eligibleCount = 0;

        for (int i = 0; i < 4; i++) {
            Direction dir = CARDINALS[(rot + i) & 3];
            BlockPos n = pos.relative(dir);
            neighbors[i] = n;
            if (canAcceptWater(level, n)) {
                int nv = getWaterVolume(level, n);
                nVols[i] = nv;
                if (nv <= startVol - 2) {
                    eligible[i] = true;
                    eligibleCount++;
                }
            }
        }
        if (eligibleCount == 0) {
            return false;
        }

        int vol = startVol;
        boolean[] modified = new boolean[4];

        for (int pass = 0; pass < 4 && vol >= 2; pass++) {
            int bestIdx = -1;
            int minVol = Integer.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                if (eligible[i] && nVols[i] <= vol - 2 && nVols[i] < minVol) {
                    minVol = nVols[i];
                    bestIdx = i;
                }
            }
            if (bestIdx < 0) {
                break;
            }
            int transfer = Math.max(1, (vol - minVol) / (eligibleCount + 1));
            transfer = Math.min(transfer, 8 - nVols[bestIdx]);
            if (transfer <= 0) {
                break;
            }
            vol -= transfer;
            nVols[bestIdx] += transfer;
            modified[bestIdx] = true;
        }

        if (vol == startVol) {
            return false;
        }

        setWaterVolume(level, pos, vol);
        addPosAndNeighbors(level, pos, nextWakeups);
        for (int i = 0; i < 4; i++) {
            if (modified[i]) {
                setWaterVolume(level, neighbors[i], nVols[i]);
                addPosAndNeighbors(level, neighbors[i], nextWakeups);
            }
        }
        return true;
    }

    /**
     * Zero-allocation horizontal plateau equalizer: bridges across flat (vol - 1) water sheets
     * up to 24 cells away to flatten staircase pools without oscillation.
     */
    private static boolean tryPlateauEqualize(ServerLevel level,
                                                BlockPos pos,
                                                int vol,
                                                int rot,
                                                LongOpenHashSet nextWakeups) {
        int plateauVol = vol - 1;
        boolean hasStepNeighbor = false;
        for (int i = 0; i < 4; i++) {
            BlockPos n = pos.relative(CARDINALS[(rot + i) & 3]);
            if (getWaterVolume(level, n) == plateauVol) {
                hasStepNeighbor = true;
                break;
            }
        }
        if (!hasStepNeighbor) {
            return false;
        }

        long[] visited = new long[MAX_PLATEAU_BFS];
        int head = 0;
        int tail = 0;
        visited[tail++] = pos.asLong();

        int bestTargetVol = Integer.MAX_VALUE;
        long bestTargetPacked = Long.MIN_VALUE;

        while (head < tail) {
            BlockPos cur = BlockPos.of(visited[head++]);
            for (int i = 0; i < 4; i++) {
                Direction dir = CARDINALS[(rot + i) & 3];
                BlockPos next = cur.relative(dir);
                long packed = next.asLong();

                boolean seen = false;
                for (int j = 0; j < tail; j++) {
                    if (visited[j] == packed) {
                        seen = true;
                        break;
                    }
                }
                if (seen) {
                    continue;
                }

                if (!canAcceptWater(level, next)) {
                    continue;
                }
                int nv = getWaterVolume(level, next);
                if (nv <= vol - 2 && nv < bestTargetVol) {
                    bestTargetVol = nv;
                    bestTargetPacked = packed;
                    if (nv == 0) {
                        break;
                    }
                } else if (nv == plateauVol && tail < MAX_PLATEAU_BFS) {
                    visited[tail++] = packed;
                }
            }
            if (bestTargetVol == 0) {
                break;
            }
        }

        if (bestTargetPacked == Long.MIN_VALUE) {
            return false;
        }

        BlockPos target = BlockPos.of(bestTargetPacked);
        setWaterVolume(level, pos, vol - 1);
        setWaterVolume(level, target, bestTargetVol + 1);
        addPosAndNeighbors(level, pos, nextWakeups);
        addPosAndNeighbors(level, target, nextWakeups);
        return true;
    }

    /**
     * O(1) local overhang equalization for submerged openings: checks only immediate neighbors
     * at the same Y so flat oceans finish in 4 quick checks with zero heap allocation.
     */
    private static boolean tryLocalOverhangUpflow(ServerLevel level,
                                                    BlockPos pos,
                                                    LongOpenHashSet nextWakeups) {
        BlockPos above = pos.above();
        if (!canAcceptWater(level, above)) {
            return false;
        }
        int aboveVol = getWaterVolume(level, above);
        if (aboveVol >= 8) {
            return false;
        }

        for (Direction dir : CARDINALS) {
            BlockPos adj = pos.relative(dir);
            if (getWaterVolume(level, adj) == 8) {
                BlockPos adjAbove = adj.above();
                int adjAboveVol = getWaterVolume(level, adjAbove);
                if (adjAboveVol >= aboveVol + 2) {
                    int move = Math.max(1, (adjAboveVol - aboveVol) / 2);
                    move = Math.min(move, 8 - aboveVol);
                    setWaterVolume(level, adjAbove, adjAboveVol - move);
                    setWaterVolume(level, above, aboveVol + move);
                    addPosAndNeighbors(level, adjAbove, nextWakeups);
                    addPosAndNeighbors(level, above, nextWakeups);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isIsolatedThinFilm(ServerLevel level, BlockPos pos) {
        if (getWaterVolume(level, pos.above()) > 0 || getWaterVolume(level, pos.below()) > 0) {
            return false;
        }
        for (Direction dir : CARDINALS) {
            if (getWaterVolume(level, pos.relative(dir)) >= 2) {
                return false;
            }
        }
        return true;
    }
}
