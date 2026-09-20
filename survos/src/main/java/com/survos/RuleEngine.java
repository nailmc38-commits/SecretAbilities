package com.survos;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import java.util.*;

public final class RuleEngine {
    public enum Kind { HEALTH_BELOW, INVENTORY_FREE_AT_MOST, DURABILITY_BELOW, XP_AT_LEAST, ITEM_AT_LEAST }
    public static final class Rule {
        public Kind kind; public double value; public String item; public boolean enabled=true;
        public Rule(){}
        public Rule(Kind k,double v,String i){kind=k;value=v;item=i;}
        @Override public String toString(){return kind+" "+(item==null?"":item+" ")+value;}
    }
    private final List<Rule> rules=new ArrayList<>();
    public List<Rule> all(){return List.copyOf(rules);}
    public void clear(){rules.clear();}
    public void add(Rule r){rules.add(r);}
    public String triggered(MinecraftClient c){
        if(c.player==null)return null;
        for(Rule r:rules){
            if(!r.enabled)continue;
            boolean hit=switch(r.kind){
                case HEALTH_BELOW -> c.player.getHealth()<r.value*2.0;
                case INVENTORY_FREE_AT_MOST -> InventoryManager.freeSlots(c.player)<=r.value;
                case DURABILITY_BELOW -> {
                    ItemStack s=c.player.getMainHandStack(); yield s.isDamageable()&&InventoryManager.durabilityPercent(s)<r.value;
                }
                case XP_AT_LEAST -> c.player.experienceLevel>=r.value;
                case ITEM_AT_LEAST -> InventoryManager.count(c.player,r.item)>=r.value;
            };
            if(hit)return r.toString();
        }
        return null;
    }
}
