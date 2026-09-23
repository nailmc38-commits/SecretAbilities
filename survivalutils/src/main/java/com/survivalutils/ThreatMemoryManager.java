package com.survivalutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class ThreatMemoryManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final LinkedHashMap<String, Contact> CONTACTS = new LinkedHashMap<>();
    private static final HashSet<String> LIVE_LAST_TICK = new HashSet<>();
    private static int ticks;
    private static boolean loaded;

    public static final class Contact {
        public String uuid="";
        public String name="";
        public String tag="NEUTRAL";
        public long firstSeen;
        public long lastSeen;
        public int encounters;
        public int combatEncounters;
        public int observedWins;
        public int observedLosses;
        public int x,y,z;
        public String dimension="";
        public String biome="";
        public String movement="UNKNOWN";
        public float lastHealth=-1;
        public String helmet="", chest="", legs="", boots="";
        public String mainHand="", offHand="";
        public final LinkedHashMap<String, Long> observedItems = new LinkedHashMap<>();
        public String notes="";
        public boolean starred;
    }

    private ThreatMemoryManager() {}

    public static void tick(MinecraftClient client) {
        if (!ExoLinkData.SETTINGS.threatMemory || client == null || client.player == null || client.world == null) return;
        ensureLoaded();
        if (++ticks % 10 != 0) return;

        HashSet<String> live = new HashSet<>();
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player) continue;
            String key=p.getUuidAsString();
            live.add(key);
            Contact c=CONTACTS.computeIfAbsent(key,k->new Contact());
            long now=System.currentTimeMillis();
            if (c.firstSeen==0) c.firstSeen=now;
            if (!LIVE_LAST_TICK.contains(key)) c.encounters++;
            c.uuid=key;
            c.name=p.getGameProfile().name();
            c.lastSeen=now;
            c.x=p.getBlockX(); c.y=p.getBlockY(); c.z=p.getBlockZ();
            c.dimension=client.world.getRegistryKey().getValue().getPath();
            c.biome=client.world.getBiome(p.getBlockPos()).getKey().map(k->k.getValue().getPath()).orElse("unknown");
            c.lastHealth=p.getHealth();
            c.movement=movement(p);
            c.helmet=item(p.getEquippedStack(EquipmentSlot.HEAD));
            c.chest=item(p.getEquippedStack(EquipmentSlot.CHEST));
            c.legs=item(p.getEquippedStack(EquipmentSlot.LEGS));
            c.boots=item(p.getEquippedStack(EquipmentSlot.FEET));
            c.mainHand=item(p.getMainHandStack());
            c.offHand=item(p.getOffHandStack());
            observe(c,c.helmet,now); observe(c,c.chest,now); observe(c,c.legs,now); observe(c,c.boots,now);
            observe(c,c.mainHand,now); observe(c,c.offHand,now);
        }

        if (client.player.getAttacker() instanceof PlayerEntity attacker) {
            Contact c=get(attacker);
            if (c != null) {
                c.combatEncounters++;
                if ("NEUTRAL".equals(c.tag)) c.tag="WATCH";
            }
        }

        LIVE_LAST_TICK.clear();
        LIVE_LAST_TICK.addAll(live);
        if ((ticks % 100)==0) { prune(); save(); }
    }

    private static String movement(PlayerEntity p) {
        double x=p.getVelocity().x, z=p.getVelocity().z;
        if (x*x+z*z < 0.0025) return "STATIONARY";
        double a=Math.toDegrees(Math.atan2(z,x));
        if (a<0) a+=360;
        if (a<22.5||a>=337.5) return "E";
        if (a<67.5) return "SE";
        if (a<112.5) return "S";
        if (a<157.5) return "SW";
        if (a<202.5) return "W";
        if (a<247.5) return "NW";
        if (a<292.5) return "N";
        return "NE";
    }

    private static void observe(Contact c,String id,long now) {
        if(id!=null&&!id.isBlank()&&!"empty".equals(id)) c.observedItems.put(id,now);
        while(c.observedItems.size()>40) c.observedItems.remove(c.observedItems.keySet().iterator().next());
    }

    private static String item(ItemStack s) {
        if(s==null||s.isEmpty()) return "empty";
        return Registries.ITEM.getId(s.getItem()).getPath();
    }

    public static Contact get(PlayerEntity p) { ensureLoaded(); return CONTACTS.get(p.getUuidAsString()); }
    public static Contact get(String uuid) { ensureLoaded(); return CONTACTS.get(uuid); }
    public static Collection<Contact> all() { ensureLoaded(); return Collections.unmodifiableCollection(CONTACTS.values()); }

    public static void cycleTag(PlayerEntity p) {
        ensureLoaded();
        Contact c=CONTACTS.computeIfAbsent(p.getUuidAsString(),k->new Contact());
        c.uuid=p.getUuidAsString(); c.name=p.getGameProfile().name();
        c.tag=switch(c.tag){case "NEUTRAL"->"FRIEND";case "FRIEND"->"WATCH";case "WATCH"->"HOSTILE";default->"NEUTRAL";};
        save();
    }

    public static void setTag(String uuid,String tag){ Contact c=get(uuid); if(c!=null){c.tag=tag;save();} }
    public static void clear(){ensureLoaded();CONTACTS.clear();save();}

    private static void prune() {
        long cutoff=System.currentTimeMillis()-ExoLinkData.SETTINGS.dataRetentionDays*86_400_000L;
        CONTACTS.values().removeIf(c->!c.starred&&c.lastSeen>0&&c.lastSeen<cutoff);
    }

    private static void ensureLoaded() {
        if(loaded)return;
        loaded=true;
        ExoLinkData.load();
        Path p=ExoLinkData.root().resolve("threat-memory.json");
        try{
            if(Files.exists(p)){
                Contact[] arr=GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8),Contact[].class);
                if(arr!=null) for(Contact c:arr) CONTACTS.put(c.uuid,c);
            }
        }catch(Exception ignored){}
    }

    public static void save() {
        try{
            Path p=ExoLinkData.root().resolve("threat-memory.json");
            Files.writeString(p,GSON.toJson(CONTACTS.values()),StandardCharsets.UTF_8);
        }catch(Exception ignored){}
    }
}
