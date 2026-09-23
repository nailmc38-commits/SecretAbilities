package com.survivalutils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExoScriptEngine {
    public enum Action { WARN, RECOMMEND }
    public record ScriptAlert(String text, WarningManager.Severity severity, int priority, Action action, String source) {}
    private record Condition(String field,String op,double value) {}
    private record Rule(String source,Condition condition,Action action,String text,WarningManager.Severity severity,int priority) {}
    public record ModuleInfo(String file,String tab,String section,List<String> text,int rules){}

    private static final Pattern QUOTED=Pattern.compile(""([^"]*)"");
    private static final ArrayList<Rule> RULES=new ArrayList<>();
    private static final ArrayList<ModuleInfo> MODULES=new ArrayList<>();
    private static final ArrayList<String> ERRORS=new ArrayList<>();
    private static ScriptAlert active;
    private static int ticks;
    private static boolean loaded;
    private static long lastLoad;

    private ExoScriptEngine(){}

    public static void tick(MinecraftClient client){
        if(!ExoLinkData.SETTINGS.exoScript||client==null||client.player==null||client.world==null){
            active=null;
            return;
        }
        ensureLoaded();
        if(++ticks%5!=0)return;

        ScriptAlert best=null;
        for(Rule rule:RULES){
            if(!test(client,rule.condition()))continue;
            ScriptAlert a=new ScriptAlert(rule.text(),rule.severity(),rule.priority(),rule.action(),rule.source());
            if(best==null||a.priority()>best.priority())best=a;
        }
        active=best;
    }

    public static ScriptAlert active(){ensureLoaded();return active;}
    public static int ruleCount(){ensureLoaded();return RULES.size();}
    public static int errorCount(){ensureLoaded();return ERRORS.size();}
    public static List<String> errors(){ensureLoaded();return List.copyOf(ERRORS);}
    public static List<ModuleInfo> modules(){ensureLoaded();return List.copyOf(MODULES);}
    public static long lastLoad(){return lastLoad;}

    public static void reload(){
        loaded=false;
        RULES.clear();
        MODULES.clear();
        ERRORS.clear();
        active=null;
        ensureLoaded();
    }

    private static void ensureLoaded(){
        if(loaded)return;
        loaded=true;
        lastLoad=System.currentTimeMillis();

        Path dir=ExoLinkData.root().resolve("scripts");
        try{
            Files.createDirectories(dir);
            Path sample=dir.resolve("example.exo");
            if(!Files.exists(sample)){
                Files.writeString(sample,
                        "# EXO // LINK example module\n"+
                        "TAB \"SURVIVAL\"\n"+
                        "SECTION \"Safety\"\n"+
                        "TEXT \"Rules are checked locally and cannot run arbitrary code.\"\n\n"+
                        "WHEN health < 6\n"+
                        "WARN CRITICAL \"HEALTH CRITICAL // SCRIPT\"\n\n"+
                        "WHEN durability < 10\n"+
                        "WARN DANGER \"HELD TOOL CRITICAL // SCRIPT\"\n\n"+
                        "WHEN player_distance < 12\n"+
                        "RECOMMEND \"PLAYER CLOSE // PREPARE OR DISENGAGE\"\n",
                        StandardCharsets.UTF_8);
            }

            try(var stream=Files.list(dir)){
                stream.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exo"))
                        .sorted()
                        .forEach(ExoScriptEngine::parse);
            }
        }catch(Exception e){
            ERRORS.add("loader // "+e.getClass().getSimpleName()+": "+safe(e.getMessage()));
        }
    }

    private static void parse(Path path){
        String file=path.getFileName().toString();
        String tab="CUSTOM",section="MODULE";
        ArrayList<String> text=new ArrayList<>();
        ArrayList<Rule> local=new ArrayList<>();
        Condition pending=null;

        try{
            List<String> lines=Files.readAllLines(path,StandardCharsets.UTF_8);
            for(int i=0;i<lines.size();i++){
                String raw=lines.get(i).trim();
                if(raw.isBlank()||raw.startsWith("#"))continue;

                try{
                    if(raw.startsWith("TAB ")){tab=quoted(raw);continue;}
                    if(raw.startsWith("SECTION ")){section=quoted(raw);continue;}
                    if(raw.startsWith("TEXT ")){text.add(quoted(raw));continue;}

                    if(raw.startsWith("WHEN ")){
                        String[] p=raw.substring(5).trim().split("\\s+");
                        if(p.length!=3)throw new IllegalArgumentException("WHEN requires: field operator number");
                        pending=new Condition(p[0].toLowerCase(Locale.ROOT),p[1],Double.parseDouble(p[2]));
                        continue;
                    }

                    if(raw.startsWith("WARN ")){
                        if(pending==null)throw new IllegalArgumentException("WARN needs a WHEN above it");
                        WarningManager.Severity sev=WarningManager.Severity.CAUTION;
                        String rest=raw.substring(5).trim();
                        for(WarningManager.Severity s:WarningManager.Severity.values()){
                            if(rest.startsWith(s.name()+" ")){sev=s;rest=rest.substring(s.name().length()).trim();break;}
                        }
                        String msg=quoted(rest);
                        int priority=switch(sev){case CRITICAL->97;case DANGER->84;case CAUTION->55;case INFO->25;};
                        local.add(new Rule(file,pending,Action.WARN,msg,sev,priority));
                        pending=null;
                        continue;
                    }

                    if(raw.startsWith("RECOMMEND ")){
                        if(pending==null)throw new IllegalArgumentException("RECOMMEND needs a WHEN above it");
                        local.add(new Rule(file,pending,Action.RECOMMEND,quoted(raw.substring(10).trim()),
                                WarningManager.Severity.INFO,45));
                        pending=null;
                        continue;
                    }

                    throw new IllegalArgumentException("unknown statement");
                }catch(Exception lineError){
                    ERRORS.add(file+":"+(i+1)+" // "+safe(lineError.getMessage()));
                    pending=null;
                }
            }
        }catch(Exception e){
            ERRORS.add(file+" // "+e.getClass().getSimpleName()+": "+safe(e.getMessage()));
        }

        RULES.addAll(local);
        MODULES.add(new ModuleInfo(file,tab,section,List.copyOf(text),local.size()));
    }

    private static boolean test(MinecraftClient c,Condition condition){
        double actual=value(c,condition.field());
        if(Double.isNaN(actual))return false;
        return switch(condition.op()){
            case "<"->actual<condition.value();
            case "<="->actual<=condition.value();
            case ">"->actual>condition.value();
            case ">="->actual>=condition.value();
            case "=="->Math.abs(actual-condition.value())<0.0001;
            case "!="->Math.abs(actual-condition.value())>=0.0001;
            default->false;
        };
    }

    private static double value(MinecraftClient c,String field){
        PlayerEntity p=c.player;
        return switch(field){
            case "health"->p.getHealth();
            case "armor"->p.getArmor();
            case "armor_integrity"->SurvMegaState.armorIntegrity(p);
            case "hunger","food"->p.getHungerManager().getFoodLevel();
            case "durability"->p.getMainHandStack().isDamageable()?InventoryUtil.durabilityPercent(p.getMainHandStack()):100;
            case "light"->c.world.getLightLevel(p.getBlockPos());
            case "totems"->InventoryUtil.count(p,"totem_of_undying");
            case "free_slots"->InventoryUtil.freeSlots(p);
            case "rockets"->InventoryUtil.count(p,"firework_rocket");
            case "y"->p.getBlockY();
            case "visor_damage"->VisorDamageSystem.damagePercent();
            case "player_distance"->c.world.getPlayers().stream()
                    .filter(other->other!=p)
                    .mapToDouble(other->Math.sqrt(p.squaredDistanceTo(other)))
                    .min().orElse(9999);
            case "hostiles"->ThreatAnalyzer.analyze(c).hostileCount();
            default->Double.NaN;
        };
    }

    public static void renderHud(DrawContext ctx,MinecraftClient client){
        if(!ExoLinkData.SETTINGS.exoScript||active==null||active.action()!=Action.RECOMMEND)return;
        int w=ctx.getScaledWindowWidth(),h=ctx.getScaledWindowHeight();
        String text="EXO SCRIPT // "+active.text();
        int tw=client.textRenderer.getWidth(text);
        int x=Math.max(10,(w-tw)/2);
        int y=h-62;
        ctx.fill(x-7,y-4,x+tw+7,y+12,0xA90A0F11);
        ctx.fill(x-7,y+12,x+tw+7,y+14,0xFF7FD7E2);
        ctx.drawTextWithShadow(client.textRenderer,text,x,y,0xFFDDF6F8);
    }

    private static String quoted(String s){
        Matcher m=QUOTED.matcher(s);
        if(!m.find())throw new IllegalArgumentException("missing quoted text");
        return m.group(1);
    }

    private static String safe(String s){
        if(s==null||s.isBlank())return "error";
        return s.length()>90?s.substring(0,90):s;
    }
}
