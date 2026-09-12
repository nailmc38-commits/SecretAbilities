package com.secret.minigames.games;

import java.util.Arrays;
import java.util.Random;

public final class MiniMinecraftGame {
    public static final int WORLD_W = 64;
    public static final int WORLD_H = 28;
    public static final int WORLD_D = 64;

    public static final byte AIR = 0;
    public static final byte GRASS = 1;
    public static final byte DIRT = 2;
    public static final byte STONE = 3;
    public static final byte WOOD = 4;
    public static final byte LEAVES = 5;
    public static final byte COBBLE = 6;
    public static final byte SAND = 7;
    public static final byte PLANKS = 8;
    public static final byte TORCH = 9;
    public static final byte WATER = 10;
    public static final byte COAL_ORE = 11;
    public static final byte IRON_ORE = 12;
    public static final byte CRAFTING_TABLE = 13;
    public static final byte FURNACE = 14;

    public static final byte[] HOTBAR = {
            DIRT, COBBLE, PLANKS, WOOD, TORCH, SAND, GRASS, CRAFTING_TABLE, FURNACE
    };

    public static final String[] BLOCK_NAMES = {
            "Air", "Grass", "Dirt", "Stone", "Wood", "Leaves", "Cobblestone", "Sand",
            "Planks", "Torch", "Water", "Coal Ore", "Iron Ore", "Crafting Table", "Furnace"
    };

    public static final String[] ANIMAL_NAMES = {"Pig", "Cow", "Chicken", "Sheep"};
    public static final int MAX_ANIMALS = 28;

    private final byte[][][] blocks = new byte[WORLD_W][WORLD_H][WORLD_D];
    private final int[] inventory = new int[15];
    private final Random lootRandom = new Random();
    private double hungerClock;
    private double regenClock;

    public final double[] animalX = new double[MAX_ANIMALS];
    public final double[] animalY = new double[MAX_ANIMALS];
    public final double[] animalZ = new double[MAX_ANIMALS];
    public final double[] animalDir = new double[MAX_ANIMALS];
    public final double[] animalWander = new double[MAX_ANIMALS];
    public final int[] animalType = new int[MAX_ANIMALS];
    public final int[] animalHp = new int[MAX_ANIMALS];
    public final boolean[] animalAlive = new boolean[MAX_ANIMALS];

    public int selectedHotbar;
    public double playerX;
    public double playerY;
    public double playerZ;
    public double velocityY;
    public double yaw;
    public double pitch;
    public double dayTime;
    public int health;
    public int hunger;
    public int apples;
    public int daysSurvived;

    public int sticks;
    public int coal;
    public int ironOre;
    public int ironIngots;
    public int rawMeat;
    public int cookedMeat;
    public int pickaxeTier;
    public int swordTier;
    public int axeTier;

    public MiniMinecraftGame() {
        newWorld();
    }

