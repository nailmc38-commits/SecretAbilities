package com.survivalutils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class WarningManager {
    public enum Severity { INFO, CAUTION, DANGER, CRITICAL }
    public record Warning(String key,String text,Severity severity,int priority,long expiresAt) {}
    private record Candidate(String key,String text,Severity severity,int priority){}

    private Warning active;
    private long lastActionbarAt;
    private String lastActionbarKey="";
    private int ticks;
    private float lastHealth=-1;
    private int previousPing=-1;
    private Warning testOverride;

    public Warning active() {
        if(testOverride!=null && System.currentTimeMillis()<=testOverride.expiresAt()) return testOverride;
        if(testOverride!=null) testOverride=null;
        if(active!=null && System.currentTimeMillis()>active.expiresAt()) active=null;
        return active;
    }

    public void test(Severity severity) {
        long now=System.currentTimeMillis();
        int priority=switch(severity){case CRITICAL->100;case DANGER->90;case CAUTION->60;case INFO->20;};
        testOverride=new Warning("system_test","SYSTEM TEST // "+severity,severity,priority,now+4000);
    }

    public void tick(MinecraftClient client) {
        if(client==null||client.player==null||client.world==null){active=null;return;}
        ExoLinkData.load();
        if(++ticks%3!=0)return;

        var p=client.player;
        List<Candidate> found=new ArrayList<>();
        ThreatAnalyzer.Snapshot threat=ThreatAnalyzer.analyze(client);
        CombatAdvisor.Snapshot advisor=CombatAdvisor.current();

        add(found,"fire",p.isOnFire(),"ON FIRE // REACH WATER OR FIRE RESISTANCE",Severity.CRITICAL,100);
        add(found,"drowning",p.getAir()<70,"AIR CRITICAL // SURFACE NOW",Severity.CRITICAL,100);
        add(found,"critical_health",p.getHealth()<=5f,"CRITICAL HEALTH // DISENGAGE NOW",Severity.CRITICAL,99);
        add(found,"low_health",p.getHealth()<=9f,"LOW HEALTH // HEAL OR DISENGAGE",Severity.DANGER,88);

        add(found,"dangerous_fall",p.fallDistance>=7f&&p.getVelocity().y<-0.42,
                "DANGEROUS FALL // "+String.format(Locale.ROOT,"%.1fm",p.fallDistance)+" // CLUTCH "+SurvivalUtilsClient.AUTO_CLUTCH.status(),
                Severity.CRITICAL,97);

        add(found,"water_bucket_missing_fall",
                p.fallDistance>=5f&&p.getVelocity().y<-0.35&&!InventoryUtil.hasWaterBucket(p),
                "FALLING // NO WATER BUCKET DETECTED",Severity.DANGER,90);

        add(found,"lava",lavaNearby(client,2),
                "LAVA VERY CLOSE // SLOW DOWN",Severity.DANGER,84);

        boolean nether=client.world.getRegistryKey().getValue().getPath().contains("nether");
        add(found,"nether_lava",nether&&lavaNearby(client,4),
                "NETHER LAVA HAZARD // KEEP ESCAPE ROUTE",Severity.CAUTION,61);

        CreeperEntity creeper=nearest(client,CreeperEntity.class,9);
        if(creeper!=null) {
            double d=Math.sqrt(p.squaredDistanceTo(creeper));
            add(found,"creeper",true,"CREEPER // "+fmt(d)+" // CREATE DISTANCE",
                    d<=4?Severity.CRITICAL:Severity.DANGER,d<=4?96:82);
        }

        SkeletonEntity skel=nearest(client,SkeletonEntity.class,14);
        if(skel!=null) add(found,"skeleton",true,"RANGED HOSTILE // SKELETON // "+fmt(Math.sqrt(p.squaredDistanceTo(skel))),
                Severity.CAUTION,54);

        List<HostileEntity> hostiles=client.world.getEntitiesByClass(
                HostileEntity.class,p.getBoundingBox().expand(18),Entity::isAlive);
        add(found,"hostile_close",hostiles.stream().anyMatch(h->p.squaredDistanceTo(h)<=16),
                "HOSTILE WITHIN 4m",Severity.DANGER,78);
        add(found,"multiple_hostiles",hostiles.size()>=4,
                "MULTIPLE HOSTILES // "+hostiles.size()+" NEARBY",Severity.DANGER,76);
        add(found,"surrounded",hostiles.size()>=5&&EscapeVector.calculate(client).clearBlocks()<5,
                "SURROUNDED // ESCAPE VECTOR LIMITED",Severity.CRITICAL,93);

        HostileEntity targeting=hostiles.stream()
                .filter(h->h.getTarget()==p)
                .min(Comparator.comparingDouble(p::squaredDistanceTo)).orElse(null);
        if(targeting!=null) add(found,"mob_targeting_you",true,
                "TARGETED // "+targeting.getName().getString()+" // "+fmt(Math.sqrt(p.squaredDistanceTo(targeting))),
                Severity.CAUTION,63);

        ProjectileEntity projectile=client.world.getEntitiesByClass(
                ProjectileEntity.class,p.getBoundingBox().expand(18),Entity::isAlive)
                .stream()
                .filter(pr->pr.getOwner()!=p)
                .filter(pr->isApproaching(p,pr))
                .min(Comparator.comparingDouble(p::squaredDistanceTo)).orElse(null);
        if(projectile!=null) {
            double pd=Math.sqrt(p.squaredDistanceTo(projectile));
            add(found,"projectile_near",true,
                    "PROJECTILE APPROACHING // "+fmt(pd),pd<=5?Severity.DANGER:Severity.CAUTION,pd<=5?85:66);
        }

        TntEntity tnt=nearest(client,TntEntity.class,9);
        if(tnt!=null) {
            double td=Math.sqrt(p.squaredDistanceTo(tnt));
            add(found,"explosion_near",true,
                    "PRIMED TNT // "+fmt(td)+" // FUSE "+String.format(Locale.ROOT,"%.1fs",tnt.getFuse()/20.0),
                    td<=5?Severity.CRITICAL:Severity.DANGER,td<=5?96:86);
        }

        HostileEntity behindHostile=hostiles.stream()
                .filter(h->isBehind(p,h))
                .min(Comparator.comparingDouble(p::squaredDistanceTo)).orElse(null);
        if(behindHostile!=null) add(found,"hostile_behind",true,
                "HOSTILE BEHIND // "+behindHostile.getName().getString()+" // "+fmt(Math.sqrt(p.squaredDistanceTo(behindHostile))),
                Severity.DANGER,79);

        var players=client.world.getPlayers().stream()
                .filter(o->o!=p&&o.squaredDistanceTo(p)<=32*32).toList();

        PlayerEntity nearestPlayer=players.stream().min(Comparator.comparingDouble(p::squaredDistanceTo)).orElse(null);
        if(nearestPlayer!=null) {
            double d=Math.sqrt(p.squaredDistanceTo(nearestPlayer));
            ThreatMemoryManager.Contact mem=ThreatMemoryManager.get(nearestPlayer);
            boolean friend=mem!=null&&"FRIEND".equals(mem.tag);
            if(!friend) {
                add(found,"player_near",d<=20,"PLAYER NEAR // "+nearestPlayer.getGameProfile().name()+" // "+fmt(d),
                        Severity.CAUTION,58);
                add(found,"player_very_close",d<=7,"PLAYER VERY CLOSE // "+nearestPlayer.getGameProfile().name()+" // "+fmt(d),
                        Severity.DANGER,86);
                add(found,"player_behind",d<=14&&isBehind(p,nearestPlayer),
                        "PLAYER BEHIND // "+nearestPlayer.getGameProfile().name(),Severity.DANGER,83);
            }
            if(mem!=null&&"HOSTILE".equals(mem.tag)) {
                add(found,"known_hostile",true,"KNOWN HOSTILE // "+nearestPlayer.getGameProfile().name()+" // "+fmt(d),
                        Severity.DANGER,87);
            }
        }

        long nonFriends=players.stream().filter(o->{
            ThreatMemoryManager.Contact m=ThreatMemoryManager.get(o);
            return m==null||!"FRIEND".equals(m.tag);
        }).count();
        add(found,"multiple_players",nonFriends>=3,"MULTIPLE PLAYERS // "+nonFriends+" UNKNOWN/HOSTILE",
                Severity.DANGER,81);

        if(client.getNetworkHandler()!=null) {
            for(var info:client.getNetworkHandler().getPlayerList()) {
                if(info.getGameMode()!=GameMode.SPECTATOR)continue;
                String name=info.getProfile().name();
                if(name==null||name.equalsIgnoreCase(p.getGameProfile().name()))continue;
                add(found,"spectator",true,"SPECTATOR DETECTED // "+name,Severity.DANGER,85);
                break;
            }
        }

        add(found,"no_totem",
                threat.level().ordinal()>=ThreatAnalyzer.Level.HIGH.ordinal()
                        && InventoryUtil.count(p,"totem_of_undying")<=0,
                "HIGH THREAT // NO TOTEM",Severity.DANGER,80);

        armorWarning(found,p,EquipmentSlot.HEAD,"helmet_critical","HELMET");
        armorWarning(found,p,EquipmentSlot.CHEST,"chest_critical","CHESTPLATE");
        armorWarning(found,p,EquipmentSlot.LEGS,"legs_critical","LEGGINGS");
        armorWarning(found,p,EquipmentSlot.FEET,"boots_critical","BOOTS");

        int armorIntegrity=SurvMegaState.armorIntegrity(p);
        add(found,"armor_critical",armorIntegrity<=22&&p.getArmor()>0,
                "ARMOR INTEGRITY CRITICAL // "+armorIntegrity+"%",Severity.CRITICAL,92);
        add(found,"power_core_critical",armorIntegrity<=15&&p.getArmor()>0,
                "POWER CORE // CRITICAL LOAD",Severity.CRITICAL,94);

        ItemStack held=p.getMainHandStack();
        add(found,"held_item_critical",held.isDamageable()&&InventoryUtil.durabilityPercent(held)<=7,
                held.getName().getString()+" // 7% OR LESS",Severity.DANGER,72);

        ItemStack shield=InventoryUtil.findShield(p);
        add(found,"shield_critical",!shield.isEmpty()&&shield.isDamageable()&&InventoryUtil.durabilityPercent(shield)<=10,
                "SHIELD CRITICAL // "+InventoryUtil.durabilityPercent(shield)+"%",Severity.DANGER,70);

        ItemStack chest=p.getEquippedStack(EquipmentSlot.CHEST);
        if(chest.isOf(Items.ELYTRA)) {
            add(found,"elytra_critical",chest.isDamageable()&&InventoryUtil.durabilityPercent(chest)<=12,
                    "ELYTRA CRITICAL // "+InventoryUtil.durabilityPercent(chest)+"%",Severity.CRITICAL,91);
            add(found,"low_rockets_flying",!p.isOnGround()&&InventoryUtil.count(p,"firework_rocket")<=4,
                    "FLIGHT // LOW ROCKETS // "+InventoryUtil.count(p,"firework_rocket"),Severity.DANGER,75);
        }

        add(found,"low_hunger",p.getHungerManager().getFoodLevel()<=6,"LOW FOOD // EAT SOON",Severity.CAUTION,40);
        add(found,"inventory_full",InventoryUtil.freeSlots(p)<=2,
                "INVENTORY // "+InventoryUtil.freeSlots(p)+" FREE SLOTS",Severity.CAUTION,37);

        long t=Math.floorMod(client.world.getTimeOfDay(),24000L);
        int nightSec=t<13000?(int)Math.round((13000L-t)/20.0):999;
        add(found,"night",nightSec<=60,"NIGHT IN "+nightSec+"s",Severity.INFO,20);
        add(found,"low_light",client.world.getLightLevel(p.getBlockPos())<=3,
                "LOW LIGHT // SPAWN RISK",Severity.INFO,18);

        float combined=p.getHealth()+p.getAbsorptionAmount();
        if(lastHealth<0)lastHealth=combined;
        float loss=lastHealth-combined;
        add(found,"sudden_damage",loss>=5f,"HEAVY IMPACT // -"+String.format(Locale.ROOT,"%.1f",loss)+" HP",
                Severity.CRITICAL,95);
        lastHealth=combined;

        add(found,"combat_undergeared",
                advisor.opponentType().equals("PLAYER")&&p.getArmor()<=8,
                "PLAYER CONTACT // ARMOR LOADOUT WEAK",Severity.DANGER,73);

        add(found,"losing_advantage",
                advisor.opponentType().equals("PLAYER")&&advisor.estimatedWinPercent()>=0&&advisor.estimatedWinPercent()<=38,
                "COMBAT ADVANTAGE LOST // EST "+advisor.estimatedWinPercent()+"%",Severity.DANGER,89);

        EscapeVector.Result escape=advisor.escape();
        add(found,"escape_blocked",
                (advisor.opponentType().equals("PLAYER")||!hostiles.isEmpty())&&escape.clearBlocks()<3,
                "ESCAPE VECTOR BLOCKED // CREATE SPACE",Severity.CRITICAL,90);

        if(client.getNetworkHandler()!=null) {
            var info=client.getNetworkHandler().getPlayerListEntry(p.getUuid());
            if(info!=null) {
                int ping=info.getLatency();
                if(previousPing>=0) add(found,"ping_spike",ping>=180&&ping-previousPing>=90,
                        "PING SPIKE // "+ping+"ms",Severity.CAUTION,47);
                previousPing=ping;
            }
        }

        ExoScriptEngine.ScriptAlert script=ExoScriptEngine.active();
        if(script!=null&&script.action()==ExoScriptEngine.Action.WARN){
            found.add(new Candidate("exo_script",script.text(),script.severity(),script.priority()));
        }

        Candidate best=found.stream().max(Comparator.comparingInt(Candidate::priority)).orElse(null);
        if(best==null){active=null;return;}

        long now=System.currentTimeMillis();
        active=new Warning(best.key,best.text,best.severity,best.priority,now+500);

        long repeat=switch(best.severity){
            case CRITICAL->1800;
            case DANGER->2800;
            case CAUTION->5000;
            case INFO->9000;
        };

        if(!best.key.equals(lastActionbarKey)||now-lastActionbarAt>=repeat) {
            Formatting color=switch(best.severity){
                case CRITICAL,DANGER->Formatting.RED;
                case CAUTION->Formatting.GOLD;
                case INFO->Formatting.YELLOW;
            };
            p.sendMessage(Text.literal("[EXO // LINK] "+best.text).formatted(color,Formatting.BOLD),true);
            lastActionbarAt=now;
            lastActionbarKey=best.key;
        }
    }

    private void armorWarning(List<Candidate> out,PlayerEntity p,EquipmentSlot slot,String key,String label) {
        ItemStack s=p.getEquippedStack(slot);
        if(!s.isEmpty()&&s.isDamageable()) {
            int pct=InventoryUtil.durabilityPercent(s);
            add(out,key,pct<=10,label+" CRITICAL // "+pct+"%",Severity.DANGER,69);
        }
    }

    private void add(List<Candidate> out,String key,boolean condition,String text,Severity severity,int priority) {
        if(condition&&ExoLinkData.warning(key))out.add(new Candidate(key,text,severity,priority));
    }

    private boolean lavaNearby(MinecraftClient c,int radius) {
        BlockPos base=c.player.getBlockPos();
        for(int y=-1;y<=1;y++)for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)
            if(c.world.getFluidState(base.add(x,y,z)).isIn(FluidTags.LAVA))return true;
        return false;
    }

    private boolean isApproaching(PlayerEntity player,ProjectileEntity projectile) {
        Vec3d toPlayer=new Vec3d(player.getX()-projectile.getX(),player.getY()+1.0-projectile.getY(),player.getZ()-projectile.getZ());
        Vec3d velocity=projectile.getVelocity();
        if(velocity.lengthSquared()<0.001||toPlayer.lengthSquared()<0.01)return false;
        return velocity.normalize().dotProduct(toPlayer.normalize())>0.72;
    }

    private boolean isBehind(PlayerEntity self,Entity other) {
        double dx=other.getX()-self.getX(), dz=other.getZ()-self.getZ();
        double angle=Math.toDegrees(Math.atan2(dz,dx))-90.0;
        double diff=wrap(angle-self.getYaw());
        return Math.abs(diff)>115;
    }

    private double wrap(double a){while(a>180)a-=360;while(a<-180)a+=360;return a;}

    private <T extends Entity> T nearest(MinecraftClient c,Class<T> type,double radius) {
        return c.world.getEntitiesByClass(type,c.player.getBoundingBox().expand(radius),Entity::isAlive)
                .stream().min(Comparator.comparingDouble(c.player::squaredDistanceTo)).orElse(null);
    }

    private String fmt(double d){return String.format(Locale.ROOT,"%.1fm",d);}
}
