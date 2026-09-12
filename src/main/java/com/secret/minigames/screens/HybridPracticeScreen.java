package com.secret.minigames.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public final class HybridPracticeScreen extends Screen {
    private static final int W=48,H=22,D=48;
    private static final byte AIR=0,GRASS=1,DIRT=2,STONE=3,OBSIDIAN=4,ANCHOR=5,CHARGED=6;
    private static final double RADIUS=.28,HEIGHT=1.75,EYE=1.62;
    private enum Item { ANCHOR, GLOWSTONE, OBSIDIAN, CRYSTAL }

    private final Screen parent;
    private final byte[][][] blocks=new byte[W][H][D];
    private final List<Crystal> crystals=new ArrayList<>();
    private double x=W/2.0+.5,y,z=D/2.0+6.5,velocityY,yaw=Math.PI,pitch=-.10;
    private boolean prevJump,prevRight,prevLeft;
    private int mouseWarmup=3;
    private long lastNanos;
    private Item selected=Item.ANCHOR;
    private int anchors,crystalsPlaced,pops,booms;
    private String status="Z anchor • X glowstone • R obsidian • Left Alt crystal";
    private double statusTime=5;

    public HybridPracticeScreen(Screen parent){super(Text.literal("Hybrid PvP Practice"));this.parent=parent;resetArena();}
    @Override protected void init(){lastNanos=System.nanoTime();mouseWarmup=3;centerMouse();}

    private void resetArena(){
        crystals.clear();
        for(int xx=0;xx<W;xx++)for(int yy=0;yy<H;yy++)Arrays.fill(blocks[xx][yy],AIR);
        for(int xx=0;xx<W;xx++)for(int zz=0;zz<D;zz++){blocks[xx][0][zz]=OBSIDIAN;blocks[xx][1][zz]=STONE;blocks[xx][2][zz]=DIRT;blocks[xx][3][zz]=GRASS;}
        for(int gx=6;gx<W-5;gx+=7)for(int gz=6;gz<D-5;gz+=7){blocks[gx][3][gz]=OBSIDIAN;if(((gx+gz)/7)%2==0)blocks[gx][4][gz]=OBSIDIAN;}
        x=W/2.0+.5;z=D/2.0+6.5;y=highestSolid((int)x,(int)z)+1.01;velocityY=0;selected=Item.ANCHOR;anchors=crystalsPlaced=pops=booms=0;
    }

    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        long now=System.nanoTime();double dt=Math.min(.05,Math.max(.001,(now-lastNanos)/1_000_000_000.0));lastNanos=now;statusTime=Math.max(0,statusTime-dt);
        look();movePlayer(dt);actions();drawWorld(ctx);drawHud(ctx);super.render(ctx,mouseX,mouseY,delta);
    }

    private void look(){if(client==null)return;double cx=client.getWindow().getWidth()/2.0,cy=client.getWindow().getHeight()/2.0,mx=client.mouse.getX(),my=client.mouse.getY();if(mouseWarmup--<=0){yaw+=(mx-cx)*.0028;pitch-=(my-cy)*.0028;pitch=clamp(pitch,-1.35,1.35);}centerMouse();}
    private void centerMouse(){if(client!=null)GLFW.glfwSetCursorPos(client.getWindow().getHandle(),client.getWindow().getWidth()/2.0,client.getWindow().getHeight()/2.0);}

    private void movePlayer(double dt){
        if(client==null)return;long win=client.getWindow().getHandle();double f=(down(win,GLFW.GLFW_KEY_W)?1:0)-(down(win,GLFW.GLFW_KEY_S)?1:0),s=(down(win,GLFW.GLFW_KEY_D)?1:0)-(down(win,GLFW.GLFW_KEY_A)?1:0),len=Math.sqrt(f*f+s*s);
        if(len>0){f/=len;s/=len;double speed=down(win,GLFW.GLFW_KEY_LEFT_CONTROL)?5.2:3.8,sin=Math.sin(yaw),cos=Math.cos(yaw);stepMove((sin*f+cos*s)*speed*dt,(cos*f-sin*s)*speed*dt);}
        boolean jump=down(win,GLFW.GLFW_KEY_SPACE),grounded=collides(x,y-.07,z);if(jump&&!prevJump&&grounded)velocityY=6.4;prevJump=jump;velocityY-=13*dt;double ny=y+velocityY*dt;
        if(!collides(x,ny,z))y=ny;else{if(velocityY<0){double ty=y;for(int i=0;i<28&&collides(x,ty-.015,z);i++)ty+=.015;y=ty;}velocityY=0;}if(y<-3)resetPlayer();
    }
    private void stepMove(double dx,double dz){boolean grounded=collides(x,y-.06,z);if(!collides(x+dx,y,z))x+=dx;else if(grounded&&!collides(x+dx,y+.62,z)){y+=.62;x+=dx;}if(!collides(x,y,z+dz))z+=dz;else if(grounded&&!collides(x,y+.62,z+dz)){y+=.62;z+=dz;}}
    private void resetPlayer(){x=W/2.0+.5;z=D/2.0+6.5;y=highestSolid((int)x,(int)z)+1.01;velocityY=0;}

    private void actions(){if(client==null)return;long win=client.getWindow().getHandle();boolean right=GLFW.glfwGetMouseButton(win,GLFW.GLFW_MOUSE_BUTTON_RIGHT)==GLFW.GLFW_PRESS,left=GLFW.glfwGetMouseButton(win,GLFW.GLFW_MOUSE_BUTTON_LEFT)==GLFW.GLFW_PRESS;if(right&&!prevRight)useSelected();if(left&&!prevLeft)breakCrystal();prevRight=right;prevLeft=left;}

    private void useSelected(){
        double[] d=direction();Hit hit=ray(x,y+EYE,z,d[0],d[1],d[2],6);if(hit==null){say("Aim at terrain.");return;}
        if(selected==Item.ANCHOR){int px=hit.px,py=hit.py,pz=hit.pz;if(!inside(px,py,pz)||blocks[px][py][pz]!=AIR||intersectsPlayer(px,py,pz)){say("No room for anchor.");return;}blocks[px][py][pz]=ANCHOR;anchors++;say("Anchor placed. X to charge.");return;}
        if(selected==Item.GLOWSTONE){byte b=blocks[hit.x][hit.y][hit.z];if(b==ANCHOR){blocks[hit.x][hit.y][hit.z]=CHARGED;say("Anchor charged. RMB again.");}else if(b==CHARGED){blocks[hit.x][hit.y][hit.z]=AIR;explode(hit.x+.5,hit.y+.5,hit.z+.5,3.25);booms++;say("Anchor exploded • no self-damage.");}else say("Glowstone needs an anchor.");return;}
        if(selected==Item.OBSIDIAN){int px=hit.px,py=hit.py,pz=hit.pz;if(!inside(px,py,pz)||blocks[px][py][pz]!=AIR||intersectsPlayer(px,py,pz)){say("No room for obsidian.");return;}blocks[px][py][pz]=OBSIDIAN;say("Obsidian placed. Left Alt for crystal.");return;}
        int bx=hit.x,by=hit.y,bz=hit.z;if(blocks[bx][by][bz]!=OBSIDIAN){say("Crystal needs obsidian.");return;}int cy=by+1;if(!inside(bx,cy,bz)||blocks[bx][cy][bz]!=AIR||blocks[bx][Math.min(H-1,cy+1)][bz]!=AIR||playerNear(bx+.5,cy+.55,bz+.5,1.0)){say("Crystal placement blocked.");return;}crystals.add(new Crystal(bx+.5,cy+.55,bz+.5));crystalsPlaced++;say("Crystal placed. LMB to pop.");
    }

    private void breakCrystal(){double[] d=direction();Crystal c=aimedCrystal(d[0],d[1],d[2],6);if(c==null)return;crystals.remove(c);pops++;explode(c.x,c.y,c.z,3.45);say("Crystal popped • no self-damage.");}
    private Crystal aimedCrystal(double dx,double dy,double dz,double max){Crystal best=null;double bestAlong=max+1,ox=x,oy=y+EYE,oz=z;for(Crystal c:crystals){double vx=c.x-ox,vy=c.y-oy,vz=c.z-oz,along=vx*dx+vy*dy+vz*dz;if(along<0||along>max)continue;double px=ox+dx*along,py=oy+dy*along,pz=oz+dz*along;if(distance(px,py,pz,c.x,c.y,c.z)<.48&&along<bestAlong){best=c;bestAlong=along;}}return best;}

    private void explode(double cx,double cy,double cz,double radius){int r=4;for(int xx=floor(cx)-r;xx<=floor(cx)+r;xx++)for(int yy=floor(cy)-r;yy<=floor(cy)+r;yy++)for(int zz=floor(cz)-r;zz<=floor(cz)+r;zz++){if(!inside(xx,yy,zz))continue;byte b=blocks[xx][yy][zz];if(b==AIR||b==OBSIDIAN)continue;double dist=distance(xx+.5,yy+.5,zz+.5,cx,cy,cz);if(dist>radius)continue;double strength=1-dist/radius;int noise=Math.floorMod(hash(xx,yy,zz),100);if(strength>.4||noise<(int)(strength*100))blocks[xx][yy][zz]=AIR;}Iterator<Crystal>it=crystals.iterator();while(it.hasNext()){Crystal c=it.next();if(distance(c.x,c.y,c.z,cx,cy,cz)<3)it.remove();}}

    private void drawWorld(DrawContext ctx){
        int pixel=Math.max(5,Math.min(7,width/250));double aspect=(double)width/Math.max(1,height),fov=Math.tan(Math.toRadians(72)/2.0);ctx.fill(0,0,width,height,0xFF7FB3E1);ctx.fill(0,height/2,width,height,0xFFBCD2E5);
        double sp=Math.sin(pitch),cp=Math.cos(pitch),sy=Math.sin(yaw),cy=Math.cos(yaw),fx=sy*cp,fy=sp,fz=cy*cp,rx=cy,rz=-sy,ux=-sy*sp,uy=cp,uz=-cy*sp;
        for(int py=0;py<height;py+=pixel){double ny=1-((py+pixel*.5)/height)*2;for(int px=0;px<width;px+=pixel){double nx=((px+pixel*.5)/width)*2-1,sx=nx*fov*aspect,syy=ny*fov,dx=fx+rx*sx+ux*syy,dy=fy+uy*syy,dz=fz+rz*sx+uz*syy,inv=1/Math.sqrt(dx*dx+dy*dy+dz*dz);dx*=inv;dy*=inv;dz*=inv;Hit h=ray(x,y+EYE,z,dx,dy,dz,22);double blockDist=h==null?Double.MAX_VALUE:h.dist;Crystal c=nearestCrystal(x,y+EYE,z,dx,dy,dz,22);double cd=c==null?Double.MAX_VALUE:projected(x,y+EYE,z,dx,dy,dz,c);if(c!=null&&cd<blockDist&&rayDistance(x,y+EYE,z,dx,dy,dz,c)<.42){int col=((px/pixel+py/pixel)&1)==0?0xFFFF76D8:0xFFF6EEFF;ctx.fill(px,py,Math.min(width,px+pixel),Math.min(height,py+pixel),col);}else if(h!=null){int base=blockColor(blocks[h.x][h.y][h.z],h.x,h.y,h.z);double face=h.face==1?1:(h.face==0?.76:.87),fog=clamp(1-h.dist/30,.42,1);ctx.fill(px,py,Math.min(width,px+pixel),Math.min(height,py+pixel),shade(base,face*fog));}}}
    }

    private Crystal nearestCrystal(double ox,double oy,double oz,double dx,double dy,double dz,double max){Crystal best=null;double bd=max+1;for(Crystal c:crystals){double t=projected(ox,oy,oz,dx,dy,dz,c);if(t>=0&&t<=max&&t<bd&&rayDistance(ox,oy,oz,dx,dy,dz,c)<.52){best=c;bd=t;}}return best;}
    private double projected(double ox,double oy,double oz,double dx,double dy,double dz,Crystal c){return(c.x-ox)*dx+(c.y-oy)*dy+(c.z-oz)*dz;}
    private double rayDistance(double ox,double oy,double oz,double dx,double dy,double dz,Crystal c){double t=projected(ox,oy,oz,dx,dy,dz,c);return distance(ox+dx*t,oy+dy*t,oz+dz*t,c.x,c.y,c.z);}

    private void drawHud(DrawContext ctx){int cx=width/2,cy=height/2;ctx.fill(cx-5,cy,cx-1,cy+1,0xFFFFFFFF);ctx.fill(cx+2,cy,cx+6,cy+1,0xFFFFFFFF);ctx.fill(cx,cy-5,cx+1,cy-1,0xFFFFFFFF);ctx.fill(cx,cy+2,cx+1,cy+6,0xFFFFFFFF);ctx.fill(6,6,278,45,0xAA000000);ctx.drawTextWithShadow(textRenderer,Text.literal("HYBRID PvP PRACTICE").formatted(Formatting.LIGHT_PURPLE,Formatting.BOLD),11,10,0xFFFFFF);ctx.drawTextWithShadow(textRenderer,Text.literal("RMB place/use • LMB pop crystal • Backspace reset"),11,22,0xFFFFFF);ctx.drawTextWithShadow(textRenderer,Text.literal("Z Anchor • X Glowstone • R Obsidian • Left Alt Crystal").formatted(Formatting.GRAY),11,34,0xFFFFFF);ctx.fill(width-190,6,width-6,45,0xAA000000);ctx.drawTextWithShadow(textRenderer,Text.literal("Selected: "+selectedName()),width-184,10,0xFFFFFF);ctx.drawTextWithShadow(textRenderer,Text.literal("Anchors "+anchors+" • Crystals "+crystalsPlaced+" • Pops "+pops),width-184,22,0xFFFFFF);ctx.drawTextWithShadow(textRenderer,Text.literal("Self damage: OFF").formatted(Formatting.GREEN),width-184,34,0xFFFFFF);int sy=height-34;drawSlot(ctx,cx-142,sy,64,"Z","Anchor",0xFF64334E,selected==Item.ANCHOR);drawSlot(ctx,cx-72,sy,64,"X","Glow",0xFFFFD452,selected==Item.GLOWSTONE);drawSlot(ctx,cx-2,sy,64,"R","Obsidian",0xFF241B31,selected==Item.OBSIDIAN);drawSlot(ctx,cx+68,sy,68,"ALT","Crystal",0xFFFF79DB,selected==Item.CRYSTAL);if(statusTime>0){int w=textRenderer.getWidth(status)+14;ctx.fill(cx-w/2,52,cx+w/2,69,0xB0000000);ctx.drawCenteredTextWithShadow(textRenderer,Text.literal(status),cx,57,0xFFFFFF);}}
    private void drawSlot(DrawContext ctx,int sx,int sy,int sw,String key,String label,int color,boolean active){ctx.fill(sx,sy,sx+sw,sy+27,active?0xFFFFFFFF:0xCC3A3A3A);ctx.fill(sx+2,sy+2,sx+sw-2,sy+25,0xEE111111);ctx.fill(sx+6,sy+6,sx+18,sy+18,color);ctx.drawTextWithShadow(textRenderer,Text.literal(key),sx+21,sy+5,0xFFFFFF);ctx.drawTextWithShadow(textRenderer,Text.literal(label),sx+21,sy+15,0xFFBBBBBB);}

    private Hit ray(double ox,double oy,double oz,double dx,double dy,double dz,double max){double step=.07;int px=floor(ox),py=floor(oy),pz=floor(oz);for(double d=0;d<=max;d+=step){int bx=floor(ox+dx*d),by=floor(oy+dy*d),bz=floor(oz+dz*d);if(inside(bx,by,bz)&&blocks[bx][by][bz]!=AIR){int face=by!=py?1:(bx!=px?0:2);return new Hit(bx,by,bz,px,py,pz,d,face);}px=bx;py=by;pz=bz;}return null;}
    private double[] direction(){double cp=Math.cos(pitch);return new double[]{Math.sin(yaw)*cp,Math.sin(pitch),Math.cos(yaw)*cp};}
    private boolean collides(double px,double py,double pz){int minX=floor(px-RADIUS),maxX=floor(px+RADIUS),minY=floor(py),maxY=floor(py+HEIGHT-.01),minZ=floor(pz-RADIUS),maxZ=floor(pz+RADIUS);for(int bx=minX;bx<=maxX;bx++)for(int by=minY;by<=maxY;by++)for(int bz=minZ;bz<=maxZ;bz++)if(solid(bx,by,bz))return true;return false;}
    private boolean solid(int bx,int by,int bz){if(by<0)return true;if(by>=H)return false;if(bx<0||bx>=W||bz<0||bz>=D)return true;return blocks[bx][by][bz]!=AIR;}
    private boolean intersectsPlayer(int bx,int by,int bz){return bx+1>x-RADIUS&&bx<x+RADIUS&&by+1>y&&by<y+HEIGHT&&bz+1>z-RADIUS&&bz<z+RADIUS;}
    private boolean playerNear(double px,double py,double pz,double r){return distance(px,py,pz,x,y+HEIGHT*.5,z)<r;}
    private int highestSolid(int bx,int bz){for(int by=H-1;by>=0;by--)if(inside(bx,by,bz)&&blocks[bx][by][bz]!=AIR)return by;return 0;}
    private boolean inside(int bx,int by,int bz){return bx>=0&&bx<W&&by>=0&&by<H&&bz>=0&&bz<D;}
    private void say(String s){status=s;statusTime=2.2;}
    private String selectedName(){return switch(selected){case ANCHOR->"ANCHOR";case GLOWSTONE->"GLOWSTONE";case OBSIDIAN->"OBSIDIAN";case CRYSTAL->"END CRYSTAL";};}

    @Override public boolean keyPressed(KeyInput input){int k=input.key();if(k==GLFW.GLFW_KEY_ESCAPE||k==GLFW.GLFW_KEY_F9){if(client!=null)client.setScreen(parent);return true;}if(k==GLFW.GLFW_KEY_Z){selected=Item.ANCHOR;say("Anchor selected.");return true;}if(k==GLFW.GLFW_KEY_X){selected=Item.GLOWSTONE;say("Glowstone selected.");return true;}if(k==GLFW.GLFW_KEY_R){selected=Item.OBSIDIAN;say("Obsidian selected.");return true;}if(k==GLFW.GLFW_KEY_LEFT_ALT){selected=Item.CRYSTAL;say("End Crystal selected.");return true;}if(k==GLFW.GLFW_KEY_BACKSPACE){resetArena();say("Arena reset.");return true;}return super.keyPressed(input);}
    @Override public boolean shouldPause(){return false;}

    private int blockColor(byte b,int bx,int by,int bz){int n=Math.floorMod(hash(bx,by,bz),18)-9,base=switch(b){case GRASS->0xFF62A84A;case DIRT->0xFF83572F;case STONE->0xFF74777A;case OBSIDIAN->0xFF241B31;case ANCHOR->0xFF64334E;case CHARGED->0xFFB54DAE;default->0xFF000000;};return vary(base,n);}
    private static int hash(int a,int b,int c){int h=a*734287+b*912931+c*438289;h^=h>>>13;return h;}
    private static int vary(int color,int delta){int r=clamp255(((color>>16)&255)+delta),g=clamp255(((color>>8)&255)+delta),b=clamp255((color&255)+delta);return 0xFF000000|(r<<16)|(g<<8)|b;}
    private static int shade(int color,double f){int r=clamp255((int)(((color>>16)&255)*f)),g=clamp255((int)(((color>>8)&255)*f)),b=clamp255((int)((color&255)*f));return 0xFF000000|(r<<16)|(g<<8)|b;}
    private static int clamp255(int v){return Math.max(0,Math.min(255,v));}
    private static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
    private static int floor(double v){return(int)Math.floor(v);}
    private static boolean down(long win,int key){return GLFW.glfwGetKey(win,key)==GLFW.GLFW_PRESS;}
    private static double distance(double ax,double ay,double az,double bx,double by,double bz){double dx=ax-bx,dy=ay-by,dz=az-bz;return Math.sqrt(dx*dx+dy*dy+dz*dz);}
    private record Hit(int x,int y,int z,int px,int py,int pz,double dist,int face){}
    private record Crystal(double x,double y,double z){}
}
