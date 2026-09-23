package com.survivalutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class ExoDroneSystem {
    public enum SatelliteState { DOCKED, DEPLOYING, TRACKING, RETURNING }
    public record Status(SatelliteState satelliteState,String target,double distance,boolean companionAlert,int ignored){}

    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final HashSet<String> IGNORED=new HashSet<>();
    private static Vec3d satellitePos;
    private static Vec3d companionPos;
    private static String manualTargetUuid="";
    private static SatelliteState state=SatelliteState.DOCKED;
    private static String currentTarget="";
    private static int ticks;
    private static boolean loaded;
    private static long phaseStart;

    private ExoDroneSystem(){}

    public static void tick(MinecraftClient client){
        if(client==null||client.player==null||client.world==null)return;
        ensureLoaded();

        Vec3d player=pos(client.player);
        double yaw=Math.toRadians(client.player.getYaw());
        Vec3d right=new Vec3d(Math.cos(yaw),0,Math.sin(yaw));
        Vec3d forward=new Vec3d(-Math.sin(yaw),0,Math.cos(yaw));

        Vec3d companionGoal=player
                .add(right.multiply(-1.25))
                .add(forward.multiply(-0.35))
                .add(0,1.75+Math.sin(System.currentTimeMillis()/380.0)*0.12,0);

        if(companionPos==null) companionPos=companionGoal;
        companionPos=approach(companionPos,companionGoal,0.24);

        PlayerEntity target=resolveTarget(client);

        Vec3d dockGoal=player
                .add(right.multiply(1.35))
                .add(forward.multiply(-0.20))
                .add(0,1.95+Math.sin(System.currentTimeMillis()/420.0)*0.10,0);

        if(satellitePos==null)satellitePos=dockGoal;

        if(!ExoLinkData.SETTINGS.satellite){
            target=null;
            state=SatelliteState.DOCKED;
            satellitePos=approach(satellitePos,dockGoal,0.34);
            currentTarget="";
        } else if(target!=null){
            String next=target.getUuidAsString();
            if(!next.equals(currentTarget)){
                currentTarget=next;
                state=SatelliteState.DEPLOYING;
                phaseStart=System.currentTimeMillis();
                ExoAudioManager.satelliteDeploy(client);
            }

            double orbit=System.currentTimeMillis()/600.0;
            Vec3d targetPos=pos(target).add(Math.cos(orbit)*1.45,2.35,Math.sin(orbit)*1.45);
            satellitePos=approach(satellitePos,targetPos,state==SatelliteState.DEPLOYING?0.46:0.28);

            if(satellitePos.distanceTo(targetPos)<0.65)state=SatelliteState.TRACKING;
        } else {
            if(!currentTarget.isBlank()&&state!=SatelliteState.RETURNING){
                state=SatelliteState.RETURNING;
                phaseStart=System.currentTimeMillis();
                ExoAudioManager.satelliteReturn(client);
            }

            satellitePos=approach(satellitePos,dockGoal,0.38);
            if(satellitePos.distanceTo(dockGoal)<0.35){
                state=SatelliteState.DOCKED;
                currentTarget="";
            }
        }

        if(++ticks%2==0){
            if(ExoLinkData.SETTINGS.companion) renderCompanion(client);
            if(ExoLinkData.SETTINGS.satellite) renderSatellite(client,target);
        }
    }

    private static void renderCompanion(MinecraftClient client){
        WarningManager.Warning warning=SurvivalUtilsClient.WARNINGS.active();
        boolean alert=warning!=null&&(warning.severity()==WarningManager.Severity.DANGER
                || warning.severity()==WarningManager.Severity.CRITICAL);

        double t=System.currentTimeMillis()/240.0;
        particle(client,ParticleTypes.GLOW,companionPos);
        particle(client,ParticleTypes.END_ROD,companionPos.add(Math.cos(t)*0.28,0,Math.sin(t)*0.28));
        particle(client,ParticleTypes.END_ROD,companionPos.add(-Math.cos(t)*0.28,0,-Math.sin(t)*0.28));
        particle(client,ParticleTypes.GLOW,companionPos.add(0,0.25,0));
        if(alert){
            particle(client,ParticleTypes.CRIT,companionPos.add(0,0.05,0));
            particle(client,ParticleTypes.CRIT,companionPos.add(0.20,0.12,0));
            particle(client,ParticleTypes.CRIT,companionPos.add(-0.20,0.12,0));
        }

        EscapeVector.Result e=CombatAdvisor.current().escape();
        if(alert&&e!=null&&!"NONE".equals(e.direction())){
            Vec3d dir=directionVector(e.direction());
            particle(client,ParticleTypes.END_ROD,companionPos.add(dir.multiply(0.55)));
            particle(client,ParticleTypes.END_ROD,companionPos.add(dir.multiply(0.85)));
        }
    }

    private static void renderSatellite(MinecraftClient client,PlayerEntity target){
        double t=System.currentTimeMillis()/190.0;
        particle(client,ParticleTypes.GLOW,satellitePos);
        particle(client,ParticleTypes.END_ROD,satellitePos.add(Math.cos(t)*0.34,0,Math.sin(t)*0.34));
        particle(client,ParticleTypes.END_ROD,satellitePos.add(-Math.cos(t)*0.34,0,-Math.sin(t)*0.34));
        particle(client,ParticleTypes.END_ROD,satellitePos.add(0,0.27,0));
        particle(client,ParticleTypes.PORTAL,satellitePos.add(0,-0.22,0));

        if(state==SatelliteState.DEPLOYING||state==SatelliteState.RETURNING){
            particle(client,ParticleTypes.PORTAL,satellitePos.add(0,-0.05,0));
        }

        if(target!=null&&state==SatelliteState.TRACKING){
            Vec3d targetEye=pos(target).add(0,1.25,0);
            Vec3d delta=targetEye.subtract(satellitePos);
            double len=delta.length();
            if(len>0.1){
                Vec3d step=delta.normalize().multiply(0.55);
                Vec3d p=satellitePos;
                for(double d=0.6;d<Math.min(len,7.0);d+=0.9){
                    p=p.add(step.multiply(0.9/0.55));
                    particle(client,ParticleTypes.END_ROD,p);
                }
            }
        }
    }

    private static PlayerEntity resolveTarget(MinecraftClient client){
        PlayerEntity manual=findByUuid(client,manualTargetUuid);
        if(manual!=null&&!ignored(manual))return manual;

        PlayerEntity locked=ExoSuitSystems.lockedThreat(client);
        if(locked!=null&&!ignored(locked))return locked;

        CombatAdvisor.Snapshot advisor=CombatAdvisor.current();
        if(advisor!=null&&"PLAYER".equals(advisor.opponentType())){
            for(PlayerEntity p:client.world.getPlayers()){
                if(p==client.player||ignored(p))continue;
                if(p.getGameProfile().name().equals(advisor.opponent()))return p;
            }
        }
        return null;
    }

    public static void track(PlayerEntity player,MinecraftClient client){
        if(player==null)return;
        manualTargetUuid=player.getUuidAsString();
        IGNORED.remove(manualTargetUuid);
        save();
        if(client!=null)ExoAudioManager.satelliteDeploy(client);
    }

    public static void clearTarget(){
        manualTargetUuid="";
    }

    public static void toggleIgnore(PlayerEntity player){
        if(player==null)return;
        String id=player.getUuidAsString();
        if(!IGNORED.add(id))IGNORED.remove(id);
        if(id.equals(manualTargetUuid))manualTargetUuid="";
        save();
    }

    public static boolean ignored(PlayerEntity player){
        return player!=null&&IGNORED.contains(player.getUuidAsString());
    }

    public static Status status(MinecraftClient client){
        PlayerEntity target=resolveTarget(client);
        double d=target==null||satellitePos==null?0:satellitePos.distanceTo(pos(target));
        WarningManager.Warning warning=SurvivalUtilsClient.WARNINGS.active();
        boolean alert=warning!=null&&(warning.severity()==WarningManager.Severity.DANGER
                ||warning.severity()==WarningManager.Severity.CRITICAL);
        return new Status(state,target==null?"NONE":target.getGameProfile().name(),d,alert,IGNORED.size());
    }

    private static PlayerEntity findByUuid(MinecraftClient client,String uuid){
        if(client==null||client.world==null||uuid==null||uuid.isBlank())return null;
        try{
            UUID id=UUID.fromString(uuid);
            for(PlayerEntity p:client.world.getPlayers())if(p.getUuid().equals(id))return p;
        }catch(Exception ignored){}
        return null;
    }

    private static Vec3d approach(Vec3d from,Vec3d to,double maxStep){
        Vec3d d=to.subtract(from);
        double len=d.length();
        if(len<=maxStep||len<0.0001)return to;
        return from.add(d.multiply(maxStep/len));
    }

    private static Vec3d pos(PlayerEntity p){
        return new Vec3d(p.getX(),p.getY(),p.getZ());
    }

    private static void particle(MinecraftClient client, net.minecraft.particle.ParticleEffect type,Vec3d p){
        client.world.addImportantParticleClient(type,true,p.x,p.y,p.z,0,0,0);
    }

    private static Vec3d directionVector(String dir){
        return switch(dir){
            case "N"->new Vec3d(0,0,-1);
            case "NE"->new Vec3d(0.707,0,-0.707);
            case "E"->new Vec3d(1,0,0);
            case "SE"->new Vec3d(0.707,0,0.707);
            case "S"->new Vec3d(0,0,1);
            case "SW"->new Vec3d(-0.707,0,0.707);
            case "W"->new Vec3d(-1,0,0);
            case "NW"->new Vec3d(-0.707,0,-0.707);
            default->Vec3d.ZERO;
        };
    }

    private static void ensureLoaded(){
        if(loaded)return;
        loaded=true;
        try{
            Path p=ExoLinkData.root().resolve("drone-ignore.json");
            if(Files.exists(p)){
                String[] arr=GSON.fromJson(Files.readString(p,StandardCharsets.UTF_8),String[].class);
                if(arr!=null)IGNORED.addAll(Arrays.asList(arr));
            }
        }catch(Exception ignored){}
    }

    private static void save(){
        try{
            Path p=ExoLinkData.root().resolve("drone-ignore.json");
            Files.writeString(p,GSON.toJson(IGNORED),StandardCharsets.UTF_8);
        }catch(Exception ignored){}
    }
}
