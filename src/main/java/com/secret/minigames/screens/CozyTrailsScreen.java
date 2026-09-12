package com.secret.minigames.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public final class CozyTrailsScreen extends Screen {
    private static final int WORLD = 128;
    private static final int TILE = 16;

    private enum Biome {
        MEADOW("Sunmeadow"), PINE("Whispering Pines"), BIRCH("Silver Birch Grove"),
        LAVENDER("Lavender Fields"), AUTUMN("Amberwood"), MARSH("Misty Marsh"),
        BEACH("Golden Shore"), COAST("Bluewater Coast"), HIGHLANDS("Cloud Highlands"),
        SNOW("Frostpeak"), ORCHARD("Apple Orchard"), WATER("Lake / Sea");
        final String label;
        Biome(String label) { this.label = label; }
    }

    private enum Season { SPRING, SUMMER, AUTUMN, WINTER }
    private enum Weather { CLEAR, RAIN, SNOW, MIST }

    private static final class Place {
        final String name;
        final String description;
        final double x, z;
        final int color;
        boolean discovered;

        Place(String name, String description, double x, double z, int color) {
            this.name = name;
            this.description = description;
            this.x = x;
            this.z = z;
            this.color = color;
        }
    }

    private final Screen parent;
    private final Random random = new Random(9123817L);
    private final Place[] places = {
            new Place("Willow Cabin", "A tiny cabin with tea, books and a warm porch.", 64, 64, 0xFFB77848),
            new Place("Moon Lake Dock", "A quiet dock where the lake reflects the sky.", 47, 45, 0xFF7FB6D8),
            new Place("Pinewatch Tower", "An old lookout above the whispering pines.", 18, 42, 0xFF7D5B3D),
            new Place("Sunset Lighthouse", "A lighthouse watching the far blue coast.", 111, 63, 0xFFE7E4D9),
            new Place("Lavender Hill", "Purple flowers cover the hill in spring and summer.", 35, 88, 0xFFB49BE6),
            new Place("Cloud Hot Spring", "Warm water tucked between highland stones.", 91, 23, 0xFF8AD9E8),
            new Place("Amber Bridge", "A wooden bridge beneath orange autumn trees.", 92, 94, 0xFFC78947),
            new Place("Frostpeak Lodge", "A snowy lodge with a glowing window.", 66, 10, 0xFFD6E1EA),
            new Place("Marsh Boardwalk", "Lanterns guide a path through reeds and fog.", 31, 111, 0xFF9E8857),
            new Place("Orchard Picnic", "Apple trees, a blanket, and a perfect lazy afternoon.", 84, 62, 0xFFDF6B5F),
            new Place("Birch Grove Picnic", "White-barked trees surround a hidden picnic table.", 33, 28, 0xFFE9E2CF),
            new Place("Clifftop Flag", "A tiny flag marks one of the best views in the valley.", 87, 14, 0xFFE85B65)
    };

    private double playerX = 64.0, playerZ = 67.0;
    private double cameraX = playerX, cameraZ = playerZ;
    private double dayTime = 0.24;
    private int day = 1;
    private Season season = Season.SPRING;
    private Weather weather = Weather.CLEAR;
    private double seasonClock;
    private double weatherClock = 34.0;
    private long lastNanos;
    private double elapsed;
    private double forageCooldown;
    private double fishCooldown;
    private boolean mapOpen;
    private boolean journalOpen;
    private boolean photoMode;
    private boolean lantern;
    private boolean campPlaced;
    private double campX, campZ;
    private String message = "No quests. No rush. Just wander.";
    private double messageTime = 5.0;

    private int flowers, shells, berries, pinecones, crystals, fish, tea;

    public CozyTrailsScreen(Screen parent) {
        super(Text.literal("Cozy Trails"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        lastNanos = System.nanoTime();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        double dt = Math.min(0.05, Math.max(0.001, (now - lastNanos) / 1_000_000_000.0));
        lastNanos = now;
        elapsed += dt;
        messageTime = Math.max(0, messageTime - dt);
        forageCooldown = Math.max(0, forageCooldown - dt);
        fishCooldown = Math.max(0, fishCooldown - dt);

        updateTime(dt);
        if (!mapOpen && !journalOpen) updateMovement(dt);
        cameraX += (playerX - cameraX) * Math.min(1.0, dt * 5.0);
        cameraZ += (playerZ - cameraZ) * Math.min(1.0, dt * 5.0);

        drawWorld(ctx);
        drawWeather(ctx);
        if (!photoMode) drawHud(ctx);
        if (mapOpen) drawMap(ctx);
        if (journalOpen) drawJournal(ctx);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void updateTime(double dt) {
        dayTime += dt / 260.0;
        if (dayTime >= 1.0) {
            dayTime -= 1.0;
            day++;
        }

        seasonClock += dt;
        if (seasonClock >= 120.0) {
            seasonClock -= 120.0;
            season = Season.values()[(season.ordinal() + 1) % Season.values().length];
            say("The season changed to " + pretty(season) + ".");
            if (season == Season.WINTER && weather == Weather.RAIN) weather = Weather.SNOW;
        }

        weatherClock -= dt;
        if (weatherClock <= 0) {
            changeWeather();
            weatherClock = 28.0 + random.nextDouble() * 42.0;
        }
    }

    private void changeWeather() {
        int roll = random.nextInt(100);
        if (season == Season.WINTER) {
            weather = roll < 45 ? Weather.SNOW : (roll < 65 ? Weather.MIST : Weather.CLEAR);
        } else if (season == Season.SPRING) {
            weather = roll < 40 ? Weather.RAIN : (roll < 52 ? Weather.MIST : Weather.CLEAR);
        } else {
            weather = roll < 24 ? Weather.RAIN : (roll < 34 ? Weather.MIST : Weather.CLEAR);
        }
        say("Weather: " + pretty(weather) + ".");
    }

    private void updateMovement(double dt) {
        if (this.client == null) return;
        long win = this.client.getWindow().getHandle();
        double dx = 0, dz = 0;
        if (down(win, GLFW.GLFW_KEY_W) || down(win, GLFW.GLFW_KEY_UP)) dz -= 1;
        if (down(win, GLFW.GLFW_KEY_S) || down(win, GLFW.GLFW_KEY_DOWN)) dz += 1;
        if (down(win, GLFW.GLFW_KEY_A) || down(win, GLFW.GLFW_KEY_LEFT)) dx -= 1;
        if (down(win, GLFW.GLFW_KEY_D) || down(win, GLFW.GLFW_KEY_RIGHT)) dx += 1;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len <= 0) return;
        dx /= len;
        dz /= len;
        double speed = down(win, GLFW.GLFW_KEY_LEFT_SHIFT) ? 6.0 : 4.1;
        if (weather == Weather.SNOW) speed *= 0.90;
        Biome current = biomeAt((int) playerX, (int) playerZ);
        if (current == Biome.MARSH) speed *= 0.78;

        double nx = clamp(playerX + dx * speed * dt, 1.2, WORLD - 1.2);
        double nz = clamp(playerZ + dz * speed * dt, 1.2, WORLD - 1.2);
        if (!isWater((int) nx, (int) playerZ)) playerX = nx;
        if (!isWater((int) playerX, (int) nz)) playerZ = nz;

        for (Place place : places) {
            if (!place.discovered && distance(playerX, playerZ, place.x, place.z) < 4.0) {
                place.discovered = true;
                say("Discovered: " + place.name + " — " + place.description);
            }
        }
    }

    private void drawWorld(DrawContext ctx) {
        double daylight = daylight();
        int sky = mix(0xFF07101C, 0xFF93C7E8, daylight);
        ctx.fill(0, 0, width, height, sky);

        int tilesX = width / TILE + 4;
        int tilesZ = height / TILE + 5;
        int startX = (int) Math.floor(cameraX - tilesX / 2.0);
        int startZ = (int) Math.floor(cameraZ - tilesZ / 2.0);
        int baseX = width / 2 - (int) ((cameraX - Math.floor(cameraX)) * TILE) - (tilesX / 2) * TILE;
        int baseZ = height / 2 - (int) ((cameraZ - Math.floor(cameraZ)) * TILE) - (tilesZ / 2) * TILE;

        for (int iz = 0; iz < tilesZ; iz++) {
            int wz = startZ + iz;
            int sy = baseZ + iz * TILE;
            for (int ix = 0; ix < tilesX; ix++) {
                int wx = startX + ix;
                int sx = baseX + ix * TILE;
                if (wx < 0 || wz < 0 || wx >= WORLD || wz >= WORLD) {
                    ctx.fill(sx, sy, sx + TILE + 1, sy + TILE + 1, 0xFF285F83);
                    continue;
                }
                Biome biome = biomeAt(wx, wz);
                int color = seasonColor(biomeColor(biome), biome);
                color = shade(color, 0.55 + daylight * 0.45);
                if (weather == Weather.MIST) color = mix(color, 0xFFB8C1C3, 0.16);
                ctx.fill(sx, sy, sx + TILE + 1, sy + TILE + 1, color);
                drawTileDetails(ctx, wx, wz, sx, sy, biome, daylight);
            }
        }

        for (Place place : places) drawPlace(ctx, place);
        if (campPlaced) drawCamp(ctx);
        drawAmbientLife(ctx, daylight);
        drawPlayer(ctx);

        if (daylight < 0.48) {
            int alpha = (int) ((0.48 - daylight) / 0.48 * 78);
            ctx.fill(0, 0, width, height, (alpha << 24) | 0x00102030);
            if (lantern) {
                int px = width / 2, py = height / 2;
                ctx.fill(px - 52, py - 52, px + 52, py + 52, 0x18FFE8A3);
                ctx.fill(px - 28, py - 28, px + 28, py + 28, 0x20FFF1B5);
            }
        }
    }

    private void drawTileDetails(DrawContext ctx, int wx, int wz, int sx, int sy, Biome biome, double daylight) {
        int h = hash(wx, wz, 77);
        if (biome == Biome.WATER || biome == Biome.COAST) {
            int wave = Math.floorMod((int) (h + elapsed * 6), 11);
            if (wave < 2) ctx.fill(sx + 2, sy + 5 + wave, sx + TILE - 2, sy + 6 + wave, 0x55D7F3FF);
            return;
        }

        if ((biome == Biome.PINE || biome == Biome.BIRCH || biome == Biome.AUTUMN || biome == Biome.ORCHARD)
                && Math.floorMod(h, 7) < 2) {
            int trunk = biome == Biome.BIRCH ? 0xFFE7E2D5 : 0xFF68492D;
            int leaf = switch (biome) {
                case AUTUMN -> season == Season.WINTER ? 0xFFDDE4E6 : 0xFFD37B39;
                case ORCHARD -> season == Season.AUTUMN ? 0xFFC7653B : 0xFF5A9B49;
                case PINE -> season == Season.WINTER ? 0xFFB9D1C6 : 0xFF2F6C4B;
                default -> season == Season.WINTER ? 0xFFDCE4E5 : 0xFF73A85C;
            };
            ctx.fill(sx + 7, sy + 7, sx + 9, sy + 15, shade(trunk, daylight));
            ctx.fill(sx + 3, sy + 2, sx + 13, sy + 10, shade(leaf, daylight));
            if (biome == Biome.ORCHARD && season != Season.WINTER && (h & 3) == 0) {
                ctx.fill(sx + 5, sy + 4, sx + 7, sy + 6, 0xFFE4574F);
            }
        } else if ((biome == Biome.MEADOW || biome == Biome.LAVENDER) && Math.floorMod(h, 9) < 3) {
            int flower = biome == Biome.LAVENDER ? 0xFFB69AE8 : ((h & 1) == 0 ? 0xFFFFD866 : 0xFFF28FB3);
            if (season == Season.WINTER) flower = 0xFFE8EEF0;
            ctx.fill(sx + 4 + Math.floorMod(h, 7), sy + 5 + Math.floorMod(h / 7, 7),
                    sx + 6 + Math.floorMod(h, 7), sy + 7 + Math.floorMod(h / 7, 7), flower);
        } else if (biome == Biome.MARSH && Math.floorMod(h, 5) == 0) {
            ctx.fill(sx + 4, sy + 5, sx + 5, sy + 14, 0xFF6A7A3E);
            ctx.fill(sx + 10, sy + 7, sx + 11, sy + 15, 0xFF6A7A3E);
        } else if ((biome == Biome.HIGHLANDS || biome == Biome.SNOW) && Math.floorMod(h, 11) < 2) {
            ctx.fill(sx + 4, sy + 7, sx + 12, sy + 13, biome == Biome.SNOW ? 0xFFD7E1E5 : 0xFF7F8588);
        } else if (biome == Biome.BEACH && Math.floorMod(h, 13) == 0) {
            ctx.fill(sx + 7, sy + 7, sx + 10, sy + 10, 0xFFF1E2C0);
        }

        if (season == Season.WINTER && biome != Biome.BEACH && biome != Biome.MARSH && Math.floorMod(h, 5) == 0) {
            ctx.fill(sx + 1, sy + 1, sx + 5, sy + 3, 0x55FFFFFF);
        }
    }

    private void drawPlace(DrawContext ctx, Place place) {
        int sx = worldToScreenX(place.x);
        int sy = worldToScreenY(place.z);
        if (sx < -30 || sy < -30 || sx > width + 30 || sy > height + 30) return;

        String name = place.name;
        if (name.contains("Cabin") || name.contains("Lodge")) {
            ctx.fill(sx - 11, sy - 8, sx + 11, sy + 9, place.color);
            ctx.fill(sx - 14, sy - 13, sx + 14, sy - 7, 0xFF674229);
            ctx.fill(sx - 3, sy - 1, sx + 3, sy + 9, 0xFF4E3526);
            ctx.fill(sx + 5, sy - 3, sx + 9, sy + 1, 0xFFFFD66D);
        } else if (name.contains("Lighthouse")) {
            ctx.fill(sx - 5, sy - 15, sx + 6, sy + 12, 0xFFE8E5DF);
            ctx.fill(sx - 8, sy - 18, sx + 9, sy - 13, 0xFFCC5353);
            ctx.fill(sx - 7, sy - 11, sx + 8, sy - 7, 0xFFCC5353);
            ctx.fill(sx - 3, sy - 22, sx + 4, sy - 18, 0xFFFFE98A);
        } else if (name.contains("Tower")) {
            ctx.fill(sx - 5, sy - 14, sx + 6, sy + 12, 0xFF725438);
            ctx.fill(sx - 10, sy - 17, sx + 11, sy - 12, 0xFF54412F);
        } else if (name.contains("Dock") || name.contains("Bridge") || name.contains("Boardwalk")) {
            ctx.fill(sx - 16, sy - 4, sx + 17, sy + 5, 0xFF8B6846);
            for (int x = sx - 14; x <= sx + 14; x += 7) ctx.fill(x, sy - 4, x + 2, sy + 5, 0xFF5C432F);
        } else if (name.contains("Hot Spring")) {
            ctx.fill(sx - 12, sy - 9, sx + 13, sy + 10, 0xFF83CFE3);
            ctx.fill(sx - 8, sy - 5, sx + 9, sy + 6, 0xFFB9EAF1);
        } else if (name.contains("Picnic")) {
            ctx.fill(sx - 9, sy - 7, sx + 10, sy + 8, 0xFFE26060);
            ctx.fill(sx - 8, sy - 6, sx + 9, sy + 7, 0xFFF3E9D0);
        } else if (name.contains("Flag")) {
            ctx.fill(sx, sy - 16, sx + 2, sy + 10, 0xFF5E4633);
            ctx.fill(sx + 2, sy - 15, sx + 12, sy - 9, 0xFFE85B65);
        } else {
            ctx.fill(sx - 7, sy - 7, sx + 8, sy + 8, place.color);
        }

        if (place.discovered || distance(playerX, playerZ, place.x, place.z) < 5.0) {
            int labelW = textRenderer.getWidth(place.name) + 8;
            ctx.fill(sx - labelW / 2, sy - 29, sx + labelW / 2, sy - 17, 0x99000000);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(place.name), sx, sy - 27, 0xFFFFFF);
        }
    }

    private void drawCamp(DrawContext ctx) {
        int sx = worldToScreenX(campX), sy = worldToScreenY(campZ);
        if (sx < -30 || sy < -30 || sx > width + 30 || sy > height + 30) return;
        ctx.fill(sx - 7, sy + 2, sx + 8, sy + 5, 0xFF5D3B2A);
        ctx.fill(sx - 3, sy - 5, sx + 4, sy + 3, 0xFFFFA33A);
        ctx.fill(sx - 1, sy - 9, sx + 2, sy - 2, 0xFFFFE06A);
        if (daylight() < 0.55) ctx.fill(sx - 17, sy - 17, sx + 18, sy + 18, 0x18FFC76A);
    }

    private void drawAmbientLife(DrawContext ctx, double daylight) {
        if (season != Season.WINTER && daylight > 0.45) {
            for (int i = 0; i < 10; i++) {
                int x = Math.floorMod((int) (i * 83 + elapsed * (12 + i)), Math.max(1, width));
                int y = 55 + Math.floorMod((int) (i * 47 + Math.sin(elapsed + i) * 20), Math.max(1, height - 90));
                int c = (i & 1) == 0 ? 0xFFFFD75C : 0xFFF19CC8;
                ctx.fill(x, y, x + 2, y + 2, c);
            }
        }
        if (daylight < 0.42 && weather != Weather.RAIN) {
            for (int i = 0; i < 18; i++) {
                int x = Math.floorMod((int) (hash(i, 13, 5) + elapsed * (2 + i % 4)), Math.max(1, width));
                int y = 45 + Math.floorMod(hash(i, 9, 71), Math.max(1, height - 80));
                ctx.fill(x, y, x + 2, y + 2, 0xAAFFF29A);
            }
        }
        for (int i = 0; i < 4; i++) {
            int x = Math.floorMod((int) (i * 211 + elapsed * 9), Math.max(1, width));
            int y = 65 + i * 24;
            ctx.fill(x, y, x + 4, y + 1, 0xAA1B2530);
            ctx.fill(x + 6, y, x + 10, y + 1, 0xAA1B2530);
        }
    }

    private void drawPlayer(DrawContext ctx) {
        int x = width / 2, y = height / 2;
        ctx.fill(x - 4, y - 6, x + 5, y + 6, 0xFF5FC9E8);
        ctx.fill(x - 3, y - 10, x + 4, y - 5, 0xFFF0C4A0);
        ctx.fill(x - 4, y + 6, x - 1, y + 11, 0xFF394D66);
        ctx.fill(x + 2, y + 6, x + 5, y + 11, 0xFF394D66);
        if (lantern) ctx.fill(x + 6, y - 2, x + 9, y + 3, 0xFFFFD86A);
    }

    private void drawWeather(DrawContext ctx) {
        if (weather == Weather.MIST) {
            ctx.fill(0, 0, width, height, 0x38D4DBDD);
        } else if (weather == Weather.RAIN) {
            for (int i = 0; i < 95; i++) {
                int x = Math.floorMod((int) (hash(i, 11, 3) + elapsed * 135), Math.max(1, width + 20)) - 10;
                int y = Math.floorMod((int) (hash(i, 71, 9) + elapsed * 240), Math.max(1, height + 25)) - 20;
                ctx.fill(x, y, x + 1, y + 8, 0x8898C8EA);
            }
            ctx.fill(0, 0, width, height, 0x180D2940);
        } else if (weather == Weather.SNOW) {
            for (int i = 0; i < 110; i++) {
                int x = Math.floorMod((int) (hash(i, 41, 13) + elapsed * (12 + i % 8)), Math.max(1, width));
                int y = Math.floorMod((int) (hash(i, 19, 7) + elapsed * (28 + i % 12)), Math.max(1, height));
                int s = i % 4 == 0 ? 2 : 1;
                ctx.fill(x, y, x + s, y + s, 0xCCFFFFFF);
            }
        }
    }

    private void drawHud(DrawContext ctx) {
        Biome biome = biomeAt((int) playerX, (int) playerZ);
        ctx.fill(6, 6, 181, 47, 0x99000000);
        ctx.drawTextWithShadow(textRenderer, Text.literal("COZY TRAILS").formatted(Formatting.AQUA, Formatting.BOLD), 11, 10, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal(biome.label), 11, 22, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(pretty(season) + " • " + pretty(weather) + " • Day " + day).formatted(Formatting.GRAY),
                11, 34, 0xFFFFFF);

        ctx.fill(width - 190, 6, width - 6, 47, 0x99000000);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Places " + discoveries() + "/" + places.length + " • Fish " + fish + " • Tea " + tea),
                width - 185, 10, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Flowers " + flowers + " • Shells " + shells + " • Berries " + berries),
                width - 185, 22, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("M map • J journal • E interact • F fish").formatted(Formatting.GRAY),
                width - 185, 34, 0xFFFFFF);

        String nearby = nearbyPrompt();
        if (!nearby.isBlank()) {
            int w = textRenderer.getWidth(nearby) + 12;
            ctx.fill(width / 2 - w / 2, height - 43, width / 2 + w / 2, height - 27, 0xA0000000);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(nearby), width / 2, height - 39, 0xFFFFFF);
        }
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("WASD/Arrows wander • Shift jog • C campfire • L lantern • H photo mode • F9/ESC hub").formatted(Formatting.GRAY),
                width / 2, height - 14, 0xFFFFFF);

        if (messageTime > 0) {
            int w = Math.min(width - 24, textRenderer.getWidth(message) + 18);
            ctx.fill(width / 2 - w / 2, 53, width / 2 + w / 2, 72, 0xB0000000);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(message), width / 2, 58, 0xFFFFFF);
        }
    }

    private void drawMap(DrawContext ctx) {
        int size = Math.min(230, Math.min(width - 36, height - 70));
        int left = width / 2 - size / 2;
        int top = height / 2 - size / 2;
        ctx.fill(left - 8, top - 22, left + size + 8, top + size + 8, 0xEE0C1118);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("VALLEY MAP").formatted(Formatting.AQUA, Formatting.BOLD), width / 2, top - 16, 0xFFFFFF);
        int cell = Math.max(2, size / 32);
        int mapSize = cell * 32;
        left = width / 2 - mapSize / 2;
        for (int z = 0; z < 32; z++) {
            for (int x = 0; x < 32; x++) {
                Biome b = biomeAt(x * 4 + 2, z * 4 + 2);
                ctx.fill(left + x * cell, top + z * cell, left + (x + 1) * cell, top + (z + 1) * cell, seasonColor(biomeColor(b), b));
            }
        }
        for (Place place : places) {
            if (!place.discovered) continue;
            int px = left + (int) (place.x / WORLD * mapSize);
            int pz = top + (int) (place.z / WORLD * mapSize);
            ctx.fill(px - 2, pz - 2, px + 3, pz + 3, place.color);
        }
        int px = left + (int) (playerX / WORLD * mapSize);
        int pz = top + (int) (playerZ / WORLD * mapSize);
        ctx.fill(px - 3, pz - 3, px + 4, pz + 4, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("M closes map"), width / 2, top + mapSize + 2, 0xFFFFFF);
    }

    private void drawJournal(DrawContext ctx) {
        int w = Math.min(360, width - 30);
        int left = width / 2 - w / 2;
        int top = 48;
        int bottom = Math.min(height - 28, top + 250);
        ctx.fill(left, top, left + w, bottom, 0xEE16130E);
        ctx.fill(left, top, left + w, top + 3, 0xFFD7B46A);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("TRAVEL JOURNAL").formatted(Formatting.GOLD, Formatting.BOLD), width / 2, top + 10, 0xFFFFFF);
        int y = top + 29;
        for (Place place : places) {
            if (y > bottom - 34) break;
            if (place.discovered) {
                ctx.drawTextWithShadow(textRenderer, Text.literal("✓ " + place.name).formatted(Formatting.GREEN), left + 13, y, 0xFFFFFF);
                y += 11;
            }
        }
        if (discoveries() == 0) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("Explore to fill your journal.").formatted(Formatting.GRAY), left + 13, y, 0xFFFFFF);
            y += 14;
        }
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Keepsakes: " + flowers + " flowers • " + shells + " shells • " + berries + " berries • " + pinecones + " pinecones • " + crystals + " frost crystals"),
                left + 13, bottom - 27, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("J closes journal").formatted(Formatting.GRAY), left + 13, bottom - 14, 0xFFFFFF);
    }

    private void interact() {
        Place nearest = nearestPlace(2.5);
        if (nearest != null) {
            if (!nearest.discovered) nearest.discovered = true;
            if (nearest.name.contains("Cabin") || nearest.name.contains("Lodge") || nearest.name.contains("Hot Spring")) {
                tea++;
                dayTime = 0.28;
                weather = Weather.CLEAR;
                say("You slow down at " + nearest.name + ", make tea, and watch the morning settle in.");
            } else if (nearest.name.contains("Picnic")) {
                berries++;
                say("You relax at " + nearest.name + " and pack a few berries for later.");
            } else {
                say(nearest.name + " — " + nearest.description);
            }
            return;
        }

        if (forageCooldown > 0) {
            say("Give the area a moment before foraging again.");
            return;
        }
        forageCooldown = 2.2;
        Biome b = biomeAt((int) playerX, (int) playerZ);
        switch (b) {
            case MEADOW, LAVENDER, BIRCH -> { flowers++; say("You picked a small wildflower for your journal."); }
            case BEACH, COAST -> { shells++; say("You found a smooth seashell."); }
            case ORCHARD, AUTUMN -> { berries++; say("You found a handful of berries."); }
            case PINE -> { pinecones++; say("You collected a pinecone."); }
            case SNOW, HIGHLANDS -> { crystals++; say("You found a tiny frost crystal among the stones."); }
            case MARSH -> { flowers++; say("You found a pale marsh flower."); }
            default -> say("Nothing to collect here right now.");
        }
    }

    private void fish() {
        if (!nearWater()) {
            say("Find a lake, dock, or shoreline to fish.");
            return;
        }
        if (fishCooldown > 0) {
            say("The water needs a moment to settle.");
            return;
        }
        fishCooldown = 3.0;
        if (random.nextInt(100) < 72) {
            fish++;
            say("You caught a little silver fish, then sit listening to the water.");
        } else {
            say("No bite this time. The ripples are still nice to watch.");
        }
    }

    private String nearbyPrompt() {
        Place p = nearestPlace(2.8);
        if (p != null) return "E • " + p.name;
        if (nearWater()) return "F • Fish   E • Forage nearby";
        return "E • Forage this biome";
    }

    private Place nearestPlace(double radius) {
        Place best = null;
        double bestD = radius;
        for (Place place : places) {
            double d = distance(playerX, playerZ, place.x, place.z);
            if (d < bestD) { bestD = d; best = place; }
        }
        return best;
    }

    private boolean nearWater() {
        int x = (int) playerX, z = (int) playerZ;
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) if (isWater(x + dx, z + dz)) return true;
        return false;
    }

    private Biome biomeAt(int x, int z) {
        if (isWater(x, z)) return Biome.WATER;
        if (x > 107) return x > 115 ? Biome.COAST : Biome.BEACH;
        if (z < 18 && x > 50 && x < 79) return Biome.SNOW;
        if (z < 32 && x > 72) return Biome.HIGHLANDS;
        if (x < 26 && z < 72) return Biome.PINE;
        if (x < 48 && z < 43) return Biome.BIRCH;
        if (z > 101 && x < 63) return Biome.MARSH;
        if (x < 58 && z > 73) return Biome.LAVENDER;
        if (x > 78 && z > 82) return Biome.AUTUMN;
        if (x > 72 && x < 101 && z > 45 && z < 80) return Biome.ORCHARD;
        return Biome.MEADOW;
    }

    private boolean isWater(int x, int z) {
        if (x < 0 || z < 0 || x >= WORLD || z >= WORLD) return true;
        if (x > 116) return true;
        double lake1 = distance(x, z, 47, 45);
        double lake2 = distance(x, z, 76, 77);
        if (lake1 < 9.0 || lake2 < 6.5) return true;
        if (z > 115 && x > 74) return true;
        return false;
    }

    private int biomeColor(Biome b) {
        return switch (b) {
            case MEADOW -> 0xFF74A85D;
            case PINE -> 0xFF416D4A;
            case BIRCH -> 0xFF79A76A;
            case LAVENDER -> 0xFF8FA66A;
            case AUTUMN -> 0xFF9C784A;
            case MARSH -> 0xFF64735A;
            case BEACH -> 0xFFD6BE79;
            case COAST, WATER -> 0xFF397EA4;
            case HIGHLANDS -> 0xFF788477;
            case SNOW -> 0xFFDDE6E8;
            case ORCHARD -> 0xFF6D9A55;
        };
    }

    private int seasonColor(int color, Biome biome) {
        if (biome == Biome.WATER || biome == Biome.COAST || biome == Biome.BEACH) {
            if (season == Season.WINTER && biome != Biome.BEACH) return mix(color, 0xFFB9D7E3, 0.25);
            return color;
        }
        return switch (season) {
            case SPRING -> mix(color, 0xFF87C36A, 0.12);
            case SUMMER -> shade(color, 1.06);
            case AUTUMN -> mix(color, 0xFFC77A3D, 0.28);
            case WINTER -> mix(color, 0xFFE4EAEB, biome == Biome.SNOW ? 0.75 : 0.48);
        };
    }

    private double daylight() {
        double sun = Math.sin(dayTime * Math.PI * 2.0 - Math.PI / 2.0) * 0.5 + 0.5;
        return 0.24 + sun * 0.76;
    }

    private int worldToScreenX(double x) {
        return width / 2 + (int) ((x - cameraX) * TILE);
    }

    private int worldToScreenY(double z) {
        return height / 2 + (int) ((z - cameraZ) * TILE);
    }

    private int discoveries() {
        int n = 0;
        for (Place p : places) if (p.discovered) n++;
        return n;
    }

    private void say(String text) {
        message = text;
        messageTime = 4.5;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_F9) {
            if (this.client != null) this.client.setScreen(parent);
            return true;
        }
        if (k == GLFW.GLFW_KEY_M) { mapOpen = !mapOpen; journalOpen = false; return true; }
        if (k == GLFW.GLFW_KEY_J) { journalOpen = !journalOpen; mapOpen = false; return true; }
        if (k == GLFW.GLFW_KEY_H) { photoMode = !photoMode; mapOpen = false; journalOpen = false; return true; }
        if (k == GLFW.GLFW_KEY_L) { lantern = !lantern; say(lantern ? "Lantern on." : "Lantern off."); return true; }
        if (k == GLFW.GLFW_KEY_C) {
            campPlaced = true;
            campX = playerX + 0.8;
            campZ = playerZ + 0.4;
            say("Campfire placed. Stay awhile if you want.");
            return true;
        }
        if (k == GLFW.GLFW_KEY_E) { interact(); return true; }
        if (k == GLFW.GLFW_KEY_F) { fish(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() { return false; }

    private static boolean down(long win, int key) { return GLFW.glfwGetKey(win, key) == GLFW.GLFW_PRESS; }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static double distance(double ax, double az, double bx, double bz) { double dx = ax - bx, dz = az - bz; return Math.sqrt(dx * dx + dz * dz); }

    private static int shade(int c, double f) {
        int r = (int) (((c >> 16) & 255) * f), g = (int) (((c >> 8) & 255) * f), b = (int) ((c & 255) * f);
        return 0xFF000000 | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    private static int mix(int a, int b, double t) {
        t = clamp(t, 0, 1);
        int ar=(a>>16)&255, ag=(a>>8)&255, ab=a&255, br=(b>>16)&255, bg=(b>>8)&255, bb=b&255;
        return 0xFF000000 | ((int)(ar+(br-ar)*t)<<16) | ((int)(ag+(bg-ag)*t)<<8) | (int)(ab+(bb-ab)*t);
    }

    private static int hash(int a, int b, int c) {
        int h = a * 73428767 ^ b * 912931 ^ c * 19349663;
        h ^= h >>> 13;
        h *= 1274126177;
        return h ^ (h >>> 16);
    }

    private static String pretty(Enum<?> value) {
        String s = value.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
