package com.survivalutils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class Echo3DScreen extends Screen {
    private final Screen parent;
    private double x,z,angle;
    private int scanY;
    private double originX,originZ;
    private long lastNanos;

    public Echo3DScreen(Screen parent) {
        super(Text.literal("SURV // ECHO 3D"));
        this.parent=parent;
    }

    @Override
    protected void init() {
        if(client!=null&&client.player!=null) {
            x=client.player.getX();
            z=client.player.getZ();
            originX=x;originZ=z;
            angle=Math.toRadians(180-client.player.getYaw());
            scanY=client.player.getBlockY()+1;
        }
        lastNanos=System.nanoTime();
    }

    @Override
    public void render(DrawContext ctx,int mouseX,int mouseY,float delta) {
        if(client==null||client.world==null||client.player==null) {
            ctx.fill(0,0,width,height,0xFF05080C);
            ctx.drawCenteredTextWithShadow(textRenderer,Text.literal("ECHO requires an active world."),width/2,height/2,0xFFFFFFFF);
            super.render(ctx,mouseX,mouseY,delta);
            return;
        }

        long now=System.nanoTime();
        double dt=Math.min(0.05,Math.max(0.001,(now-lastNanos)/1_000_000_000.0));
        lastNanos=now;
        update(dt);

        int horizon=height/2;
        ctx.fill(0,0,width,horizon,0xFF07131C);
        ctx.fill(0,horizon,width,height,0xFF11191D);

        double fov=Math.toRadians(72);
        int step=Math.max(2,width/420);
        for(int sx=0;sx<width;sx+=step) {
            double rayAngle=angle-fov/2+fov*((double)sx/width);
            double sin=Math.sin(rayAngle),cos=Math.cos(rayAngle);
            double dist=0.08;
            BlockPos hit=null;
            while(dist<42) {
                int bx=(int)Math.floor(x+sin*dist);
                int bz=(int)Math.floor(z+cos*dist);
                BlockPos p=new BlockPos(bx,scanY,bz);
                var state=client.world.getBlockState(p);
                if(!state.isAir()&&!state.getCollisionShape(client.world,p).isEmpty()) {hit=p;break;}
                dist+=0.06;
            }
            if(hit==null)continue;
            double corrected=dist*Math.cos(rayAngle-angle);
            int wallH=(int)Math.min(height*1.6,height/Math.max(0.12,corrected));
            int top=horizon-wallH/2,bottom=horizon+wallH/2;
            int base=blockColor(hit);
            double fog=Math.max(0.18,1.0-corrected/45.0);
            int color=shade(base,fog);
            ctx.fill(sx,Math.max(0,top),Math.min(width,sx+step),Math.min(height,bottom),color);
        }

        drawEntities(ctx,fov);
        drawHud(ctx);
        super.render(ctx,mouseX,mouseY,delta);
    }

    private void update(double dt) {
        long w=client.getWindow().getHandle();
        double turn=((down(w,GLFW.GLFW_KEY_RIGHT)?1:0)-(down(w,GLFW.GLFW_KEY_LEFT)?1:0))*1.75*dt;
        angle+=turn;
        double forward=((down(w,GLFW.GLFW_KEY_W)?1:0)-(down(w,GLFW.GLFW_KEY_S)?1:0));
        double strafe=((down(w,GLFW.GLFW_KEY_D)?1:0)-(down(w,GLFW.GLFW_KEY_A)?1:0));
        double speed=7.0;
        double dx=(Math.sin(angle)*forward+Math.cos(angle)*strafe)*speed*dt;
        double dz=(Math.cos(angle)*forward-Math.sin(angle)*strafe)*speed*dt;
        tryMove(dx,dz);
        if(down(w,GLFW.GLFW_KEY_PAGE_UP))scanY++;
        if(down(w,GLFW.GLFW_KEY_PAGE_DOWN))scanY--;
        scanY=Math.max(client.world.getBottomY()+1,Math.min(client.world.getTopYInclusive()-1,scanY));
    }

    private void tryMove(double dx,double dz) {
        double nx=x+dx,nz=z+dz;
        double max=48;
        if(Math.abs(nx-originX)>max||Math.abs(nz-originZ)>max)return;
        x=nx;z=nz;
    }

    private void drawEntities(DrawContext ctx,double fov) {
        for(Entity e:client.world.getEntities()) {
            if(e==client.player)continue;
            double ox=e.getX()-x,oz=e.getZ()-z;
            double dist=Math.sqrt(ox*ox+oz*oz);
            if(dist<0.2||dist>40)continue;
            double a=Math.atan2(ox,oz)-angle;
            while(a>Math.PI)a-=Math.PI*2;
            while(a<-Math.PI)a+=Math.PI*2;
            if(Math.abs(a)>fov/2)continue;
            int sx=(int)(width/2+(a/fov)*width);
            int size=(int)Math.max(4,42/dist);
            int sy=height/2-size/2;
            int color=e instanceof PlayerEntity?0xFF72E6FF:0xFFFFB65A;
            ctx.fill(sx-size/2,sy,sx+size/2+1,sy+size,color);
        }
    }

    private int blockColor(BlockPos pos) {
        String id=Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).getPath();
        if(id.contains("lava"))return 0xFFFF5B35;
        if(id.contains("water"))return 0xFF377CCB;
        if(id.contains("grass")||id.contains("leaves"))return 0xFF4D8A52;
        if(id.contains("sand"))return 0xFFC9B677;
        if(id.contains("deepslate"))return 0xFF3B4248;
        if(id.contains("stone"))return 0xFF6D747A;
        if(id.contains("wood")||id.contains("plank")||id.contains("log"))return 0xFF8D6747;
        if(id.contains("ore"))return 0xFF7DB4C8;
        return 0xFF53636D;
    }

    private void drawHud(DrawContext ctx) {
        ctx.fill(0,0,width,42,0xB8070C11);
        ctx.drawTextWithShadow(textRenderer,Text.literal("SURV // ECHO 3D").formatted(Formatting.AQUA,Formatting.BOLD),10,9,0xFFFFFFFF);
        String pos=String.format(Locale.ROOT,"CAM %.1f  Y %d  %.1f",x,scanY,z);
        ctx.drawTextWithShadow(textRenderer,pos,10,24,0xFF9FB4C0);
        String info="WASD move • ←/→ turn • PgUp/PgDn slice Y • R reset • F9/ESC return";
        ctx.drawTextWithShadow(textRenderer,info,Math.max(10,width-textRenderer.getWidth(info)-10),24,0xFF718894);

        int cx=width/2,cy=height/2;
        ctx.fill(cx-5,cy,cx+6,cy+1,0xFF7DE3FF);
        ctx.fill(cx,cy-5,cx+1,cy+6,0xFF7DE3FF);

        BlockPos p=centerHit();
        if(p!=null) {
            String id=Registries.BLOCK.getId(client.world.getBlockState(p).getBlock()).getPath();
            String s=id+" // "+p.getX()+" "+p.getY()+" "+p.getZ();
            ctx.fill(cx-4,cy+14,cx+textRenderer.getWidth(s)+8,cy+29,0xB3070C11);
            ctx.drawTextWithShadow(textRenderer,s,cx+2,cy+18,0xFFBFE8FF);
        }
    }

    private BlockPos centerHit() {
        double sin=Math.sin(angle),cos=Math.cos(angle);
        for(double d=.08;d<42;d+=.06) {
            BlockPos p=new BlockPos((int)Math.floor(x+sin*d),scanY,(int)Math.floor(z+cos*d));
            var state=client.world.getBlockState(p);
            if(!state.isAir()&&!state.getCollisionShape(client.world,p).isEmpty())return p;
        }
        return null;
    }

    private void reset() {
        if(client!=null&&client.player!=null) {
            x=client.player.getX();z=client.player.getZ();originX=x;originZ=z;
            angle=Math.toRadians(180-client.player.getYaw());
            scanY=client.player.getBlockY()+1;
        }
    }

    private static boolean down(long w,int key){return GLFW.glfwGetKey(w,key)==GLFW.GLFW_PRESS;}
    private static int shade(int c,double f){int r=(int)(((c>>16)&255)*f),g=(int)(((c>>8)&255)*f),b=(int)((c&255)*f);return 0xFF000000|(Math.min(255,r)<<16)|(Math.min(255,g)<<8)|Math.min(255,b);}

    @Override
    public boolean keyPressed(KeyInput input) {
        int k=input.key();
        if(k==GLFW.GLFW_KEY_ESCAPE||k==GLFW.GLFW_KEY_F9){if(client!=null)client.setScreen(parent);return true;}
        if(k==GLFW.GLFW_KEY_R){reset();return true;}
        return super.keyPressed(input);
    }
    @Override public boolean shouldPause(){return false;}
}
