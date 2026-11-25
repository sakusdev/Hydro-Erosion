package com.hydroerosion.compat;

import com.hydroerosion.HydroErosionMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;

import java.lang.reflect.Method;

/**
 * Safe reflection-based bridge for optional Create and Mekanism integrations.
 * Core classes must never directly import classes from optional mods.
 */
public final class CompatManager {

    private static boolean createLoaded = false;
    private static boolean mekanismLoaded = false;
    private static Method mekIntakeHook = null;
    private static Method mekUpgradeHook = null;

    private CompatManager() {
    }

    public static void initCreate(IEventBus modEventBus) {
        try {
            Class<?> clazz = Class.forName("com.hydroerosion.compat.create.CreateCompatModule");
            clazz.getMethod("register", IEventBus.class).invoke(null, modEventBus);
            createLoaded = true;
            HydroErosionMod.LOGGER.info("Create compatibility module registered.");
        } catch (ClassNotFoundException ignored) {
            HydroErosionMod.LOGGER.debug("Create compatibility module not present yet.");
        } catch (Throwable t) {
            HydroErosionMod.LOGGER.error("Failed to initialize Create compatibility module", t);
        }
    }

    public static void initMekanism(IEventBus modEventBus) {
        try {
            Class<?> clazz = Class.forName("com.hydroerosion.compat.mekanism.MekanismCompatModule");
            clazz.getMethod("register", IEventBus.class).invoke(null, modEventBus);
            mekIntakeHook = clazz.getMethod("onDeepPressureIntakeTick", Level.class, BlockPos.class, int.class, boolean.class);
            mekUpgradeHook = clazz.getMethod("onHighPressureUpgradeUsed", Level.class, BlockPos.class, Player.class);
            mekanismLoaded = true;
            HydroErosionMod.LOGGER.info("Mekanism compatibility module registered.");
        } catch (ClassNotFoundException ignored) {
            HydroErosionMod.LOGGER.debug("Mekanism compatibility module not present yet.");
        } catch (Throwable t) {
            HydroErosionMod.LOGGER.error("Failed to initialize Mekanism compatibility module", t);
        }
    }

    public static boolean isCreateLoaded() {
        return createLoaded;
    }

    public static boolean isMekanismLoaded() {
        return mekanismLoaded;
    }

    public static void onDeepPressureIntakeTick(Level level, BlockPos pos, int waterHead, boolean upgraded) {
        if (!mekanismLoaded || mekIntakeHook == null) {
            return;
        }
        try {
            mekIntakeHook.invoke(null, level, pos, waterHead, upgraded);
        } catch (Throwable t) {
            HydroErosionMod.LOGGER.error("Error executing Mekanism deep-pressure intake hook", t);
        }
    }

    public static boolean onHighPressureUpgradeUsed(Level level, BlockPos pos, Player player) {
        if (!mekanismLoaded || mekUpgradeHook == null) {
            return false;
        }
        try {
            return (Boolean) mekUpgradeHook.invoke(null, level, pos, player);
        } catch (Throwable t) {
            HydroErosionMod.LOGGER.error("Error executing Mekanism high-pressure upgrade hook", t);
            return false;
        }
    }
}
