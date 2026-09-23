package com.survivalutils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.util.*;

public final class PackManager {
    public enum Order { NONE, SIT, FOLLOW }

    private static final LinkedHashSet<UUID> DOGS = new LinkedHashSet<>();
    private static final ArrayDeque<UUID> QUEUE = new ArrayDeque<>();
    private static Order order=Order.NONE;
    private static int ticks;
    private static int commanded;
    private static int skipped;

    private PackManager() {}

    public static void scan(MinecraftClient client) {
        if(client==null||client.player==null||client.world==null)return;
        DOGS.clear();
        for(WolfEntity wolf:client.world.getEntitiesByClass(
                WolfEntity.class,
                client.player.getBoundingBox().expand(96),
                w->w.isAlive()&&w.isOwner(client.player))) {
            DOGS.add(wolf.getUuid());
        }
        msg(client,"PACK SCAN // "+DOGS.size()+" owned loaded dogs");
    }

    public static void issue(MinecraftClient client, Order wanted) {
        if(client==null||client.player==null||client.world==null)return;
        if(DOGS.isEmpty()) scan(client);
        QUEUE.clear();
        QUEUE.addAll(DOGS);
        order=wanted;
        commanded=0;
        skipped=0;
        msg(client,(wanted==Order.SIT?"SIT ALL":"FOLLOW / RECALL")+" // queued "+QUEUE.size());
        ExoAudioManager.packCommand(client);
    }

    public static void tick(MinecraftClient client) {
        if(!ExoLinkData.SETTINGS.pack||client==null||client.player==null||client.world==null)return;
        if(++ticks%3!=0)return;

        DOGS.removeIf(id->{
            Entity e=client.world.getEntity(id);
            return !(e instanceof WolfEntity w)||!w.isAlive()||!w.isOwner(client.player);
        });

        if(order==Order.NONE||QUEUE.isEmpty())return;

        UUID id=QUEUE.removeFirst();
        Entity e=client.world.getEntity(id);
        if(!(e instanceof WolfEntity wolf)||!wolf.isOwner(client.player)) {
            skipped++; finish(client); return;
        }

        boolean wantSit=order==Order.SIT;
        boolean sitting=wolf.isSitting()||wolf.isInSittingPose();
        if(sitting==wantSit) {
            commanded++; finish(client); return;
        }

        // Vanilla servers reject remote interactions. We only send valid nearby interactions.
        if(client.player.squaredDistanceTo(wolf)>36.0) {
            skipped++; finish(client); return;
        }

        int empty=findEmptyHotbar(client);
        if(empty<0) {
            skipped++; finish(client); return;
        }

        int old=client.player.getInventory().getSelectedSlot();
        client.player.getInventory().setSelectedSlot(empty);
        client.interactionManager.interactEntity(client.player,wolf, Hand.MAIN_HAND);
        client.player.swingHand(Hand.MAIN_HAND);
        client.player.getInventory().setSelectedSlot(old);
        commanded++;
        finish(client);
    }

    private static void finish(MinecraftClient client) {
        if(!QUEUE.isEmpty())return;
        Order done=order;
        order=Order.NONE;
        String tail=skipped>0?" // "+skipped+" unreachable/skipped":"";
        msg(client,(done==Order.SIT?"SIT":"FOLLOW")+" COMPLETE // "+commanded+" dogs"+tail);
    }

    private static int findEmptyHotbar(MinecraftClient client) {
        for(int i=0;i<9;i++) {
            ItemStack s=client.player.getInventory().getStack(i);
            if(s.isEmpty())return i;
        }
        return -1;
    }

    public static Status status(MinecraftClient client) {
        int loaded=0,sitting=0,standing=0,near=0;
        if(client!=null&&client.player!=null&&client.world!=null) {
            for(UUID id:DOGS) {
                Entity e=client.world.getEntity(id);
                if(e instanceof WolfEntity w && w.isOwner(client.player)) {
                    loaded++;
                    if(w.isSitting()||w.isInSittingPose())sitting++; else standing++;
                    if(client.player.squaredDistanceTo(w)<=36.0)near++;
                }
            }
        }
        return new Status(DOGS.size(),loaded,sitting,standing,near,order);
    }

    public record Status(int known,int loaded,int sitting,int standing,int reachable,Order activeOrder){}

    public static void clear(MinecraftClient client){DOGS.clear();QUEUE.clear();order=Order.NONE;msg(client,"PACK CLEARED");}

    private static void msg(MinecraftClient client,String s) {
        if(client.player!=null)client.player.sendMessage(Text.literal("[EXO // PACK] "+s),false);
    }
}
