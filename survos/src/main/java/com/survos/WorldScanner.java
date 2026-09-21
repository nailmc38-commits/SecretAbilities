package com.survos;

import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public final class WorldScanner {
    private WorldScanner(){}

    public static BlockPos exposedOre(MinecraftClient c, int r, String filter) {
        return nearestBlock(c,r,6,(s,p)->{
            String id=Registries.BLOCK.getId(s.getBlock()).getPath();
            boolean ore=id.endsWith("_ore") || id.equals("ancient_debris");
            boolean match=filter==null || filter.isBlank() || id.contains(normalize(filter));
            return ore && match && exposed(c,p);
        });
    }

    public static BlockPos visibleBlock(MinecraftClient c, int r, int vertical, String filter) {
        String q = normalize(filter == null ? "" : filter);
        return nearestBlock(c,r,vertical,(s,p)->{
            if(s.isAir()) return false;
            String id=Registries.BLOCK.getId(s.getBlock()).getPath();
            if(!q.isBlank() && !matchesResource(id,q)) return false;
            return exposed(c,p);
        });
    }

    public static String nearbySummary(MinecraftClient c, int r) {
        if (c.player==null||c.world==null) return "none";
        BlockPos base=new BlockPos(c.player.getBlockX(),c.player.getBlockY(),c.player.getBlockZ());
        java.util.LinkedHashMap<String,Integer> counts=new java.util.LinkedHashMap<>();

        for(int y=-5;y<=5;y++) for(int x=-r;x<=r;x++) for(int z=-r;z<=r;z++){
            BlockPos p=base.add(x,y,z);
            BlockState s=c.world.getBlockState(p);
            if(s.isAir()) continue;
            String id=Registries.BLOCK.getId(s.getBlock()).getPath();

            boolean important=id.endsWith("_ore")
                    || id.equals("ancient_debris")
                    || id.contains("log")
                    || id.contains("stem")
                    || id.contains("obsidian")
                    || id.contains("portal")
                    || id.contains("crafting_table")
                    || id.contains("furnace")
                    || id.contains("chest")
                    || id.contains("bed")
                    || id.contains("spawner")
                    || id.contains("water")
                    || id.contains("lava");

            if(important && exposed(c,p)) counts.merge(id,1,Integer::sum);
        }

        if(counts.isEmpty()) return "none";
        StringBuilder out=new StringBuilder();
        int n=0;
        for(var e:counts.entrySet()){
            if(n++>=14) break;
            if(!out.isEmpty()) out.append(", ");
            out.append(e.getKey()).append(" x").append(e.getValue());
        }
        return out.toString();
    }

    private static boolean matchesResource(String id,String q){
        if(id.contains(q)) return true;
        if(q.equals("stone") && (id.equals("stone")||id.equals("cobblestone")||id.contains("deepslate"))) return true;
        if(q.equals("wood") && (id.endsWith("_log")||id.endsWith("_stem")||id.endsWith("_hyphae"))) return true;
        if(q.equals("log") && (id.endsWith("_log")||id.endsWith("_stem")||id.endsWith("_hyphae"))) return true;
        if(q.equals("iron") && id.contains("iron_ore")) return true;
        if(q.equals("gold") && id.contains("gold_ore")) return true;
        if(q.equals("diamond") && id.contains("diamond_ore")) return true;
        if(q.equals("coal") && id.contains("coal_ore")) return true;
        return false;
    }

    public static BlockPos log(MinecraftClient c, int r) {
        return nearestBlock(c,r,8,(s,p)->{
            String id=Registries.BLOCK.getId(s.getBlock()).getPath();
            return (id.endsWith("_log")||id.endsWith("_stem")||id.endsWith("_hyphae")) && exposed(c,p);
        });
    }

    public static BlockPos matureCrop(MinecraftClient c, int r) {
        return nearestBlock(c,r,3,(s,p)-> s.getBlock() instanceof CropBlock crop && crop.isMature(s));
    }

    public static ItemEntity item(MinecraftClient c, double r) {
        if (c.player==null||c.world==null) return null;
        List<ItemEntity> list=c.world.getEntitiesByClass(ItemEntity.class,c.player.getBoundingBox().expand(r),e->e.isAlive());
        return list.stream().min(Comparator.comparingDouble(c.player::squaredDistanceTo)).orElse(null);
    }

    public static HostileEntity hostile(MinecraftClient c,double r,SurvConfig cfg) {
        return hostile(c,r,cfg,"");
    }

    public static HostileEntity hostile(MinecraftClient c,double r,SurvConfig cfg,String filter) {
        if (c.player==null||c.world==null) return null;
        String q=normalize(filter==null?"":filter);
        List<HostileEntity> list=c.world.getEntitiesByClass(
                HostileEntity.class,
                c.player.getBoundingBox().expand(r),
                e->{
                    if(!e.isAlive()) return false;
                    String id=Registries.ENTITY_TYPE.getId(e.getType()).getPath();

                    if(!q.isBlank() && !id.contains(q)) return false;
                    if(q.isBlank() && cfg.avoidCreepers && id.contains("creeper")) return false;
                    if(q.isBlank() && cfg.avoidEndermen && id.contains("enderman")) return false;
                    return true;
                });
        return list.stream().min(Comparator.comparingDouble(c.player::squaredDistanceTo)).orElse(null);
    }

    public static AnimalEntity animal(MinecraftClient c,double r) {
        if (c.player==null||c.world==null) return null;
        List<AnimalEntity> list=c.world.getEntitiesByClass(AnimalEntity.class,c.player.getBoundingBox().expand(r),
                e->e.isAlive() && e.canEat() && !e.isInLove());
        return list.stream().min(Comparator.comparingDouble(c.player::squaredDistanceTo)).orElse(null);
    }

    private interface BlockTest { boolean ok(BlockState s, BlockPos p); }

    private static BlockPos nearestBlock(MinecraftClient c,int horizontal,int vertical,BlockTest test) {
        if (c.player==null||c.world==null) return null;
        BlockPos base=new BlockPos(c.player.getBlockX(),c.player.getBlockY(),c.player.getBlockZ());
        BlockPos best=null;
        double bestD=Double.MAX_VALUE;
        for(int y=-vertical;y<=vertical;y++) for(int x=-horizontal;x<=horizontal;x++) for(int z=-horizontal;z<=horizontal;z++){
            BlockPos p=base.add(x,y,z);
            BlockState s=c.world.getBlockState(p);
            if(test.ok(s,p)){
                double d=p.getSquaredDistance(base);
                if(d<bestD){bestD=d;best=p.toImmutable();}
            }
        }
        return best;
    }

    private static boolean exposed(MinecraftClient c,BlockPos p) {
        for(Direction d:Direction.values()){
            BlockPos q=p.offset(d);
            BlockState s=c.world.getBlockState(q);
            if(s.isAir() || s.getCollisionShape(c.world,q).isEmpty()) return true;
        }
        return false;
    }

    public static String normalize(String s){
        return s.toLowerCase(Locale.ROOT).replace(" ","_").replace("diamonds","diamond").replace("iron_ingots","iron");
    }
}
