package com.survos;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.Direction;

public final class PlayController {
    public enum Phase {
        IDLE, SURVIVE, DEFEND, DIRECTIVE,
        WOOD, BASICS, STONE, COAL, IRON, FOOD, DIAMOND,
        NETHER_PREP, BLAZE, PEARLS, END_PREP,
        EXPLORE, RETURN_HOME
    }

    private boolean enabled;
    private Phase phase = Phase.IDLE;
    private String status = "OFF";

    private int ticks;
    private int blockedTicks;
    private int searchingTicks;
    private int calmTicks;
    private int exploreStep;
    private int craftCooldown;

    private AutomationManager.Mode directiveMode;
    private String directiveTarget = "";
    private int directiveCount;
    private boolean directiveActive;

    public boolean enabled() { return enabled; }
    public Phase phase() { return phase; }
    public String status() { return status; }

    public String directiveStatus() {
        if (!directiveActive || directiveMode == null) return "none";
        return directiveMode + (directiveTarget.isBlank() ? "" : " " + directiveTarget)
                + (directiveCount > 0 ? " x" + directiveCount : "");
    }

    public void start(MinecraftClient c) {
        enabled = true;
        phase = Phase.SURVIVE;
        status = "Reading inventory and choosing progression";
        blockedTicks = 0;
        searchingTicks = 0;
        calmTicks = 0;
        craftCooldown = 0;

        SurvOsClient.AUTOMATION.stop(c, "PLAY takeover");
        SurvOsClient.CONTROL.stop(c);

        SurvOsClient.notice("PLAY mode ON");
        SurvOsClient.TTS.speak(
                "Play mode online. I'll use what you already have and keep progressing.",
                "NORMAL");
    }

    public void stop(MinecraftClient c) {
        enabled = false;
        phase = Phase.IDLE;
        status = "OFF";
        blockedTicks = 0;
        searchingTicks = 0;
        calmTicks = 0;
        craftCooldown = 0;
        directiveActive = false;
        directiveMode = null;
        directiveTarget = "";
        directiveCount = 0;

        SurvOsClient.AUTOMATION.stop(c, "PLAY stopped");
        SurvOsClient.CONTROL.stop(c);

        SurvOsClient.notice("PLAY mode OFF");
        SurvOsClient.TTS.speak("Play mode stopped.", "MINIMAL");
    }

    public void setDirective(
            MinecraftClient c,
            AutomationManager.Mode mode,
            String target,
            int count
    ) {
        enabled = true;
        directiveActive = true;
        directiveMode = mode;
        directiveTarget = target == null ? "" : WorldScanner.normalize(target);
        directiveCount = Math.max(0, count);

        phase = Phase.DIRECTIVE;
        status = "Goal: " + directiveStatus();
        blockedTicks = 0;
        searchingTicks = 0;
        calmTicks = 0;

        startDirective(c);
        SurvOsClient.notice("PLAY goal: " + directiveStatus());
    }

    public void clearDirective() {
        directiveActive = false;
        directiveMode = null;
        directiveTarget = "";
        directiveCount = 0;
    }

    public void tick(MinecraftClient c, SurvConfig cfg) {
        if (!enabled || c.player == null || c.world == null) return;
        if (++ticks % 10 != 0) return;
        if (craftCooldown > 0) craftCooldown -= 10;

        if (cfg.playSafety
                && (c.player.getHealth() <= Math.max(8f, cfg.retreatHealth)
                || c.player.getHungerManager().getFoodLevel() <= 6)) {
            phase = Phase.SURVIVE;
            status = "Recovering";

            if (SurvOsClient.AUTOMATION.active()) {
                SurvOsClient.AUTOMATION.stop(c, "PLAY recovery");
            }

            if (InventoryManager.selectFood(c, true)) {
                c.options.useKey.setPressed(true);
            }
            return;
        } else {
            c.options.useKey.setPressed(false);
        }

        int hostiles = c.world.getEntitiesByClass(
                HostileEntity.class,
                c.player.getBoundingBox().expand(9),
                e -> e.isAlive()).size();

        if (hostiles > 0 && c.player.getHealth() > 12f) {
            calmTicks = 0;
            phase = Phase.DEFEND;
            status = "Defending";
            InventoryManager.applyLoadout(c, "COMBAT");

            if (SurvOsClient.AUTOMATION.mode() != AutomationManager.Mode.MOB_GRIND) {
                SurvOsClient.AUTOMATION.setGoal("", 0);
                SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MOB_GRIND, c);
            }
            return;
        }

