package com.survos;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

public final class StatsTracker {
    private long startMs=System.currentTimeMillis();
    private Vec3d lastPos;
    private double distance;
    private int blocksMined;
    private int mobsHit;
    private int harvested;
    private int fishActions;
    private final Map<String,Integer> lastCounts=new HashMap<>();
    private final Map<String,Integer> gained=new HashMap<>();

    public void reset(){startMs=System.currentTimeMillis();lastPos=null;distance=0;blocksMined=0;mobsHit=0;harvested=0;fishActions=0;lastCounts.clear();gained.clear();}
    public void tick(MinecraftClient c){
        if(c.player==null)return;
        if(lastPos!=null){double d=c.player.getEntityPos().distanceTo(lastPos); if(d<20)distance+=d;}
        lastPos=c.player.getEntityPos();
        Map<String,Integer> now=new HashMap<>();
        for(int i=0;i<c.player.getInventory().size();i++){
            var s=c.player.getInventory().getStack(i);if(s.isEmpty())continue;
            String id=Registries.ITEM.getId(s.getItem()).toString();now.merge(id,s.getCount(),Integer::sum);
        }
        for(var e:now.entrySet()){int old=lastCounts.getOrDefault(e.getKey(),e.getValue()); if(e.getValue()>old)gained.merge(e.getKey(),e.getValue()-old,Integer::sum);}
        lastCounts.clear();lastCounts.putAll(now);
    }
    public void blockMined(){blocksMined++;}
    public void mobHit(){mobsHit++;}
    public void harvested(){harvested++;}
    public void fished(){fishActions++;}
    public int blocksMined(){return blocksMined;}
    public int mobsHit(){return mobsHit;}
    public double distance(){return distance;}
    public long seconds(){return Math.max(1,(System.currentTimeMillis()-startMs)/1000);}
    public double perHour(String query){
        String q=WorldScanner.normalize(query);int n=0;
        for(var e:gained.entrySet())if(e.getKey().contains(q))n+=e.getValue();
        return n*3600.0/seconds();
    }
    public String eta(String query,int remaining){
        double r=perHour(query); if(r<0.1)return "learning rate";
        long sec=(long)Math.ceil(remaining/(r/3600.0)); return (sec/60)+"m "+(sec%60)+"s";
    }
    public String summary(){return "time "+seconds()/60+"m | mined "+blocksMined+" | hits "+mobsHit+" | walk "+(int)distance+"m";}
}
