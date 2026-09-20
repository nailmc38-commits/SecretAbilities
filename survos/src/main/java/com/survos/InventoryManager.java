package com.survos;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Locale;
import java.util.OptionalInt;

public final class InventoryManager {
    private InventoryManager(){}

    public static int freeSlots(PlayerEntity p) {
        int n=0;
        for(int i=0;i<p.getInventory().size();i++) if(p.getInventory().getStack(i).isEmpty()) n++;
        return n;
    }

    public static int count(PlayerEntity p,String query) {
        if(query==null||query.isBlank()) return 0;
        String q=WorldScanner.normalize(query);
        int n=0;
        for(int i=0;i<p.getInventory().size();i++){
            ItemStack s=p.getInventory().getStack(i);
            if(!s.isEmpty() && id(s).contains(q)) n+=s.getCount();
        }
        return n;
    }

    public static String id(ItemStack s){ return Registries.ITEM.getId(s.getItem()).toString(); }

    public static int durabilityPercent(ItemStack s){
        if(s==null||s.isEmpty()||!s.isDamageable()) return 100;
        return (int)Math.round((s.getMaxDamage()-s.getDamage())*100.0/Math.max(1,s.getMaxDamage()));
    }

    public static boolean selectBestTool(MinecraftClient c, BlockState state, int protectBelowPercent) {
        if(c.player==null) return false;
        PlayerEntity p=c.player;
        int best=-1; float speed=1.0f;
        for(int i=0;i<p.getInventory().size();i++){
            ItemStack s=p.getInventory().getStack(i);
            if(s.isEmpty() || durabilityPercent(s)<=protectBelowPercent) continue;
            float v=s.getItem().getMiningSpeed(s,state);
            if(v>speed){speed=v;best=i;}
        }
        return best>=0 && selectInventoryIndex(c,best,0);
    }

    public static boolean selectItemByKeyword(MinecraftClient c, String keyword, int preferredHotbar) {
        if (keyword == null || keyword.isBlank()) return false;
        String q = WorldScanner.normalize(keyword);
        return selectMatching(c, s -> !s.isEmpty() && id(s).contains(q), preferredHotbar);
    }

    public static boolean selectFishingRod(MinecraftClient c){
        return selectMatching(c,s->s.isOf(Items.FISHING_ROD),4);
    }

    public static boolean selectBreedingItem(MinecraftClient c, AnimalEntity a){
        return selectMatching(c,a::isBreedingItem,5);
    }

    public static boolean selectFood(MinecraftClient c, boolean protectRare){
        return selectMatching(c,s->{
            if(s.isEmpty()) return false;
            String id=id(s);
            if(protectRare && (id.contains("golden_apple")||id.contains("enchanted_golden_apple"))) return false;
            return s.getItem().getUseAction(s)== UseAction.EAT;
        },7);
    }

    private interface StackTest{boolean ok(ItemStack s);}

    private static boolean selectMatching(MinecraftClient c,StackTest test,int preferredHotbar) {
        if(c.player==null) return false;
        for(int i=0;i<9;i++) if(test.ok(c.player.getInventory().getStack(i))){
            c.player.getInventory().setSelectedSlot(i); return true;
        }
        for(int i=9;i<c.player.getInventory().size();i++) if(test.ok(c.player.getInventory().getStack(i))){
            return selectInventoryIndex(c,i,preferredHotbar);
        }
        return false;
    }

    public static boolean selectInventoryIndex(MinecraftClient c,int invIndex,int hotbarSlot) {
        if(c.player==null||c.interactionManager==null) return false;
        PlayerEntity p=c.player;
        if(invIndex>=0&&invIndex<9){p.getInventory().setSelectedSlot(invIndex);return true;}
        OptionalInt slot=p.playerScreenHandler.getSlotIndex(p.getInventory(),invIndex);
        if(slot.isEmpty()) return false;
        c.interactionManager.clickSlot(p.playerScreenHandler.syncId,slot.getAsInt(),hotbarSlot, SlotActionType.SWAP,p);
        p.getInventory().setSelectedSlot(hotbarSlot);
        return true;
    }

    public static void applyLoadout(MinecraftClient c,String profile) {
        if(c.player==null) return;
        String[][] layouts={
                {"combat","sword","pickaxe","axe","bow","blocks","food","water_bucket","shield","totem"},
                {"mining","sword","pickaxe","pickaxe","shovel","blocks","torch","water_bucket","food","totem"},
                {"building","sword","pickaxe","axe","shovel","blocks","blocks","scaffolding","food","water_bucket"}
        };
        String[] chosen=layouts[1];
        String p=profile==null?"":profile.toLowerCase(Locale.ROOT);
        for(String[] a:layouts) if(a[0].equals(p)) chosen=a;
        for(int slot=0;slot<9;slot++){
            String need=chosen[slot+1];
            int idx=find(c.player,need);
            if(idx>=0 && idx!=slot) selectInventoryIndex(c,idx,slot);
        }
    }

    private static int find(PlayerEntity p,String keyword){
        for(int i=0;i<p.getInventory().size();i++){
            ItemStack s=p.getInventory().getStack(i);
            if(s.isEmpty()) continue;
            String id=id(s);
            if(keyword.equals("food") && s.getItem().getUseAction(s)==UseAction.EAT) return i;
            if(keyword.equals("blocks") && s.getCount()>=16 && id.contains("block")) return i;
            if(id.contains(keyword)) return i;
        }
        return -1;
    }
}
