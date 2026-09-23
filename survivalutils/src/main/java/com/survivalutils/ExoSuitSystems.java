package com.survivalutils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import java.util.Locale;
import java.util.UUID;

public final class ExoSuitSystems {
    public enum VisorMode { AUTO, STANDARD, COMBAT, MINING, NIGHT, FLIGHT }
    private static VisorMode mode=VisorMode.AUTO;
    private static String lockedThreatUuid="";

    public record Flight(double speed,double descentRate,int altitude,int rockets,int elytraDurability,boolean pullUp){}
    public record Readiness(String profile,int percent,String missing){}

    private ExoSuitSystems(){}

    public static VisorMode effectiveMode(MinecraftClient c) {
        if(mode!=VisorMode.AUTO)return mode;
        if(c==null||c.player==null||c.world==null)return VisorMode.STANDARD;

        ItemStack chest=c.player.getEquippedStack(EquipmentSlot.CHEST);
        if(chest.isOf(Items.ELYTRA)&&!c.player.isOnGround())return VisorMode.FLIGHT;

        CombatAdvisor.Snapshot a=CombatAdvisor.current();
        if(a!=null&&!"NONE".equals(a.opponentType()))return VisorMode.COMBAT;

        String held=Registries.ITEM.getId(c.player.getMainHandStack().getItem()).getPath();
        if(held.contains("pickaxe"))return VisorMode.MINING;

        if(c.world.getLightLevel(c.player.getBlockPos())<=4)return VisorMode.NIGHT;
        return VisorMode.STANDARD;
    }

    public static void cycleMode() {
        mode=switch(mode){
            case AUTO->VisorMode.STANDARD;
            case STANDARD->VisorMode.COMBAT;
            case COMBAT->VisorMode.MINING;
            case MINING->VisorMode.NIGHT;
            case NIGHT->VisorMode.FLIGHT;
            case FLIGHT->VisorMode.AUTO;
        };
    }

    public static VisorMode configuredMode(){return mode;}

    public static Flight flight(MinecraftClient c) {
        if(c==null||c.player==null)return new Flight(0,0,0,0,0,false);
        var p=c.player;
        double vx=p.getVelocity().x,vy=p.getVelocity().y,vz=p.getVelocity().z;
        double speed=Math.sqrt(vx*vx+vz*vz)*20.0;
        double descent=vy*20.0;
        int rockets=InventoryUtil.count(p,"firework_rocket");
        ItemStack chest=p.getEquippedStack(EquipmentSlot.CHEST);
        int dura=chest.isOf(Items.ELYTRA)&&chest.isDamageable()?InventoryUtil.durabilityPercent(chest):0;
        boolean pull=!p.isOnGround()&&descent<-12&&p.getBlockY()<80;
        return new Flight(speed,descent,p.getBlockY(),rockets,dura,pull);
    }

    public static Readiness readiness(MinecraftClient c) {
        if(c==null||c.player==null||c.world==null)return new Readiness("UNKNOWN",0,"No world");
        PlayerEntity p=c.player;
        String dim=c.world.getRegistryKey().getValue().getPath();
        String held=Registries.ITEM.getId(p.getMainHandStack().getItem()).getPath();

        String profile;
        if("COMBAT".equals(effectiveMode(c).name()))profile="PVP";
        else if(dim.contains("nether"))profile="NETHER";
        else if(held.contains("pickaxe"))profile="MINING";
        else profile="SURVIVAL";

        int score=0,max=0;
        StringBuilder missing=new StringBuilder();

        max+=20; score+=Math.min(20,p.getArmor());
        max+=20; score+=Math.min(20,p.getHungerManager().getFoodLevel());
        max+=20; score+=(int)Math.min(20,p.getHealth());

        if("PVP".equals(profile)) {
            max+=20;
            if(InventoryUtil.count(p,"totem_of_undying")>0)score+=20; else addMissing(missing,"totem");
            max+=10;
            if(p.getMainHandStack().getName().getString().toLowerCase(Locale.ROOT).contains("sword")
                    ||p.getMainHandStack().getName().getString().toLowerCase(Locale.ROOT).contains("axe"))score+=10;
            else addMissing(missing,"weapon");
        } else if("NETHER".equals(profile)) {
            max+=15;
            if(InventoryUtil.foodCount(p)>=8)score+=15; else addMissing(missing,"food");
            max+=15;
            if(InventoryUtil.count(p,"fire_resistance")>0)score+=15; else addMissing(missing,"fire-res");
        } else if("MINING".equals(profile)) {
            max+=20;
            if(!InventoryUtil.findBestPickaxe(p).isEmpty())score+=20; else addMissing(missing,"pickaxe");
            max+=10;
            if(InventoryUtil.count(p,"torch")>=16)score+=10; else addMissing(missing,"torches");
        } else {
            max+=15;
            if(InventoryUtil.foodCount(p)>=8)score+=15; else addMissing(missing,"food");
            max+=15;
            if(InventoryUtil.hasWaterBucket(p))score+=15; else addMissing(missing,"water");
        }

        int pct=max==0?0:(int)Math.round(score*100.0/max);
        return new Readiness(profile,Math.max(0,Math.min(100,pct)),missing.length()==0?"READY":missing.toString());
    }

    private static void addMissing(StringBuilder b,String value) {
        if(b.length()>0)b.append(", ");
        b.append(value);
    }

    public static void lockThreat(PlayerEntity p) {
        lockedThreatUuid=p==null?"":p.getUuidAsString();
    }

    public static void clearThreatLock(){lockedThreatUuid="";}
    public static boolean hasThreatLock(){return !lockedThreatUuid.isBlank();}

    public static PlayerEntity lockedThreat(MinecraftClient c) {
        if(c==null||c.world==null||lockedThreatUuid.isBlank())return null;
        try{
            UUID id=UUID.fromString(lockedThreatUuid);
            var e=c.world.getEntity(id);
            return e instanceof PlayerEntity p?p:null;
        }catch(Exception e){return null;}
    }
}
