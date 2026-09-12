package com.secret.minigames.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

public final class TextWastelandScreen extends Screen {
    private final Screen parent;
    private final Random random = new Random();
    private final Deque<String> log = new ArrayDeque<>();
    private int health;
    private int rads;
    private int caps;
    private int ammo;
    private int food;
    private int water;
    private int medkits;
    private int bunkerCodes;
    private int day;
    private int distance;
    private boolean won;
    private boolean ended;
    private ButtonWidget bunkerButton;

    public TextWastelandScreen(Screen parent) {
        super(Text.literal("Wasteland Terminal"));
        this.parent = parent;
        reset();
    }

    private void reset() {
        health = 100;
        rads = 0;
        caps = 14;
        ammo = 8;
        food = 3;
        water = 4;
        medkits = 1;
        bunkerCodes = 0;
        day = 1;
        distance = 0;
        won = false;
        ended = false;
        log.clear();
        add("BOOT: Shelter terminal online.");
        add("GOAL: Find 3 bunker code fragments and reach Shelter 09.");
        add("The highway outside is quiet. Your canteen is half full.");
    }

    @Override
    protected void init() {
        int w = Math.min(180, Math.max(120, (this.width - 70) / 5));
        int gap = 6;
        int total = w * 5 + gap * 4;
        int left = Math.max(10, (this.width - total) / 2);
        int y = this.height - 36;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("[1] EXPLORE"), b -> explore())
                .dimensions(left, y, w, 24).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("[2] SCAVENGE"), b -> scavenge())
                .dimensions(left + (w + gap), y, w, 24).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("[3] CAMP"), b -> camp())
                .dimensions(left + (w + gap) * 2, y, w, 24).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("[4] MEDKIT"), b -> medkit())
                .dimensions(left + (w + gap) * 3, y, w, 24).build());
        bunkerButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("[5] SHELTER 09"), b -> bunker())
                .dimensions(left + (w + gap) * 4, y, w, 24).build());
        refresh();
    }

    private void refresh() {
        if (bunkerButton != null) bunkerButton.active = !ended && bunkerCodes >= 3;
    }

    private void explore() {
        if (!canAct()) return;
        day++;
        distance += 2 + random.nextInt(5);
        consume(1, 1);
        add("DAY " + day + ": You push farther along the cracked highway.");
        int roll = random.nextInt(100);
        if (roll < 18) {
            rads = Math.min(100, rads + 7 + random.nextInt(8));
            add("A dust storm rolls in. Radiation climbs to " + rads + ".");
        } else if (roll < 38) {
            findCache();
        } else if (roll < 56) {
            encounter();
        } else if (roll < 72) {
            caps += 6 + random.nextInt(15);
            add("You find an old roadside register. +caps.");
        } else if (roll < 84 && bunkerCodes < 3) {
            bunkerCodes++;
            add("SIGNAL FOUND: bunker code fragment " + bunkerCodes + "/3.");
        } else {
            add("Nothing but wind, distant towers, and a long road.");
        }
        environmentalCheck();
        refresh();
    }

    private void scavenge() {
        if (!canAct()) return;
        consume(0, 1);
        add("You search the nearest ruin room by room.");
        int roll = random.nextInt(100);
        if (roll < 25) {
            food += 1 + random.nextInt(2);
            water += 1 + random.nextInt(2);
            add("SUPPLIES: food and sealed water recovered.");
        } else if (roll < 45) {
            ammo += 3 + random.nextInt(6);
            caps += 4 + random.nextInt(9);
            add("CACHE: ammunition and old currency recovered.");
        } else if (roll < 58) {
            medkits++;
            add("MEDICAL: one usable medkit recovered.");
        } else if (roll < 70 && bunkerCodes < 3) {
            bunkerCodes++;
            add("TERMINAL NOTE: code fragment " + bunkerCodes + "/3.");
        } else if (roll < 85) {
            encounter();
        } else {
            rads = Math.min(100, rads + 4);
            add("The building was picked clean. The basement was mildly irradiated.");
        }
        environmentalCheck();
        refresh();
    }

    private void camp() {
        if (!canAct()) return;
        day++;
        if (food > 0 && water > 0) {
            food--;
            water--;
            int healed = 10 + random.nextInt(9);
            health = Math.min(100, health + healed);
            rads = Math.max(0, rads - 3);
            add("CAMP: you eat, drink, repair gear, and recover " + healed + " HP.");
        } else {
            health = Math.max(1, health - 7);
            add("CAMP: without enough supplies, the rest barely helps.");
        }
        refresh();
    }

    private void medkit() {
        if (!canAct()) return;
        if (medkits <= 0) {
            add("No medkits left.");
            return;
        }
        medkits--;
        health = Math.min(100, health + 35);
        add("MEDKIT: health restored to " + health + ".");
    }

    private void bunker() {
        if (!canAct() || bunkerCodes < 3) return;
        add("You enter the three recovered code fragments into Shelter 09.");
        if (health > 0) {
            won = true;
            ended = true;
            add("ACCESS GRANTED. The shelter door opens. RUN COMPLETE.");
            add("Press R to start a new run, or F9/ESC to return.");
        }
        refresh();
    }

    private void findCache() {
        int type = random.nextInt(4);
        if (type == 0) {
            food += 2;
            add("CACHE: two preserved meals.");
        } else if (type == 1) {
            water += 2;
            add("CACHE: two bottles of clean water.");
        } else if (type == 2) {
            ammo += 5;
            add("CACHE: five rounds in a sealed box.");
        } else {
            caps += 12;
            add("CACHE: a pouch of old trade tokens.");
        }
    }

    private void encounter() {
        int threat = 8 + random.nextInt(18);
        add("ENCOUNTER: a hostile wasteland machine blocks the route.");
        if (ammo >= 2) {
            ammo -= 2;
            caps += 3 + random.nextInt(7);
            add("You drive it off using 2 ammo and salvage useful parts.");
        } else {
            health = Math.max(0, health - threat);
            add("With no spare ammo, you retreat. -" + threat + " HP.");
        }
    }

    private void consume(int foodCost, int waterCost) {
        if (foodCost > 0) {
            if (food >= foodCost) food -= foodCost;
            else health = Math.max(0, health - 7);
        }
        if (water >= waterCost) water -= waterCost;
        else health = Math.max(0, health - 10);
    }

    private void environmentalCheck() {
        if (rads >= 80) health = Math.max(0, health - 8);
        else if (rads >= 55) health = Math.max(0, health - 3);
        if (health <= 0) {
            ended = true;
            add("RUN ENDED. Your expedition can no longer continue.");
            add("Press R to restart.");
        }
    }

    private boolean canAct() {
        return !ended && health > 0;
    }

    private void add(String line) {
        log.addLast(line);
        while (log.size() > 12) log.removeFirst();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF080B08);
        context.fill(0, 0, this.width, 5, 0xFF6DFF70);
        context.fill(14, 14, this.width - 14, this.height - 49, 0xEE101610);
        context.fill(20, 52, this.width - 20, 53, 0xFF29562A);

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("WASTELAND TERMINAL // TEXT RPG").formatted(Formatting.GREEN, Formatting.BOLD),
                24, 24, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Original wasteland survival story • choices + resource management").formatted(Formatting.DARK_GREEN),
                24, 38, 0xFFFFFF);

        String status = "HP " + health + "  RAD " + rads + "  CAPS " + caps + "  AMMO " + ammo
                + "  FOOD " + food + "  WATER " + water + "  MED " + medkits
                + "  CODES " + bunkerCodes + "/3  DAY " + day + "  DIST " + distance + "km";
        context.drawTextWithShadow(this.textRenderer, Text.literal(status).formatted(Formatting.GREEN), 24, 61, 0xFFFFFF);

        int y = 84;
        for (String line : log) {
            int color = line.startsWith("GOAL") || line.startsWith("SIGNAL") || line.startsWith("ACCESS")
                    ? 0xFFFFD75A : 0xFFB8E6B9;
            context.drawTextWithShadow(this.textRenderer, Text.literal("> " + line), 28, y, color);
            y += 14;
        }

        if (won) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("SHELTER 09 REACHED — YOU WIN").formatted(Formatting.GOLD, Formatting.BOLD),
                    this.width / 2, this.height - 62, 0xFFFFFF);
        } else if (!ended) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("1 Explore • 2 Scavenge • 3 Camp • 4 Medkit • 5 Shelter when unlocked • R restart").formatted(Formatting.GRAY),
                    this.width / 2, this.height - 62, 0xFFFFFF);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_F9) {
            if (this.client != null) this.client.setScreen(parent);
            return true;
        }
        if (k == GLFW.GLFW_KEY_1) { explore(); return true; }
        if (k == GLFW.GLFW_KEY_2) { scavenge(); return true; }
        if (k == GLFW.GLFW_KEY_3) { camp(); return true; }
        if (k == GLFW.GLFW_KEY_4) { medkit(); return true; }
        if (k == GLFW.GLFW_KEY_5) { bunker(); return true; }
        if (k == GLFW.GLFW_KEY_R) {
            reset();
            refresh();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
