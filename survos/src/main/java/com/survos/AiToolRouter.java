package com.survos;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.Locale;

public final class AiToolRouter {
    private AiToolRouter(){}

    public static String execute(MinecraftClient c, LocalAiService.AiAction action) {
        if (c == null || action == null) return "invalid";
        String tool = action.tool();
        JsonObject a = action.args() == null ? new JsonObject() : action.args();
        try {
            return switch (tool) {
                case "start_task" -> {
                    String m = str(a,"mode","MINING").toUpperCase(Locale.ROOT);
                    String target = str(a,"target","");
                    int count = num(a,"count",0);
                    AutomationManager.Mode mode = AutomationManager.Mode.valueOf(m);
                    SurvOsClient.AUTOMATION.setGoal(target,count);
                    SurvOsClient.AUTOMATION.start(mode,c);
                    yield "started " + m;
                }
                case "stop_task" -> { SurvOsClient.AUTOMATION.stop(c,"AI request"); yield "stopped"; }
                case "pause_task" -> { SurvOsClient.AUTOMATION.pause(c); yield "paused"; }
                case "resume_task" -> { SurvOsClient.AUTOMATION.resume(); yield "resumed"; }
                case "set_profile" -> {
                    String n=str(a,"name","SURVIVAL"); SurvOsClient.CONFIG.applyProfile(n); yield "profile "+n;
                }
                case "add_rule" -> {
                    RuleEngine.Kind kind=RuleEngine.Kind.valueOf(str(a,"type","HEALTH_BELOW").toUpperCase(Locale.ROOT));
                    double v=dbl(a,"value",0); String item=str(a,"item",null);
                    SurvOsClient.RULES.add(new RuleEngine.Rule(kind,v,item)); yield "rule added";
                }
                case "clear_rules" -> { SurvOsClient.RULES.clear(); yield "rules cleared"; }
                case "save_waypoint" -> {
                    String n=str(a,"name","home"); SurvOsClient.MEMORY.setWaypoint(c,n); yield "waypoint saved";
                }
                case "go_waypoint" -> {
                    String n=str(a,"name","home"); boolean ok=SurvOsClient.AUTOMATION.goToWaypoint(c,n); yield ok?"navigating":"waypoint missing";
                }
                case "start_route_recording" -> {
                    String n=str(a,"name","route"); SurvOsClient.MEMORY.startRoute(n); yield "recording route";
                }
                case "stop_route_recording" -> {
                    int n=SurvOsClient.MEMORY.stopRoute(); yield "route saved "+n+" points";
                }
                case "apply_loadout" -> {
                    String n=str(a,"name","MINING"); InventoryManager.applyLoadout(c,n); yield "loadout applied";
                }
                case "open_villager" -> { c.setScreen(new SurvScreen(SurvScreen.Tab.VILLAGER)); yield "opened villagers"; }
                case "toggle_villager" -> { SurvOsClient.LEGACY.toggleVillager(c); yield "villager cycler toggled"; }
                case "open_enchant" -> { SurvOsClient.LEGACY.openEnchantBuilder(c); yield "opened enchant lab"; }
                case "open_craft" -> { c.setScreen(new SurvScreen(SurvScreen.Tab.CRAFT)); yield "opened craft"; }
                case "set_hud" -> {
                    String module=str(a,"module","hud").toLowerCase(Locale.ROOT); boolean on=bool(a,"enabled",true);
                    setHud(module,on); SurvOsClient.CONFIG.save(); yield module+" "+on;
                }
                case "find_storage" -> {
                    String item=str(a,"item",""); List<String> list=SurvOsClient.MEMORY.containersWith(item);
                    yield list.isEmpty()?"not remembered":String.join("; ",list.subList(0,Math.min(4,list.size())));
                }
                case "session_stats" -> SurvOsClient.STATS.summary();
                default -> "unknown tool";
            };
        } catch (Throwable t) {
            return "tool error";
        }
    }

    private static void setHud(String m,boolean v){
        switch(m){
            case "hud" -> SurvOsClient.CONFIG.hudEnabled=v;
            case "health" -> SurvOsClient.CONFIG.showHealth=v;
            case "hunger" -> SurvOsClient.CONFIG.showHunger=v;
            case "armor" -> SurvOsClient.CONFIG.showArmor=v;
            case "xp" -> SurvOsClient.CONFIG.showXp=v;
            case "coords","coordinates" -> SurvOsClient.CONFIG.showCoords=v;
            case "dimension" -> SurvOsClient.CONFIG.showDimension=v;
            case "day","night","time" -> SurvOsClient.CONFIG.showDayNight=v;
            case "durability" -> SurvOsClient.CONFIG.showDurability=v;
            case "inventory" -> SurvOsClient.CONFIG.showInventory=v;
            case "hostiles","threat" -> SurvOsClient.CONFIG.showHostiles=v;
            case "automation" -> SurvOsClient.CONFIG.showAutomation=v;
            case "voice" -> SurvOsClient.CONFIG.showVoice=v;
        }
    }

    private static String str(JsonObject o,String k,String d){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():d;}
    private static int num(JsonObject o,String k,int d){return o.has(k)?o.get(k).getAsInt():d;}
    private static double dbl(JsonObject o,String k,double d){return o.has(k)?o.get(k).getAsDouble():d;}
    private static boolean bool(JsonObject o,String k,boolean d){return o.has(k)?o.get(k).getAsBoolean():d;}
}
