package com.secret.abilities;

import java.lang.reflect.Method;

public final class BaritoneBridge {
    private static Boolean available;

    private BaritoneBridge() {}

    public static boolean isAvailable() {
        if (available != null) return available;
        try {
            Class.forName("baritone.api.BaritoneAPI");
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
        return available;
    }

    public static boolean execute(String command) {
        if (!isAvailable()) return false;

        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object commandManager = baritone.getClass().getMethod("getCommandManager").invoke(baritone);

            for (Method method : commandManager.getClass().getMethods()) {
                if (method.getName().equals("execute")
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == String.class) {
                    method.invoke(commandManager, command);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static void stop() {
        execute("stop");
    }
}