        if (phase == Phase.DEFEND) {
            calmTicks += 10;
            if (calmTicks < 50) return;

            calmTicks = 0;
            SurvOsClient.AUTOMATION.stop(c, "Area clear");
            phase = directiveActive ? Phase.DIRECTIVE : Phase.SURVIVE;
            status = "Area clear — resuming";
        }

        if (directiveActive && directiveSatisfied(c)) {
            String finished = directiveStatus();
            clearDirective();
            SurvOsClient.AUTOMATION.stop(c, "PLAY goal complete");
            SurvOsClient.notice("PLAY goal complete: " + finished);
            SurvOsClient.TTS.speak("Goal complete.", "NORMAL");
        }

        if (SurvOsClient.AUTOMATION.active()) {
            status = SurvOsClient.AUTOMATION.reason();

            if (SurvOsClient.AUTOMATION.state() == AutomationManager.State.BLOCKED) {
                blockedTicks += 10;
            } else {
                blockedTicks = 0;
            }

            if (SurvOsClient.AUTOMATION.state() == AutomationManager.State.SEARCHING) {
                searchingTicks += 10;
            } else {
                searchingTicks = 0;
            }

            if (blockedTicks >= 70 || searchingTicks >= 140) {
                SurvOsClient.AUTOMATION.stop(
                        c,
                        blockedTicks >= 70 ? "PLAY replanning" : "PLAY exploring for target");
                blockedTicks = 0;
                searchingTicks = 0;
                beginExploration(c, directiveActive ? 34 : 28);
            }
            return;
        }

        blockedTicks = 0;
        searchingTicks = 0;

        if (directiveActive) {
            startDirective(c);
            return;
        }

