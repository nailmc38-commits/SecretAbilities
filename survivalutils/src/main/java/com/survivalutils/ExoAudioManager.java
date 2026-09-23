package com.survivalutils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

public final class ExoAudioManager {
    private static boolean hadHelmet;
    private static boolean initialized;
    private static float lastHealth=-1;
    private static int lastVisorDamage;
    private static String lastWarningKey="";
    private static String lastTarget="";
    private static long bootStarted;
    private static int bootSoundStage=-1;

    private ExoAudioManager(){}

    public static void tick(MinecraftClient client){
        if(client==null||client.player==null||client.world==null)return;

        boolean helmet=!client.player.getEquippedStack(EquipmentSlot.HEAD).isEmpty();
        if(!initialized){
            initialized=true;
            hadHelmet=helmet;
            lastHealth=client.player.getHealth()+client.player.getAbsorptionAmount();
            lastVisorDamage=VisorDamageSystem.damagePercent();
            if(helmet) startBoot(client);
        }

        if(helmet&&!hadHelmet) startBoot(client);
        if(!helmet&&hadHelmet) {
            bootStarted=0;
            play(client,SoundEvents.BLOCK_IRON_DOOR_CLOSE,0.45f,0.80f,ExoLinkData.SETTINGS.bootSound);
        }
        hadHelmet=helmet;

        tickBootSounds(client);

        float health=client.player.getHealth()+client.player.getAbsorptionAmount();
        if(lastHealth>=0&&health<lastHealth-0.05f){
            play(client,SoundEvents.BLOCK_IRON_HIT,0.38f,0.72f,ExoLinkData.SETTINGS.damageSound);
        }
        lastHealth=health;

        int visor=VisorDamageSystem.damagePercent();
        if(visor<lastVisorDamage){
            play(client,SoundEvents.ENTITY_IRON_GOLEM_REPAIR,0.28f,1.45f,ExoLinkData.SETTINGS.repairSound);
        }
        lastVisorDamage=visor;

        WarningManager.Warning warning=SurvivalUtilsClient.WARNINGS.active();
        String key=warning==null?"":warning.key();
        if(warning!=null&&!key.equals(lastWarningKey)){
            switch(warning.severity()){
                case CRITICAL -> play(client,SoundEvents.BLOCK_BELL_USE,0.72f,0.72f,ExoLinkData.SETTINGS.criticalSound);
                case DANGER -> play(client,SoundEvents.BLOCK_ANVIL_HIT,0.42f,1.55f,ExoLinkData.SETTINGS.warningSound);
                case CAUTION -> play(client,SoundEvents.BLOCK_BEACON_POWER_SELECT,0.32f,1.25f,ExoLinkData.SETTINGS.warningSound);
                case INFO -> {}
            }
        }
        lastWarningKey=key;

        CombatAdvisor.Snapshot advisor=CombatAdvisor.current();
        String target=advisor==null?"":advisor.opponent();
        if(target!=null&&!target.equals("NONE")&&!target.equals(lastTarget)){
            play(client,SoundEvents.BLOCK_BEACON_POWER_SELECT,0.36f,1.70f,ExoLinkData.SETTINGS.targetSound);
        }
        lastTarget=target==null?"":target;
    }

    public static void startBoot(MinecraftClient client){
        bootStarted=System.currentTimeMillis();
        bootSoundStage=-1;
        play(client,SoundEvents.BLOCK_IRON_DOOR_OPEN,0.45f,0.72f,ExoLinkData.SETTINGS.bootSound);
    }

    private static void tickBootSounds(MinecraftClient client){
        if(bootStarted<=0)return;
        long age=System.currentTimeMillis()-bootStarted;
        int stage=age<450?0:age<900?1:age<1350?2:age<1850?3:4;
        if(stage!=bootSoundStage){
            bootSoundStage=stage;
            if(stage==1) play(client,SoundEvents.BLOCK_IRON_HIT,0.25f,1.35f,ExoLinkData.SETTINGS.bootSound);
            if(stage==2) play(client,SoundEvents.BLOCK_BEACON_POWER_SELECT,0.30f,1.20f,ExoLinkData.SETTINGS.bootSound);
            if(stage==3) play(client,SoundEvents.BLOCK_BEACON_ACTIVATE,0.38f,1.35f,ExoLinkData.SETTINGS.bootSound);
        }
        if(age>2700)bootStarted=0;
    }

    public static boolean bootActive(){
        return bootStarted>0&&System.currentTimeMillis()-bootStarted<=2700;
    }

    public static long bootAge(){
        return bootStarted<=0?Long.MAX_VALUE:System.currentTimeMillis()-bootStarted;
    }

    public static void satelliteDeploy(MinecraftClient client){
        play(client,SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN,0.35f,1.25f,ExoLinkData.SETTINGS.satelliteSound);
    }

    public static void satelliteReturn(MinecraftClient client){
        play(client,SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE,0.35f,1.15f,ExoLinkData.SETTINGS.satelliteSound);
    }

    public static void packCommand(MinecraftClient client){
        play(client,SoundEvents.BLOCK_BEACON_POWER_SELECT,0.26f,1.85f,ExoLinkData.SETTINGS.toolSound);
    }

    public static void toolSuccess(MinecraftClient client){
        play(client,SoundEvents.BLOCK_BEACON_ACTIVATE,0.32f,1.55f,ExoLinkData.SETTINGS.toolSound);
    }

    private static void play(MinecraftClient client, SoundEvent sound,float volume,float pitch,boolean channelEnabled){
        if(!ExoLinkData.SETTINGS.sounds||!channelEnabled||client==null||client.player==null)return;
        client.player.playSound(sound,volume,pitch);
    }
}
