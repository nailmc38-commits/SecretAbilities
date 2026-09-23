package com.survivalutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.gui.DrawContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HudLayoutManager {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final LinkedHashMap<String,Panel> PANELS=new LinkedHashMap<>();
    private static boolean loaded;

    public static final class Panel {
        public double x;
        public double y;
        public double scale=1.0;
        public double opacity=1.0;

        public Panel(){}
        public Panel(double x,double y,double scale,double opacity){
            this.x=x;this.y=y;this.scale=scale;this.opacity=opacity;
        }
    }

    private HudLayoutManager(){}

    public static void load(){
        if(loaded)return;
        loaded=true;
        defaults();
        Path p=ExoLinkData.root().resolve("hud-layout.json");
        try{
            if(Files.exists(p)){
                PanelMap saved=GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8),PanelMap.class);
                if(saved!=null&&saved.panels!=null){
                    for(var e:saved.panels.entrySet()){
                        Panel v=e.getValue();
                        if(v==null)continue;
                        v.x=clamp(v.x,0,1);
                        v.y=clamp(v.y,0,1);
                        v.scale=clamp(v.scale,0.65,1.50);
                        v.opacity=clamp(v.opacity,0.35,1.0);
                        PANELS.put(e.getKey(),v);
                    }
                }
            }
        }catch(Exception ignored){}
    }

    private static void defaults(){
        PANELS.put("core",new Panel(0.012,0.060,1.0,1.0));
        PANELS.put("advisor",new Panel(0.770,0.060,1.0,1.0));
        PANELS.put("identify",new Panel(0.535,0.535,1.0,1.0));
        PANELS.put("pack",new Panel(0.012,0.820,1.0,1.0));
        PANELS.put("escape",new Panel(0.500,0.900,1.0,1.0));
        PANELS.put("flight",new Panel(0.755,0.340,1.0,1.0));
        PANELS.put("recovery",new Panel(0.012,0.270,1.0,1.0));
        PANELS.put("warning",new Panel(0.500,0.035,1.0,1.0));
    }

    public static Panel panel(String key){
        load();
        return PANELS.computeIfAbsent(key,k->new Panel(0.5,0.5,1.0,1.0));
    }

    public static Map<String,Panel> all(){
        load();
        return PANELS;
    }

    public static int x(String key,int screenW,int actualW){
        Panel p=panel(key);
        double raw=p.x*screenW;
        if(centered(key))raw-=actualW*p.scale/2.0;
        return (int)Math.round(clamp(raw,2,Math.max(2,screenW-actualW*p.scale-2)));
    }

    public static int y(String key,int screenH,int actualH){
        Panel p=panel(key);
        double raw=p.y*screenH;
        return (int)Math.round(clamp(raw,2,Math.max(2,screenH-actualH*p.scale-2)));
    }

    public static boolean centered(String key){
        return "escape".equals(key)||"warning".equals(key);
    }

    public static void setScreenPosition(String key,double pixelX,double pixelY,int screenW,int screenH,int actualW,int actualH){
        Panel p=panel(key);
        if(centered(key)) p.x=clamp((pixelX+actualW*p.scale/2.0)/Math.max(1,screenW),0,1);
        else p.x=clamp(pixelX/Math.max(1,screenW),0,1);
        p.y=clamp(pixelY/Math.max(1,screenH),0,1);
    }

    public static void adjustScale(String key,double delta){
        Panel p=panel(key);
        p.scale=clamp(p.scale+delta,0.65,1.50);
    }

    public static void adjustOpacity(String key,double delta){
        Panel p=panel(key);
        p.opacity=clamp(p.opacity+delta,0.35,1.0);
    }

    public static int alpha(String key,int argb){
        double o=panel(key).opacity;
        int a=(argb>>>24)&255;
        int na=(int)Math.round(a*o);
        return (argb&0x00FFFFFF)|(Math.max(0,Math.min(255,na))<<24);
    }

    public static void reset(){
        PANELS.clear();
        defaults();
        save();
    }

    public static void save(){
        load();
        try{
            Path p=ExoLinkData.root().resolve("hud-layout.json");
            Files.writeString(p,GSON.toJson(new PanelMap(PANELS)),StandardCharsets.UTF_8);
        }catch(Exception ignored){}
    }

    public record PanelMap(Map<String,Panel> panels){}

    private static double clamp(double v,double min,double max){return Math.max(min,Math.min(max,v));}
}
