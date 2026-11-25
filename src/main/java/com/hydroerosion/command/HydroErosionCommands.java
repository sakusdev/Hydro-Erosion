package com.hydroerosion.command;

import com.hydroerosion.HydroErosionMod;
import com.hydroerosion.block.DeepPressureIntakeBlock;
import com.hydroerosion.block.WaterPressureSensorBlock;
import com.hydroerosion.blockentity.DeepPressureIntakeBlockEntity;
import com.hydroerosion.compat.CompatManager;
import com.hydroerosion.pressure.BreachManager;
import com.hydroerosion.pressure.ErosionSavedData;
import com.hydroerosion.pressure.WaterPressureCalculator;
import com.hydroerosion.registry.ModBlocks;
import com.hydroerosion.water.FiniteWaterPhysicsManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * Registers diagnostic and test-building commands under /hydroerosion.
 */
@Mod.EventBusSubscriber(modid = HydroErosionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HydroErosionCommands {

    private HydroErosionCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("hydroerosion")
                        .then(Commands.literal("inspect")
                                .executes(ctx -> executeInspect(ctx.getSource())))
                        .then(Commands.literal("testdam")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> executeTestDam(ctx.getSource())))
        );
    }

    private static int executeInspect(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be executed by a player."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        ErosionSavedData erosionData = ErosionSavedData.get(level);

        BlockPos feetPos = player.blockPosition();
        sendPositionReport(source, level, erosionData, "Feet", feetPos);

        HitResult hit = player.pick(32.0D, 0.0F, true);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            BlockPos targetPos = blockHit.getBlockPos();
            if (!targetPos.equals(feetPos)) {
                sendPositionReport(source, level, erosionData, "Target", targetPos);
            }
        } else {
            source.sendSuccess(() -> Component.literal("[Target] No block/fluid within 32m line of sight.")
                    .withStyle(ChatFormatting.GRAY), false);
        }

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[Env] Raining: %s | ActiveWaterQueue: %d | Create Compat: %s | Mekanism Compat: %s",
                level.isRaining(), FiniteWaterPhysicsManager.getActiveQueueSize(level),
                CompatManager.isCreateLoaded(), CompatManager.isMekanismLoaded()))
                .withStyle(ChatFormatting.DARK_AQUA), false);
        return 1;
    }

    private static void sendPositionReport(CommandSourceStack source,
                                           ServerLevel level,
                                           ErosionSavedData erosionData,
                                           String label,
                                           BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        String blockId = String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock()));
        int head = WaterPressureCalculator.getMaxAdjacentWaterHead(level, pos);
        float pressureKpa = WaterPressureCalculator.calculatePressureKpa(head);
        float limitKpa = WaterPressureCalculator.getBlockPressureLimitKpa(state);
        float damage = erosionData.getDamage(pos);
        int stage = erosionData.getCrackStage(pos);
        boolean skyAccess = level.canSeeSky(pos);
        boolean rainBiome = level.getBiome(pos).value().getPrecipitationAt(pos) == Biome.Precipitation.RAIN;
        boolean deepBonus = head >= DeepPressureIntakeBlockEntity.HIGH_PRESSURE_DEPTH_THRESHOLD;

        int volume = FiniteWaterPhysicsManager.getWaterVolume(level, pos);
        String limitStr = Float.isInfinite(limitKpa) ? "INF" : String.format(Locale.ROOT, "%.1f kPa", limitKpa);

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "[%s] %s @ (%d,%d,%d) | Vol: %d/8 | Head: %d m (%.1f kPa) | Limit: %s | Fatigue: %.1f%% (Stage %d) | Sky: %s (RainBiome: %s) | Deep40mBonus: %s",
                label, blockId, pos.getX(), pos.getY(), pos.getZ(),
                volume, head, pressureKpa, limitStr, damage, stage,
                skyAccess, rainBiome, deepBonus ? "ACTIVE" : "OFF"))
                .withStyle(deepBonus ? ChatFormatting.AQUA : ChatFormatting.WHITE), false);
    }

    private static int executeTestDam(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be executed by a player."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Direction forward = player.getDirection();
        Direction right = forward.getClockWise();
        BlockPos center = player.blockPosition().relative(forward, 8);
        int baseY = player.blockPosition().getY() - 1;

        BlockState concrete = ModBlocks.REINFORCED_DAM_CONCRETE.get().defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();

        // Clear build footprint and construct the 7x11x7 reservoir + 7x5x9 downstream containment basin.
        for (int dz = -11; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = 0; dy <= 11; dy++) {
                    BlockPos p = offsetLocal(center, baseY, forward, right, dx, dy, dz);
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }

        // 1. Reservoir floor & walls (dx in -3..3, dz in -3..3, dy in 0..10)
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                boolean isWall = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                BlockPos floorPos = offsetLocal(center, baseY, forward, right, dx, 0, dz);
                level.setBlock(floorPos, concrete, Block.UPDATE_ALL);

                for (int dy = 1; dy <= 10; dy++) {
                    BlockPos p = offsetLocal(center, baseY, forward, right, dx, dy, dz);
                    if (isWall) {
                        level.setBlock(p, concrete, Block.UPDATE_ALL);
                    }
                }
            }
        }

        // 2. Fill reservoir strictly bottom-up (dy = 1..10 -> 10m depth = 98.1 kPa at base)
        for (int dy = 1; dy <= 10; dy++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    BlockPos p = offsetLocal(center, baseY, forward, right, dx, dy, dz);
                    level.setBlock(p, water, Block.UPDATE_ALL);
                }
            }
        }

        // 3. Downstream spill containment basin facing the player (dz in -11..-4, 4m walls to equalize to ~3.8m)
        for (int dz = -11; dz <= -4; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                BlockPos apronFloor = offsetLocal(center, baseY, forward, right, dx, 0, dz);
                level.setBlock(apronFloor, concrete, Block.UPDATE_ALL);
                if (Math.abs(dx) == 3 || dz == -11) {
                    for (int dy = 1; dy <= 4; dy++) {
                        BlockPos apronWall = offsetLocal(center, baseY, forward, right, dx, dy, dz);
                        level.setBlock(apronWall, concrete, Block.UPDATE_ALL);
                    }
                }
            }
        }

        // 4. Water Pressure Sensor + Redstone Lamp indicator on right wall (dx = 3, dz = 0)
        BlockPos sensorPos = offsetLocal(center, baseY, forward, right, 3, 1, 0);
        level.setBlock(sensorPos, ModBlocks.WATER_PRESSURE_SENSOR.get().defaultBlockState()
                .setValue(WaterPressureSensorBlock.FACING, forward.getOpposite()), Block.UPDATE_ALL);

        BlockPos wireSupport = offsetLocal(center, baseY, forward, right, 4, 0, 0);
        BlockPos wirePos = offsetLocal(center, baseY, forward, right, 4, 1, 0);
        BlockPos lampSupport = offsetLocal(center, baseY, forward, right, 5, 0, 0);
        BlockPos lampPos = offsetLocal(center, baseY, forward, right, 5, 1, 0);
        level.setBlock(wireSupport, concrete, Block.UPDATE_ALL);
        level.setBlock(wirePos, Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(lampSupport, concrete, Block.UPDATE_ALL);
        level.setBlock(lampPos, Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);

        // 5. Deep Pressure Intake on left wall (dx = -3, dz = 0)
        BlockPos intakePos = offsetLocal(center, baseY, forward, right, -3, 1, 0);
        level.setBlock(intakePos, ModBlocks.DEEP_PRESSURE_INTAKE.get().defaultBlockState()
                .setValue(DeepPressureIntakeBlock.FACING, right), Block.UPDATE_ALL);

        // 6. Fragile breach test plug on player-facing front wall (dx = 0, dz = -3, dy = 1..3)
        BlockPos dirtPlug = offsetLocal(center, baseY, forward, right, 0, 1, -3);
        BlockPos cobblePlugLow = offsetLocal(center, baseY, forward, right, 0, 2, -3);
        BlockPos cobblePlugHigh = offsetLocal(center, baseY, forward, right, 0, 3, -3);
        level.setBlock(dirtPlug, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(cobblePlugLow, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(cobblePlugHigh, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
        BreachManager.queueForInspection(level, dirtPlug);
        BreachManager.queueForInspection(level, cobblePlugLow);
        BreachManager.queueForInspection(level, cobblePlugHigh);

        source.sendSuccess(() -> Component.literal(
                "[Hydro-Erosion] Built 10m-deep Test Dam at (" + center.getX() + ", " + (baseY + 1) + ", " + center.getZ()
                        + "). Watch the Dirt/Cobblestone plug crack & breach into the spill basin, "
                        + "check the Water Pressure Sensor -> Redstone Lamp output, or run /hydroerosion inspect!")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static BlockPos offsetLocal(BlockPos origin,
                                        int baseY,
                                        Direction forward,
                                        Direction right,
                                        int dx,
                                        int dy,
                                        int dz) {
        return new BlockPos(
                origin.getX() + right.getStepX() * dx + forward.getStepX() * dz,
                baseY + dy,
                origin.getZ() + right.getStepZ() * dx + forward.getStepZ() * dz
        );
    }
}