    public void newWorld() {
        long seed = System.nanoTime();
        generate(seed);
        Arrays.fill(inventory, 0);
        selectedHotbar = 0;
        yaw = 0.55;
        pitch = -0.08;
        velocityY = 0.0;
        health = 20;
        hunger = 20;
        apples = 1;
        dayTime = 0.20;
        daysSurvived = 0;
        sticks = 0;
        coal = 0;
        ironOre = 0;
        ironIngots = 0;
        rawMeat = 0;
        cookedMeat = 0;
        pickaxeTier = 0;
        swordTier = 0;
        axeTier = 0;
        hungerClock = 0;
        regenClock = 0;
        spawnAnimals(new Random(seed ^ 0x5EEDBEEFL));
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

    public void survivalTick(double dt, boolean moving, boolean sprinting, boolean swimming) {
        dayTime += dt / 210.0;
        if (dayTime >= 1.0) {
            dayTime -= 1.0;
            daysSurvived++;
        }

        double drain = 1.0 + (moving ? 0.45 : 0.0) + (sprinting ? 1.1 : 0.0) + (swimming ? 0.35 : 0.0);
        hungerClock += dt * drain;
        if (hungerClock >= 18.0) {
            hungerClock -= 18.0;
            if (hunger > 0) hunger--;
        }

        if (hunger <= 0) {
            regenClock += dt;
            if (regenClock >= 4.0) {
                regenClock = 0;
                damage(1);
            }
        } else if (hunger >= 18 && health < 20) {
            regenClock += dt;
            if (regenClock >= 5.0) {
                regenClock = 0;
                health++;
            }
        } else {
            regenClock = 0;
        }

        tickAnimals(dt);
    }

    public double daylight() {
        double sun = Math.sin(dayTime * Math.PI * 2.0 - Math.PI / 2.0) * 0.5 + 0.5;
        return 0.28 + sun * 0.72;
    }

    public boolean isNight() {
        return daylight() < 0.48;
    }

    public void damage(int amount) {
        health = Math.max(0, health - Math.max(0, amount));
        if (health <= 0) {
            for (int i = 1; i < inventory.length; i++) inventory[i] /= 2;
            apples /= 2;
            rawMeat /= 2;
            cookedMeat /= 2;
            coal /= 2;
            ironOre /= 2;
            ironIngots /= 2;
            health = 20;
            hunger = 14;
            respawn();
        }
    }

    public String eatBestFood() {
        if (hunger >= 20 && health >= 20) return "You are already full.";
        if (cookedMeat > 0) {
            cookedMeat--;
            hunger = Math.min(20, hunger + 8);
            health = Math.min(20, health + 3);
            return "Ate cooked meat.";
        }
        if (apples > 0) {
            apples--;
            hunger = Math.min(20, hunger + 5);
            health = Math.min(20, health + 1);
            return "Ate an apple.";
        }
        if (rawMeat > 0) {
            rawMeat--;
            hunger = Math.min(20, hunger + 3);
            return "Ate raw meat.";
        }
        return "No food in your inventory.";
    }

    public boolean craftPlanks() {
        if (inventory[WOOD] < 1) return false;
        inventory[WOOD]--;
        inventory[PLANKS] = Math.min(999, inventory[PLANKS] + 4);
        return true;
    }

    public boolean craftSticks() {
        if (inventory[PLANKS] < 2) return false;
        inventory[PLANKS] -= 2;
        sticks += 4;
        return true;
    }

    public boolean craftTable() {
        if (inventory[PLANKS] < 4) return false;
        inventory[PLANKS] -= 4;
        inventory[CRAFTING_TABLE]++;
        return true;
    }

    public boolean craftFurnace() {
        if (inventory[COBBLE] < 8) return false;
        inventory[COBBLE] -= 8;
        inventory[FURNACE]++;
        return true;
    }

    public boolean craftTorches() {
        if (sticks < 1 || coal < 1) return false;
        sticks--;
        coal--;
        inventory[TORCH] = Math.min(999, inventory[TORCH] + 4);
        return true;
    }

    public boolean craftStonePickaxe() {
        if (inventory[COBBLE] < 3 || sticks < 2) return false;
        inventory[COBBLE] -= 3;
        sticks -= 2;
        pickaxeTier = Math.max(pickaxeTier, 1);
        return true;
    }

    public boolean craftIronPickaxe() {
        if (ironIngots < 3 || sticks < 2) return false;
        ironIngots -= 3;
        sticks -= 2;
        pickaxeTier = Math.max(pickaxeTier, 2);
        return true;
    }

    public boolean craftStoneSword() {
        if (inventory[COBBLE] < 2 || sticks < 1) return false;
        inventory[COBBLE] -= 2;
        sticks--;
        swordTier = Math.max(swordTier, 1);
        return true;
    }

    public boolean craftIronSword() {
        if (ironIngots < 2 || sticks < 1) return false;
        ironIngots -= 2;
        sticks--;
        swordTier = Math.max(swordTier, 2);
        return true;
    }

    public boolean craftStoneAxe() {
        if (inventory[COBBLE] < 3 || sticks < 2) return false;
        inventory[COBBLE] -= 3;
        sticks -= 2;
        axeTier = Math.max(axeTier, 1);
        return true;
    }

    public boolean smeltIron() {
        if (inventory[FURNACE] < 1 || ironOre < 1 || coal < 1) return false;
        ironOre--;
        coal--;
        ironIngots++;
        return true;
    }

    public boolean cookMeat() {
        if (inventory[FURNACE] < 1 || rawMeat < 1 || coal < 1) return false;
        rawMeat--;
        coal--;
        cookedMeat++;
        return true;
    }

    private void generate(long seed) {
        Random random = new Random(seed);
        for (int x = 0; x < WORLD_W; x++) {
            for (int y = 0; y < WORLD_H; y++) Arrays.fill(blocks[x][y], AIR);
        }

        final int sea = 6;
        for (int x = 0; x < WORLD_W; x++) {
            for (int z = 0; z < WORLD_D; z++) {
                double hills = Math.sin(x * 0.22) * 1.65 + Math.cos(z * 0.19) * 1.5;
                double detail = Math.sin((x + z) * 0.12) * 0.8 + Math.cos((x - z) * 0.08) * 0.5;
                int surface = clamp(8 + (int) Math.round(hills + detail), 4, 13);
                boolean beach = surface <= sea + 1 || (x < 12 && z > 40);

                for (int y = 0; y <= surface; y++) {
                    if (y == surface) blocks[x][y][z] = beach ? SAND : GRASS;
                    else if (y >= surface - 2) blocks[x][y][z] = beach ? SAND : DIRT;
                    else {
                        double oreRoll = random.nextDouble();
                        if (y < 7 && oreRoll < 0.028) blocks[x][y][z] = IRON_ORE;
                        else if (y < 10 && oreRoll < 0.075) blocks[x][y][z] = COAL_ORE;
                        else blocks[x][y][z] = STONE;
                    }
                }
                if (surface < sea) {
                    for (int y = surface + 1; y <= sea; y++) blocks[x][y][z] = WATER;
                }
            }
        }

        for (int cave = 0; cave < 18; cave++) {
            int cx = 6 + random.nextInt(WORLD_W - 12);
            int cy = 3 + random.nextInt(7);
            int cz = 6 + random.nextInt(WORLD_D - 12);
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

        for (int tree = 0; tree < 44; tree++) {
            int x = 3 + random.nextInt(WORLD_W - 6);
            int z = 3 + random.nextInt(WORLD_D - 6);
            if (Math.abs(x - WORLD_W / 2) < 5 && Math.abs(z - WORLD_D / 2) < 5) continue;
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

    private void spawnAnimals(Random random) {
        Arrays.fill(animalAlive, false);
        for (int i = 0; i < MAX_ANIMALS; i++) {
            for (int tries = 0; tries < 80; tries++) {
                int x = 3 + random.nextInt(WORLD_W - 6);
                int z = 3 + random.nextInt(WORLD_D - 6);
                int y = highestSolidY(x, z);
                if (block(x, y, z) == GRASS && block(x, y + 1, z) == AIR) {
                    animalX[i] = x + 0.5;
                    animalZ[i] = z + 0.5;
                    animalY[i] = y + 1.0;
                    animalType[i] = random.nextInt(4);
                    animalHp[i] = switch (animalType[i]) {
                        case 2 -> 2;
                        case 0 -> 4;
                        case 3 -> 4;
                        default -> 5;
                    };
                    animalDir[i] = random.nextDouble() * Math.PI * 2.0;
                    animalWander[i] = 1.0 + random.nextDouble() * 4.0;
                    animalAlive[i] = true;
                    break;
                }
            }
        }
    }

    public void tickAnimals(double dt) {
        for (int i = 0; i < MAX_ANIMALS; i++) {
            if (!animalAlive[i]) continue;
            animalWander[i] -= dt;
            if (animalWander[i] <= 0) {
                animalDir[i] += (lootRandom.nextDouble() - 0.5) * 2.4;
                animalWander[i] = 1.5 + lootRandom.nextDouble() * 4.5;
            }
            double speed = animalType[i] == 2 ? 0.55 : 0.35;
            double nx = animalX[i] + Math.cos(animalDir[i]) * speed * dt;
            double nz = animalZ[i] + Math.sin(animalDir[i]) * speed * dt;
            int tx = (int) Math.floor(nx), tz = (int) Math.floor(nz);
            if (tx > 1 && tx < WORLD_W - 2 && tz > 1 && tz < WORLD_D - 2) {
                int ground = highestSolidY(tx, tz);
                byte groundBlock = block(tx, ground, tz);
                if (groundBlock == GRASS && Math.abs((ground + 1.0) - animalY[i]) <= 1.25) {
                    animalX[i] = nx;
                    animalZ[i] = nz;
                    animalY[i] += ((ground + 1.0) - animalY[i]) * Math.min(1.0, dt * 5.0);
                } else {
                    animalDir[i] += Math.PI * 0.6;
                }
            } else {
                animalDir[i] += Math.PI;
            }
        }
    }

    public String attackAnimal(int index) {
        if (index < 0 || index >= MAX_ANIMALS || !animalAlive[index]) return "";
        int damage = 1 + swordTier * 2;
        animalHp[index] -= damage;
        if (animalHp[index] > 0) return "Hit " + ANIMAL_NAMES[animalType[index]] + " (-" + damage + ").";

        animalAlive[index] = false;
        int meat = switch (animalType[index]) {
            case 2 -> 1;
            case 0, 3 -> 2;
            default -> 3;
        };
        rawMeat += meat;
        return ANIMAL_NAMES[animalType[index]] + " dropped " + meat + " raw meat.";
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
        byte b = block(x, y, z);
        return b != AIR && b != TORCH && b != WATER;
    }

    public boolean isWaterAt(double x, double y, double z) {
        return block((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)) == WATER;
    }

    public int highestSolidY(int x, int z) {
        for (int y = WORLD_H - 1; y >= 0; y--) {
            byte b = block(x, y, z);
            if (b != AIR && b != LEAVES && b != WOOD && b != TORCH && b != WATER) return y;
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
        if (broken == AIR || broken == WATER) return;
        if (broken == LEAVES && lootRandom.nextInt(5) == 0) apples = Math.min(99, apples + 1);
        if (broken == COAL_ORE) {
            coal = Math.min(999, coal + 1 + (lootRandom.nextInt(4) == 0 ? 1 : 0));
            return;
        }
        if (broken == IRON_ORE) {
            ironOre = Math.min(999, ironOre + 1);
            return;
        }
        byte drop = broken == STONE ? COBBLE : broken;
        if (drop > AIR && drop < inventory.length) inventory[drop] = Math.min(999, inventory[drop] + 1);
    }

    public boolean consumeSelected() {
        byte block = selectedBlock();
        if (inventory[block] <= 0) return false;
        inventory[block]--;
        return true;
    }

    public double miningSeconds(byte block) {
        double base = switch (block) {
            case TORCH -> 0.08;
            case LEAVES -> 0.20;
            case DIRT, GRASS, SAND -> 0.35;
            case WOOD, PLANKS, CRAFTING_TABLE -> 0.85;
            case STONE, COBBLE, COAL_ORE -> 1.35;
            case IRON_ORE -> 1.65;
            case FURNACE -> 1.25;
            default -> 0.45;
        };
        if (block == STONE || block == COBBLE || block == COAL_ORE || block == IRON_ORE || block == FURNACE) {
            base /= 1.0 + pickaxeTier * 0.85;
        }
        if (block == WOOD || block == PLANKS || block == CRAFTING_TABLE) {
            base /= 1.0 + axeTier * 0.75;
        }
        return Math.max(0.12, base);
    }

    public String selectedName() {
        return BLOCK_NAMES[selectedBlock()];
    }

    public String toolSummary() {
        return "Pick " + tierName(pickaxeTier) + "  Sword " + tierName(swordTier) + "  Axe " + tierName(axeTier);
    }

    private String tierName(int tier) {
        return switch (tier) {
            case 1 -> "Stone";
            case 2 -> "Iron";
            default -> "Hand";
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
