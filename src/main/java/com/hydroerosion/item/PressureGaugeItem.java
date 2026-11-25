package com.hydroerosion.item;

import com.hydroerosion.pressure.ErosionSavedData;
import com.hydroerosion.pressure.WaterPressureCalculator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Locale;

/**
 * Diagnostic tool that measures water head depth, hydrostatic pressure (kPa),
 * and structural pressure limits on right-clicked blocks or water bodies.
 */
public class PressureGaugeItem extends Item {

    public PressureGaugeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockPos targetPos = context.getClickedPos();
        // If clicking into water on the clicked face, prioritize the water side when it has higher head
        BlockPos facePos = targetPos.relative(context.getClickedFace());
        if (!level.isClientSide) {
            BlockPos measurePos = WaterPressureCalculator.isWater(level, facePos)
                    && !WaterPressureCalculator.isWater(level, targetPos)
                    ? targetPos
                    : targetPos;
            reportReading(level, player, measurePos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() == HitResult.Type.BLOCK) {
            if (!level.isClientSide) {
                reportReading(level, player, hit.getBlockPos());
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!level.isClientSide) {
            reportReading(level, player, player.blockPosition());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void reportReading(Level level, Player player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        int head = WaterPressureCalculator.getMaxAdjacentWaterHead(level, pos);
        float pressureKpa = WaterPressureCalculator.calculatePressureKpa(head);
        float limitKpa = WaterPressureCalculator.getBlockPressureLimitKpa(state);

        String limitText = Float.isInfinite(limitKpa)
                ? "INF"
                : String.format(Locale.ROOT, "%.1f kPa", limitKpa);

        ChatFormatting statusColor;
        String statusLabel;
        if (!state.getFluidState().isEmpty() || state.isAir()) {
            statusColor = head >= 40 ? ChatFormatting.AQUA : ChatFormatting.BLUE;
            statusLabel = head >= 40 ? "DEEP HIGH-PRESSURE (>=40m)" : "FLUID READING";
        } else if (Float.isInfinite(limitKpa) || pressureKpa <= limitKpa * 0.75f) {
            statusColor = ChatFormatting.GREEN;
            statusLabel = "SAFE";
        } else if (pressureKpa <= limitKpa) {
            statusColor = ChatFormatting.YELLOW;
            statusLabel = "WARNING (Near Limit)";
        } else {
            statusColor = ChatFormatting.RED;
            statusLabel = "CRITICAL (Exceeds Limit!)";
        }

        float damagePct = level instanceof ServerLevel serverLevel
                ? ErosionSavedData.get(serverLevel).getDamage(pos)
                : 0.0f;
        String crackInfo = damagePct > 0.0f
                ? String.format(Locale.ROOT, " | Crack: %.0f%%", damagePct)
                : "";

        Component msg = Component.literal("[Hydro-Erosion] ")
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(String.format(Locale.ROOT,
                        "%s @ (%d, %d, %d) | Depth: %d m | Pressure: %.1f kPa | Limit: %s%s | ",
                        state.getBlock().getName().getString(),
                        pos.getX(), pos.getY(), pos.getZ(),
                        head, pressureKpa, limitText, crackInfo))
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(statusLabel).withStyle(statusColor));

        player.displayClientMessage(msg, false);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.35f, 1.4f);
    }
}
