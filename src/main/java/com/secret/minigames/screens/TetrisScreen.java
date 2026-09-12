package com.secret.minigames.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public final class TetrisScreen extends Screen {
    private static final int W = 10, H = 20;
    private static final int[][][] SHAPES = {
            {{0,1},{1,1},{2,1},{3,1}},
            {{0,0},{0,1},{1,1},{2,1}},
            {{2,0},{0,1},{1,1},{2,1}},
            {{1,0},{2,0},{1,1},{2,1}},
            {{1,0},{2,0},{0,1},{1,1}},
            {{1,0},{0,1},{1,1},{2,1}},
            {{0,0},{1,0},{1,1},{2,1}}
    };
    private static final int[] COLORS = {
            0xFF44DDEE, 0xFF3F67FF, 0xFFFFA12E, 0xFFFFE347, 0xFF55D965, 0xFFB650E8, 0xFFE84B4B
    };

    private final Screen parent;
    private final int[][] board = new int[H][W];
    private final Random random = new Random();
    private int type, rot, px, py, score, lines;
    private boolean gameOver;
    private long lastDrop;

    public TetrisScreen(Screen parent) {
        super(Text.literal("Block Drop"));
        this.parent = parent;
        reset();
    }

    private void reset() {
        for (int y=0;y<H;y++) for (int x=0;x<W;x++) board[y][x]=0;
        score=0; lines=0; gameOver=false; spawn(); lastDrop=System.currentTimeMillis();
    }

    private void spawn() {
        type=random.nextInt(SHAPES.length); rot=0; px=3; py=0;
        if (collides(px,py,rot)) gameOver=true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0,0,this.width,this.height,0xFF0C1018);
        long now=System.currentTimeMillis();
        long interval=Math.max(120,650-lines*12L);
        if(!gameOver && now-lastDrop>=interval){ stepDown(); lastDrop=now; }

        int cell=Math.max(12,Math.min(22,(this.height-60)/H));
        int bw=W*cell, bh=H*cell;
        int left=this.width/2-bw/2, top=this.height/2-bh/2;
        ctx.fill(left-4,top-4,left+bw+4,top+bh+4,0xFF222938);
        ctx.fill(left,top,left+bw,top+bh,0xFF090C12);

        for(int y=0;y<H;y++) for(int x=0;x<W;x++) if(board[y][x]!=0) drawCell(ctx,left,top,cell,x,y,COLORS[board[y][x]-1]);
        if(!gameOver) for(int[] p:SHAPES[type]){
            int[] r=rotate(p[0],p[1],rot);
            drawCell(ctx,left,top,cell,px+r[0],py+r[1],COLORS[type]);
        }

        ctx.drawTextWithShadow(this.textRenderer,Text.literal("BLOCK DROP").formatted(Formatting.AQUA,Formatting.BOLD),10,10,0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,Text.literal("Tetris-style arcade").formatted(Formatting.GRAY),10,23,0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,Text.literal("Score: "+score),left+bw+15,top+12,0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,Text.literal("Lines: "+lines),left+bw+15,top+26,0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,Text.literal("← → move"),left+bw+15,top+56,0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,Text.literal("↑ rotate"),left+bw+15,top+69,0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,Text.literal("↓ soft drop"),left+bw+15,top+82,0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,Text.literal("Space hard drop"),left+bw+15,top+95,0xFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,Text.literal("R restart • F9/ESC hub").formatted(Formatting.GRAY),10,this.height-18,0xFFFFFF);

        if(gameOver){
            ctx.fill(left,top+bh/2-24,left+bw,top+bh/2+24,0xCC000000);
            ctx.drawCenteredTextWithShadow(this.textRenderer,Text.literal("GAME OVER").formatted(Formatting.RED,Formatting.BOLD),this.width/2,top+bh/2-8,0xFFFFFF);
            ctx.drawCenteredTextWithShadow(this.textRenderer,Text.literal("Press R to restart"),this.width/2,top+bh/2+7,0xFFFFFF);
        }
        super.render(ctx,mouseX,mouseY,delta);
    }

    private void drawCell(DrawContext ctx,int left,int top,int cell,int x,int y,int color){
        if(x<0||x>=W||y<0||y>=H)return;
        int sx=left+x*cell, sy=top+y*cell;
        ctx.fill(sx+1,sy+1,sx+cell-1,sy+cell-1,color);
        ctx.fill(sx+3,sy+3,sx+cell-3,sy+5,0x44FFFFFF);
    }

    private void stepDown(){
        if(!collides(px,py+1,rot)) py++; else lock();
    }

    private void lock(){
        for(int[] p:SHAPES[type]){
            int[] r=rotate(p[0],p[1],rot); int x=px+r[0], y=py+r[1];
            if(y>=0&&y<H&&x>=0&&x<W) board[y][x]=type+1;
        }
        int cleared=0;
        for(int y=H-1;y>=0;y--){
            boolean full=true; for(int x=0;x<W;x++) if(board[y][x]==0){full=false;break;}
            if(full){ cleared++; for(int yy=y;yy>0;yy--) System.arraycopy(board[yy-1],0,board[yy],0,W); for(int x=0;x<W;x++) board[0][x]=0; y++; }
        }
        if(cleared>0){ lines+=cleared; score+=switch(cleared){case 1->100;case 2->300;case 3->500;default->800;}; }
        spawn();
    }

    private boolean collides(int nx,int ny,int nr){
        for(int[] p:SHAPES[type]){
            int[] r=rotate(p[0],p[1],nr); int x=nx+r[0], y=ny+r[1];
            if(x<0||x>=W||y>=H) return true;
            if(y>=0&&board[y][x]!=0) return true;
        }
        return false;
    }

    private static int[] rotate(int x,int y,int r){
        r&=3;
        return switch(r){
            case 1->new int[]{3-y,x};
            case 2->new int[]{3-x,3-y};
            case 3->new int[]{y,3-x};
            default->new int[]{x,y};
        };
    }

    @Override
    public boolean keyPressed(KeyInput input){
        int k=input.key();
        if(k==GLFW.GLFW_KEY_ESCAPE||k==GLFW.GLFW_KEY_F9){ if(this.client!=null)this.client.setScreen(parent); return true; }
        if(k==GLFW.GLFW_KEY_R){ reset(); return true; }
        if(gameOver) return true;
        if(k==GLFW.GLFW_KEY_LEFT&&!collides(px-1,py,rot)) px--;
        else if(k==GLFW.GLFW_KEY_RIGHT&&!collides(px+1,py,rot)) px++;
        else if(k==GLFW.GLFW_KEY_DOWN){ stepDown(); score++; }
        else if(k==GLFW.GLFW_KEY_UP){ int nr=(rot+1)&3; if(!collides(px,py,nr))rot=nr; }
        else if(k==GLFW.GLFW_KEY_SPACE){ int d=0; while(!collides(px,py+1,rot)){py++;d++;} score+=d*2; lock(); }
        else return super.keyPressed(input);
        return true;
    }

    @Override public boolean shouldPause(){return false;}
}
