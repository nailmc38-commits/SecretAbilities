package com.survos;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;

public final class AutomationManager {
    public enum Mode { IDLE, MOB_GRIND, MINING, TREE_FARM, CROP_FARM, FISHING, ANIMAL_FARM, INVENTORY, NAVIGATE, ROUTE }
    public enum State { IDLE, SEARCHING, MOVING, INTERACTING, FIGHTING, LOOTING, EATING, ESCAPING, PAUSED, BLOCKED, COMPLETE }

    public record QueuedTask(Mode mode, String target, int count) {}

    private final LocalNavigator navigator = new LocalNavigator();
    private final Queue<QueuedTask> queue = new ArrayDeque<>();

    private Mode mode = Mode.IDLE;
    private State state = State.IDLE;
    private Entity entityTarget;
    private BlockPos blockTarget;
    private Vec3d start;
    private String reason = "Ready";
    private boolean paused;
    private int actionCooldown;
    private int noTargetTicks;
    private int eatingTicks;

    private String goalItem = "";
    private int goalCount;

    private WorldMemory.Point waypointTarget;
    private String waypointName = "";
    private String routeName = "";
    private int routeIndex;

    private BlockPos replantPos;
    private String replantItem = "";
    private int fishingTicks;
    private boolean fishingCast;
    private int branchDx;
    private int branchDz;

    public Mode mode() { return mode; }
    public State state() { return state; }
    public String reason() { return reason; }
    public boolean active() { return mode != Mode.IDLE; }
    public boolean paused() { return paused; }
    public String goalItem() { return goalItem; }
    public int goalCount() { return goalCount; }
    public int queuedTasks() { return queue.size(); }

    public void setGoal(String item, int count) {
        goalItem = item == null ? "" : WorldScanner.normalize(item);
        goalCount = Math.max(0, count);
    }

    public void queue(Mode m, String target, int count) {
        queue.offer(new QueuedTask(m, target == null ? "" : target, Math.max(0, count)));
    }

    public void clearQueue() { queue.clear(); }

    public void start(Mode newMode, MinecraftClient client) {
        stopMovement(client);
        navigator.reset(client);
        mode = newMode == null ? Mode.IDLE : newMode;
        state = mode == Mode.IDLE ? State.IDLE : State.SEARCHING;
        paused = false;
        entityTarget = null;
        blockTarget = null;
        replantPos = null;
        noTargetTicks = 0;
        fishingTicks = 0;
        fishingCast = false;
        if (client.player != null) {
            start = client.player.getEntityPos();
            setBranchHeading(client.player);
        }
        reason = mode == Mode.IDLE ? "Ready" : "Task started";
        SurvOsClient.notice("Automation: " + pretty(mode));
    }

    public boolean goToWaypoint(MinecraftClient client, String name) {
        WorldMemory.Point p = SurvOsClient.MEMORY.waypoint(name);
        if (p == null || client.world == null) return false;
        String dim = client.world.getRegistryKey().getValue().toString();
        if (!dim.equals(p.dimension)) {
            reason = "Waypoint is in " + p.dimension;
            SurvOsClient.notice(reason);
            return false;
        }
        waypointTarget = p;
        waypointName = name;
        start(Mode.NAVIGATE, client);
        return true;
    }

    public boolean playRoute(MinecraftClient client, String name) {
        if (SurvOsClient.MEMORY.route(name).isEmpty()) return false;
        routeName = name;
        routeIndex = 0;
        start(Mode.ROUTE, client);
        return true;
    }

    public void pause(MinecraftClient client) {
        if (!active()) return;
        paused = true;
        state = State.PAUSED;
        reason = "Paused by user";
        stopMovement(client);
        navigator.reset(client);
        SurvOsClient.notice("Automation paused");
    }

    public void resume() {
        if (!active()) return;
        paused = false;
        state = State.SEARCHING;
        reason = "Resumed";
        SurvOsClient.notice("Automation resumed");
    }

    public void stop(MinecraftClient client, String why) {
        stopMovement(client);
        navigator.reset(client);
        mode = Mode.IDLE;
        state = State.IDLE;
        entityTarget = null;
        blockTarget = null;
        paused = false;
        replantPos = null;
        fishingCast = false;
        reason = why == null ? "Stopped" : why;
        SurvOsClient.notice("Automation stopped" + (why == null ? "" : ": " + why));
    }

