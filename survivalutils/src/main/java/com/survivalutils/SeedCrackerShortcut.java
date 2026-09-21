package com.survivalutils;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class SeedCrackerShortcut {
    private SeedCrackerShortcut() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    literal("crackseed")
                            .executes(context -> run())
            );
        });
    }

    private static int run() {
        MinecraftClient client = MinecraftClient.getInstance();

        try {
            Class<?> configClass = Class.forName("kaptainwutax.seedcrackerX.config.Config");
            Object config = configClass.getMethod("get").invoke(null);

            Field active = configClass.getField("active");
            active.setBoolean(config, true);
            configClass.getMethod("save").invoke(null);

            Class<?> seedClass = Class.forName("kaptainwutax.seedcrackerX.SeedCracker");
            Object seedCracker = seedClass.getMethod("get").invoke(null);

            if (seedCracker == null) {
                message(client,
                        "SeedCrackerX is still starting. Try /crackseed again in a second.",
                        Formatting.YELLOW);
                return 0;
            }

            Object storage = seedClass.getMethod("getDataStorage").invoke(seedCracker);
            Class<?> storageClass = storage.getClass();

            double baseBits = number(storageClass.getMethod("getBaseBits").invoke(storage));
            double wantedBits = number(storageClass.getMethod("getWantedBits").invoke(storage));
            double liftingBits = number(storageClass.getMethod("getLiftingBits").invoke(storage));

            // Process any structure/biome data that SeedCrackerX already queued.
            storageClass.getMethod("tick").invoke(storage);

            String progress = String.format(
                    Locale.ROOT,
                    "SeedCracker ON // %.1f/%.1f bits // lifting %.1f/40",
                    baseBits,
                    wantedBits,
                    liftingBits);

            if (baseBits >= wantedBits) {
                message(client,
                        progress + " // enough data collected — cracking now.",
                        Formatting.GREEN);
            } else {
                message(client,
                        progress + " // move around and load structures; it will keep collecting automatically.",
                        Formatting.AQUA);
            }

            return 1;
        } catch (ClassNotFoundException e) {
            message(client,
                    "SeedCrackerX was not found inside this build.",
                    Formatting.RED);
            return 0;
        } catch (Throwable t) {
            message(client,
                    "SeedCracker shortcut error: " + t.getClass().getSimpleName(),
                    Formatting.RED);
            return 0;
        }
    }

    private static double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static void message(MinecraftClient client, String text, Formatting color) {
        if (client.player != null) {
            client.player.sendMessage(
                    Text.literal(text).formatted(color),
                    false
            );
        }
    }
}
