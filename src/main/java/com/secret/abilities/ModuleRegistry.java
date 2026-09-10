package com.secret.abilities;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ModuleRegistry {
    private static final Map<String, ClientModule> MODULES = new LinkedHashMap<>();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("voidclient-modules.txt");

    static {
        add("beat_game", "Beat Game", ModuleCategory.BOT, "Experimental survival objective controller.", false);
        add("auto_mine", "Auto Mine", ModuleCategory.BOT, "Mine a selected block using Baritone when available.", false);
        add("vein_mine", "Vein Mine", ModuleCategory.BOT, "Keep mining connected or nearby target blocks.", false);
        add("auto_farm", "Auto Farm", ModuleCategory.BOT, "Farm crops with pathing support.", false);
        add("auto_fish", "Auto Fish", ModuleCategory.BOT, "Automate repeated fishing actions.", false);
        add("auto_craft", "Auto Craft", ModuleCategory.BOT, "Craft selected recipes when possible.", false);
        add("auto_tunnel", "Auto Tunnel", ModuleCategory.BOT, "Dig a forward tunnel.", false);
        add("auto_staircase", "Auto Staircase", ModuleCategory.BOT, "Dig a descending staircase.", false);
        add("pathfinder", "Pathfinder / Go To", ModuleCategory.BOT, "Path to coordinates or a saved waypoint.", false);

        add("auto_totem", "Auto Totem", ModuleCategory.PVP, "Keep a totem ready when inventory rules permit.", false);
        add("auto_armor", "Auto Armor", ModuleCategory.PVP, "Equip better armor from inventory.", false);
        add("target_hud", "Target HUD", ModuleCategory.PVP, "Show information about the entity you are targeting.", false);
        add("pop_counter", "Totem Pop Counter", ModuleCategory.PVP, "Track observed totem pops.", false);
        add("combat_timer", "Combat Timer", ModuleCategory.PVP, "Show time since recent combat interaction.", false);
        add("cps", "CPS Counter", ModuleCategory.PVP, "Show recent left click rate.", false);
        add("reach_display", "Reach Display", ModuleCategory.PVP, "Show current target distance.", false);
        add("damage_indicator", "Damage Indicator", ModuleCategory.PVP, "Show recent target damage changes.", false);

        add("speed", "Speed", ModuleCategory.MOVEMENT, "Adjust client movement speed.", false);
        add("flight", "Flight", ModuleCategory.MOVEMENT, "Client flight controls where allowed.", false);
        add("water_walk", "Water Walk", ModuleCategory.MOVEMENT, "Walk on water where supported.", false);
        add("step", "Step", ModuleCategory.MOVEMENT, "Step over taller terrain.", false);
        add("safe_walk", "Safe Walk", ModuleCategory.MOVEMENT, "Help avoid walking off block edges.", false);
        add("auto_sprint", "Auto Sprint", ModuleCategory.MOVEMENT, "Automatically sprint while moving forward.", false);
        add("toggle_sneak", "Toggle Sneak", ModuleCategory.MOVEMENT, "Keep sneak toggled without holding the key.", false);
        add("auto_jump", "Auto Jump", ModuleCategory.MOVEMENT, "Automatically jump while moving.", false);

        add("xray", "X-Ray", ModuleCategory.RENDER, "Render selected valuable blocks through terrain.", false);
        add("player_esp", "Player ESP", ModuleCategory.RENDER, "Highlight other players.", false);
        add("mob_esp", "Mob ESP", ModuleCategory.RENDER, "Highlight living non player entities.", false);
        add("storage_esp", "Storage ESP", ModuleCategory.RENDER, "Highlight nearby storage containers.", false);
        add("tracers", "Tracers", ModuleCategory.RENDER, "Draw direction lines toward selected entities.", false);
        add("fullbright", "Fullbright", ModuleCategory.RENDER, "Raise brightness while enabled.", false);
        add("no_fog", "No Fog", ModuleCategory.RENDER, "Reduce client fog where supported.", false);
        add("name_tags", "Name Tags", ModuleCategory.RENDER, "Enhanced entity name information.", false);

        add("structure_finder", "Structure Finder", ModuleCategory.WORLD, "Choose and locate a structure.", false);
        add("biome_finder", "Biome Finder", ModuleCategory.WORLD, "Choose and locate a biome.", false);
        add("waypoints", "Waypoints", ModuleCategory.WORLD, "Display saved waypoints.", false);
        add("death_waypoint", "Death Waypoint", ModuleCategory.WORLD, "Automatically save a waypoint when you die.", false);
        add("block_search", "Block Search", ModuleCategory.WORLD, "Highlight or search for a chosen block.", false);
        add("nether_calc", "Nether Calculator", ModuleCategory.WORLD, "Convert Overworld and Nether coordinates.", false);

        add("auto_eat", "Auto Eat", ModuleCategory.PLAYER, "Use food when hunger drops.", false);
        add("auto_tool", "Auto Tool", ModuleCategory.PLAYER, "Select the best hotbar tool for the targeted block.", false);
        add("inventory_cleaner", "Inventory Cleaner", ModuleCategory.PLAYER, "Manage configured junk items.", false);
        add("auto_refill", "Auto Refill", ModuleCategory.PLAYER, "Refill matching hotbar stacks from inventory.", false);
        add("middle_click_friend", "Middle Click Friend", ModuleCategory.PLAYER, "Middle click a player to toggle friend status.", false);
        add("friend_list", "Friend List", ModuleCategory.PLAYER, "Enable Void Client friend filtering.", false);

        add("stats_hud", "FPS / Ping HUD", ModuleCategory.HUD, "Show FPS and latency.", true);
        add("coords_hud", "Coordinates HUD", ModuleCategory.HUD, "Show current coordinates.", false);
        add("armor_hud", "Armor HUD", ModuleCategory.HUD, "Show equipped armor.", false);
        add("durability_hud", "Durability HUD", ModuleCategory.HUD, "Show armor and tool durability.", false);
        add("potion_hud", "Potion HUD", ModuleCategory.HUD, "Show active effect count.", false);
        add("inventory_hud", "Inventory HUD", ModuleCategory.HUD, "Show inventory usage.", false);
        add("item_counters", "Item Counters", ModuleCategory.HUD, "Show PvP item counts.", false);
        add("active_modules", "Active Modules", ModuleCategory.HUD, "Show currently enabled modules.", false);

        add("panic", "Panic", ModuleCategory.CLIENT, "Disable all modules immediately.", false);
        add("notifications", "Notifications", ModuleCategory.CLIENT, "Show Void Client toggle notifications.", true);
        add("profiles", "Profiles", ModuleCategory.CLIENT, "Module profile support.", false);
        add("module_search", "Module Search", ModuleCategory.CLIENT, "Search modules in the GUI.", false);
        add("theme_editor", "Theme Editor", ModuleCategory.CLIENT, "Void Client GUI theme settings.", false);
        add("keybind_manager", "Keybind Manager", ModuleCategory.CLIENT, "Manage module keybinds.", false);
    }

    private ModuleRegistry() {}

    private static void add(String id, String name, ModuleCategory category, String description, boolean defaultEnabled) {
        MODULES.put(id, new ClientModule(id, name, category, description, defaultEnabled));
    }

    public static Collection<ClientModule> all() {
        return MODULES.values();
    }

    public static List<ClientModule> byCategory(ModuleCategory category) {
        List<ClientModule> result = new ArrayList<>();
        for (ClientModule module : MODULES.values()) {
            if (module.category == category) result.add(module);
        }
        return result;
    }

    public static ClientModule get(String id) {
        return MODULES.get(id);
    }

    public static boolean isEnabled(String id) {
        ClientModule module = MODULES.get(id);
        return module != null && module.isEnabled();
    }

    public static void toggle(String id) {
        ClientModule module = MODULES.get(id);
        if (module != null) setEnabled(id, !module.isEnabled());
    }

    public static void setEnabled(String id, boolean enabled) {
        ClientModule module = MODULES.get(id);
        if (module == null) return;

        if ("panic".equals(id) && enabled) {
            disableAll();
            module.setEnabledRaw(false);
            save();
            return;
        }

        if (module.isEnabled() == enabled) return;
        module.setEnabledRaw(enabled);
        save();
        VoidClientRuntime.onModuleChanged(module);
    }

    public static void toggleByLooseName(String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        for (ClientModule module : MODULES.values()) {
            if (module.id.equalsIgnoreCase(normalized)
                    || module.name.equalsIgnoreCase(query.trim())
                    || module.name.toLowerCase(Locale.ROOT).replace(" ", "_").equals(normalized)) {
                toggle(module.id);
                return;
            }
        }
    }

    public static void disableAll() {
        for (ClientModule module : MODULES.values()) {
            if (module.isEnabled()) {
                module.setEnabledRaw(false);
                VoidClientRuntime.onModuleChanged(module);
            }
        }
        save();
    }

    public static int enabledCount() {
        int count = 0;
        for (ClientModule module : MODULES.values()) if (module.isEnabled()) count++;
        return count;
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|", 2);
                if (parts.length != 2) continue;
                ClientModule module = MODULES.get(parts[0]);
                if (module != null) module.setEnabledRaw(Boolean.parseBoolean(parts[1]));
            }
        } catch (IOException ignored) {
        }
    }

    public static void save() {
        List<String> lines = new ArrayList<>();
        for (ClientModule module : MODULES.values()) lines.add(module.id + "|" + module.isEnabled());

        try {
            Files.createDirectories(FILE.getParent());
            Files.write(FILE, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