    public void tick(MinecraftClient client, SurvConfig cfg) {
        if (client.player == null || client.world == null || client.interactionManager == null) return;

        if (mode == Mode.IDLE) {
            maybeStartQueued(client);
            return;
        }
        if (paused) return;
        if (actionCooldown > 0) actionCooldown--;

        PlayerEntity p = client.player;
        String rule = SurvOsClient.RULES.triggered(client);
        if (rule != null) {
            state = State.BLOCKED;
            reason = "Rule: " + rule;
            stopMovement(client);
            navigator.reset(client);
            return;
        }

        if (goalCount > 0 && !goalItem.isBlank() && InventoryManager.count(p, goalItem) >= goalCount) {
            complete(client, "Goal reached: " + goalItem + " " + goalCount);
            return;
        }

        if (cfg.lowHealthSafety && p.getHealth() <= cfg.retreatHealth) {
            tickRecovery(client, cfg);
            return;
        }

        if (InventoryManager.freeSlots(p) <= 0 && mode != Mode.NAVIGATE && mode != Mode.ROUTE) {
            state = State.BLOCKED;
            reason = "Inventory full";
            stopMovement(client);
            return;
        }

        if (start != null && p.getEntityPos().distanceTo(start) > cfg.maxDistanceFromStart
                && mode != Mode.NAVIGATE && mode != Mode.ROUTE) {
            state = State.BLOCKED;
            reason = "Distance limit reached";
            stopMovement(client);
            return;
        }

        switch (mode) {
            case MOB_GRIND -> tickMobGrind(client, cfg);
            case MINING -> tickMining(client, cfg);
            case TREE_FARM -> tickTrees(client, cfg);
            case CROP_FARM -> tickCrops(client, cfg);
            case FISHING -> tickFishing(client);
            case ANIMAL_FARM -> tickAnimals(client, cfg);
            case INVENTORY -> tickInventory(client);
            case NAVIGATE -> tickNavigate(client);
            case ROUTE -> tickRoute(client);
            default -> {}
        }
    }

    private void tickRecovery(MinecraftClient c, SurvConfig cfg) {
        state = State.EATING;
        reason = "Safety recovery";
        stopMovement(c);
        navigator.reset(c);

        if (c.player.getHungerManager().isNotFull() && InventoryManager.selectFood(c, true)) {
            c.options.useKey.setPressed(true);
            eatingTicks++;
            if (eatingTicks > 50) {
                c.options.useKey.setPressed(false);
                eatingTicks = 0;
            }
        } else {
            c.options.useKey.setPressed(false);
        }

        if (c.player.getHealth() > Math.min(c.player.getMaxHealth() - 1f, cfg.retreatHealth + 6f)) {
            eatingTicks = 0;
            c.options.useKey.setPressed(false);
            state = State.SEARCHING;
            reason = "Recovered";
        }
    }

    private void tickMobGrind(MinecraftClient c, SurvConfig cfg) {
        PlayerEntity p = c.player;
        if (entityTarget == null || !entityTarget.isAlive() || entityTarget.distanceTo(p) > cfg.automationRange + 6) {
            entityTarget = WorldScanner.hostile(c, cfg.automationRange, cfg);
        }

        if (entityTarget == null) {
            ItemEntity item = cfg.collectLoot ? WorldScanner.item(c, Math.min(10, cfg.automationRange)) : null;
            if (item != null) {
                entityTarget = item;
                state = State.LOOTING;
            } else {
                state = State.SEARCHING;
                reason = "Scanning for hostiles";
                stopMovement(c);
                return;
            }
        }

        double d = p.distanceTo(entityTarget);
        LocalNavigator.lookAt(p, entityTarget.getBoundingBox().getCenter());

        if (entityTarget instanceof ItemEntity item) {
            LocalNavigator.Status nav = navigator.moveNear(c,
                    new BlockPos(item.getBlockX(), item.getBlockY(), item.getBlockZ()), 0);
            state = nav == LocalNavigator.Status.MOVING ? State.LOOTING : State.SEARCHING;
            reason = "Collecting loot " + String.format("%.1fm", d);
            if (d < 1.2) entityTarget = null;
            return;
        }

        HostileEntity mob = (HostileEntity) entityTarget;
        state = State.FIGHTING;
        reason = Registries.ENTITY_TYPE.getId(mob.getType()).getPath() + " " + String.format("%.1fm", d);

        if (d > 2.9) {
            navigator.moveNear(c, new BlockPos(mob.getBlockX(), mob.getBlockY(), mob.getBlockZ()), 2);
        } else {
            navigator.reset(c);
            stopMovement(c);
            LocalNavigator.lookAt(p, mob.getBoundingBox().getCenter());
            if (actionCooldown <= 0 && p.getAttackCooldownProgress(0f) >= 0.88f) {
                c.interactionManager.attackEntity(p, mob);
                p.swingHand(Hand.MAIN_HAND);
                SurvOsClient.STATS.mobHit();
                actionCooldown = 4;
            }
        }
    }