        chooseNext(c);
    }

    private void startDirective(MinecraftClient c) {
        if (!directiveActive || directiveMode == null) return;

        phase = Phase.DIRECTIVE;
        status = "Working: " + directiveStatus();

        SurvOsClient.AUTOMATION.setGoal(directiveTarget, directiveCount);
        SurvOsClient.AUTOMATION.start(directiveMode, c);
    }

    private boolean directiveSatisfied(MinecraftClient c) {
        if (!directiveActive || c.player == null) return true;
        if (directiveCount <= 0 || directiveTarget.isBlank()) return false;
        return progressCount(c, directiveTarget) >= directiveCount;
    }

    private int progressCount(MinecraftClient c, String target) {
        if (c.player == null || target == null) return 0;
        String q = WorldScanner.normalize(target);

        if (q.contains("blaze")) return InventoryManager.count(c.player, "blaze_rod");
        if (q.contains("enderman")) return InventoryManager.count(c.player, "ender_pearl");
        if (q.equals("iron")) {
            return InventoryManager.count(c.player, "iron_ingot")
                    + InventoryManager.count(c.player, "raw_iron");
        }
        if (q.equals("stone")) {
            return InventoryManager.countAny(c.player, "cobblestone", "cobbled_deepslate");
        }
        if (q.equals("log") || q.equals("wood")) {
            return InventoryManager.countAny(c.player, "_log", "_stem", "_hyphae");
        }

        return InventoryManager.count(c.player, q);
    }

    private void chooseNext(MinecraftClient c) {
        String dim = c.world.getRegistryKey().getValue().toString();

        if (InventoryManager.freeSlots(c.player) <= 2) {
            WorldMemory.Point home = SurvOsClient.MEMORY.waypoint("home");
            if (home != null && SurvOsClient.AUTOMATION.goToWaypoint(c, "home")) {
                phase = Phase.RETURN_HOME;
                status = "Inventory full — returning home";
                return;
            }
        }

        if (dim.contains("the_nether")) {
            chooseNether(c);
            return;
        }

        if (dim.contains("the_end")) {
            phase = Phase.END_PREP;
            status = "End progression — keeping combat loadout ready";
            InventoryManager.applyLoadout(c, "COMBAT");
            SurvOsClient.AUTOMATION.setGoal("", 0);
            SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MOB_GRIND, c);
            return;
        }

        chooseOverworld(c);
    }

    private void chooseOverworld(MinecraftClient c) {
        var p = c.player;

        int logs = progressCount(c, "log");
        int planks = InventoryManager.count(p, "_planks");
        int sticks = InventoryManager.count(p, "stick");
        int stone = progressCount(c, "stone");
        int coal = InventoryManager.countAny(p, "coal", "charcoal");
        int iron = progressCount(c, "iron");
        int food = InventoryManager.foodCount(p);
        int diamonds = InventoryManager.count(p, "diamond");
        int obsidian = InventoryManager.count(p, "obsidian");

        if (logs < 24) {
            gather(c, Phase.WOOD, AutomationManager.Mode.TREE_FARM, "log", 24, "Gathering wood");
            return;
        }

        if (planks < 16) {
            String log = InventoryManager.firstId(p, "_log");
            if (log.isBlank()) log = InventoryManager.firstId(p, "_stem");
            String planksId = plankFromLog(log);

            if (!planksId.isBlank() && tryCraft(c, planksId, 4)) {
                phase = Phase.BASICS;
                status = "Crafting planks";
                return;
            }
        }

        if (sticks < 8 && tryCraft(c, "stick", 8)) {
            phase = Phase.BASICS;
            status = "Crafting sticks";
            return;
        }

        if (!InventoryManager.has(p, "crafting_table")
                && tryCraft(c, "crafting_table", 1)) {
            phase = Phase.BASICS;
            status = "Crafting a table";
            return;
        }

        if (!InventoryManager.hasAny(p, "wooden_pickaxe", "stone_pickaxe", "iron_pickaxe", "diamond_pickaxe")) {
            if (tryCraft(c, "wooden_pickaxe", 1)) {
                phase = Phase.BASICS;
                status = "Crafting first pickaxe";
                return;
            }
        }

        if (stone < 32) {
            gather(c, Phase.STONE, AutomationManager.Mode.MINING, "stone", 32, "Gathering stone");
            return;
        }

        if (!InventoryManager.hasAny(p, "stone_pickaxe", "iron_pickaxe", "diamond_pickaxe")
                && tryCraft(c, "stone_pickaxe", 1)) {
            phase = Phase.STONE;
            status = "Crafting stone pickaxe";
            return;
        }

        if (!InventoryManager.has(p, "furnace")
                && tryCraft(c, "furnace", 1)) {
            phase = Phase.STONE;
            status = "Crafting furnace";
            return;
        }

        if (coal < 24) {
            gather(c, Phase.COAL, AutomationManager.Mode.MINING, "coal", 24, "Gathering fuel");
            return;
        }

        if (iron < 32) {
            gather(c, Phase.IRON, AutomationManager.Mode.MINING, "iron", 32, "Gathering iron");
            return;
        }

        int ironIngots = InventoryManager.count(p, "iron_ingot");

        if (ironIngots >= 3
                && !InventoryManager.hasAny(p, "iron_pickaxe", "diamond_pickaxe")
                && tryCraft(c, "iron_pickaxe", 1)) {
            phase = Phase.IRON;
            status = "Upgrading pickaxe";
            return;
        }

        if (ironIngots >= 1
                && !InventoryManager.has(p, "shield")
                && tryCraft(c, "shield", 1)) {
            phase = Phase.IRON;
            status = "Crafting shield";
            return;
        }

        if (ironIngots >= 3
                && !InventoryManager.hasAny(p, "iron_sword", "diamond_sword")
                && tryCraft(c, "iron_sword", 1)) {
            phase = Phase.IRON;
            status = "Crafting sword";
            return;
        }

        if (food < 12) {
            if (InventoryManager.has(p, "fishing_rod")) {
                phase = Phase.FOOD;
                status = "Fishing for food";
                SurvOsClient.AUTOMATION.setGoal("", 0);
                SurvOsClient.AUTOMATION.start(AutomationManager.Mode.FISHING, c);
                return;
            }

            phase = Phase.FOOD;
            status = "Food low — exploring for supplies";
            beginExploration(c, 28);
            return;
        }

        if (diamonds < 5
                && InventoryManager.hasAny(p, "iron_pickaxe", "diamond_pickaxe")) {
            gather(c, Phase.DIAMOND, AutomationManager.Mode.MINING, "diamond", 5, "Mining diamonds");
            return;
        }

        if (diamonds >= 3
                && !InventoryManager.has(p, "diamond_pickaxe")
                && tryCraft(c, "diamond_pickaxe", 1)) {
            phase = Phase.DIAMOND;
            status = "Crafting diamond pickaxe";
            return;
        }

        if (InventoryManager.has(p, "diamond_pickaxe") && obsidian < 10) {
            gather(c, Phase.NETHER_PREP, AutomationManager.Mode.MINING, "obsidian", 10, "Gathering obsidian");
            return;
        }

        if (!InventoryManager.has(p, "flint_and_steel")
                && InventoryManager.has(p, "flint")
                && ironIngots >= 1
                && tryCraft(c, "flint_and_steel", 1)) {
            phase = Phase.NETHER_PREP;
            status = "Crafting flint and steel";
            return;
        }

        int rods = InventoryManager.count(p, "blaze_rod");
        int pearls = InventoryManager.count(p, "ender_pearl");

        if (rods >= 6 && pearls >= 12) {
            int eyes = InventoryManager.count(p, "ender_eye");
            if (eyes < 12 && tryCraft(c, "ender_eye", Math.max(1, 12 - eyes))) {
                phase = Phase.END_PREP;
                status = "Crafting eyes of ender";
                return;
            }

            phase = Phase.END_PREP;
            status = "End-ready supplies detected — preserving combat gear";
            InventoryManager.applyLoadout(c, "COMBAT");
            return;
        }

        phase = Phase.NETHER_PREP;
        status = "Prepared for Nether progression — looking for known portal";
        if (SurvOsClient.MEMORY.waypoint("nether_portal") != null
                && SurvOsClient.AUTOMATION.goToWaypoint(c, "nether_portal")) {
            return;
        }

        beginExploration(c, 36);
    }

    private void chooseNether(MinecraftClient c) {
        var p = c.player;
        int rods = InventoryManager.count(p, "blaze_rod");
        int pearls = InventoryManager.count(p, "ender_pearl");

        if (rods < 8) {
            gather(c, Phase.BLAZE, AutomationManager.Mode.MOB_GRIND, "blaze", 8, "Hunting blazes");
            return;
        }

        if (pearls < 12) {
            gather(c, Phase.PEARLS, AutomationManager.Mode.MOB_GRIND, "enderman", 12, "Hunting endermen");
            return;
        }

        phase = Phase.NETHER_PREP;
        status = "Nether supplies complete — returning to known portal";
        if (SurvOsClient.MEMORY.waypoint("nether_portal") != null
                && SurvOsClient.AUTOMATION.goToWaypoint(c, "nether_portal")) {
            return;
        }

        beginExploration(c, 32);
    }

    private void gather(
            MinecraftClient c,
            Phase nextPhase,
            AutomationManager.Mode mode,
            String target,
            int count,
            String message
    ) {
        phase = nextPhase;
        status = message;
        SurvOsClient.AUTOMATION.setGoal(target, count);
        SurvOsClient.AUTOMATION.start(mode, c);
    }

    private boolean tryCraft(MinecraftClient c, String item, int count) {
        if (craftCooldown > 0) return false;
        if (!SurvOsClient.LEGACY.canCraftNow(c, item)) return false;

        boolean ok = SurvOsClient.LEGACY.queueCraftDirect(c, item, count, false);
        if (ok) craftCooldown = 80;
        return ok;
    }

    private String plankFromLog(String id) {
        if (id == null || id.isBlank()) return "";
        if (id.endsWith("_log")) return id.substring(0, id.length() - 4) + "_planks";
        if (id.endsWith("_stem")) return id.substring(0, id.length() - 5) + "_planks";
        if (id.endsWith("_hyphae")) return id.substring(0, id.length() - 7) + "_planks";
        return "";
    }

    private void beginExploration(MinecraftClient c, int distance) {
        if (c.player == null) return;

        phase = Phase.EXPLORE;
        exploreStep = (exploreStep + 1) & 3;

        Direction facing = Direction.fromHorizontalDegrees(c.player.getYaw());
        Direction direction = switch (exploreStep) {
            case 1 -> facing.rotateYClockwise();
            case 2 -> facing.getOpposite();
            case 3 -> facing.rotateYCounterclockwise();
            default -> facing;
        };

        int x = c.player.getBlockX() + direction.getOffsetX() * distance;
        int z = c.player.getBlockZ() + direction.getOffsetZ() * distance;
        int y = c.player.getBlockY();

        status = "Exploring toward " + x + ", " + z;
        SurvOsClient.AUTOMATION.navigateTo(c, x, y, z);
    }
}
