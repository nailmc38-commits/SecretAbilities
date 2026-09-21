package com.survivalutils;

public enum Feature {
    MAIN_HUD(Category.HUD, "Main Survival HUD", "Master switch for the right-side survival panel.", true),
    HEALTH_HUD(Category.HUD, "Health", "Current and maximum health.", true),
    HUNGER_HUD(Category.HUD, "Hunger", "Food level.", true),
    ARMOR_HUD(Category.HUD, "Armor", "Armor points.", true),
    XP_HUD(Category.HUD, "XP Level", "Current experience level.", true),
    COORDS_HUD(Category.HUD, "Coordinates", "Current X Y Z.", true),
    BIOME_HUD(Category.HUD, "Biome", "Current biome.", true),
    DIMENSION_HUD(Category.HUD, "Dimension", "Current dimension.", true),
    LIGHT_HUD(Category.HUD, "Light Level", "Light level at your position.", true),
    DAY_NIGHT_HUD(Category.HUD, "Day / Night Timer", "Time left in the current day or night.", true),
    WEATHER_HUD(Category.HUD, "Weather", "Clear, rain or thunder.", false),
    EFFECTS_HUD(Category.HUD, "Effects", "Number of active status effects.", false),
    HELD_ITEM_HUD(Category.HUD, "Held Item", "Held item name and stack count.", true),
    DURABILITY_HUD(Category.HUD, "Held Durability", "Held item durability percentage.", true),
    INVENTORY_SPACE(Category.HUD, "Inventory Space", "Free inventory slots.", true),
    FPS_HUD(Category.HUD, "FPS", "Current frames per second.", true),
    PING_HUD(Category.HUD, "Ping", "Current server latency.", true),
    PLAYER_RADAR(Category.HUD, "Players Nearby", "Nearby player names, distance and spectator status.", true),
    THREAT_HUD(Category.HUD, "Threat Level", "Multi-factor danger score.", true),
    READINESS_HUD(Category.HUD, "Survival Readiness", "Readiness percentage based on health, food and gear.", true),

    STATS_PANEL(Category.STATS, "Left Stats Panel", "Master switch for the left stats panel.", true),
    SESSION_TIMER(Category.STATS, "Session Time", "Time in the current session.", true),
    JOURNEY_DISTANCE(Category.STATS, "Journey Distance", "Approximate distance traveled this session.", true),
    SPEED_HUD(Category.STATS, "Movement Speed", "Current horizontal speed.", true),
    WORLD_DAY_COUNTER(Category.STATS, "World Day", "Current world day number.", true),
    DISTANCE_FROM_SPAWN(Category.STATS, "Distance From Spawn", "Horizontal distance from world spawn.", true),
    CHUNK_HUD(Category.STATS, "Chunk Position", "Current chunk coordinates.", false),
    MEMORY_USAGE(Category.STATS, "Java Memory", "Current JVM memory usage.", false),

    HELMET_OVERLAY(Category.HELMET, "Helmet HUD", "Show a suit-style overlay while any helmet is equipped.", true),
    HELMET_THREAT(Category.HELMET, "Helmet Threat Scan", "Threat level and nearest danger.", true),
    HELMET_COORDS(Category.HELMET, "Helmet Navigation", "Coordinates in the helmet overlay.", true),
    HELMET_DURABILITY(Category.HELMET, "Helmet / Tool Durability", "Helmet and held-tool durability.", true),
    HELMET_TIME(Category.HELMET, "Helmet Clock", "Day/night status in helmet HUD.", true),
    HELMET_PLAYERS(Category.HELMET, "Helmet Player Scan", "Number of nearby players.", true),

