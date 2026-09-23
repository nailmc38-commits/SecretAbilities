package com.survivalutils;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public final class SurvMegaState {
    private SurvMegaState() {}

    public static int armorIntegrity(PlayerEntity p) {
        int sum=0,count=0;
        for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}) {
            ItemStack s=p.getEquippedStack(slot);
            if(s==null||s.isEmpty())continue;
            count++;
            sum+=s.isDamageable()?InventoryUtil.durabilityPercent(s):100;
        }
        return count==0?100:(int)Math.round(sum/(double)count);
    }

    public static String armorName(PlayerEntity p) {
        String material="UNARMORED";
        for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.CHEST,EquipmentSlot.HEAD,EquipmentSlot.LEGS,EquipmentSlot.FEET}) {
            ItemStack s=p.getEquippedStack(slot);
            if(s==null||s.isEmpty())continue;
            String id=Registries.ITEM.getId(s.getItem()).getPath();
            if(id.startsWith("netherite_")) return "NETHERITE";
            if(id.startsWith("diamond_")) material="DIAMOND";
            else if(id.startsWith("iron_")&&"UNARMORED".equals(material)) material="IRON";
            else if(id.startsWith("golden_")&&"UNARMORED".equals(material)) material="GOLD";
            else if(id.startsWith("chainmail_")&&"UNARMORED".equals(material)) material="CHAIN";
            else if(id.startsWith("leather_")&&"UNARMORED".equals(material)) material="LEATHER";
        }
        return material;
    }
}
