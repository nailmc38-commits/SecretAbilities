package com.survos;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Locale;

public final class PlayController {
    public enum Phase {
        IDLE, SURVIVE, DEFEND, DIRECTIVE,
        WOOD, COAL, IRON, DIAMOND, EXPLORE, RETURN_HOME
    }

    private boolean enabled;
    private Phase phase = Phase.IDLE;
    private String status = "OFF";

    private int ticks;
    private int blockedTicks;
    private int searchingTicks;
    private int calmTicks;
    private int exploreStep;

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
        status = "Taking over";
        blockedTicks = 0;
        searchingTicks = 0;
        calmTicks = 0;

        SurvOsClient.AUTOMATION.stop(c, "PLAY takeover");
        SurvOsClient.CONTROL.stop(c);

        SurvOsClient.notice("PLAY mode ON");
        SurvOsClient.TTS.speak("Play mode online. I'll keep working until you stop me.", "NORMAL");
    }

    public void stop(MinecraftClient c) {
        enabled = false;
        phase = Phase.IDLE;
        status = "OFF";
        blockedTicks = 0;
        searchingTicks = 0;
        calmTicks = 0;
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

        // Safety interrupt: preserve the current goal and resume it afterward.
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

        // Combat interrupt. Unlike before, this exits again once the area stays clear.
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

        // A directed task outranks the autonomous progression.
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

            // If a task cannot find anything locally, reposition and retry it later.
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
        return InventoryManager.count(c.player, directiveTarget) >= directiveCount;
    }

    private void chooseNext(MinecraftClient c) {
        int logs = InventoryManager.count(c.player, "_log")
                + InventoryManager.count(c.player, "_stem");
        int coal = InventoryManager.count(c.player, "coal");
        int iron = InventoryManager.count(c.player, "iron_ingot")
                + InventoryManager.count(c.player, "raw_iron");
        int diamonds = InventoryManager.count(c.player, "diamond");

        if (InventoryManager.freeSlots(c.player) <= 2) {
            WorldMemory.Point home = SurvOsClient.MEMORY.waypoint("home");
            if (home != null && SurvOsClient.AUTOMATION.goToWaypoint(c, "home")) {
                phase = Phase.RETURN_HOME;
                status = "Returning home";
                return;
            }

            // No home known: keep useful gear in the hotbar and continue rather than freezing.
            InventoryManager.applyLoadout(c, "MINING");
        }

        if (logs < 32) {
            phase = Phase.WOOD;
            status = "Getting wood";
            SurvOsClient.AUTOMATION.setGoal("log", 32);
            SurvOsClient.AUTOMATION.start(AutomationManager.Mode.TREE_FARM, c);
            return;
        }

        if (coal < 32) {
            phase = Phase.COAL;
            status = "Getting coal";
            SurvOsClient.AUTOMATION.setGoal("coal", 32);
            SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MINING, c);
            return;
        }

        if (iron < 48) {
            phase = Phase.IRON;
            status = "Getting iron";
            SurvOsClient.AUTOMATION.setGoal("iron", 48);
            SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MINING, c);
            return;
        }

        if (diamonds < 8) {
            phase = Phase.DIAMOND;
            status = "Looking for diamonds";
            SurvOsClient.AUTOMATION.setGoal("diamond", 8);
            SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MINING, c);
            return;
        }

        beginExploration(c, 32);
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
