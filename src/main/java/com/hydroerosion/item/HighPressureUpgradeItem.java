package com.hydroerosion.item;

import com.hydroerosion.blockentity.DeepPressureIntakeBlockEntity;
import com.hydroerosion.compat.CompatManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Upgrade module installed into Deep Pressure Intakes or Mekanism water machines
 * to double Heavy Water and cooling water output under >= 40m water depth.
 */
public class HighPressureUpgradeItem extends Item {

    public HighPressureUpgradeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DeepPressureIntakeBlockEntity intake) {
            if (intake.isUpgraded()) {
                player.displayClientMessage(Component.literal("[Hydro-Erosion] Intake is already upgraded.")
                        .withStyle(ChatFormatting.YELLOW), false);
                return InteractionResult.CONSUME;
            }
            intake.setUpgraded(true);
            if (!player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5f, 1.35f);
            player.displayClientMessage(Component.literal("[Hydro-Erosion] Installed High-Pressure Upgrade (2x output at >= 40m depth).")
                    .withStyle(ChatFormatting.AQUA), false);
            return InteractionResult.CONSUME;
        }

        if (CompatManager.onHighPressureUpgradeUsed(level, pos, player)) {
            if (!player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5f, 1.35f);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hydroerosion.high_pressure_upgrade").withStyle(ChatFormatting.GRAY));
    }
}
