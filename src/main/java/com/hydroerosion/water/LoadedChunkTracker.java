package com.hydroerosion.water;

import com.hydroerosion.HydroErosionMod;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks loaded chunks per server dimension for low-overhead periodic sampling.
 */
@Mod.EventBusSubscriber(modid = HydroErosionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LoadedChunkTracker {

    private static final Map<ResourceKey<Level>, Set<Long>> LOADED_CHUNKS = new ConcurrentHashMap<>();

    private LoadedChunkTracker() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            LOADED_CHUNKS
                    .computeIfAbsent(serverLevel.dimension(), k -> ConcurrentHashMap.newKeySet())
                    .add(event.getChunk().getPos().toLong());
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            Set<Long> set = LOADED_CHUNKS.get(serverLevel.dimension());
            if (set != null) {
                set.remove(event.getChunk().getPos().toLong());
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            LOADED_CHUNKS.remove(serverLevel.dimension());
        }
    }

    /**
     * Returns up to {@code maxCount} randomly sampled loaded chunks in the given level.
     */
    public static List<ChunkPos> sampleLoadedChunks(ServerLevel level, int maxCount, RandomSource random) {
        Set<Long> set = LOADED_CHUNKS.get(level.dimension());
        if (set == null || set.isEmpty() || maxCount <= 0) {
            return Collections.emptyList();
        }
        Long[] snapshot = set.toArray(new Long[0]);
        if (snapshot.length == 0) {
            return Collections.emptyList();
        }
        int count = Math.min(maxCount, snapshot.length);
        List<ChunkPos> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long packed = snapshot[random.nextInt(snapshot.length)];
            ChunkPos pos = new ChunkPos(packed);
            if (level.isLoaded(pos.getWorldPosition())) {
                result.add(pos);
            }
        }
        return result;
    }
}
