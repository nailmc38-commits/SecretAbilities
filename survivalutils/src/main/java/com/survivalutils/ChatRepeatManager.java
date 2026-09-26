package com.survivalutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class ChatRepeatManager {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final ArrayList<Profile> PROFILES=new ArrayList<>();
    private static final ArrayDeque<Pending> QUEUE=new ArrayDeque<>();
    private static final ArrayList<String> DEFAULT_TRIGGERS=new ArrayList<>(List.of(
            "repeat me","everyone repeat","everyone say","everyone type","copy me",
            "say this","type this","repeat","say","type"
    ));
    private static int activeIndex=-1;
    private static long lastSendAt;
    private static boolean loaded;

    public static final class Profile {
        public String player="";
        public boolean enabled=true;
        public long delayMs=1000;
        public long cooldownMs=1200;
        public ArrayList<String> triggers=new ArrayList<>(DEFAULT_TRIGGERS);
    }

    private record Pending(String text,long dueAt) {}

    private ChatRepeatManager(){}

    public static void init(){
        load();
        ClientReceiveMessageEvents.CHAT.register((message,signedMessage,sender,params,receptionTimestamp)->{
            if(sender==null)return;
            onChat(sender.getName(),message.getString());
        });
        registerCommands();
    }

    private static void registerCommands(){
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,registryAccess)->{
            dispatcher.register(ClientCommandManager.literal("repeat")
                    .then(ClientCommandManager.literal("off").executes(ctx->{
                        activeIndex=-1;
                        feedback("Repeat OFF");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("status").executes(ctx->{
                        feedback(status());
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("list").executes(ctx->{
                        feedback(profileList());
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("delay")
                            .then(ClientCommandManager.argument("ms",IntegerArgumentType.integer(0,60000))
                                    .executes(ctx->{
                                        Profile p=active();
                                        if(p==null){feedback("No active repeat profile.");return 0;}
                                        p.delayMs=IntegerArgumentType.getInteger(ctx,"ms");
                                        save();
                                        feedback("Delay // "+p.delayMs+"ms");
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("cooldown")
                            .then(ClientCommandManager.argument("ms",IntegerArgumentType.integer(1000,60000))
                                    .executes(ctx->{
                                        Profile p=active();
                                        if(p==null){feedback("No active repeat profile.");return 0;}
                                        p.cooldownMs=IntegerArgumentType.getInteger(ctx,"ms");
                                        save();
                                        feedback("Send cooldown // "+p.cooldownMs+"ms");
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("addtrigger")
                            .then(ClientCommandManager.argument("phrase",StringArgumentType.greedyString())
                                    .executes(ctx->{
                                        Profile p=active();
                                        if(p==null){feedback("No active repeat profile.");return 0;}
                                        String phrase=StringArgumentType.getString(ctx,"phrase").trim().toLowerCase(Locale.ROOT);
                                        if(!phrase.isBlank()&&!p.triggers.contains(phrase))p.triggers.add(phrase);
                                        save();
                                        feedback("Trigger added // "+phrase);
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("remove")
                            .then(ClientCommandManager.argument("player",StringArgumentType.word())
                                    .executes(ctx->{
                                        String name=StringArgumentType.getString(ctx,"player");
                                        PROFILES.removeIf(p->p.player.equalsIgnoreCase(name));
                                        if(activeIndex>=PROFILES.size())activeIndex=PROFILES.isEmpty()?-1:0;
                                        save();
                                        feedback("Removed repeat profile // "+name);
                                        return 1;
                                    })))
                    .then(ClientCommandManager.argument("player",StringArgumentType.word())
                            .suggests((ctx,builder)->{
                                MinecraftClient c=MinecraftClient.getInstance();
                                if(c.getNetworkHandler()!=null){
                                    for(var e:c.getNetworkHandler().getPlayerList())builder.suggest(e.getProfile().name());
                                }
                                return builder.buildFuture();
                            })
                            .executes(ctx->{
                                setActive(StringArgumentType.getString(ctx,"player"));
                                return 1;
                            })));
        });
    }

    public static void tick(MinecraftClient client){
        if(client==null||client.player==null||client.getNetworkHandler()==null)return;
        Profile p=active();
        if(p==null||!p.enabled||QUEUE.isEmpty())return;
        long now=System.currentTimeMillis();
        Pending next=QUEUE.peekFirst();
        if(next==null||now<next.dueAt()||now-lastSendAt<p.cooldownMs)return;
        QUEUE.removeFirst();
        String text=next.text();
        if(text.length()>256)text=text.substring(0,256);
        client.getNetworkHandler().sendChatMessage(text);
        lastSendAt=now;
    }

    private static void onChat(String sender,String raw){
        Profile p=active();
        if(p==null||!p.enabled||!p.player.equalsIgnoreCase(sender))return;

        MinecraftClient c=MinecraftClient.getInstance();
        if(c.player!=null&&sender.equalsIgnoreCase(c.player.getGameProfile().name()))return;

        String extracted=extract(raw,p.triggers);
        if(extracted==null||extracted.isBlank())return;
        QUEUE.addLast(new Pending(extracted,System.currentTimeMillis()+Math.max(0,p.delayMs)));
        while(QUEUE.size()>10)QUEUE.removeFirst();
        if(c.player!=null)c.player.sendMessage(Text.literal("[SURV // REPEAT] "+sender+" → "+extracted),true);
    }

    private static String extract(String raw,List<String> triggers){
        if(raw==null)return null;
        String lower=raw.toLowerCase(Locale.ROOT);
        String best=null;
        int bestAt=Integer.MAX_VALUE;
        int bestLen=0;
        for(String t:triggers){
            if(t==null||t.isBlank())continue;
            int at=lower.indexOf(t.toLowerCase(Locale.ROOT));
            if(at>=0&&(at<bestAt||(at==bestAt&&t.length()>bestLen))){
                best=t;bestAt=at;bestLen=t.length();
            }
        }
        if(best==null)return null;
        String out=raw.substring(Math.min(raw.length(),bestAt+best.length())).trim();
        while(!out.isEmpty() && (out.charAt(0)==':' || out.charAt(0)==',' || out.charAt(0)=='-' || out.charAt(0)=='–' || out.charAt(0)=='—' || out.charAt(0)=='>' || out.charAt(0)=='"' || out.charAt(0)=='\'')) {
            out=out.substring(1).trim();
        }
        if(out.startsWith("me "))out=out.substring(3).trim();
        if(out.length()>=2&&out.startsWith("\"")&&out.endsWith("\""))out=out.substring(1,out.length()-1);
        return out;
    }

    public static void setActive(String player){
        load();
        for(int i=0;i<PROFILES.size();i++){
            if(PROFILES.get(i).player.equalsIgnoreCase(player)){
                activeIndex=i;
                feedback("Repeat target // "+PROFILES.get(i).player+" // profile "+(i+1)+"/10");
                return;
            }
        }
        if(PROFILES.size()>=10){
            feedback("Repeat profiles full // max 10");
            return;
        }
        Profile p=new Profile();
        p.player=player;
        PROFILES.add(p);
        activeIndex=PROFILES.size()-1;
        save();
        feedback("Repeat target added // "+player+" // profile "+(activeIndex+1)+"/10");
    }

    public static Profile active(){
        load();
        return activeIndex>=0&&activeIndex<PROFILES.size()?PROFILES.get(activeIndex):null;
    }

    public static String status(){
        Profile p=active();
        if(p==null)return "Repeat OFF";
        return "Repeat // "+p.player+" // delay "+p.delayMs+"ms // cooldown "+p.cooldownMs+"ms // queued "+QUEUE.size();
    }

    public static String profileList(){
        load();
        if(PROFILES.isEmpty())return "No repeat profiles.";
        StringBuilder b=new StringBuilder("Repeat profiles // ");
        for(int i=0;i<PROFILES.size();i++){
            if(i>0)b.append(" | ");
            b.append(i==activeIndex?"*":"").append(i+1).append(":").append(PROFILES.get(i).player);
        }
        return b.toString();
    }

    public static int profileCount(){load();return PROFILES.size();}

    private static void feedback(String s){
        MinecraftClient c=MinecraftClient.getInstance();
        if(c.player!=null)c.player.sendMessage(Text.literal("[SURV // UTILS] "+s),false);
    }

    private static void load(){
        if(loaded)return;
        loaded=true;
        try{
            Path p=ExoLinkData.root().resolve("repeat-profiles.json");
            if(Files.exists(p)){
                Profile[] arr=GSON.fromJson(Files.readString(p,StandardCharsets.UTF_8),Profile[].class);
                if(arr!=null)PROFILES.addAll(Arrays.asList(arr));
            }
        }catch(Exception ignored){}
    }

    public static void save(){
        load();
        try{
            Path p=ExoLinkData.root().resolve("repeat-profiles.json");
            Files.writeString(p,GSON.toJson(PROFILES),StandardCharsets.UTF_8);
        }catch(Exception ignored){}
    }
}
