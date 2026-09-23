package com.secret.autoenchanter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class EngineBridge {
    private EngineBridge() {}

    public static void lockBookshelves(int shelves) {
        try {
            Class<?> configs = Class.forName("net.earthcomputer.clientcommands.Configs");
            Method setMin = configs.getMethod("setMinEnchantBookshelves", int.class);
            Method setMax = configs.getMethod("setMaxEnchantBookshelves", int.class);
            setMin.invoke(null, shelves);
            setMax.invoke(null, shelves);
        } catch (Throwable t) {
            throw new IllegalStateException("ClientCommands enchanting engine is not available", t);
        }
    }

    public static boolean isPlayerSeedCracked() {
        try {
            Class<?> configs = Class.forName("net.earthcomputer.clientcommands.Configs");
            Field stateField = configs.getField("playerCrackState");
            Object state = stateField.get(null);
            Method knowsSeed = state.getClass().getMethod("knowsSeed");
            Object result = knowsSeed.invoke(state);
            return result instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }
}
