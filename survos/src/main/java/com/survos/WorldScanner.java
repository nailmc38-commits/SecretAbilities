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
        if (c.player==null||c.world==null) return null;
        List<HostileEntity> list=c.world.getEntitiesByClass(HostileEntity.class,c.player.getBoundingBox().expand(r),e->e.isAlive()
                && (!cfg.avoidCreepers || !Registries.ENTITY_TYPE.getId(e.getType()).getPath().contains("creeper"))
                && (!cfg.avoidEndermen || !Registries.ENTITY_TYPE.getId(e.getType()).getPath().contains("enderman")));
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
