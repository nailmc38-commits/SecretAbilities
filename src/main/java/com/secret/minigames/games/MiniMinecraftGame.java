package com.secret.minigames.games;

import java.util.Random;

public final class MiniMinecraftGame {
    public static final int WORLD_W = 48;
    public static final int WORLD_H = 24;
    public static final int WORLD_D = 48;

    public static final byte AIR = 0;
    public static final byte GRASS = 1;
    public static final byte DIRT = 2;
    public static final byte STONE = 3;
    public static final byte WOOD = 4;
    public static final byte LEAVES = 5;
    public static final byte COBBLE = 6;

    public static final byte[] HOTBAR = {DIRT, COBBLE, WOOD, LEAVES, GRASS};
    public static final String[] BLOCK_NAMES = {
            "Air", "Grass", "Dirt", "Stone", "Wood", "Leaves", "Cobblestone"
    };

    private final byte[][][] blocks = new byte[WORLD_W][WORLD_H][WORLD_D];
    private final int[] inventory = new int[7];

    public int selectedHotbar;
    public double playerX;
    public double playerY;
    public double playerZ;
    public double velocityY;
    public double yaw;
    public double pitch;

    public MiniMinecraftGame() {
        newWorld();
    }

    public void newWorld() {
        generate(System.nanoTime());
        for (int i = 0; i < inventory.length; i++) inventory[i] = 0;
        inventory[DIRT] = 24;
        inventory[COBBLE] = 16;
        inventory[WOOD] = 8;
        inventory[LEAVES] = 16;
        inventory[GRASS] = 8;
        selectedHotbar = 0;
        yaw = 0.55;
        pitch = -0.08;
        velocityY = 0.0;
        respawn();
    }

    public void respawn() {
        int sx = WORLD_W / 2;
        int sz = WORLD_D / 2;
        playerX = sx + 0.5;
        playerY = highestSolidY(sx, sz) + 1.01;
        playerZ = sz + 0.5;
        velocityY = 0.0;
    }

    private void generate(long seed) {
        Random random = new Random(seed);
        for (int x = 0; x < WORLD_W; x++) {
            for (int z = 0; z < WORLD_D; z++) {
                double hills = Math.sin(x * 0.31) * 1.25 + Math.cos(z * 0.27) * 1.15;
                double detail = Math.sin((x + z) * 0.19) * 0.55;
                int surface = clamp(7 + (int) Math.round(hills + detail), 5, 11);
                for (int y = 0; y <= surface; y++) {
                    blocks[x][y][z] = y == surface ? GRASS : (y >= surface - 2 ? DIRT : STONE);
                }
            }
        }

        for (int cave = 0; cave < 10; cave++) {
            int cx = 5 + random.nextInt(WORLD_W - 10);
            int cy = 3 + random.nextInt(5);
            int cz = 5 + random.nextInt(WORLD_D - 10);
            int radius = 2 + random.nextInt(3);
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int y = cy - radius; y <= cy + radius; y++) {
                    for (int z = cz - radius; z <= cz + radius; z++) {
                        if (!inside(x, y, z) || y <= 0) continue;
                        double dx = x - cx, dy = y - cy, dz = z - cz;
                        if (dx * dx + dy * dy + dz * dz <= radius * radius) blocks[x][y][z] = AIR;
                    }
                }
            }
        }

        for (int tree = 0; tree < 24; tree++) {
            int x = 3 + random.nextInt(WORLD_W - 6);
            int z = 3 + random.nextInt(WORLD_D - 6);
            if (Math.abs(x - WORLD_W / 2) < 4 && Math.abs(z - WORLD_D / 2) < 4) continue;
            int ground = highestSolidY(x, z);
            if (block(x, ground, z) != GRASS || ground + 7 >= WORLD_H) continue;
            int trunk = 4 + random.nextInt(2);
            for (int y = 1; y <= trunk; y++) setBlock(x, ground + y, z, WOOD);
            int leafBase = ground + trunk - 1;
            for (int ox = -2; ox <= 2; ox++) {
                for (int oy = 0; oy <= 3; oy++) {
                    for (int oz = -2; oz <= 2; oz++) {
                        if (Math.abs(ox) + Math.abs(oz) + (oy == 3 ? 1 : 0) > 3) continue;
                        int bx = x + ox, by = leafBase + oy, bz = z + oz;
                        if (inside(bx, by, bz) && block(bx, by, bz) == AIR) setBlock(bx, by, bz, LEAVES);
                    }
                }
            }
        }
    }

    public byte block(int x, int y, int z) {
        return inside(x, y, z) ? blocks[x][y][z] : AIR;
    }

    public void setBlock(int x, int y, int z, byte value) {
        if (inside(x, y, z)) blocks[x][y][z] = value;
    }

    public boolean inside(int x, int y, int z) {
        return x >= 0 && x < WORLD_W && y >= 0 && y < WORLD_H && z >= 0 && z < WORLD_D;
    }

    public boolean collisionSolid(int x, int y, int z) {
        if (y < 0) return true;
        if (y >= WORLD_H) return false;
        if (x < 0 || x >= WORLD_W || z < 0 || z >= WORLD_D) return true;
        return block(x, y, z) != AIR;
    }

    public int highestSolidY(int x, int z) {
        for (int y = WORLD_H - 1; y >= 0; y--) {
            byte b = block(x, y, z);
            if (b != AIR && b != LEAVES && b != WOOD) return y;
        }
        return 0;
    }

    public byte selectedBlock() {
        return HOTBAR[selectedHotbar];
    }

    public int inventory(byte block) {
        return block >= 0 && block < inventory.length ? inventory[block] : 0;
    }

    public void addDrop(byte broken) {
        byte drop = broken == STONE ? COBBLE : broken;
        if (drop > AIR && drop < inventory.length) inventory[drop] = Math.min(999, inventory[drop] + 1);
    }

    public boolean consumeSelected() {
        byte block = selectedBlock();
        if (inventory[block] <= 0) return false;
        inventory[block]--;
        return true;
    }

    public String selectedName() {
        return BLOCK_NAMES[selectedBlock()];
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
