package com.survivalutils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class CombatAdvisor {
    public record Snapshot(
            String recommendation,
            String opponent,
            String opponentType,
            int estimatedWinPercent,
            String confidence,
            List<String> details,
            EscapeVector.Result escape
    ) {}

    private static Snapshot current = new Snapshot(
            "STABLE","NONE","NONE",-1,"LOW",List.of("No immediate opponent."),new EscapeVector.Result("NONE",0,0,""));

    private static int ticks;

    private CombatAdvisor() {}

    public static void tick(MinecraftClient client) {
        if (!ExoLinkData.SETTINGS.advisor || client==null || client.player==null || client.world==null) return;
        if(++ticks%5!=0)return;
        current=analyze(client);
    }

    public static Snapshot current(){ return current; }

    public static Snapshot analyze(MinecraftClient client) {
        PlayerEntity self=client.player;
        PlayerEntity playerTarget=findPlayerTarget(client);
        EscapeVector.Result escape=EscapeVector.calculate(client);

        if(playerTarget!=null) return analyzePlayer(client,self,playerTarget,escape);

        HostileEntity mob=client.world.getEntitiesByClass(
                HostileEntity.class,self.getBoundingBox().expand(18),EntityAlive::test)
                .stream().min(Comparator.comparingDouble(self::squaredDistanceTo)).orElse(null);

        if(mob!=null) {
            double d=Math.sqrt(self.squaredDistanceTo(mob));
            String rec=self.getHealth()<=8 || d<3 && self.getHealth()<12 ? "DISENGAGE" : "ENGAGE";
            List<String> lines=new ArrayList<>();
            lines.add("Opponent // "+mob.getName().getString());
            lines.add("Distance // "+String.format(Locale.ROOT,"%.1fm",d));
            lines.add("Your HP // "+String.format(Locale.ROOT,"%.1f",self.getHealth()));
            lines.add("Armor // "+self.getArmor()+"/20");
            lines.add("Escape // "+escape.direction()+" // "+escape.clearBlocks()+" blocks");
            return new Snapshot(rec,mob.getName().getString(),"MOB",-1,"HIGH",lines,escape);
        }

        return new Snapshot("STABLE","NONE","NONE",-1,"HIGH",
                List.of("No immediate opponent.","Escape path // "+escape.direction()+" // "+escape.clearBlocks()+" blocks"),escape);
    }

    private static Snapshot analyzePlayer(MinecraftClient client, PlayerEntity self, PlayerEntity enemy, EscapeVector.Result escape) {
        ThreatMemoryManager.Contact memory=ThreatMemoryManager.get(enemy);

        double your=power(self,true);
        double their=power(enemy,false);
        double baseYour=your;
        double baseTheir=their;

        int friends=0;
        List<String> friendNames=new ArrayList<>();
        for(PlayerEntity p:client.world.getPlayers()) {
            if(p==self||p==enemy||p.squaredDistanceTo(self)>18*18) continue;
            ThreatMemoryManager.Contact c=ThreatMemoryManager.get(p);
            if(c!=null && "FRIEND".equals(c.tag)) {
                friends++;
                friendNames.add(p.getGameProfile().name());
                your += 12.0 + p.getArmor()*0.6 + p.getHealth()*0.35;
            }
        }

        int extraThreats=0;
        for(PlayerEntity p:client.world.getPlayers()) {
            if(p==self||p==enemy||p.squaredDistanceTo(self)>18*18) continue;
            ThreatMemoryManager.Contact c=ThreatMemoryManager.get(p);
            if(c==null || !"FRIEND".equals(c.tag)) {
                extraThreats++;
                their += 8.0;
            }
        }

        double ratio=your/(Math.max(1.0,your+their));
        int estimate=(int)Math.round(100.0/(1.0+Math.exp(-8.0*(ratio-0.5))));
        estimate=Math.max(5,Math.min(95,estimate));

        String confidence="MEDIUM";
        int observed=0;
        if(memory!=null) {
            observed=memory.observedItems.size();
            if(memory.combatEncounters>=3 && observed>=6) confidence="HIGH";
            else if(memory.encounters<=1 && observed<=2) confidence="LOW";
        } else confidence="LOW";

        String rec;
        if(self.getHealth()<=6) rec="DISENGAGE";
        else if(estimate>=67 && extraThreats==0) rec="ENGAGE";
        else if(estimate<=40) rec="DISENGAGE";
        else rec="CAUTION";

        ArrayList<String> lines=new ArrayList<>();
        lines.add("Opponent // "+enemy.getGameProfile().name());
        lines.add("Distance // "+String.format(Locale.ROOT,"%.1fm",Math.sqrt(self.squaredDistanceTo(enemy))));
        lines.add("Your HP // "+String.format(Locale.ROOT,"%.1f",self.getHealth())+" // Armor "+self.getArmor()+"/20");
        lines.add("Their visible HP // "+String.format(Locale.ROOT,"%.1f",enemy.getHealth())+" // Armor "+enemy.getArmor()+"/20");
        lines.add("Your weapon // "+itemName(self.getMainHandStack()));
        lines.add("Their visible weapon // "+itemName(enemy.getMainHandStack()));

        if(memory!=null) {
            lines.add("Memory tag // "+memory.tag+" // encounters "+memory.encounters);
            if(!memory.observedItems.isEmpty()) {
                lines.add("Observed inventory // "+Math.min(40,memory.observedItems.size())+" item types");
                String recent=memory.observedItems.entrySet().stream()
                        .sorted(java.util.Map.Entry.<String,Long>comparingByValue().reversed())
                        .limit(4)
                        .map(e->e.getKey().replace('_',' '))
                        .reduce((a,b)->a+", "+b).orElse("");
                if(!recent.isBlank()) lines.add("Recently observed // "+recent);
            }
        }

        double gearDelta=baseYour-baseTheir;
        lines.add("Gear/health edge // "+(gearDelta>6?"YOU":gearDelta<-6?"THEM":"EVEN"));
        if(friends>0) lines.add("Ally support // +"+friends+" // "+String.join(", ",friendNames));
        if(extraThreats>0) lines.add("Enemy pressure // +"+extraThreats+" nearby unknown/hostile");
        if(escape.clearBlocks()>=8) lines.add("Escape path // STRONG // "+escape.direction());
        else if(escape.clearBlocks()<=3) lines.add("Escape path // POOR // "+escape.direction());
        lines.add("Estimate // "+estimate+"% // "+confidence+" confidence");
        lines.add("Estimate factors // visible HP, armor, weapon, effects, observed history, nearby allies/threats");
        lines.add("Escape // "+escape.direction()+" // "+escape.clearBlocks()+" clear blocks // "+escape.reason());

        return new Snapshot(rec,enemy.getGameProfile().name(),"PLAYER",estimate,confidence,lines,escape);
    }

    private static PlayerEntity findPlayerTarget(MinecraftClient client) {
        PlayerEntity locked=ExoSuitSystems.lockedThreat(client);
        if(locked!=null&&locked!=client.player&&locked.squaredDistanceTo(client.player)<=64*64) return locked;
        if(client.targetedEntity instanceof PlayerEntity p && p!=client.player) return p;
        return client.world.getPlayers().stream()
                .filter(p->p!=client.player)
                .filter(p->p.squaredDistanceTo(client.player)<=16*16)
                .min(Comparator.comparingDouble(client.player::squaredDistanceTo))
                .orElse(null);
    }

    private static double power(PlayerEntity p, boolean self) {
        double v=p.getHealth()*1.6 + p.getAbsorptionAmount()*1.3 + p.getArmor()*2.2;
        v += weaponScore(p.getMainHandStack());
        if(p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) v+=7;
        if(p.getOffHandStack().isOf(Items.SHIELD)) v+=4;
        v += p.getStatusEffects().size()*1.5;
        v += armorDurabilityScore(p);
        return v;
    }

    private static double armorDurabilityScore(PlayerEntity p) {
        double total=0; int count=0;
        for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}) {
            ItemStack s=p.getEquippedStack(slot);
            if(s.isEmpty()) continue;
            count++;
            total += s.isDamageable() ? InventoryUtil.durabilityPercent(s) : 100;
        }
        return count==0?0:(total/count)*0.06;
    }

    private static double weaponScore(ItemStack s) {
        if(s==null||s.isEmpty())return 0;
        String id=Registries.ITEM.getId(s.getItem()).getPath();
        if(id.contains("netherite_sword"))return 12;
        if(id.contains("diamond_sword"))return 10;
        if(id.contains("sword"))return 7;
        if(id.contains("netherite_axe"))return 11;
        if(id.contains("diamond_axe"))return 9;
        if(id.contains("axe"))return 6;
        if(id.contains("mace"))return 12;
        if(id.contains("bow")||id.contains("crossbow"))return 7;
        return 2;
    }

    private static String itemName(ItemStack s) {
        if(s==null||s.isEmpty())return "empty";
        return s.getName().getString();
    }

    private static final class EntityAlive {
        static boolean test(HostileEntity e){ return e.isAlive(); }
    }
}
