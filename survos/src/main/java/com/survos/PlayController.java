package com.survos;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.HostileEntity;

public final class PlayController {
    public enum Phase { IDLE, SURVIVE, DEFEND, WOOD, COAL, IRON, DIAMOND, EXPLORE, RETURN_HOME }

    private boolean enabled;
    private Phase phase = Phase.IDLE;
    private String status = "OFF";
    private int ticks;
    private int blockedTicks;
    private int exploreStep;

    public boolean enabled() { return enabled; }
    public Phase phase() { return phase; }
    public String status() { return status; }

    public void start(MinecraftClient c) {
        enabled = true;
        phase = Phase.SURVIVE;
        status = "Taking over";
        blockedTicks = 0;
        SurvOsClient.AUTOMATION.stop(c, "PLAY takeover");
        SurvOsClient.CONTROL.stop(c);
        SurvOsClient.notice("PLAY mode ON");
        SurvOsClient.TTS.speak("Play mode online. I'll take it from here.", "NORMAL");
    }

    public void stop(MinecraftClient c) {
        enabled = false;
        phase = Phase.IDLE;
        status = "OFF";
        blockedTicks = 0;
        SurvOsClient.AUTOMATION.stop(c, "PLAY stopped");
        SurvOsClient.CONTROL.stop(c);
        SurvOsClient.notice("PLAY mode OFF");
        SurvOsClient.TTS.speak("Play mode stopped.", "MINIMAL");
    }

    public void tick(MinecraftClient c, SurvConfig cfg) {
        if (!enabled || c.player == null || c.world == null) return;
        if (++ticks % 10 != 0) return;

        // Immediate survival interrupts.
        if (c.player.getHealth() <= Math.max(8f, cfg.retreatHealth)
                || c.player.getHungerManager().getFoodLevel() <= 6) {
            phase = Phase.SURVIVE;
            status = "Recovering";
            SurvOsClient.AUTOMATION.stop(c, "PLAY recovery");
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
            phase = Phase.DEFEND;
            status = "Defending";
            if (SurvOsClient.AUTOMATION.mode() != AutomationManager.Mode.MOB_GRIND) {
                SurvOsClient.AUTOMATION.setGoal("", 0);
                SurvOsClient.AUTOMATION.start(AutomationManager.Mode.MOB_GRIND, c);
            }
            return;
        }

        if (SurvOsClient.AUTOMATION.active()) {
            status = SurvOsClient.AUTOMATION.reason();
            if (SurvOsClient.AUTOMATION.state() == AutomationManager.State.BLOCKED) {
                blockedTicks += 10;
                if (blockedTicks >= 80) {
                    SurvOsClient.AUTOMATION.stop(c, "PLAY retry");
                    blockedTicks = 0;
                    exploreStep++;
                }
            } else {
                blockedTicks = 0;
            }
            return;
        }

        chooseNext(c);
    }

    private void chooseNext(MinecraftClient c) {
        int logs = InventoryManager.count(c.player, "_log") + InventoryManager.count(c.player, "_stem");
        int coal = InventoryManager.count(c.player, "coal");
        int iron = InventoryManager.count(c.player, "iron_ingot") + InventoryManager.count(c.player, "raw_iron");
        int diamonds = InventoryManager.count(c.player, "diamond");

        if (InventoryManager.freeSlots(c.player) <= 2) {
            WorldMemory.Point home = SurvOsClient.MEMORY.waypoint("home");
            if (home != null && SurvOsClient.AUTOMATION.goToWaypoint(c, "home")) {
                phase = Phase.RETURN_HOME;
                status = "Returning home";
                return;
            }
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

        phase = Phase.EXPLORE;
        status = "Exploring";
        exploreStep = (exploreStep + 1) % 4;
        switch (exploreStep) {
            case 0 -> SurvOsClient.CONTROL.turn(c, 35f);
            case 1 -> SurvOsClient.CONTROL.turn(c, -55f);
            case 2 -> SurvOsClient.CONTROL.turn(c, 80f);
            default -> SurvOsClient.CONTROL.turn(c, -25f);
        }
        SurvOsClient.CONTROL.move("forward", 4.0);
    }
}