    TOTEM_COUNT(Category.INVENTORY, "Totem Count", "Count totems in inventory.", true),
    FOOD_COUNT(Category.INVENTORY, "Food Count", "Count safe edible items.", true),
    TORCH_COUNT(Category.INVENTORY, "Torch Count", "Count torches.", true),
    ARROW_COUNT(Category.INVENTORY, "Arrow Count", "Count arrows.", true),
    ROCKET_COUNT(Category.INVENTORY, "Rocket Count", "Count firework rockets.", false),
    PEARL_COUNT(Category.INVENTORY, "Pearl Count", "Count ender pearls.", false),
    BED_ALERT(Category.INVENTORY, "Bed Check", "Warn when you have no bed.", false),
    WATER_BUCKET_ALERT(Category.INVENTORY, "Water Bucket Check", "Warn when you have no water bucket.", false),

    NETHER_COORD_CONVERTER(Category.EXPLORATION, "Nether Coordinate Converter", "Show Nether/Overworld coordinate conversion.", false),
    ALTITUDE_HINT(Category.EXPLORATION, "Altitude Hint", "Contextual Y-level survival hint.", true),
    LIGHT_WARNING(Category.EXPLORATION, "Low Light Warning", "Warn in very dark areas.", true),
    SPAWN_DISTANCE(Category.EXPLORATION, "Spawn Distance", "Distance from world spawn.", false),

    PICKAXE_DURABILITY(Category.MINING, "Pickaxe Durability", "Show best pickaxe durability.", true),
    DIAMOND_Y_HINT(Category.MINING, "Diamond Y Hint", "Hint when in deep mining levels.", false),
    ANCIENT_DEBRIS_Y_HINT(Category.MINING, "Ancient Debris Y Hint", "Hint in common Nether debris levels.", false),
    ORE_SUMMARY(Category.MINING, "Ore Inventory Summary", "Quick count of valuable ores and gems.", true),

    HOSTILES_NEARBY(Category.COMBAT, "Hostiles Nearby", "Count nearby hostile mobs.", true),
    CREEPER_ALERT(Category.COMBAT, "Creeper Alert", "Extra threat weight and warning for close creepers.", true),
    SKELETON_ALERT(Category.COMBAT, "Skeleton Alert", "Warn about nearby ranged skeletons.", false),
    SHIELD_DURABILITY(Category.COMBAT, "Shield Durability", "Show shield durability.", false),
    NO_TOTEM_ALERT(Category.COMBAT, "No Totem Warning", "Warn when threat is high and no totem is available.", true),
    SPECTATOR_ALERT(Category.COMBAT, "Spectator Warning", "Warn when the server reports another player in spectator mode.", true),

    LOW_HEALTH_ALERT(Category.ALERTS, "Low Health Warning", "Tactical low-health warning.", true),
    LOW_HUNGER_ALERT(Category.ALERTS, "Low Hunger Warning", "Warn before hunger becomes dangerous.", true),
    DURABILITY_ALERT(Category.ALERTS, "Low Durability Warning", "Warn when held gear is close to breaking.", true),
    INVENTORY_FULL_ALERT(Category.ALERTS, "Inventory Full Warning", "Warn when inventory space is almost gone.", true),
    FIRE_ALERT(Category.ALERTS, "Fire Warning", "Critical warning while burning.", true),
    DROWNING_ALERT(Category.ALERTS, "Low Air Warning", "Critical warning when air is low.", true),
    NIGHT_WARNING(Category.ALERTS, "Night Approaching", "Warn shortly before night.", true),
    THREAT_ALERT(Category.ALERTS, "High Threat Warning", "Show tactical disengage recommendations at high threat.", true);

    public final Category category;
    public final String title;
    public final String description;
    public final boolean defaultEnabled;

    Feature(Category category, String title, String description, boolean defaultEnabled) {
        this.category = category;
        this.title = title;
        this.description = description;
        this.defaultEnabled = defaultEnabled;
    }

    public enum Category {
        HUD("Main HUD"),
        STATS("Left Stats"),
        HELMET("Helmet HUD"),
        INVENTORY("Inventory"),
        EXPLORATION("Exploration"),
        MINING("Mining"),
        COMBAT("Combat"),
        ALERTS("Warnings"),
        SETTINGS("Settings");

        public final String title;

        Category(String title) {
            this.title = title;
        }
    }
}
