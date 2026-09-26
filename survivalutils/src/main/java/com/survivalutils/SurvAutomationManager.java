package com.survivalutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.consume.UseAction;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class SurvAutomationManager {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    public static final Settings SETTINGS=new Settings();

    public static final class Settings {
        public boolean autoEat=true;
        public boolean refillTotemSlot=true;
        public boolean refillShieldSlot=true;
        public boolean refillFoodSlot=true;
        public boolean refillRocketSlot=true;
        public boolean refillTorchSlot=false;
        public boolean refillBlockSlot=false;
        public boolean toolSaver=true;
        public boolean fireWaterResponse=false;
        public boolean autoTorch=false;
        public boolean autoSafeWalk=false;
        public boolean autoSprint=false;
        public boolean packRecallOnDanger=true;
        public boolean autoSleepNearby=false;
        public boolean combatFocus=true;
        public boolean refillPearlSlot=false;
        public boolean refillGoldenAppleSlot=false;
        public boolean refillWaterBucketSlot=false;
        public boolean emergencyShieldReady=false;
        public boolean lowFoodAutoReserve=true;

        public int eatAtHunger=12;
        public int toolSaverPercent=5;
        public int totemSlot=7;
        public int shieldSlot=8;
        public int foodSlot=6;
        public int rocketSlot=5;
        public int torchSlot=4;
        public int blockSlot=3;
        public int pearlSlot=1;
        public int goldenAppleSlot=2;
        public int waterBucketSlot=0;
    }

    private static boolean loaded;
    private static int ticks;
    private static int actionCooldown;
    private static int eatRestoreTicks;
    private static int oldEatSlot=-1;
    private static boolean safeWalkPressed;
    private static boolean sprintPressed;
    private static long lastPackRecall;
    private static long lastTorchPlace;
    private static long lastFireResponse;
    private static long lastSleepTry;

    private SurvAutomationManager(){}

    public static void load(){
        if(loaded)return;
        loaded=true;
        try{
            Path p=ExoLinkData.root().resolve("automation.json");
            if(Files.exists(p)){
                Settings s=GSON.fromJson(Files.readString(p,StandardCharsets.UTF_8),Settings.class);
                if(s!=null)copy(s);
            }
            save();
        }catch(Exception ignored){}
    }

    private static void copy(Settings s){
        SETTINGS.autoEat=s.autoEat;
        SETTINGS.refillTotemSlot=s.refillTotemSlot;
        SETTINGS.refillShieldSlot=s.refillShieldSlot;
        SETTINGS.refillFoodSlot=s.refillFoodSlot;
        SETTINGS.refillRocketSlot=s.refillRocketSlot;
        SETTINGS.refillTorchSlot=s.refillTorchSlot;
        SETTINGS.refillBlockSlot=s.refillBlockSlot;
        SETTINGS.toolSaver=s.toolSaver;
        SETTINGS.fireWaterResponse=s.fireWaterResponse;
        SETTINGS.autoTorch=s.autoTorch;
        SETTINGS.autoSafeWalk=s.autoSafeWalk;
        SETTINGS.autoSprint=s.autoSprint;
        SETTINGS.packRecallOnDanger=s.packRecallOnDanger;
        SETTINGS.autoSleepNearby=s.autoSleepNearby;
        SETTINGS.combatFocus=s.combatFocus;
        SETTINGS.refillPearlSlot=s.refillPearlSlot;
        SETTINGS.refillGoldenAppleSlot=s.refillGoldenAppleSlot;
        SETTINGS.refillWaterBucketSlot=s.refillWaterBucketSlot;
        SETTINGS.emergencyShieldReady=s.emergencyShieldReady;
        SETTINGS.lowFoodAutoReserve=s.lowFoodAutoReserve;
        SETTINGS.eatAtHunger=s.eatAtHunger;
        SETTINGS.toolSaverPercent=s.toolSaverPercent;
        SETTINGS.totemSlot=clampSlot(s.totemSlot);
        SETTINGS.shieldSlot=clampSlot(s.shieldSlot);
        SETTINGS.foodSlot=clampSlot(s.foodSlot);
        SETTINGS.rocketSlot=clampSlot(s.rocketSlot);
        SETTINGS.torchSlot=clampSlot(s.torchSlot);
        SETTINGS.blockSlot=clampSlot(s.blockSlot);
        SETTINGS.pearlSlot=clampSlot(s.pearlSlot);
        SETTINGS.goldenAppleSlot=clampSlot(s.goldenAppleSlot);
        SETTINGS.waterBucketSlot=clampSlot(s.waterBucketSlot);
    }

    public static void save(){
        load();
        try{
            Files.writeString(ExoLinkData.root().resolve("automation.json"),GSON.toJson(SETTINGS),StandardCharsets.UTF_8);
        }catch(Exception ignored){}
    }

    public static void tick(MinecraftClient c){
        load();
        if(c==null||c.player==null||c.world==null||c.interactionManager==null)return;
        if(actionCooldown>0)actionCooldown--;

        ChatRepeatManager.tick(c);
        PlayerNotifierManager.tick(c);

        if(c.currentScreen!=null){
            releaseKeys(c);
            return;
        }

        if(eatRestoreTicks>0){
            eatRestoreTicks--;
            if(eatRestoreTicks==0){
                c.options.useKey.setPressed(false);
                if(oldEatSlot>=0&&oldEatSlot<9)c.player.getInventory().setSelectedSlot(oldEatSlot);
                oldEatSlot=-1;
            }
        }

        if(++ticks%2==0){
            if(SETTINGS.refillTotemSlot)refill(c,SETTINGS.totemSlot,"totem_of_undying",1);
            if(SETTINGS.refillShieldSlot)refill(c,SETTINGS.shieldSlot,"shield",1);
            if(SETTINGS.refillFoodSlot)refillFood(c,SETTINGS.foodSlot,8);
            if(SETTINGS.refillRocketSlot)refill(c,SETTINGS.rocketSlot,"firework_rocket",8);
            if(SETTINGS.refillTorchSlot)refill(c,SETTINGS.torchSlot,"torch",16);
            if(SETTINGS.refillBlockSlot)refillBlock(c,SETTINGS.blockSlot,32);
            if(SETTINGS.refillPearlSlot)refill(c,SETTINGS.pearlSlot,"ender_pearl",4);
            if(SETTINGS.refillGoldenAppleSlot)refill(c,SETTINGS.goldenAppleSlot,"golden_apple",2);
            if(SETTINGS.refillWaterBucketSlot)refill(c,SETTINGS.waterBucketSlot,"water_bucket",1);
        }

        if(SETTINGS.lowFoodAutoReserve&&InventoryUtil.foodCount(c.player)<=4&&SETTINGS.autoEat){
            SETTINGS.eatAtHunger=Math.min(14,SETTINGS.eatAtHunger);
        }

        if(SETTINGS.toolSaver)toolSaver(c);
        if(SETTINGS.autoEat)autoEat(c);
        if(SETTINGS.fireWaterResponse)fireResponse(c);
        if(SETTINGS.autoTorch)autoTorch(c);
        if(SETTINGS.autoSafeWalk)autoSafeWalk(c); else releaseSafeWalk(c);
        if(SETTINGS.autoSprint)autoSprint(c); else releaseSprint(c);
        if(SETTINGS.packRecallOnDanger)packRecall(c);
        if(SETTINGS.autoSleepNearby)autoSleep(c);
        if(SETTINGS.emergencyShieldReady)emergencyShieldReady(c);

        if(SETTINGS.combatFocus){
            WarningManager.Warning w=SurvivalUtilsClient.WARNINGS.active();
            ExoLinkData.SETTINGS.autoEmergencyLayout=w!=null
                    && (w.severity()==WarningManager.Severity.DANGER||w.severity()==WarningManager.Severity.CRITICAL);
        }
    }

    private static void refill(MinecraftClient c,int slot,String keyword,int minCount){
        ItemStack target=c.player.getInventory().getStack(slot);
        if(!target.isEmpty()&&InventoryUtil.id(target).contains(keyword)&&target.getCount()>=minCount)return;
        int src=findInventory(c,keyword,9);
        if(src<0)return;
        InventoryUtil.swapInventoryToHotbar(c,src,slot);
        actionCooldown=4;
    }

    private static void refillFood(MinecraftClient c,int slot,int minCount){
        ItemStack target=c.player.getInventory().getStack(slot);
        if(isSafeFood(target)&&target.getCount()>=minCount)return;
        for(int i=9;i<c.player.getInventory().size();i++){
            ItemStack s=c.player.getInventory().getStack(i);
            if(isSafeFood(s)){
                InventoryUtil.swapInventoryToHotbar(c,i,slot);
                actionCooldown=4;
                return;
            }
        }
    }

    private static void refillBlock(MinecraftClient c,int slot,int minCount){
        ItemStack target=c.player.getInventory().getStack(slot);
        if(!target.isEmpty()&&target.getCount()>=minCount&&isBuildingBlock(target))return;
        for(int i=9;i<c.player.getInventory().size();i++){
            ItemStack s=c.player.getInventory().getStack(i);
            if(isBuildingBlock(s)&&s.getCount()>=16){
                InventoryUtil.swapInventoryToHotbar(c,i,slot);
                actionCooldown=4;
                return;
            }
        }
    }

    private static int findInventory(MinecraftClient c,String keyword,int start){
        for(int i=start;i<c.player.getInventory().size();i++){
            ItemStack s=c.player.getInventory().getStack(i);
            if(!s.isEmpty()&&InventoryUtil.id(s).contains(keyword))return i;
        }
        return -1;
    }

    private static void toolSaver(MinecraftClient c){
        ItemStack held=c.player.getMainHandStack();
        if(held.isEmpty()||!held.isDamageable())return;
        if(InventoryUtil.durabilityPercent(held)>SETTINGS.toolSaverPercent)return;
        for(int i=0;i<9;i++){
            if(i==c.player.getInventory().getSelectedSlot())continue;
            ItemStack s=c.player.getInventory().getStack(i);
            if(s.isEmpty()||!s.isDamageable()||InventoryUtil.durabilityPercent(s)>20){
                c.player.getInventory().setSelectedSlot(i);
                notify(c,"TOOL SAVER // switched away at "+InventoryUtil.durabilityPercent(held)+"%");
                return;
            }
        }
    }

    private static void autoEat(MinecraftClient c){
        if(eatRestoreTicks>0||c.player.getHungerManager().getFoodLevel()>SETTINGS.eatAtHunger||actionCooldown>0)return;
        if(hasThreatClose(c,7))return;

        int slot=findFoodHotbar(c);
        if(slot<0){
            for(int i=9;i<c.player.getInventory().size();i++){
                if(isSafeFood(c.player.getInventory().getStack(i))){
                    int temp=SETTINGS.foodSlot;
                    InventoryUtil.swapInventoryToHotbar(c,i,temp);
                    slot=temp;
                    break;
                }
            }
        }
        if(slot<0)return;

        oldEatSlot=c.player.getInventory().getSelectedSlot();
        c.player.getInventory().setSelectedSlot(slot);
        c.options.useKey.setPressed(true);
        eatRestoreTicks=36;
        actionCooldown=40;
    }

    private static int findFoodHotbar(MinecraftClient c){
        for(int i=0;i<9;i++)if(isSafeFood(c.player.getInventory().getStack(i)))return i;
        return -1;
    }

    private static boolean isSafeFood(ItemStack s){
        if(s==null||s.isEmpty()||s.getItem().getUseAction(s)!=UseAction.EAT)return false;
        String id=InventoryUtil.id(s);
        return !id.contains("rotten_flesh")&&!id.contains("spider_eye")&&!id.contains("pufferfish")
                &&!id.contains("chorus_fruit")&&!id.contains("poisonous_potato");
    }

    private static boolean isBuildingBlock(ItemStack s){
        if(s==null||s.isEmpty())return false;
        String id=InventoryUtil.id(s);
        return id.contains("cobblestone")||id.contains("stone")||id.contains("dirt")
                ||id.contains("planks")||id.contains("netherrack")||id.contains("deepslate");
    }

    private static void fireResponse(MinecraftClient c){
        if(!c.player.isOnFire()||System.currentTimeMillis()-lastFireResponse<3000)return;
        if(c.world.getRegistryKey().getValue().getPath().contains("nether"))return;
        int inv=InventoryUtil.findInventoryIndex(c.player,"water_bucket");
        if(inv<0)return;

        int old=c.player.getInventory().getSelectedSlot();
        int slot=inv<9?inv:InventoryUtil.firstEmptyHotbar(c.player);
        if(slot<0)slot=old;
        if(inv>=9&&!InventoryUtil.swapInventoryToHotbar(c,inv,slot))return;
        c.player.getInventory().setSelectedSlot(slot);

        BlockPos floor=c.player.getBlockPos().down();
        BlockHitResult hit=new BlockHitResult(Vec3d.ofCenter(floor).add(0,0.5,0),Direction.UP,floor,false);
        c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,hit);
        c.player.swingHand(Hand.MAIN_HAND);
        c.player.getInventory().setSelectedSlot(old);
        lastFireResponse=System.currentTimeMillis();
        notify(c,"FIRE RESPONSE // water deployed");
    }

    private static void autoTorch(MinecraftClient c){
        long now=System.currentTimeMillis();
        if(now-lastTorchPlace<5000||!c.player.isOnGround()||c.world.getLightLevel(c.player.getBlockPos())>3)return;
        if(hasThreatClose(c,5))return;
        int slot=findHotbar(c,"torch");
        if(slot<0)return;

        int old=c.player.getInventory().getSelectedSlot();
        c.player.getInventory().setSelectedSlot(slot);
        BlockPos floor=c.player.getBlockPos().down();
        BlockHitResult hit=new BlockHitResult(Vec3d.ofCenter(floor).add(0,0.5,0),Direction.UP,floor,false);
        c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,hit);
        c.player.swingHand(Hand.MAIN_HAND);
        c.player.getInventory().setSelectedSlot(old);
        lastTorchPlace=now;
    }

    private static void autoSafeWalk(MinecraftClient c){
        if(!c.player.isOnGround()){releaseSafeWalk(c);return;}
        double yaw=Math.toRadians(c.player.getYaw());
        int dx=(int)Math.round(-Math.sin(yaw));
        int dz=(int)Math.round(Math.cos(yaw));
        BlockPos ahead=c.player.getBlockPos().add(dx,-1,dz);
        boolean edge=c.world.getBlockState(ahead).getCollisionShape(c.world,ahead).isEmpty();
        c.options.sneakKey.setPressed(edge);
        safeWalkPressed=edge;
    }

    private static void releaseSafeWalk(MinecraftClient c){
        if(safeWalkPressed)c.options.sneakKey.setPressed(false);
        safeWalkPressed=false;
    }

    private static void autoSprint(MinecraftClient c){
        boolean should=c.options.forwardKey.isPressed()&&c.player.getHungerManager().getFoodLevel()>6&&!c.player.isSneaking();
        c.options.sprintKey.setPressed(should);
        sprintPressed=should;
    }

    private static void releaseSprint(MinecraftClient c){
        if(sprintPressed)c.options.sprintKey.setPressed(false);
        sprintPressed=false;
    }

    private static void emergencyShieldReady(MinecraftClient c){
        WarningManager.Warning w=SurvivalUtilsClient.WARNINGS.active();
        if(w==null)return;
        boolean danger=w.text().toLowerCase(Locale.ROOT).contains("projectile")
                || w.text().toLowerCase(Locale.ROOT).contains("player very close")
                || w.text().toLowerCase(Locale.ROOT).contains("hostile within");
        if(!danger)return;
        int slot=findHotbar(c,"shield");
        if(slot>=0&&c.player.getInventory().getSelectedSlot()!=slot){
            c.player.getInventory().setSelectedSlot(slot);
            notify(c,"SHIELD READY // slot "+(slot+1));
        }
    }

    private static void packRecall(MinecraftClient c){
        WarningManager.Warning w=SurvivalUtilsClient.WARNINGS.active();
        if(w==null||w.severity().ordinal()<WarningManager.Severity.DANGER.ordinal())return;
        long now=System.currentTimeMillis();
        if(now-lastPackRecall<10000)return;
        PackManager.issue(c,PackManager.Order.FOLLOW);
        lastPackRecall=now;
    }

    private static void autoSleep(MinecraftClient c){
        long now=System.currentTimeMillis();
        if(now-lastSleepTry<5000)return;
        long t=Math.floorMod(c.world.getTimeOfDay(),24000L);
        if(t<12542||t>=23460||hasThreatClose(c,8))return;

        BlockPos base=c.player.getBlockPos();
        for(int x=-3;x<=3;x++)for(int y=-2;y<=2;y++)for(int z=-3;z<=3;z++){
            BlockPos p=base.add(x,y,z);
            String id=Registries.BLOCK.getId(c.world.getBlockState(p).getBlock()).getPath();
            if(!id.endsWith("_bed"))continue;
            BlockHitResult hit=new BlockHitResult(Vec3d.ofCenter(p),Direction.UP,p,false);
            c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,hit);
            lastSleepTry=now;
            return;
        }
    }

    private static boolean hasThreatClose(MinecraftClient c,double r){
        return !c.world.getEntitiesByClass(HostileEntity.class,c.player.getBoundingBox().expand(r),e->e.isAlive()).isEmpty()
                || c.world.getPlayers().stream().anyMatch(p->p!=c.player&&p.squaredDistanceTo(c.player)<=r*r);
    }

    private static int findHotbar(MinecraftClient c,String keyword){
        for(int i=0;i<9;i++){
            ItemStack s=c.player.getInventory().getStack(i);
            if(!s.isEmpty()&&InventoryUtil.id(s).contains(keyword))return i;
        }
        return -1;
    }

    private static void releaseKeys(MinecraftClient c){
        releaseSafeWalk(c);
        releaseSprint(c);
        if(eatRestoreTicks<=0)c.options.useKey.setPressed(false);
    }

    private static int clampSlot(int s){return Math.max(0,Math.min(8,s));}

    private static void notify(MinecraftClient c,String s){
        if(c.player!=null)c.player.sendMessage(Text.literal("[SURV // AUTO] "+s),true);
    }

    public static int enabledCount(){
        int n=0;
        if(SETTINGS.autoEat)n++;
        if(SETTINGS.refillTotemSlot)n++;
        if(SETTINGS.refillShieldSlot)n++;
        if(SETTINGS.refillFoodSlot)n++;
        if(SETTINGS.refillRocketSlot)n++;
        if(SETTINGS.refillTorchSlot)n++;
        if(SETTINGS.refillBlockSlot)n++;
        if(SETTINGS.toolSaver)n++;
        if(SETTINGS.fireWaterResponse)n++;
        if(SETTINGS.autoTorch)n++;
        if(SETTINGS.autoSafeWalk)n++;
        if(SETTINGS.autoSprint)n++;
        if(SETTINGS.packRecallOnDanger)n++;
        if(SETTINGS.autoSleepNearby)n++;
        if(SETTINGS.combatFocus)n++;
        if(SETTINGS.refillPearlSlot)n++;
        if(SETTINGS.refillGoldenAppleSlot)n++;
        if(SETTINGS.refillWaterBucketSlot)n++;
        if(SETTINGS.emergencyShieldReady)n++;
        if(SETTINGS.lowFoodAutoReserve)n++;
        return n;
    }
}
