package com.survivalutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

public final class PlayerNotifierManager {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final ArrayList<Watch> WATCHES=new ArrayList<>();
    private static final LinkedHashSet<String> ONLINE=new LinkedHashSet<>();
    private static boolean primed;
    private static boolean loaded;
    private static int ticks;

    public static final class Watch {
        public String player="";
        public String group="Watchlist";
        public String priority="NORMAL";
        public boolean join=true;
        public boolean leave=true;
        public boolean popup=true;
        public long lastJoin;
        public long lastLeave;
        public long sessionStarted;
        public long reconnectIgnoreMs=5000;
    }

    private PlayerNotifierManager(){}

    public static void init(){
        load();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,registryAccess)->{
            dispatcher.register(ClientCommandManager.literal("notify")
                    .then(ClientCommandManager.literal("list").executes(ctx->{feedback(list());return 1;}))
                    .then(ClientCommandManager.literal("add")
                            .then(ClientCommandManager.argument("player",StringArgumentType.word())
                                    .suggests((ctx,builder)->{
                                        MinecraftClient c=MinecraftClient.getInstance();
                                        if(c.getNetworkHandler()!=null)for(var e:c.getNetworkHandler().getPlayerList())builder.suggest(e.getProfile().name());
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx->{add(StringArgumentType.getString(ctx,"player"));return 1;})))
                    .then(ClientCommandManager.literal("remove")
                            .then(ClientCommandManager.argument("player",StringArgumentType.word())
                                    .executes(ctx->{remove(StringArgumentType.getString(ctx,"player"));return 1;})))
                    .then(ClientCommandManager.literal("priority")
                            .then(ClientCommandManager.argument("player",StringArgumentType.word())
                                    .then(ClientCommandManager.argument("level",StringArgumentType.word())
                                            .suggests((ctx,b)->{b.suggest("NORMAL");b.suggest("IMPORTANT");b.suggest("CRITICAL");return b.buildFuture();})
                                            .executes(ctx->{
                                                setPriority(StringArgumentType.getString(ctx,"player"),StringArgumentType.getString(ctx,"level"));
                                                return 1;
                                            })))));
        });
    }

    public static void tick(MinecraftClient client){
        if(client==null||client.getNetworkHandler()==null)return;
        if(++ticks%20!=0)return;

        LinkedHashSet<String> now=new LinkedHashSet<>();
        for(var e:client.getNetworkHandler().getPlayerList())now.add(e.getProfile().name().toLowerCase(Locale.ROOT));

        if(!primed){
            ONLINE.clear();
            ONLINE.addAll(now);
            long time=System.currentTimeMillis();
            for(Watch w:WATCHES)if(now.contains(w.player.toLowerCase(Locale.ROOT)))w.sessionStarted=time;
            primed=true;
            return;
        }

        long time=System.currentTimeMillis();
        for(Watch w:WATCHES){
            String key=w.player.toLowerCase(Locale.ROOT);
            boolean was=ONLINE.contains(key), is=now.contains(key);
            if(!was&&is){
                if(time-w.lastLeave>=w.reconnectIgnoreMs){
                    w.lastJoin=time;w.sessionStarted=time;
                    if(w.join)notify(client,w,true,0);
                }
            }else if(was&&!is){
                long onlineFor=w.sessionStarted>0?time-w.sessionStarted:0;
                w.lastLeave=time;
                if(w.leave)notify(client,w,false,onlineFor);
                w.sessionStarted=0;
            }
        }

        ONLINE.clear();
        ONLINE.addAll(now);
        save();
    }

    private static void notify(MinecraftClient c,Watch w,boolean join,long duration){
        if(c.player==null)return;
        String pfx="CRITICAL".equalsIgnoreCase(w.priority)?"⚠ ":"IMPORTANT".equalsIgnoreCase(w.priority)?"! ":"";
        String msg=join
                ?pfx+w.player+" joined // "+w.group+" // "+w.priority
                :pfx+w.player+" left // online "+formatDuration(duration)+" // "+w.priority;
        c.player.sendMessage(Text.literal("[SURV // PLAYER ALERT] "+msg),false);
        if(w.popup)c.player.sendMessage(Text.literal(msg),true);
    }

    public static void add(String player){
        load();
        if(WATCHES.stream().anyMatch(w->w.player.equalsIgnoreCase(player))){feedback("Already watching "+player);return;}
        if(WATCHES.size()>=10){feedback("Notifier profiles full // max 10");return;}
        Watch w=new Watch();w.player=player;WATCHES.add(w);save();
        feedback("Watching "+player+" // "+WATCHES.size()+"/10");
    }

    public static void remove(String player){load();WATCHES.removeIf(w->w.player.equalsIgnoreCase(player));save();feedback("Removed "+player);}
    public static void setPriority(String player,String level){
        load();
        for(Watch w:WATCHES)if(w.player.equalsIgnoreCase(player)){
            String v=level.toUpperCase(Locale.ROOT);
            if(!List.of("NORMAL","IMPORTANT","CRITICAL").contains(v))v="NORMAL";
            w.priority=v;save();feedback(player+" priority // "+v);return;
        }
        feedback("Not watched // "+player);
    }

    public static String list(){
        load();
        if(WATCHES.isEmpty())return "Notifier list empty.";
        StringBuilder b=new StringBuilder("Notifier // ");
        for(int i=0;i<WATCHES.size();i++){
            if(i>0)b.append(" | ");
            Watch w=WATCHES.get(i);
            b.append(i+1).append(":").append(w.player).append("(").append(w.priority).append(")");
        }
        return b.toString();
    }

    public static int count(){load();return WATCHES.size();}

    private static String formatDuration(long ms){
        long s=Math.max(0,Duration.ofMillis(ms).toSeconds());
        return (s/60)+"m "+(s%60)+"s";
    }

    private static void feedback(String s){
        MinecraftClient c=MinecraftClient.getInstance();
        if(c.player!=null)c.player.sendMessage(Text.literal("[SURV // UTILS] "+s),false);
    }

    private static void load(){
        if(loaded)return;loaded=true;
        try{
            Path p=ExoLinkData.root().resolve("player-notifier.json");
            if(Files.exists(p)){
                Watch[] arr=GSON.fromJson(Files.readString(p,StandardCharsets.UTF_8),Watch[].class);
                if(arr!=null)WATCHES.addAll(Arrays.asList(arr));
            }
        }catch(Exception ignored){}
    }

    public static void save(){
        load();
        try{
            Path p=ExoLinkData.root().resolve("player-notifier.json");
            Files.writeString(p,GSON.toJson(WATCHES),StandardCharsets.UTF_8);
        }catch(Exception ignored){}
    }
}
