package com.survivalutils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
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
        super(Text.literal("EXO // ECHO 3D"));
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

    private int blockColor(BlockPos pos) {
        String id=Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).getPath();

        if(id.contains("lava"))return 0xFFFF5638;
        if(id.contains("water"))return 0xFF3E86D8;
        if(id.contains("diamond_ore"))return 0xFF5DEAF1;
        if(id.contains("emerald_ore"))return 0xFF55D982;
        if(id.contains("gold_ore")||id.contains("raw_gold"))return 0xFFE7C34E;
        if(id.contains("iron_ore")||id.contains("raw_iron"))return 0xFFD9B58F;
        if(id.contains("redstone_ore"))return 0xFFE34D4D;
        if(id.contains("lapis_ore"))return 0xFF426FD1;
        if(id.contains("copper_ore"))return 0xFFC77B52;
        if(id.contains("coal_ore"))return 0xFF30373A;
        if(id.contains("amethyst"))return 0xFF9D72CF;
        if(id.contains("obsidian"))return 0xFF312B45;
        if(id.contains("glass"))return 0xFF9AC6CF;
        if(id.contains("brick"))return 0xFF9B584D;
        if(id.contains("netherrack"))return 0xFF8D3D43;
        if(id.contains("basalt")||id.contains("blackstone"))return 0xFF414146;
        if(id.contains("end_stone"))return 0xFFD8D7A4;
        if(id.contains("grass")||id.contains("moss")||id.contains("leaves"))return 0xFF4F9758;
        if(id.contains("dirt")||id.contains("mud"))return 0xFF705341;
        if(id.contains("sand"))return 0xFFD2BE7E;
        if(id.contains("snow")||id.contains("ice"))return 0xFFCBE9F1;
        if(id.contains("deepslate"))return 0xFF3B4248;
        if(id.contains("stone")||id.contains("cobblestone"))return 0xFF71797E;
        if(id.contains("wood")||id.contains("plank")||id.contains("log"))return 0xFF966B47;
        if(id.contains("wool"))return 0xFFB6B6B6;
        if(id.contains("ore"))return 0xFF80B4C6;
        return 0xFF586970;
    }

    private void drawHud(DrawContext ctx) {
        ctx.fill(0,0,width,42,0xB8070C11);
        ctx.drawTextWithShadow(textRenderer,Text.literal("EXO // ECHO 3D").formatted(Formatting.AQUA,Formatting.BOLD),10,9,0xFFFFFFFF);
        String pos=String.format(Locale.ROOT,"CAM %.1f  Y %d  %.1f",x,scanY,z);
        ctx.drawTextWithShadow(textRenderer,pos,10,24,0xFF9FB4C0);
        String info="WASD move • ←/→ turn • PgUp/PgDn Y-slice • R reset • F9/ESC return";
        ctx.drawTextWithShadow(textRenderer,info,Math.max(10,width-textRenderer.getWidth(info)-10),24,0xFF718894);

        int cx=width/2,cy=height/2;
        ctx.fill(cx-5,cy,cx+6,cy+1,0xFF7DE3FF);
        ctx.fill(cx,cy-5,cx+1,cy+6,0xFF7DE3FF);

        drawMiniMap(ctx);
        drawLegend(ctx);

        BlockPos p=centerHit();
        if(p!=null) {
            String id=Registries.BLOCK.getId(client.world.getBlockState(p).getBlock()).getPath();
            String s=id+" // "+p.getX()+" "+p.getY()+" "+p.getZ();
            ctx.fill(cx-4,cy+14,cx+textRenderer.getWidth(s)+8,cy+29,0xB3070C11);
            ctx.drawTextWithShadow(textRenderer,s,cx+2,cy+18,0xFFBFE8FF);
        }
    }

    private void drawMiniMap(DrawContext ctx) {
        int size=74;
        int scale=2;
        int cells=size/scale;
        int left=10;
        int top=50;

        ctx.fill(left-3,top-3,left+size+3,top+size+3,0xB8070C11);
        ctx.fill(left-3,top-3,left+size+3,top-1,0xFF5DDDE8);

        int half=cells/2;
        for(int dz=-half;dz<half;dz++) {
            for(int dx=-half;dx<half;dx++) {
                BlockPos p=new BlockPos((int)Math.floor(x)+dx,scanY,(int)Math.floor(z)+dz);
                var state=client.world.getBlockState(p);
                if(state.isAir())continue;
                int color=blockColor(p);
                int px=left+(dx+half)*scale;
                int py=top+(dz+half)*scale;
                ctx.fill(px,py,px+scale,py+scale,color);
            }
        }

        int cx=left+size/2,cy=top+size/2;
        ctx.fill(cx-1,cy-1,cx+2,cy+2,0xFFFFFFFF);
    }

    private void drawLegend(DrawContext ctx) {
        int x=10;
        int y=130;
        String[] names={"STONE","WOOD","WATER","LAVA","ORE","OBSIDIAN"};
        int[] colors={0xFF71797E,0xFF966B47,0xFF3E86D8,0xFFFF5638,0xFF5DEAF1,0xFF312B45};
        ctx.fill(x-3,y-3,x+112,y+names.length*12+5,0xA8070C11);
        for(int i=0;i<names.length;i++) {
            ctx.fill(x,y+i*12,x+7,y+i*12+7,colors[i]);
            ctx.drawTextWithShadow(textRenderer,names[i],x+12,y+i*12-1,0xFF9FB4C0);
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
