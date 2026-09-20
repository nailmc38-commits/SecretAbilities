package com.survos;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class WorldMemory {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    public static final class Point { public int x,y,z; public String dimension; public Point(){} public Point(BlockPos p,String d){x=p.getX();y=p.getY();z=p.getZ();dimension=d;} public BlockPos pos(){return new BlockPos(x,y,z);} }
    public static final class ContainerRecord { public Point point; public long seenAt; public Map<String,Integer> items=new LinkedHashMap<>(); }
    private static final class Data {
        Map<String,Point> waypoints=new LinkedHashMap<>();
        Map<String,List<Point>> routes=new LinkedHashMap<>();
        Map<String,ContainerRecord> containers=new LinkedHashMap<>();
    }

    private Data data=new Data();
    private String worldKey="";
    private List<Point> recording;
    private String recordingName;
    private int recordCooldown;
    private int lastHandlerId=-999;

    public void tick(MinecraftClient c){
        if(c.player==null||c.world==null) return;
        String key=key(c);
        if(!key.equals(worldKey)){worldKey=key;load();}
        if(recording!=null && recordCooldown--<=0){
            recordCooldown=8;
            Point p=point(c);
            if(recording.isEmpty() || recording.get(recording.size()-1).pos().getSquaredDistance(p.pos())>=4) recording.add(p);
        }
        int sync=c.player.currentScreenHandler.syncId;
        if(sync!=lastHandlerId){
            lastHandlerId=sync;
            if(c.player.currentScreenHandler instanceof GenericContainerScreenHandler || c.player.currentScreenHandler instanceof ShulkerBoxScreenHandler) rememberOpenContainer(c);
        }
    }

    public void setWaypoint(MinecraftClient c,String name){
        if(c.player==null||c.world==null)return;
        data.waypoints.put(clean(name),point(c)); save();
    }
    public Point waypoint(String name){return data.waypoints.get(clean(name));}
    public Set<String> waypointNames(){return Collections.unmodifiableSet(data.waypoints.keySet());}

    public void startRoute(String name){recordingName=clean(name); recording=new ArrayList<>();}
    public int stopRoute(){if(recording==null)return 0; data.routes.put(recordingName,new ArrayList<>(recording)); int n=recording.size(); recording=null; recordingName=null; save(); return n;}
    public List<Point> route(String name){return data.routes.getOrDefault(clean(name),List.of());}
    public Set<String> routeNames(){return Collections.unmodifiableSet(data.routes.keySet());}

    public List<String> containersWith(String query){
        String q=WorldScanner.normalize(query);
        List<String> out=new ArrayList<>();
        for(var e:data.containers.entrySet()) for(var it:e.getValue().items.entrySet()) if(it.getKey().contains(q)&&it.getValue()>0){
            out.add(e.getKey()+" ("+it.getValue()+")"); break;
        }
        return out;
    }

    private void rememberOpenContainer(MinecraftClient c){
        if(!(c.crosshairTarget instanceof BlockHitResult hit)) return;
        BlockPos p=hit.getBlockPos();
        ContainerRecord r=new ContainerRecord();
        r.point=new Point(p,dimension(c)); r.seenAt=System.currentTimeMillis();
        int total=c.player.currentScreenHandler.getStacks().size();
        int playerSlots=36;
        int containerSlots=Math.max(0,total-playerSlots);
        for(int i=0;i<containerSlots;i++){
            ItemStack s=c.player.currentScreenHandler.getSlot(i).getStack();
            if(s.isEmpty()) continue;
            String id=Registries.ITEM.getId(s.getItem()).toString();
            r.items.merge(id,s.getCount(),Integer::sum);
        }
        data.containers.put(p.getX()+","+p.getY()+","+p.getZ(),r);
        save();
    }

    private Point point(MinecraftClient c){return new Point(new BlockPos(c.player.getBlockX(),c.player.getBlockY(),c.player.getBlockZ()),dimension(c));}
    private String dimension(MinecraftClient c){return c.world.getRegistryKey().getValue().toString();}
    private String key(MinecraftClient c){
        String server=c.getCurrentServerEntry()!=null?c.getCurrentServerEntry().address:"singleplayer";
        return clean(server);
    }
    private static String clean(String s){return (s==null?"unnamed":s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+","_"));}

    private Path file(){return FabricLoader.getInstance().getConfigDir().resolve("surv-os").resolve("memory-"+worldKey+".json");}
    private void load(){
        try{Path f=file(); if(Files.exists(f)){Data d=GSON.fromJson(Files.readString(f,StandardCharsets.UTF_8),Data.class); if(d!=null)data=d; else data=new Data();} else data=new Data();}
        catch(Exception e){data=new Data();}
    }
    public void save(){
        try{Path f=file();Files.createDirectories(f.getParent());Files.writeString(f,GSON.toJson(data),StandardCharsets.UTF_8);}
        catch(Exception ignored){}
    }
}
