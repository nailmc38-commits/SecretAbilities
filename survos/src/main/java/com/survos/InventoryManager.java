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

    public static boolean has(PlayerEntity p, String query) {
        return count(p, query) > 0;
    }

    public static int countAny(PlayerEntity p, String... queries) {
        int total = 0;
        if (queries == null) return 0;
        for (String q : queries) total += count(p, q);
        return total;
    }

    public static int foodCount(PlayerEntity p) {
        int total = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem().getUseAction(s) == UseAction.EAT) {
                String id = id(s);
                if (!id.contains("rotten_flesh")
                        && !id.contains("spider_eye")
                        && !id.contains("pufferfish")) {
                    total += s.getCount();
                }
            }
        }
        return total;
    }

    public static String hotbarSummary(PlayerEntity p) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            if (i > 0) out.append(" | ");
            ItemStack s = p.getInventory().getStack(i);
            out.append(i + 1).append(":");
            if (s.isEmpty()) out.append("empty");
            else out.append(id(s).replace("minecraft:", "")).append(" x").append(s.getCount());
        }
        return out.toString();
    }

    public static String fullSummary(PlayerEntity p) {
        java.util.LinkedHashMap<String,Integer> counts = new java.util.LinkedHashMap<>();
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            counts.merge(id(s).replace("minecraft:", ""), s.getCount(), Integer::sum);
        }
        StringBuilder out = new StringBuilder();
        for (var e : counts.entrySet()) {
            if (!out.isEmpty()) out.append(", ");
            out.append(e.getKey()).append(" x").append(e.getValue());
        }
        return out.isEmpty() ? "empty" : out.toString();
    }

    public static String equipmentSummary(PlayerEntity p) {
        StringBuilder out = new StringBuilder("armor=");
        boolean first = true;
        for (ItemStack s : p.getArmorItems()) {
            if (!first) out.append(",");
            first = false;
            out.append(s.isEmpty() ? "empty" : id(s).replace("minecraft:", ""));
            if (!s.isEmpty() && s.isDamageable()) out.append("@").append(durabilityPercent(s)).append("%");
        }
        ItemStack off = p.getOffHandStack();
        out.append(" offhand=").append(off.isEmpty() ? "empty" : id(off).replace("minecraft:", ""));
        return out.toString();
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

    public static boolean moveToHotbar(MinecraftClient c,int invIndex,int hotbarSlot) {
        if(c.player==null||c.interactionManager==null) return false;
        PlayerEntity p=c.player;
        int old=p.getInventory().getSelectedSlot();
        if(invIndex>=0&&invIndex<9){
            if(invIndex==hotbarSlot) return true;
            OptionalInt slot=p.playerScreenHandler.getSlotIndex(p.getInventory(),invIndex);
            if(slot.isEmpty()) return false;
            c.interactionManager.clickSlot(p.playerScreenHandler.syncId,slot.getAsInt(),hotbarSlot,SlotActionType.SWAP,p);
            p.getInventory().setSelectedSlot(old);
            return true;
        }
        OptionalInt slot=p.playerScreenHandler.getSlotIndex(p.getInventory(),invIndex);
        if(slot.isEmpty()) return false;
        c.interactionManager.clickSlot(p.playerScreenHandler.syncId,slot.getAsInt(),hotbarSlot,SlotActionType.SWAP,p);
        p.getInventory().setSelectedSlot(old);
        return true;
    }

    public static void tickAutoHotbar(MinecraftClient c) {
        if(c.player==null||c.currentScreen!=null) return;
        refillKeyword(c,7,"food");
        refillKeyword(c,8,"totem");
        refillKeyword(c,5,"torch");
    }

    private static void refillKeyword(MinecraftClient c,int slot,String keyword) {
        ItemStack current=c.player.getInventory().getStack(slot);
        boolean good=!current.isEmpty() && (keyword.equals("food")
                ? current.getItem().getUseAction(current)==UseAction.EAT
                : id(current).contains(keyword));
        if(good) return;
        int idx=find(c.player,keyword);
        if(idx>=0 && idx!=slot) moveToHotbar(c,idx,slot);
    }

    public static void applyLoadout(MinecraftClient c,String profile) {
        if(c.player==null) return;
        String p=profile==null?"":profile.toLowerCase(Locale.ROOT);

        if ("combat".equals(p)) {
            SurvConfig cfg = SurvOsClient.CONFIG;
            String[] primary = {
                    cfg.combatSlot1, cfg.combatSlot2, cfg.combatSlot3,
                    cfg.combatSlot4, cfg.combatSlot5, cfg.combatSlot6,
                    cfg.combatSlot7, cfg.combatSlot8, cfg.combatSlot9
            };
            String[] fallback = {
                    "", "", "", "", "",
                    cfg.combatSlot6Fallback,
                    cfg.combatSlot7Fallback,
                    "", ""
            };
            applyCustomHotbar(c, primary, fallback);
            return;
        }

        String[][] layouts={
                {"mining","sword","pickaxe","pickaxe","shovel","blocks","torch","water_bucket","food","totem"},
                {"building","sword","pickaxe","axe","shovel","blocks","blocks","scaffolding","food","water_bucket"}
        };
        String[] chosen=layouts[0];
        for(String[] a:layouts) if(a[0].equals(p)) chosen=a;
        for(int slot=0;slot<9;slot++){
            String need=chosen[slot+1];
            int idx=find(c.player,need);
            if(idx>=0 && idx!=slot) moveToHotbar(c,idx,slot);
        }
    }

    public static void applyCustomHotbar(MinecraftClient c, String[] primary, String[] fallback) {
        if (c.player == null || primary == null) return;
        for (int slot = 0; slot < Math.min(9, primary.length); slot++) {
            String need = primary[slot] == null ? "" : primary[slot].trim();
            int idx = need.isBlank() ? -1 : find(c.player, need);
            if (idx < 0 && fallback != null && slot < fallback.length) {
                String fb = fallback[slot] == null ? "" : fallback[slot].trim();
                if (!fb.isBlank()) idx = find(c.player, fb);
            }
            if (idx >= 0 && idx != slot) moveToHotbar(c, idx, slot);
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