    private void tickMining(MinecraftClient c, SurvConfig cfg) {
        if (blockTarget == null || c.world.getBlockState(blockTarget).isAir()) {
            blockTarget = WorldScanner.exposedOre(c, 12, goalItem);
            if (blockTarget == null && goalItem.isBlank()) blockTarget = WorldScanner.exposedOre(c, 12, "");
        }

        if (blockTarget == null) {
            noTargetTicks++;
            if (noTargetTicks < 20) {
                state = State.SEARCHING;
                reason = "Scanning loaded area for exposed ores";
                return;
            }
            tickBranchMine(c, cfg);
            return;
        }

        noTargetTicks = 0;
        BlockState s = c.world.getBlockState(blockTarget);
        reason = "Mining " + shortBlock(s);

        if (distanceSq(c.player, blockTarget) > 18.0) {
            state = State.MOVING;
            LocalNavigator.Status nav = navigator.moveNear(c, blockTarget, 2);
            if (nav == LocalNavigator.Status.FAILED) {
                blockTarget = null;
                noTargetTicks = 0;
            }
            return;
        }

        navigator.reset(c);
        stopMovement(c);
        InventoryManager.selectBestTool(c, s, cfg.durabilityStopPercent);
        state = State.INTERACTING;
        breakBlock(c, blockTarget);
        if (c.world.getBlockState(blockTarget).isAir()) {
            SurvOsClient.STATS.blockMined();
            blockTarget = null;
        }
    }

    private void tickBranchMine(MinecraftClient c, SurvConfig cfg) {
        PlayerEntity p = c.player;
        state = State.INTERACTING;
        reason = goalItem.isBlank() ? "Branch mining" : "Branch mining for " + goalItem;

        BlockPos feet = new BlockPos(p.getBlockX(), p.getBlockY(), p.getBlockZ());
        BlockPos frontLow = feet.add(branchDx, 0, branchDz);
        BlockPos frontHigh = frontLow.up();

        if (!c.world.getBlockState(frontLow).isAir()) {
            blockTarget = frontLow;
            BlockState s = c.world.getBlockState(blockTarget);
            InventoryManager.selectBestTool(c, s, cfg.durabilityStopPercent);
            breakBlock(c, blockTarget);
            return;
        }
        if (!c.world.getBlockState(frontHigh).isAir()) {
            blockTarget = frontHigh;
            BlockState s = c.world.getBlockState(blockTarget);
            InventoryManager.selectBestTool(c, s, cfg.durabilityStopPercent);
            breakBlock(c, blockTarget);
            return;
        }

        blockTarget = null;
        LocalNavigator.lookAt(p, new Vec3d(p.getX() + branchDx * 4, p.getEyeY(), p.getZ() + branchDz * 4));
        c.options.forwardKey.setPressed(true);
        c.options.sprintKey.setPressed(false);
    }

    private void tickTrees(MinecraftClient c, SurvConfig cfg) {
        if (blockTarget == null || c.world.getBlockState(blockTarget).isAir()) {
            blockTarget = WorldScanner.log(c, 14);
        }
        if (blockTarget == null) {
            state = State.SEARCHING;
            reason = "Scanning for trees";
            stopMovement(c);
            return;
        }

        BlockState s = c.world.getBlockState(blockTarget);
        reason = "Tree " + shortBlock(s);
        if (distanceSq(c.player, blockTarget) > 18.0) {
            state = State.MOVING;
            if (navigator.moveNear(c, blockTarget, 2) == LocalNavigator.Status.FAILED) blockTarget = null;
            return;
        }

        navigator.reset(c);
        stopMovement(c);
        InventoryManager.selectBestTool(c, s, cfg.durabilityStopPercent);
        state = State.INTERACTING;
        breakBlock(c, blockTarget);
        if (c.world.getBlockState(blockTarget).isAir()) {
            SurvOsClient.STATS.blockMined();
            blockTarget = null;
        }
    }

