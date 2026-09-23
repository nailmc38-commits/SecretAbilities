package com.survivalutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class ExoTelemetry {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final ArrayDeque<Frame> BLACKBOX=new ArrayDeque<>();
    private static final ArrayList<CombatRecord> COMBATS=new ArrayList<>();
    private static final ArrayList<RoutePoint> ROUTE=new ArrayList<>();
    private static final ArrayList<PortalLink> PORTALS=new ArrayList<>();

    private static int ticks;
    private static float lastHealth=-1;
    private static boolean wasAlive=true;
    private static boolean combatActive;
    private static long combatStarted;
    private static float combatMinHp;
    private static int combatHitsTaken;
    private static int combatStartArmor;
    private static String combatOpponent="NONE";
    private static long combatQuietUntil;
    private static RoutePoint lastRoutePoint;
    private static String lastDimension="";
    private static RoutePoint lastDimPoint;
    private static Recovery recovery;
    private static boolean loaded;

    public record Frame(long time,float hp,float absorption,int armor,int armorIntegrity,int food,
                        int x,int y,int z,String dimension,String held,String warning,String advisor){}
    public record CombatRecord(long started,long ended,String opponent,int hitsTaken,float lowestHp,
                               int armorStart,int armorEnd,String result){}
    public record RoutePoint(long time,int x,int y,int z,String dimension){}
    public record PortalLink(long time,String fromDimension,int fromX,int fromY,int fromZ,
                             String toDimension,int toX,int toY,int toZ){}
    public record Recovery(long time,int x,int y,int z,String dimension){}

    private ExoTelemetry(){}

    public static void tick(MinecraftClient client) {
        if(client==null||client.player==null||client.world==null)return;
        ensureLoaded();
        if(++ticks%5!=0)return;

        var p=client.player;
        long now=System.currentTimeMillis();
        String dim=client.world.getRegistryKey().getValue().getPath();
        float hp=p.getHealth()+p.getAbsorptionAmount();

        String held=p.getMainHandStack().isEmpty()?"empty":
                Registries.ITEM.getId(p.getMainHandStack().getItem()).getPath();
        WarningManager.Warning warning=SurvivalUtilsClient.WARNINGS.active();
        CombatAdvisor.Snapshot advisor=CombatAdvisor.current();

        BLACKBOX.addLast(new Frame(
                now,p.getHealth(),p.getAbsorptionAmount(),p.getArmor(),SurvMegaState.armorIntegrity(p),
                p.getHungerManager().getFoodLevel(),p.getBlockX(),p.getBlockY(),p.getBlockZ(),dim,held,
                warning==null?"NONE":warning.severity()+" // "+warning.text(),
                advisor.recommendation()+" // "+advisor.opponent()
        ));
        while(BLACKBOX.size()>240)BLACKBOX.removeFirst();

        if(lastHealth<0)lastHealth=hp;
        if(hp<lastHealth-0.05f) {
            combatHitsTaken++;
            combatQuietUntil=now+6000;
        }
        lastHealth=hp;

        boolean inCombat=now<combatQuietUntil
                || p.getAttacker()!=null
                || !"NONE".equals(advisor.opponentType());

        if(inCombat&&!combatActive) {
            combatActive=true;
            combatStarted=now;
            combatMinHp=hp;
            combatHitsTaken=0;
            combatStartArmor=SurvMegaState.armorIntegrity(p);
            combatOpponent=advisor.opponent();
        }

        if(combatActive) {
            combatMinHp=Math.min(combatMinHp,hp);
            if(!"NONE".equals(advisor.opponent())) combatOpponent=advisor.opponent();
            if(!inCombat) finishCombat(now,p.isAlive()?"SURVIVED":"DEATH",SurvMegaState.armorIntegrity(p));
        }

        if(wasAlive&&!p.isAlive()) {
            recovery=new Recovery(now,p.getBlockX(),p.getBlockY(),p.getBlockZ(),dim);
            saveBlackbox();
            saveRecovery();
            if(combatActive)finishCombat(now,"DEATH",SurvMegaState.armorIntegrity(p));
        }
        wasAlive=p.isAlive();

        if(ticks%20==0) {
            RoutePoint point=new RoutePoint(now,p.getBlockX(),p.getBlockY(),p.getBlockZ(),dim);
            if(lastRoutePoint==null||!lastRoutePoint.dimension().equals(dim)
                    || dist2(lastRoutePoint,point)>=16) {
                ROUTE.add(point);
                lastRoutePoint=point;
                while(ROUTE.size()>1200)ROUTE.remove(0);
            }

            if(lastDimension.isBlank()) {
                lastDimension=dim;
                lastDimPoint=point;
            } else if(!lastDimension.equals(dim)) {
                if(lastDimPoint!=null) {
                    PORTALS.add(new PortalLink(now,lastDimension,lastDimPoint.x(),lastDimPoint.y(),lastDimPoint.z(),
                            dim,point.x(),point.y(),point.z()));
                    while(PORTALS.size()>100)PORTALS.remove(0);
                }
                lastDimension=dim;
                lastDimPoint=point;
                savePortals();
            } else {
                lastDimPoint=point;
            }

            if(ticks%100==0) {
                saveRoute();
                saveCombats();
            }
        }
    }

    private static double dist2(RoutePoint a,RoutePoint b) {
        long dx=(long)a.x()-b.x(),dy=(long)a.y()-b.y(),dz=(long)a.z()-b.z();
        return dx*dx+dy*dy+dz*dz;
    }

    private static void finishCombat(long now,String result,int armorEnd) {
        COMBATS.add(new CombatRecord(combatStarted,now,combatOpponent,combatHitsTaken,combatMinHp,
                combatStartArmor,armorEnd,result));
        while(COMBATS.size()>100)COMBATS.remove(0);
        combatActive=false;
        combatQuietUntil=0;
        saveCombats();
    }

    public static List<Frame> blackbox(){ensureLoaded();return List.copyOf(BLACKBOX);}
    public static List<CombatRecord> combats(){ensureLoaded();return List.copyOf(COMBATS);}
    public static List<RoutePoint> route(){ensureLoaded();return List.copyOf(ROUTE);}
    public static List<PortalLink> portals(){ensureLoaded();return List.copyOf(PORTALS);}
    public static Recovery recovery(){ensureLoaded();return recovery;}

    public static RoutePoint previousRoutePoint(int stepsBack) {
        ensureLoaded();
        if(ROUTE.isEmpty())return null;
        int i=Math.max(0,ROUTE.size()-1-Math.max(1,stepsBack));
        return ROUTE.get(i);
    }

    public static void clearBlackbox(){BLACKBOX.clear();delete("blackbox-last-death.json");}
    public static void clearCombats(){COMBATS.clear();saveCombats();}
    public static void clearRoute(){ROUTE.clear();lastRoutePoint=null;saveRoute();}
    public static void clearPortals(){PORTALS.clear();savePortals();}
    public static void clearRecovery(){recovery=null;saveRecovery();}

    public static Map<String,String> diagnostics(MinecraftClient client) {
        LinkedHashMap<String,String> m=new LinkedHashMap<>();
        m.put("HUD","ONLINE");
        m.put("WARNINGS","ONLINE");
        m.put("ADVISOR",ExoLinkData.SETTINGS.advisor?"ONLINE":"DISABLED");
        m.put("THREAT MEMORY",ExoLinkData.SETTINGS.threatMemory?"ONLINE":"DISABLED");
        m.put("ECHO",ExoLinkData.SETTINGS.echo?"ONLINE":"DISABLED");
        m.put("PACK",ExoLinkData.SETTINGS.pack?"ONLINE":"DISABLED");
        m.put("CLIENTCOMMANDS", FabricLoader.getInstance().isModLoaded("clientcommands")?"ONLINE":"LIMITED");
        m.put("SEEDCRACKERX",FabricLoader.getInstance().isModLoaded("seedcrackerx")?"ONLINE":"CHECK ENGINE");
        return m;
    }

    private static void ensureLoaded() {
        if(loaded)return;
        loaded=true;
        try{
            Path root=ExoLinkData.root();
            CombatRecord[] c=read(root.resolve("combat-history.json"),CombatRecord[].class);
            if(c!=null)COMBATS.addAll(Arrays.asList(c));
            RoutePoint[] r=read(root.resolve("route-memory.json"),RoutePoint[].class);
            if(r!=null)ROUTE.addAll(Arrays.asList(r));
            PortalLink[] p=read(root.resolve("portal-links.json"),PortalLink[].class);
            if(p!=null)PORTALS.addAll(Arrays.asList(p));
            Recovery rec=read(root.resolve("recovery.json"),Recovery.class);
            if(rec!=null)recovery=rec;
        }catch(Exception ignored){}
    }

    private static <T>T read(Path p,Class<T> type){
        try{
            if(Files.exists(p))return GSON.fromJson(Files.readString(p,StandardCharsets.UTF_8),type);
        }catch(Exception ignored){}
        return null;
    }

    private static void saveBlackbox(){write("blackbox-last-death.json",BLACKBOX);}
    private static void saveCombats(){write("combat-history.json",COMBATS);}
    private static void saveRoute(){write("route-memory.json",ROUTE);}
    private static void savePortals(){write("portal-links.json",PORTALS);}
    private static void saveRecovery(){write("recovery.json",recovery);}

    private static void write(String file,Object value){
        try{
            Path p=ExoLinkData.root().resolve(file);
            if(value==null){Files.deleteIfExists(p);return;}
            Files.writeString(p,GSON.toJson(value),StandardCharsets.UTF_8);
        }catch(Exception ignored){}
    }

    private static void delete(String file){
        try{Files.deleteIfExists(ExoLinkData.root().resolve(file));}catch(Exception ignored){}
    }
}
