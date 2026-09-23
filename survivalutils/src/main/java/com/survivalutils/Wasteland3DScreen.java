package com.survivalutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class Wasteland3DScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE = FabricLoader.getInstance().getConfigDir().resolve("surv-os").resolve("wasteland-save.json");

    private static final String[] MAP = {
            "################################",
            "#B.....#.............#.........#",
            "#......#..SS.........#....T....#",
            "#......#.............#.........#",
            "#......#####..############..####",
            "#..............................#",
            "#.............####.............#",
            "#..S..........#..#......S......#",
            "#.............#..#.............#",
            "#.....#########..#########.....#",
            "#..............................#",
            "#...........A..................#",
            "#..............................#",
            "#....#####...........#####.....#",
            "#....#...#.....F.....#...#.....#",
            "#....#...#...........#...#.....#",
            "#....#####...........#####.....#",
            "#..............................#",
            "#.........S....................#",
            "#.........................T....#",
            "#..............................#",
            "#.....######........######.....#",
            "#.....#....#........#....#.....#",
            "#.....#....#....S...#....#.....#",
            "#.....######........######.....#",
            "#..............................#",
            "#.............S................#",
            "#..............................#",
            "#.......................F......#",
            "#..............................#",
            "#..............................#",
            "################################"
    };

    private final Screen parent;
    private double x=2.5,z=2.5,angle=0;
    private long lastNanos;
    private long lastSave;
    private int health=100;
    private int hunger=80;
    private int water=80;
    private int supplies=2;
    private int fusionCores;
    private int credits=20;
    private boolean foundArmor;
    private boolean armorPowered;
    private boolean metTown;
    private boolean storySignal;
    private int day=1;
    private final Set<String> collected = new HashSet<>();
    private final ArrayDeque<String> messages = new ArrayDeque<>();

    private static final class SaveData {
        double x,z,angle;
        int health,hunger,water,supplies,fusionCores,credits,day;
        boolean foundArmor,armorPowered,metTown,storySignal;
        Set<String> collected;
    }

    public Wasteland3DScreen(Screen parent) {
        super(Text.literal("SURV // WASTELAND 3D"));
        this.parent=parent;
        load();
    }

    @Override protected void init(){lastNanos=System.nanoTime();lastSave=System.currentTimeMillis();}

    @Override
    public void render(DrawContext ctx,int mouseX,int mouseY,float delta) {
        long now=System.nanoTime();
        double dt=Math.min(0.05,Math.max(0.001,(now-lastNanos)/1_000_000_000.0));
        lastNanos=now;
        update(dt);

        int horizon=height/2;
        ctx.fill(0,0,width,horizon,0xFF72604E);
        ctx.fill(0,horizon,width,height,0xFF3B352F);

        double fov=Math.toRadians(68);
        int step=Math.max(2,width/420);
        for(int sx=0;sx<width;sx+=step){
            double rayAngle=angle-fov/2+fov*((double)sx/width);
            double sin=Math.sin(rayAngle),cos=Math.cos(rayAngle);
            double dist=.04;
            int hx=0,hz=0;
            while(dist<24){
                hx=(int)Math.floor(x+sin*dist);
                hz=(int)Math.floor(z+cos*dist);
                if(isWall(hx,hz))break;
                dist+=.035;
            }
            double corrected=dist*Math.cos(rayAngle-angle);
            int wallH=(int)Math.min(height*1.45,height/Math.max(.12,corrected));
            int top=horizon-wallH/2,bottom=horizon+wallH/2;
            int base=((hx+hz)&1)==0?0xFF6B625B:0xFF81766B;
            double fog=Math.max(.26,1.0-corrected/24.0);
            ctx.fill(sx,Math.max(0,top),Math.min(width,sx+step),Math.min(height,bottom),shade(base,fog));
        }

        drawWorldMarkers(ctx,fov);
        drawHud(ctx);

        if(System.currentTimeMillis()-lastSave>10000L){save();lastSave=System.currentTimeMillis();}
        super.render(ctx,mouseX,mouseY,delta);
    }

    private void update(double dt){
        if(client==null)return;
        long w=client.getWindow().getHandle();
        double turn=((down(w,GLFW.GLFW_KEY_RIGHT)?1:0)-(down(w,GLFW.GLFW_KEY_LEFT)?1:0))*1.8*dt;
        angle+=turn;
        double forward=((down(w,GLFW.GLFW_KEY_W)?1:0)-(down(w,GLFW.GLFW_KEY_S)?1:0));
        double strafe=((down(w,GLFW.GLFW_KEY_D)?1:0)-(down(w,GLFW.GLFW_KEY_A)?1:0));
        double speed=armorPowered?3.6:3.0;
        tryMove((Math.sin(angle)*forward+Math.cos(angle)*strafe)*speed*dt,
                (Math.cos(angle)*forward-Math.sin(angle)*strafe)*speed*dt);

        if((Math.abs(forward)+Math.abs(strafe))>0){
            if((System.currentTimeMillis()/1000)%8==0){
                hunger=Math.max(0,hunger-1);
                water=Math.max(0,water-1);
                if(hunger==0||water==0)health=Math.max(1,health-1);
            }
        }
    }

    private void tryMove(double dx,double dz){
        if(!isWall((int)Math.floor(x+dx),(int)Math.floor(z)))x+=dx;
        if(!isWall((int)Math.floor(x),(int)Math.floor(z+dz)))z+=dz;
    }

    private void interact(){
        int gx=(int)Math.floor(x),gz=(int)Math.floor(z);
        char t=tile(gx,gz);
        String key=gx+","+gz;

        if(t=='B'){
            hunger=Math.min(100,hunger+12);
            water=Math.min(100,water+12);
            add("UNDER-BRIDGE HOME // supplies checked and gear rested.");
            day++;
            save();
            return;
        }

        if(t=='S'&&!collected.contains(key)){
            collected.add(key);
            supplies+=1;
            credits+=4;
            hunger=Math.min(100,hunger+8);
            water=Math.min(100,water+8);
            add("SUPPLY CACHE // food, water and useful parts recovered.");
            return;
        }

        if(t=='F'&&!collected.contains(key)){
            collected.add(key);
            fusionCores++;
            add("FUSION CORE RECOVERED // cores "+fusionCores);
            if(foundArmor&&!armorPowered) add("POWER ARMOR CAN NOW BE ACTIVATED AT ITS FRAME.");
            return;
        }

        if(t=='A'){
            foundArmor=true;
            if(!armorPowered&&fusionCores>0){
                fusionCores--;
                armorPowered=true;
                add("POWER ARMOR // CORE INSTALLED // SYSTEM ONLINE.");
            }else if(!armorPowered){
                add("POWER ARMOR // NO FUSION CORE. Find a core before the frame can move.");
            }else{
                add("POWER ARMOR // ACTIVE // integrity stable.");
            }
            return;
        }

        if(t=='T'){
            metTown=true;
            credits+=2;
            add("TOWN // traders share water, repairs and rumors.");
            if(supplies>0){
                supplies--;
                hunger=Math.min(100,hunger+18);
                water=Math.min(100,water+18);
                health=Math.min(100,health+8);
                add("You trade one supply bundle for food, clean water and basic repairs.");
            }
            if(!storySignal){
                storySignal=true;
                add("RUMOR // a repeating radio signal was heard beyond the eastern ruins.");
            }
            return;
        }

        add("Nothing here needs attention.");
    }

    private void drawWorldMarkers(DrawContext ctx,double fov){
        for(int gz=0;gz<MAP.length;gz++)for(int gx=0;gx<MAP[gz].length();gx++){
            char t=tile(gx,gz);
            if(t!='S'&&t!='F'&&t!='A'&&t!='T'&&t!='B')continue;
            String key=gx+","+gz;
            if((t=='S'||t=='F')&&collected.contains(key))continue;
            double ox=gx+.5-x,oz=gz+.5-z;
            double dist=Math.sqrt(ox*ox+oz*oz);
            if(dist<.2||dist>20)continue;
            double a=Math.atan2(ox,oz)-angle;
            while(a>Math.PI)a-=Math.PI*2;while(a<-Math.PI)a+=Math.PI*2;
            if(Math.abs(a)>fov/2)continue;
            int sx=(int)(width/2+(a/fov)*width);
            int size=(int)Math.max(8,64/dist);
            int sy=height/2-size/2;
            int color=switch(t){
                case 'F'->0xFF9CFF7B;
                case 'A'->0xFF7DE3FF;
                case 'T'->0xFFFFD36A;
                case 'B'->0xFFBFA5FF;
                default->0xFFE4C98D;
            };
            ctx.fill(sx-size/3,sy,sx+size/3,sy+size,color);
            ctx.fill(sx-size/2,sy+size/3,sx+size/2,sy+2*size/3,color);
        }
    }

    private void drawHud(DrawContext ctx){
        ctx.fill(0,0,width,49,0xC90A0E10);
        ctx.drawTextWithShadow(textRenderer,Text.literal("SURV // WASTELAND 3D").formatted(Formatting.GOLD,Formatting.BOLD),10,8,0xFFFFFFFF);

        String stats="HP "+health+"  FOOD "+hunger+"  WATER "+water+"  SUP "+supplies+"  CORE "+fusionCores+"  CR "+credits+"  DAY "+day;
        ctx.drawTextWithShadow(textRenderer,stats,10,23,0xFFE7DCC8);
        String pa="POWER ARMOR // "+(armorPowered?"ONLINE":foundArmor?"FRAME FOUND":"NOT FOUND");
        ctx.drawTextWithShadow(textRenderer,pa,10,36,armorPowered?0xFF7DE3FF:0xFF9C9185);

        int yy=60;
        for(String s:messages){
            ctx.drawTextWithShadow(textRenderer,"> "+s,10,yy,0xFFD8C9B7);
            yy+=13;
        }

        String objective=storySignal
                ?"STORY // The eastern radio signal is waiting. Exploring is optional."
                :foundArmor&&!armorPowered?"GOAL // Find a fusion core or ignore the frame and keep surviving."
                :!foundArmor?"GOAL // Explore. Your under-bridge shelter is marked purple."
                :"GOAL // Survive, trade and explore.";
        ctx.drawCenteredTextWithShadow(textRenderer,objective,width/2,height-35,0xFFFFD36A);
        ctx.drawCenteredTextWithShadow(textRenderer,"WASD move • ←/→ turn • E interact • F5 save • F9/ESC return",width/2,height-20,0xFFB1A79C);

        int cx=width/2,cy=height/2;
        ctx.fill(cx-4,cy,cx+5,cy+1,0xFFFFFFFF);
        ctx.fill(cx,cy-4,cx+1,cy+5,0xFFFFFFFF);
    }

    private void add(String s){
        messages.addFirst(s);
        while(messages.size()>8)messages.removeLast();
    }

    private void save(){
        try{
            Files.createDirectories(SAVE.getParent());
            SaveData d=new SaveData();
            d.x=x;d.z=z;d.angle=angle;d.health=health;d.hunger=hunger;d.water=water;d.supplies=supplies;
            d.fusionCores=fusionCores;d.credits=credits;d.day=day;d.foundArmor=foundArmor;d.armorPowered=armorPowered;
            d.metTown=metTown;d.storySignal=storySignal;d.collected=new HashSet<>(collected);
            Files.writeString(SAVE,GSON.toJson(d),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        }catch(Exception e){add("SAVE WARNING // could not save.");}
    }

    private void load(){
        try{
            if(!Files.exists(SAVE)){
                add("You wake beneath the highway bridge. The wasteland is quiet.");
                add("Your parents are gone. What happens next is up to you.");
                return;
            }
            SaveData d=GSON.fromJson(Files.readString(SAVE),SaveData.class);
            if(d==null)return;
            x=d.x;z=d.z;angle=d.angle;health=d.health;hunger=d.hunger;water=d.water;supplies=d.supplies;
            fusionCores=d.fusionCores;credits=d.credits;day=Math.max(1,d.day);foundArmor=d.foundArmor;armorPowered=d.armorPowered;
            metTown=d.metTown;storySignal=d.storySignal;if(d.collected!=null)collected.addAll(d.collected);
            add("SAVE LOADED // bridge survivor record restored.");
        }catch(Exception e){add("SAVE COULD NOT BE READ // starting with current defaults.");}
    }

    private boolean isWall(int gx,int gz){return gx<0||gz<0||gz>=MAP.length||gx>=MAP[0].length()||tile(gx,gz)=='#';}
    private char tile(int gx,int gz){return MAP[gz].charAt(gx);}
    private static boolean down(long w,int k){return GLFW.glfwGetKey(w,k)==GLFW.GLFW_PRESS;}
    private static int shade(int c,double f){int r=(int)(((c>>16)&255)*f),g=(int)(((c>>8)&255)*f),b=(int)((c&255)*f);return 0xFF000000|(Math.min(255,r)<<16)|(Math.min(255,g)<<8)|Math.min(255,b);}

    @Override
    public boolean keyPressed(KeyInput input){
        int k=input.key();
        if(k==GLFW.GLFW_KEY_ESCAPE||k==GLFW.GLFW_KEY_F9){save();if(client!=null)client.setScreen(parent);return true;}
        if(k==GLFW.GLFW_KEY_E){interact();return true;}
        if(k==GLFW.GLFW_KEY_F5){save();add("GAME SAVED.");return true;}
        return super.keyPressed(input);
    }

    @Override public void close(){save();super.close();}
    @Override public boolean shouldPause(){return false;}
}