    private void tickCrops(MinecraftClient c, SurvConfig cfg) {
        if (replantPos != null) {
            tickReplant(c);
            return;
        }

        if (blockTarget == null || c.world.getBlockState(blockTarget).isAir()) {
            blockTarget = WorldScanner.matureCrop(c, 12);
        }
        if (blockTarget == null) {
            state = State.SEARCHING;
            reason = "Scanning for mature crops";
            stopMovement(c);
            return;
        }

        if (distanceSq(c.player, blockTarget) > 16.0) {
            state = State.MOVING;
            if (navigator.moveNear(c, blockTarget, 2) == LocalNavigator.Status.FAILED) blockTarget = null;
            return;
        }

        BlockState s = c.world.getBlockState(blockTarget);
        String id = Registries.BLOCK.getId(s.getBlock()).getPath();
        replantItem = cropSeed(id);
        replantPos = blockTarget.toImmutable();
        navigator.reset(c);
        stopMovement(c);
        state = State.INTERACTING;
        reason = "Harvesting " + id;
        breakBlock(c, blockTarget);

        if (c.world.getBlockState(blockTarget).isAir()) {
            SurvOsClient.STATS.harvested();
            blockTarget = null;
            actionCooldown = 5;
        }
    }

    private void tickReplant(MinecraftClient c) {
        if (actionCooldown > 0) return;
        if (replantItem.isBlank() || !InventoryManager.selectItemByKeyword(c, replantItem, 5)) {
            replantPos = null;
            return;
        }
        BlockPos soil = replantPos.down();
        LocalNavigator.lookAt(c.player, Vec3d.ofCenter(soil).add(0, 0.5, 0));
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(soil).add(0, 0.5, 0), Direction.UP, soil, false);
        c.interactionManager.interactBlock(c.player, Hand.MAIN_HAND, hit);
        c.player.swingHand(Hand.MAIN_HAND);
        replantPos = null;
        replantItem = "";
        actionCooldown = 5;
    }

    private void tickFishing(MinecraftClient c) {
        state = State.INTERACTING;
        reason = fishingCast ? "Fishing — waiting for bite window" : "Casting fishing rod";
        stopMovement(c);

        if (!InventoryManager.selectFishingRod(c)) {
            state = State.BLOCKED;
            reason = "No fishing rod";
            return;
        }

        fishingTicks++;
        if (!fishingCast && actionCooldown <= 0) {
            c.interactionManager.interactItem(c.player, Hand.MAIN_HAND);
            c.player.swingHand(Hand.MAIN_HAND);
            fishingCast = true;
            fishingTicks = 0;
            actionCooldown = 10;
        } else if (fishingCast && fishingTicks >= 150 && actionCooldown <= 0) {
            c.interactionManager.interactItem(c.player, Hand.MAIN_HAND);
            c.player.swingHand(Hand.MAIN_HAND);
            fishingCast = false;
            fishingTicks = 0;
            actionCooldown = 12;
            SurvOsClient.STATS.fished();
        }
    }

    private void tickAnimals(MinecraftClient c, SurvConfig cfg) {
        if (!(entityTarget instanceof AnimalEntity animal) || !animal.isAlive()
                || animal.distanceTo(c.player) > cfg.automationRange + 4 || animal.isInLove()) {
            entityTarget = WorldScanner.animal(c, cfg.automationRange);
        }

        if (!(entityTarget instanceof AnimalEntity animal)) {
            state = State.SEARCHING;
            reason = "Scanning for breedable animals";
            stopMovement(c);
            return;
        }

        if (!InventoryManager.selectBreedingItem(c, animal)) {
            state = State.BLOCKED;
            reason = "No breeding food for " + Registries.ENTITY_TYPE.getId(animal.getType()).getPath();
            stopMovement(c);
            return;
        }

        double d = c.player.distanceTo(animal);
        reason = "Breeding " + Registries.ENTITY_TYPE.getId(animal.getType()).getPath();
        if (d > 2.7) {
            state = State.MOVING;
            navigator.moveNear(c, new BlockPos(animal.getBlockX(), animal.getBlockY(), animal.getBlockZ()), 2);
        } else {
            navigator.reset(c);
            stopMovement(c);
            state = State.INTERACTING;
            LocalNavigator.lookAt(c.player, animal.getBoundingBox().getCenter());
            if (actionCooldown <= 0) {
                c.interactionManager.interactEntity(c.player, animal, Hand.MAIN_HAND);
                c.player.swingHand(Hand.MAIN_HAND);
                actionCooldown = 12;
                entityTarget = null;
            }
        }
    }

    private void tickInventory(MinecraftClient c) {
        state = State.INTERACTING;
        reason = "Applying " + SurvOsClient.CONFIG.profile + " loadout";
        InventoryManager.applyLoadout(c, SurvOsClient.CONFIG.profile);
        complete(c, "Inventory loadout applied");
    }

    private void tickNavigate(MinecraftClient c) {
        if (waypointTarget == null) {
            complete(c, "Waypoint unavailable");
            return;
        }
        state = State.MOVING;
        reason = "Waypoint " + waypointName;
        LocalNavigator.Status s = navigator.moveNear(c, waypointTarget.pos(), 1);
        if (s == LocalNavigator.Status.ARRIVED) complete(c, "Arrived at " + waypointName);
        else if (s == LocalNavigator.Status.FAILED) {
            state = State.BLOCKED;
            reason = "Could not reach " + waypointName + " locally";
        }
    }

    private void tickRoute(MinecraftClient c) {
        var route = SurvOsClient.MEMORY.route(routeName);
        if (route.isEmpty() || routeIndex >= route.size()) {
            complete(c, "Route complete");
            return;
        }
        WorldMemory.Point p = route.get(routeIndex);
        if (!c.world.getRegistryKey().getValue().toString().equals(p.dimension)) {
            state = State.BLOCKED;
            reason = "Route crosses dimensions";
            return;
        }
        state = State.MOVING;
        reason = "Route " + routeName + " " + (routeIndex + 1) + "/" + route.size();
        LocalNavigator.Status s = navigator.moveNear(c, p.pos(), 1);
        if (s == LocalNavigator.Status.ARRIVED) {
            navigator.reset(c);
            routeIndex++;
        } else if (s == LocalNavigator.Status.FAILED) {
            state = State.BLOCKED;
            reason = "Route blocked at point " + (routeIndex + 1);
        }
    }

    private void breakBlock(MinecraftClient c, BlockPos pos) {
        if (actionCooldown > 0) return;
        Direction face = faceFromPlayer(c.player, pos);
        LocalNavigator.lookAt(c.player, Vec3d.ofCenter(pos));
        c.interactionManager.updateBlockBreakingProgress(pos, face);
        c.player.swingHand(Hand.MAIN_HAND);
    }

    private void maybeStartQueued(MinecraftClient c) {
        QueuedTask q = queue.poll();
        if (q == null) return;
        setGoal(q.target(), q.count());
        start(q.mode(), c);
    }

    private void complete(MinecraftClient c, String message) {
        stopMovement(c);
        navigator.reset(c);
        state = State.COMPLETE;
        reason = message;
        mode = Mode.IDLE;
        entityTarget = null;
        blockTarget = null;
        SurvOsClient.notice(message);
        if (SurvOsClient.TTS.enabled()) SurvOsClient.TTS.speak(message);
    }

    private void setBranchHeading(PlayerEntity p) {
        float yaw = p.getYaw();
        Direction d = Direction.fromHorizontalDegrees(yaw);
        branchDx = d.getOffsetX();
        branchDz = d.getOffsetZ();
        if (branchDx == 0 && branchDz == 0) branchDz = 1;
    }

    private static Direction faceFromPlayer(PlayerEntity p, BlockPos b) {
        double dx = p.getX() - (b.getX() + 0.5);
        double dy = p.getEyeY() - (b.getY() + 0.5);
        double dz = p.getZ() - (b.getZ() + 0.5);
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        if (ay > ax && ay > az) return dy > 0 ? Direction.UP : Direction.DOWN;
        if (ax > az) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static double distanceSq(PlayerEntity p, BlockPos b) {
        return p.getEntityPos().squaredDistanceTo(Vec3d.ofCenter(b));
    }

    private static String shortBlock(BlockState s) {
        return Registries.BLOCK.getId(s.getBlock()).getPath();
    }

    private static String cropSeed(String blockId) {
        return switch (blockId.toLowerCase(Locale.ROOT)) {
            case "wheat" -> "wheat_seeds";
            case "carrots" -> "carrot";
            case "potatoes" -> "potato";
            case "beetroots" -> "beetroot_seeds";
            default -> "";
        };
    }

    public static void stopMovement(MinecraftClient c) {
        LocalNavigator.clearKeys(c);
    }

    private static String pretty(Mode m) {
        return m.name().replace('_', ' ').toLowerCase(Locale.ROOT);
    }
}
