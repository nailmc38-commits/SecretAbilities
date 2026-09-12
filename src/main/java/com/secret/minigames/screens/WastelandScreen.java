package com.secret.minigames.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class WastelandScreen extends Screen {
    private static final double WORLD = 100.0;

    private final Screen parent;
    private final List<WorldObject> objects = new ArrayList<>();
    private final List<Drone> drones = new ArrayList<>();

    private double x=12,z=12,angle=0.7;
    private int health=100,rads=0,scrap=0,water=2;
    private long lastNanos;
    private long lastDamage;
    private boolean won;

    public WastelandScreen(Screen parent){
        super(Text.literal("Wasteland: Third Person"));
        this.parent=parent;
        generate();
    }

    private void generate(){
        objects.clear();drones.clear();
        Random r=new Random(381991L);
        for(int i=0;i<34;i++){
            double ox=8+r.nextDouble()*84,oz=8+r.nextDouble()*84;
            int kind=i<14?0:(i<24?1:2); // ruin, scrap, water
            objects.add(new WorldObject(ox,oz,kind,false));
        }
        objects.add(new WorldObject(88,88,3,false)); // bunker
        for(int i=0;i<7;i++)drones.add(new Drone(18+r.nextDouble()*70,18+r.nextDouble()*70,r.nextDouble()*Math.PI*2));
        x=12;z=12;angle=0.7;health=100;rads=0;scrap=0;water=2;won=false;lastDamage=0;
    }

    @Override protected void init(){lastNanos=System.nanoTime();}

    @Override
    public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        long now=System.nanoTime();
        double dt=Math.min(0.05,Math.max(0.001,(now-lastNanos)/1_000_000_000.0));
        lastNanos=now;
        update(dt);
        drawScene(ctx);
        drawHud(ctx);
        super.render(ctx,mouseX,mouseY,delta);
    }

    private void update(double dt){
        if(this.client==null||won||health<=0)return;
        long w=this.client.getWindow().getHandle();
        double turn=((down(w,GLFW.GLFW_KEY_D)?1:0)-(down(w,GLFW.GLFW_KEY_A)?1:0))*1.8*dt;
        angle+=turn;
        double forward=((down(w,GLFW.GLFW_KEY_W)?1:0)-(down(w,GLFW.GLFW_KEY_S)?1:0));
        double strafe=((down(w,GLFW.GLFW_KEY_E)?1:0)-(down(w,GLFW.GLFW_KEY_Q)?1:0));
        double speed=down(w,GLFW.GLFW_KEY_LEFT_SHIFT)?6.0:3.6;
        x=clamp(x+(Math.sin(angle)*forward+Math.cos(angle)*strafe)*speed*dt,1,WORLD-1);
        z=clamp(z+(Math.cos(angle)*forward-Math.sin(angle)*strafe)*speed*dt,1,WORLD-1);

        boolean inRad=false;
        for(WorldObject o:objects) if(o.kind==0&&distance(x,z,o.x,o.z)<4.3){inRad=true;break;}
        if(inRad)rads=Math.min(100,rads+(int)Math.ceil(7*dt)); else rads=Math.max(0,rads-(int)Math.ceil(2*dt));
        if(rads>=100)health=Math.max(0,health-1);

        for(Drone d:drones){
            double dist=distance(x,z,d.x,d.z);
            if(dist<15){
                double a=Math.atan2(x-d.x,z-d.z);
                d.x+=Math.sin(a)*1.1*dt;d.z+=Math.cos(a)*1.1*dt;
            }else{
                d.x+=Math.sin(d.angle)*0.45*dt;d.z+=Math.cos(d.angle)*0.45*dt;
                if(d.x<4||d.x>96||d.z<4||d.z>96)d.angle+=Math.PI*0.7;
            }
            if(dist<1.25&&System.currentTimeMillis()-lastDamage>900){health=Math.max(0,health-6);lastDamage=System.currentTimeMillis();}
        }

        if(water>0&&rads>75&&System.currentTimeMillis()%1400<30){water--;rads=Math.max(0,rads-18);}
        if(scrap>=10&&distance(x,z,88,88)<2.8)won=true;
    }

    private void interact(){
        if(won||health<=0)return;
        WorldObject nearest=null;double best=2.4;
        for(WorldObject o:objects){
            if(o.taken||o.kind==0||o.kind==3)continue;
            double d=distance(x,z,o.x,o.z);if(d<best){best=d;nearest=o;}
        }
        if(nearest!=null){nearest.taken=true;if(nearest.kind==1)scrap+=2;else if(nearest.kind==2)water++;}
        if(distance(x,z,88,88)<2.8&&scrap>=10)won=true;
    }

    private void drink(){if(water>0){water--;rads=Math.max(0,rads-25);health=Math.min(100,health+8);}}

    private void drawScene(DrawContext ctx){
        int horizon=(int)(this.height*0.37);
        ctx.fill(0,0,this.width,horizon,0xFFB47A48);
        ctx.fill(0,horizon,this.width,this.height,0xFF715437);
        ctx.fill(0,horizon-5,this.width,horizon+5,0xFF8E5B3C);

        for(int i=1;i<=8;i++){
            int y=horizon+(int)((this.height-horizon)*(1.0-1.0/(1.0+i*0.42)));
            ctx.fill(0,y,this.width,y+1,0x44705A43);
        }
        for(int i=-6;i<=6;i++){
            int x1=this.width/2+i*22;
            ctx.fill(x1,horizon,x1+1,this.height,0x33705A43);
        }

        List<RenderObj> draw=new ArrayList<>();
        for(WorldObject o:objects)if(!o.taken)project(draw,o.x,o.z,o.kind);
        for(Drone d:drones)project(draw,d.x,d.z,4);
        draw.sort(Comparator.comparingDouble((RenderObj r)->r.dist).reversed());

        for(RenderObj r:draw){
            int base=(int)Math.max(10,135/r.dist);
            int h=r.kind==0?base*2:(r.kind==3?base*3/2:base);
            int w=r.kind==0?base:(r.kind==3?base*2:base);
            int sx=r.sx-w/2,sy=(int)(horizon+(this.height-horizon)*0.58)-h;
            int c=switch(r.kind){
                case 0->0xFF6B6257; // ruins
                case 1->0xFFD8A94A; // scrap
                case 2->0xFF4AA7D8; // water
                case 3->0xFF44594D; // bunker
                default->0xFFB14F4F; // drone
            };
            ctx.fill(sx,sy,sx+w,sy+h,c);
            if(r.kind==0)ctx.fill(sx+w/4,sy+h/3,sx+3*w/4,sy+h,0xFF4D4842);
            if(r.kind==4){ctx.fill(sx-w/3,sy+h/3,sx+w+w/3,sy+h/2,0xFF8C3C3C);ctx.fill(sx+w/3,sy+h/4,sx+2*w/3,sy+h/2,0xFFFFC857);}
        }

        // Third-person player, always visible from behind.
        int px=this.width/2,py=this.height-78;
        ctx.fill(px-9,py-30,px+9,py-12,0xFF3D5568);
        ctx.fill(px-7,py-46,px+7,py-31,0xFFC7956D);
        ctx.fill(px-15,py-27,px-8,py-9,0xFF4A6477);
        ctx.fill(px+8,py-27,px+15,py-9,0xFF4A6477);
        ctx.fill(px-8,py-11,px-1,py+9,0xFF293944);
        ctx.fill(px+1,py-11,px+8,py+9,0xFF293944);
    }

    private void project(List<RenderObj> out,double ox,double oz,int kind){
        double dx=ox-x,dz=oz-z,dist=Math.sqrt(dx*dx+dz*dz);if(dist<0.4||dist>35)return;
        double a=Math.atan2(dx,dz)-angle;while(a>Math.PI)a-=Math.PI*2;while(a<-Math.PI)a+=Math.PI*2;
        double fov=Math.toRadians(72);if(Math.abs(a)>fov*0.62)return;
        int sx=(int)(this.width/2+(a/fov)*this.width);
        out.add(new RenderObj(sx,dist,kind));
    }

    private void drawHud(DrawContext ctx){
        ctx.fill(0,0,this.width,50,0xB0000000);
        ctx.drawTextWithShadow(this.textRenderer,Text.literal("WASTELAND: THIRD PERSON").formatted(Formatting.GOLD,Formatting.BOLD),8,7,0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,Text.literal("Original wasteland scavenger RPG • collect 10 scrap and reach the bunker"),8,20,0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,Text.literal("HP "+health+"   RAD "+rads+"   Scrap "+scrap+"/10   Water "+water),8,34,0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,Text.literal("W/S move • A/D turn • Q/E strafe • Shift sprint • F scavenge • C drink • R restart • F9/ESC hub").formatted(Formatting.GRAY),8,this.height-18,0xFFFFFF);
        if(distance(x,z,88,88)<6)ctx.drawCenteredTextWithShadow(this.textRenderer,Text.literal(scrap>=10?"BUNKER READY - get closer":"BUNKER LOCKED - need 10 scrap").formatted(scrap>=10?Formatting.GREEN:Formatting.RED),this.width/2,62,0xFFFFFF);
        if(won){ctx.fill(this.width/2-150,this.height/2-35,this.width/2+150,this.height/2+35,0xE0000000);ctx.drawCenteredTextWithShadow(this.textRenderer,Text.literal("BUNKER REACHED!").formatted(Formatting.GREEN,Formatting.BOLD),this.width/2,this.height/2-8,0xFFFFFF);ctx.drawCenteredTextWithShadow(this.textRenderer,Text.literal("You survived the wasteland. Press R to replay."),this.width/2,this.height/2+9,0xFFFFFF);}
        if(health<=0){ctx.fill(this.width/2-150,this.height/2-35,this.width/2+150,this.height/2+35,0xE0000000);ctx.drawCenteredTextWithShadow(this.textRenderer,Text.literal("RUN ENDED").formatted(Formatting.RED,Formatting.BOLD),this.width/2,this.height/2-8,0xFFFFFF);ctx.drawCenteredTextWithShadow(this.textRenderer,Text.literal("Press R to restart"),this.width/2,this.height/2+9,0xFFFFFF);}
    }

    private static boolean down(long w,int k){return GLFW.glfwGetKey(w,k)==GLFW.GLFW_PRESS;}
    private static double distance(double ax,double az,double bx,double bz){double dx=ax-bx,dz=az-bz;return Math.sqrt(dx*dx+dz*dz);}
    private static double clamp(double v,double min,double max){return Math.max(min,Math.min(max,v));}

    @Override public boolean keyPressed(KeyInput input){int k=input.key();if(k==GLFW.GLFW_KEY_ESCAPE||k==GLFW.GLFW_KEY_F9){if(this.client!=null)this.client.setScreen(parent);return true;}if(k==GLFW.GLFW_KEY_F){interact();return true;}if(k==GLFW.GLFW_KEY_C){drink();return true;}if(k==GLFW.GLFW_KEY_R){generate();return true;}return super.keyPressed(input);}
    @Override public boolean shouldPause(){return false;}

    private static final class WorldObject{double x,z;int kind;boolean taken;WorldObject(double x,double z,int kind,boolean taken){this.x=x;this.z=z;this.kind=kind;this.taken=taken;}}
    private static final class Drone{double x,z,angle;Drone(double x,double z,double angle){this.x=x;this.z=z;this.angle=angle;}}
    private record RenderObj(int sx,double dist,int kind){}
}
