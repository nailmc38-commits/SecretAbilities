package com.secret.abilities;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public final class VoidClientRuntime {
    private static final Set<String> FRIENDS = new HashSet<>();
    private static final Deque<Long> CLICKS = new ArrayDeque<>();
    private static boolean previousMiddle;
    private static boolean previousLeft;
    private static boolean previousDead;
    private static double originalGamma = -1;
    private static long combatUntil;
    private static float previousTargetHealth = -1;
    private static float lastDamage;
    private static String botStatus = "Idle";

    private VoidClientRuntime() {}

    public static void tick(MinecraftClient client) {
        syncLegacyState();
        if (client.player == null || client.world == null) return;

        handleClicks(client);
        handleDeathWaypoint(client);
        handleMovement(client);
        handlePlayerUtilities(client);
        handleBrightness(client);
        handleBeatGameStatus(client);
        MacroManager.tick(client);
    }

    public static void onModuleChanged(ClientModule module) {
        MinecraftClient client = MinecraftClient.getInstance();

        if ("xray".equals(module.id)) SecretAbilitiesClient.refreshXray();

        if ("auto_mine".equals(module.id)) {
            if (module.isEnabled()) restartAutoMine();
            else {
                BaritoneBridge.stop();
                botStatus = "Idle";
            }
        }

        if ("auto_farm".equals(module.id)) {
            if (module.isEnabled()) {
                if (BaritoneBridge.execute("farm")) botStatus = "Farming";
                else botStatus = "Baritone 1.21.11 not detected";
            } else BaritoneBridge.stop();
        }

        if ("pathfinder".equals(module.id)) {
            if (module.isEnabled()) pathToNearestWaypoint();
            else BaritoneBridge.stop();
        }

        if ("beat_game".equals(module.id)) {
            if (module.isEnabled()) {
                botStatus = BaritoneBridge.isAvailable() ? "Beat Game: preparing" : "Beat Game needs Baritone pathing";
            } else {
                BaritoneBridge.stop();
                botStatus = "Idle";
            }
        }

        if ("flight".equals(module.id) && !module.isEnabled() && client.player != null) {
            if (!client.player.isCreative()) {
                client.player.getAbilities().flying = false;
                client.player.getAbilities().allowFlying = false;
            }
        }

        if ("toggle_sneak".equals(module.id) && !module.isEnabled()) client.options.sneakKey.setPressed(false);
        if ("auto_eat".equals(module.id) && !module.isEnabled()) client.options.useKey.setPressed(false);
    }

    private static void syncLegacyState() {
        ModState.walkOnWater = ModuleRegistry.isEnabled("water_walk");
        ModState.xray = ModuleRegistry.isEnabled("xray");
        ModState.playerEsp = ModuleRegistry.isEnabled("player_esp");
        ModState.mobEsp = ModuleRegistry.isEnabled("mob_esp");
        ModState.statsHud = ModuleRegistry.isEnabled("stats_hud");
        ModState.waypointHud = ModuleRegistry.isEnabled("waypoints");
    }

    private static void handleMovement(MinecraftClient client) {
        PlayerEntity player = client.player;

        if (ModuleRegistry.isEnabled("auto_sprint")
                && client.options.forwardKey.isPressed()
                && !player.isSneaking()) {
            player.setSprinting(true);
        }

        if (ModuleRegistry.isEnabled("toggle_sneak")) client.options.sneakKey.setPressed(true);

        if (ModuleRegistry.isEnabled("auto_jump")
                && client.options.forwardKey.isPressed()
                && player.isOnGround()) {
            player.jump();
        }

        if (ModuleRegistry.isEnabled("flight")) {
            player.getAbilities().allowFlying = true;
            player.getAbilities().flying = true;
            player.getAbilities().setFlySpeed(RuntimeSettings.flightSpeed);
        }

        if (ModuleRegistry.isEnabled("speed")
                && player.isOnGround()
                && client.options.forwardKey.isPressed()) {
            Vec3d velocity = player.getVelocity();
            double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            double max = 0.30 * RuntimeSettings.speedMultiplier;

            if (horizontal > 0.01 && horizontal < max) {
                double scale = Math.min(RuntimeSettings.speedMultiplier, max / horizontal);
                player.setVelocity(velocity.x * scale, velocity.y, velocity.z * scale);
            }
        }
    }

    private static void handlePlayerUtilities(MinecraftClient client) {
        if (ModuleRegistry.isEnabled("auto_eat")) autoEat(client);
        if (ModuleRegistry.isEnabled("auto_tool")) autoTool(client);

        if (ModuleRegistry.isEnabled("middle_click_friend")) {
            long handle = client.getWindow().getHandle();
            boolean middle = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;

            if (middle && !previousMiddle && client.targetedEntity instanceof PlayerEntity player) {
                String name = player.getName().getString();
                if (!FRIENDS.add(name)) FRIENDS.remove(name);
            }
            previousMiddle = middle;
        }
    }

    private static void autoEat(MinecraftClient client) {
        if (client.player.getHungerManager().getFoodLevel() >= 14) {
            client.options.useKey.setPressed(false);
            return;
        }

        int selected = client.player.getInventory().getSelectedSlot();
        ItemStack selectedStack = client.player.getInventory().getStack(selected);

        if (!selectedStack.contains(DataComponentTypes.FOOD)) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = client.player.getInventory().getStack(i);
                if (stack.contains(DataComponentTypes.FOOD)) {
                    client.player.getInventory().setSelectedSlot(i);
                    break;
                }
            }
        }

        ItemStack stack = client.player.getInventory().getStack(client.player.getInventory().getSelectedSlot());
        client.options.useKey.setPressed(stack.contains(DataComponentTypes.FOOD));
    }

    private static void autoTool(MinecraftClient client) {
        if (!(client.crosshairTarget instanceof BlockHitResult hit)) return;

        var state = client.world.getBlockState(hit.getBlockPos());
        int bestSlot = client.player.getInventory().getSelectedSlot();
        float bestSpeed = client.player.getInventory().getStack(bestSlot).getMiningSpeedMultiplier(state);

        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        client.player.getInventory().setSelectedSlot(bestSlot);
    }

    private static void handleBrightness(MinecraftClient client) {
        if (ModuleRegistry.isEnabled("fullbright")) {
            if (originalGamma < 0) originalGamma = client.options.getGamma().getValue();
            client.options.getGamma().setValue(1.0);
        } else if (originalGamma >= 0) {
            client.options.getGamma().setValue(originalGamma);
            originalGamma = -1;
        }
    }

    private static void handleDeathWaypoint(MinecraftClient client) {
        boolean dead = client.player.isDead() || client.player.getHealth() <= 0.0f;
        if (ModuleRegistry.isEnabled("death_waypoint") && dead && !previousDead) {
            WaypointManager.addNamedCurrent(client, "Death");
        }
        previousDead = dead;
    }

    private static void handleClicks(MinecraftClient client) {
        long now = System.currentTimeMillis();
        long handle = client.getWindow().getHandle();
        boolean left = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (left && !previousLeft) {
            CLICKS.addLast(now);
            combatUntil = now + 15000;

            if (client.targetedEntity instanceof LivingEntity living) {
                float health = living.getHealth();
                if (previousTargetHealth >= 0 && health < previousTargetHealth) {
                    lastDamage = previousTargetHealth - health;
                }
                previousTargetHealth = health;
            }
        }
        previousLeft = left;

        while (!CLICKS.isEmpty() && CLICKS.peekFirst() < now - 1000) CLICKS.removeFirst();
    }

    private static void handleBeatGameStatus(MinecraftClient client) {
        if (!ModuleRegistry.isEnabled("beat_game")) return;

        if (!BaritoneBridge.isAvailable()) {
            botStatus = "Beat Game: Baritone pathing missing";
            return;
        }

        if (client.player.getHungerManager().getFoodLevel() < 10) botStatus = "Beat Game: needs food";
        else if (client.player.getInventory().isEmpty()) botStatus = "Beat Game: gathering first resources";
        else botStatus = "Beat Game: survival planner active (experimental)";
    }

    public static void restartAutoMine() {
        if (!ModuleRegistry.isEnabled("auto_mine")) return;

        if (BaritoneBridge.execute("mine " + RuntimeSettings.mineTarget())) {
            botStatus = "Mining minecraft:" + RuntimeSettings.mineTarget();
        } else {
            botStatus = "Auto Mine needs Baritone 1.21.11";
        }
    }

    public static void pathToNearestWaypoint() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || WaypointManager.getAll().isEmpty()) {
            botStatus = "No waypoint available";
            return;
        }

        String dimension = client.world.getRegistryKey().getValue().toString();
        WaypointManager.Waypoint best = null;
        double bestDistance = Double.MAX_VALUE;

        for (WaypointManager.Waypoint waypoint : WaypointManager.getAll()) {
            if (!dimension.equals(waypoint.dimension())) continue;

            double distance = client.player.squaredDistanceTo(
                    waypoint.x() + 0.5, waypoint.y() + 0.5, waypoint.z() + 0.5
            );
            if (distance < bestDistance) {
                bestDistance = distance;
                best = waypoint;
            }
        }

        if (best == null) {
            botStatus = "No waypoint in this dimension";
            return;
        }

        if (BaritoneBridge.execute("goto " + best.x() + " " + best.y() + " " + best.z())) {
            botStatus = "Pathing to " + best.name();
        } else {
            botStatus = "Pathfinder needs Baritone 1.21.11";
        }
    }

    public static int cps() {
        return CLICKS.size();
    }

    public static long combatSeconds() {
        long remaining = combatUntil - System.currentTimeMillis();
        return Math.max(0, (remaining + 999) / 1000);
    }

    public static float lastDamage() {
        return lastDamage;
    }

    public static String botStatus() {
        return botStatus;
    }

    public static boolean isFriend(String name) {
        return FRIENDS.contains(name);
    }
}
