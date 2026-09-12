package com.secret.minigames.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public final class Maze3DScreen extends Screen {
    private static final String[] MAP = {
            "################",
            "#S....#....o...#",
            "#.##..#..####..#",
            "#..#..#........#",
            "##.#..####.###.#",
            "#..#.....#...#.#",
            "#.####.#.#.#.#.#",
            "#....#.#...#...#",
            "#.##.#.#####.###",
            "#o.#.#.....#...#",
            "##.#.#####.###.#",
            "#..#.....#.....#",
            "#.#####..#.###.#",
            "#.....o..#...E.#",
            "#..............#",
            "################"
    };

    private final Screen parent;
    private double x=1.5,z=1.5,angle=0;
    private long lastNanos;
    private int orbs;
    private boolean won;
    private final boolean[][] collected=new boolean[MAP.length][MAP[0].length()];

    public Maze3DScreen(Screen parent){
        super(Text.literal("Neon Maze 3D"));
        this.parent=parent;
    }

    @Override protected void init(){ lastNanos=System.nanoTime(); }

    @Override
    public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        long now=System.nanoTime();
        double dt=Math.min(0.05,Math.max(0.001,(now-lastNanos)/1_000_000_000.0));
        lastNanos=now;
        update(dt);

        int horizon=this.height/2;
        ctx.fill(0,0,this.width,horizon,0xFF090D1D);
        ctx.fill(0,horizon,this.width,this.height,0xFF111217);

        double fov=Math.toRadians(66);
        int step=Math.max(2,this.width/360);
        for(int sx=0;sx<this.width;sx+=step){
            double rayAngle=angle-fov/2+fov*((double)sx/this.width);
            double sin=Math.sin(rayAngle), cos=Math.cos(rayAngle);
            double dist=0.04;
            int hitX=0,hitZ=0;
            while(dist<18){
                hitX=(int)Math.floor(x+sin*dist);
                hitZ=(int)Math.floor(z+cos*dist);
                if(isWall(hitX,hitZ)) break;
                dist+=0.035;
            }
            double corrected=dist*Math.cos(rayAngle-angle);
            int wallH=(int)Math.min(this.height*1.3,this.height/Math.max(0.12,corrected));
            int top=horizon-wallH/2, bottom=horizon+wallH/2;
            int c=((hitX+hitZ)&1)==0?0xFF2E5E8B:0xFF3479A8;
            double fog=Math.max(0.32,1.0-corrected/18.0);
            c=shade(c,fog);
            ctx.fill(sx,Math.max(0,top),Math.min(this.width,sx+step),Math.min(this.height,bottom),c);
        }

        drawSprites(ctx);
        drawHud(ctx);
        super.render(ctx,mouseX,mouseY,delta);
    }

    private void update(double dt){
        if(this.client==null||won)return;
        long w=this.client.getWindow().getHandle();
        double turn=((down(w,GLFW.GLFW_KEY_D)?1:0)-(down(w,GLFW.GLFW_KEY_A)?1:0))*1.9*dt;
        angle+=turn;
        double forward=((down(w,GLFW.GLFW_KEY_W)?1:0)-(down(w,GLFW.GLFW_KEY_S)?1:0));
        double strafe=((down(w,GLFW.GLFW_KEY_E)?1:0)-(down(w,GLFW.GLFW_KEY_Q)?1:0));
        double speed=3.0;
        double dx=(Math.sin(angle)*forward+Math.cos(angle)*strafe)*speed*dt;
        double dz=(Math.cos(angle)*forward-Math.sin(angle)*strafe)*speed*dt;
        tryMove(dx,dz);
        int gx=(int)Math.floor(x),gz=(int)Math.floor(z);
        char tile=tile(gx,gz);
        if(tile=='o'&&!collected[gz][gx]){collected[gz][gx]=true;orbs++;}
        if(tile=='E'&&orbs>=3)won=true;
    }

    private void tryMove(double dx,double dz){
        if(!isWall((int)Math.floor(x+dx),(int)Math.floor(z)))x+=dx;
        if(!isWall((int)Math.floor(x),(int)Math.floor(z+dz)))z+=dz;
    }

    private void drawSprites(DrawContext ctx){
        for(int gz=0;gz<MAP.length;gz++) for(int gx=0;gx<MAP[gz].length();gx++){
            char t=tile(gx,gz);
            if(t!='o'&&t!='E')continue;
            if(t=='o'&&collected[gz][gx])continue;
            double ox=gx+0.5-x, oz=gz+0.5-z;
            double dist=Math.sqrt(ox*ox+oz*oz);
            double a=Math.atan2(ox,oz)-angle;
            while(a>Math.PI)a-=Math.PI*2; while(a<-Math.PI)a+=Math.PI*2;
            if(Math.abs(a)>Math.toRadians(38)||dist<0.2||dist>14)continue;
            int sx=(int)(this.width/2+(a/Math.toRadians(66))*this.width);
            int size=(int)Math.max(8,85/dist);
            int sy=this.height/2-size/2;
            int color=t=='E'?0xFFFFD34A:0xFF65F6FF;
            ctx.fill(sx-size/4,sy,sx+size/4,sy+size,color);
            ctx.fill(sx-size/2,sy+size/3,sx+size/2,sy+2*size/3,color);
        }
    }

    private void drawHud(DrawContext ctx){
        ctx.drawTextWithShadow(this.textRenderer,Text.literal("NEON MAZE 3D").formatted(Formatting.LIGHT_PURPLE,Formatting.BOLD),8,8,0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,Text.literal("Find 3 energy cores, then reach EXIT"),8,22,0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,Text.literal("Cores: "+orbs+" / 3").formatted(orbs>=3?Formatting.GREEN:Formatting.AQUA),8,36,0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,Text.literal("W/S move • A/D turn • Q/E strafe • R reset • F9/ESC hub").formatted(Formatting.GRAY),8,this.height-18,0xFFFFFF);
        int cx=this.width/2,cy=this.height/2;
        ctx.fill(cx-4,cy,cx+5,cy+1,0xFFFFFFFF);ctx.fill(cx,cy-4,cx+1,cy+5,0xFFFFFFFF);
        if(won){
            ctx.fill(this.width/2-130,this.height/2-38,this.width/2+130,this.height/2+38,0xDD000000);
            ctx.drawCenteredTextWithShadow(this.textRenderer,Text.literal("MAZE CLEARED!").formatted(Formatting.GREEN,Formatting.BOLD),this.width/2,this.height/2-10,0xFFFFFF);
            ctx.drawCenteredTextWithShadow(this.textRenderer,Text.literal("Press R to play again"),this.width/2,this.height/2+8,0xFFFFFF);
        }
    }

    private void reset(){x=1.5;z=1.5;angle=0;orbs=0;won=false;for(int y=0;y<collected.length;y++)for(int xx=0;xx<collected[y].length;xx++)collected[y][xx]=false;}
    private boolean isWall(int gx,int gz){return gx<0||gz<0||gz>=MAP.length||gx>=MAP[0].length()||tile(gx,gz)=='#';}
    private char tile(int gx,int gz){return MAP[gz].charAt(gx);}
    private static boolean down(long w,int k){return GLFW.glfwGetKey(w,k)==GLFW.GLFW_PRESS;}
    private static int shade(int c,double f){int r=(int)(((c>>16)&255)*f),g=(int)(((c>>8)&255)*f),b=(int)((c&255)*f);return 0xFF000000|(Math.min(255,r)<<16)|(Math.min(255,g)<<8)|Math.min(255,b);}

    @Override public boolean keyPressed(KeyInput input){int k=input.key();if(k==GLFW.GLFW_KEY_ESCAPE||k==GLFW.GLFW_KEY_F9){if(this.client!=null)this.client.setScreen(parent);return true;}if(k==GLFW.GLFW_KEY_R){reset();return true;}return super.keyPressed(input);}
    @Override public boolean shouldPause(){return false;}
}
