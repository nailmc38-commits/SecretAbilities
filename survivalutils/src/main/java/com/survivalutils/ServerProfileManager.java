package com.survivalutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ServerProfileManager {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static String currentKey="";
    private static int ticks;

    private ServerProfileManager(){}

    public static void tick(MinecraftClient client){
        if(client==null)return;
        if(++ticks%20!=0)return;

        if(!ExoLinkData.SETTINGS.perServerProfiles){
            currentKey="";
            return;
        }

        String next=key(client);
        if(next.equals(currentKey))return;

        if(!currentKey.isBlank())save(currentKey);
        currentKey=next;

        if(!currentKey.isBlank())loadOrCreate(currentKey);
    }

    public static String currentKey(){return currentKey.isBlank()?"GLOBAL":currentKey;}

    public static void saveCurrent(){
        if(!currentKey.isBlank())save(currentKey);
    }

    private static String key(MinecraftClient client){
        if(client.world==null)return "";
        if(client.isInSingleplayer())return "singleplayer";
        var info=client.getCurrentServerEntry();
        if(info==null||info.address==null||info.address.isBlank())return "multiplayer_unknown";
        return sanitize(info.address.toLowerCase(Locale.ROOT));
    }

    private static String sanitize(String s){
        String out=s.replaceAll("[^a-z0-9._-]","_");
        if(out.length()>72)out=out.substring(0,72);
        return out.isBlank()?"profile":out;
    }

    private static Path file(String key){
        return ExoLinkData.root().resolve("profiles").resolve(key+".json");
    }

    private static void loadOrCreate(String key){
        try{
            Path p=file(key);
            Files.createDirectories(p.getParent());
            if(Files.exists(p)){
                ExoLinkData.Settings settings=GSON.fromJson(Files.readString(p,StandardCharsets.UTF_8),ExoLinkData.Settings.class);
                if(settings!=null)ExoLinkData.applyProfile(settings);
            }else{
                save(key);
            }
        }catch(Exception ignored){}
    }

    private static void save(String key){
        try{
            Path p=file(key);
            Files.createDirectories(p.getParent());
            Files.writeString(p,GSON.toJson(ExoLinkData.snapshot()),StandardCharsets.UTF_8);
        }catch(Exception ignored){}
    }
}
