package com.secret.abilities;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MacroManager {
    public static final class Macro {
        public String name;
        public boolean enabled;
        public String key;
        public String script;

        public Macro(String name, boolean enabled, String key, String script) {
            this.name = name;
            this.enabled = enabled;
            this.key = key;
            this.script = script;
        }
    }

    private static final List<Macro> MACROS = new ArrayList<>();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("voidclient-macros.txt");
    private static final Map<Integer, Boolean> PREVIOUS_KEY_STATE = new HashMap<>();

    private static Macro running;
    private static String[] runningSteps = new String[0];
    private static int stepIndex;
    private static int waitTicks;
    private static KeyBinding heldBinding;
    private static int heldTicks;
    private static int repeatRemaining;

    private MacroManager() {}

    public static List<Macro> all() {
        return Collections.unmodifiableList(MACROS);
    }

    public static void add(String name, String key, String script) {
        MACROS.add(new Macro(cleanName(name), true, cleanKey(key), script.trim()));
        save();
    }

    public static void update(int index, String name, String key, String script) {
        if (index < 0 || index >= MACROS.size()) return;
        Macro macro = MACROS.get(index);
        macro.name = cleanName(name);
        macro.key = cleanKey(key);
        macro.script = script.trim();
        save();
    }

    public static void delete(int index) {
        if (index < 0 || index >= MACROS.size()) return;
        Macro removed = MACROS.remove(index);
        if (removed == running) stopRunning();
        save();
    }

    public static void toggle(int index) {
        if (index < 0 || index >= MACROS.size()) return;
        Macro macro = MACROS.get(index);
        macro.enabled = !macro.enabled;
        save();
    }

    public static void start(MinecraftClient client, Macro macro) {
        if (macro == null || macro.script.isBlank()) return;
        stopRunning();
        running = macro;
        runningSteps = macro.script.split(";");
        stepIndex = 0;
        waitTicks = 0;
        repeatRemaining = 0;
    }

    public static String runningName() {
        return running == null ? "None" : running.name;
    }

    public static void tick(MinecraftClient client) {
        pollMacroKeys(client);

        if (heldBinding != null) {
            heldTicks--;
            if (heldTicks <= 0) {
                heldBinding.setPressed(false);
                heldBinding = null;
            }
            return;
        }

        if (running == null) return;

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        if (stepIndex >= runningSteps.length) {
            stopRunning();
            return;
        }

        String step = runningSteps[stepIndex++].trim();
        if (!step.isEmpty()) executeStep(client, step);
    }

    private static void pollMacroKeys(MinecraftClient client) {
        if (client.currentScreen != null) return;
        long handle = client.getWindow().getHandle();

        for (Macro macro : MACROS) {
            if (!macro.enabled) continue;
            int code = keyCode(macro.key);
            if (code == GLFW.GLFW_KEY_UNKNOWN) continue;

            boolean down = GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS;
            boolean previous = PREVIOUS_KEY_STATE.getOrDefault(code, false);
            if (down && !previous) start(client, macro);
            PREVIOUS_KEY_STATE.put(code, down);
        }
    }

    private static void executeStep(MinecraftClient client, String step) {
        String lower = step.toLowerCase(Locale.ROOT);

        try {
            if (lower.startsWith("wait ")) {
                long millis = Long.parseLong(step.substring(5).trim());
                waitTicks = Math.max(1, (int) Math.ceil(millis / 50.0));
                return;
            }

            if (lower.startsWith("cmd ")) {
                if (client.getNetworkHandler() != null) {
                    String command = step.substring(4).trim();
                    if (command.startsWith("/")) command = command.substring(1);
                    client.getNetworkHandler().sendChatCommand(command);
                }
                return;
            }

            if (lower.startsWith("chat ")) {
                if (client.getNetworkHandler() != null) {
                    client.getNetworkHandler().sendChatMessage(step.substring(5));
                }
                return;
            }

            if (lower.startsWith("toggle ")) {
                ModuleRegistry.toggleByLooseName(step.substring(7));
                return;
            }

            if (lower.startsWith("mouse ")) {
                if (client.player == null) return;
                String[] parts = step.substring(6).trim().split("\\s+");
                if (parts.length >= 2) {
                    float dx = Float.parseFloat(parts[0]);
                    float dy = Float.parseFloat(parts[1]);
                    client.player.setYaw(client.player.getYaw() + dx * 0.15f);
                    client.player.setPitch(MathHelper.clamp(client.player.getPitch() + dy * 0.15f, -90.0f, 90.0f));
                }
                return;
            }

            if (lower.startsWith("slot ")) {
                if (client.player != null) {
                    int slot = Integer.parseInt(step.substring(5).trim()) - 1;
                    if (slot >= 0 && slot <= 8) client.player.getInventory().setSelectedSlot(slot);
                }
                return;
            }

            if (lower.startsWith("forward ")) {
                hold(client.options.forwardKey, Integer.parseInt(step.substring(8).trim()));
                return;
            }
            if (lower.startsWith("back ")) {
                hold(client.options.backKey, Integer.parseInt(step.substring(5).trim()));
                return;
            }
            if (lower.startsWith("left ")) {
                hold(client.options.leftKey, Integer.parseInt(step.substring(5).trim()));
                return;
            }
            if (lower.startsWith("right ")) {
                hold(client.options.rightKey, Integer.parseInt(step.substring(6).trim()));
                return;
            }
            if (lower.startsWith("sneak ")) {
                hold(client.options.sneakKey, Integer.parseInt(step.substring(6).trim()));
                return;
            }
            if (lower.startsWith("use ")) {
                hold(client.options.useKey, Integer.parseInt(step.substring(4).trim()));
                return;
            }
            if (lower.startsWith("attack ")) {
                hold(client.options.attackKey, Integer.parseInt(step.substring(7).trim()));
                return;
            }

            if (lower.equals("click left") || lower.equals("attack")) {
                hold(client.options.attackKey, 1);
                return;
            }
            if (lower.equals("click right") || lower.equals("use")) {
                hold(client.options.useKey, 1);
                return;
            }
            if (lower.equals("jump")) {
                if (client.player != null) client.player.jump();
                return;
            }

            if (lower.startsWith("repeat ")) {
                int amount = Math.max(1, Integer.parseInt(step.substring(7).trim()));
                if (repeatRemaining == 0) repeatRemaining = amount - 1;
                if (repeatRemaining > 0) {
                    repeatRemaining--;
                    stepIndex = 0;
                }
                return;
            }

            if (lower.equals("stop")) stopRunning();
        } catch (Exception ignored) {
        }
    }

    private static void hold(KeyBinding binding, int ticks) {
        if (heldBinding != null) heldBinding.setPressed(false);
        heldBinding = binding;
        heldTicks = Math.max(1, ticks);
        heldBinding.setPressed(true);
    }

    private static void stopRunning() {
        if (heldBinding != null) heldBinding.setPressed(false);
        heldBinding = null;
        heldTicks = 0;
        running = null;
        runningSteps = new String[0];
        stepIndex = 0;
        waitTicks = 0;
        repeatRemaining = 0;
    }

    public static int keyCode(String keyName) {
        String key = cleanKey(keyName);
        if (key.length() == 1) {
            char c = key.charAt(0);
            if (c >= 'A' && c <= 'Z') return GLFW.GLFW_KEY_A + (c - 'A');
            if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + (c - '0');
        }

        return switch (key) {
            case "F1" -> GLFW.GLFW_KEY_F1;
            case "F2" -> GLFW.GLFW_KEY_F2;
            case "F3" -> GLFW.GLFW_KEY_F3;
            case "F4" -> GLFW.GLFW_KEY_F4;
            case "F5" -> GLFW.GLFW_KEY_F5;
            case "F6" -> GLFW.GLFW_KEY_F6;
            case "F7" -> GLFW.GLFW_KEY_F7;
            case "F8" -> GLFW.GLFW_KEY_F8;
            case "F9" -> GLFW.GLFW_KEY_F9;
            case "F10" -> GLFW.GLFW_KEY_F10;
            case "F11" -> GLFW.GLFW_KEY_F11;
            case "F12" -> GLFW.GLFW_KEY_F12;
            case "SPACE" -> GLFW.GLFW_KEY_SPACE;
            case "SHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "CTRL", "CONTROL" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "ALT" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "TAB" -> GLFW.GLFW_KEY_TAB;
            case "CAPS" -> GLFW.GLFW_KEY_CAPS_LOCK;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
    }

    private static String cleanName(String name) {
        String cleaned = name == null ? "" : name.trim();
        return cleaned.isEmpty() ? "New Macro" : cleaned.replace("|", "");
    }

    private static String cleanKey(String key) {
        if (key == null) return "NONE";
        String cleaned = key.trim().toUpperCase(Locale.ROOT);
        return cleaned.isEmpty() ? "NONE" : cleaned;
    }

    public static void load() {
        MACROS.clear();
        if (!Files.exists(FILE)) return;

        try {
            for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|", 4);
                if (parts.length != 4) continue;

                try {
                    String name = new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                    boolean enabled = Boolean.parseBoolean(parts[1]);
                    String key = parts[2];
                    String script = new String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8);
                    MACROS.add(new Macro(name, enabled, key, script));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static void save() {
        List<String> lines = new ArrayList<>();
        for (Macro macro : MACROS) {
            String name = Base64.getEncoder().encodeToString(macro.name.getBytes(StandardCharsets.UTF_8));
            String script = Base64.getEncoder().encodeToString(macro.script.getBytes(StandardCharsets.UTF_8));
            lines.add(name + "|" + macro.enabled + "|" + macro.key + "|" + script);
        }

        try {
            Files.createDirectories(FILE.getParent());
            Files.write(FILE, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
