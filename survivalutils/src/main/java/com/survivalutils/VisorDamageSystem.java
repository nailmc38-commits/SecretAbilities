package com.survivalutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class VisorDamageSystem {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final ArrayList<Impact> IMPACTS=new ArrayList<>();
    private static float lastHealth=-1;
    private static long lastDamageAt;
    private static long lastRepairSound;
    private static String lastDirection="";
    private static long directionUntil;
    private static boolean loaded;

    public static final class Impact {
        public double anchor;
        public String edge;
        public double severity;
        public long created;
    }

    private VisorDamageSystem(){}

    public static void tick(MinecraftClient client) {
        if(client==null||client.player==null)return;
        ensureLoaded();

        float hp=client.player.getHealth()+client.player.getAbsorptionAmount();
        if(lastHealth<0)lastHealth=hp;

        if(hp<lastHealth-0.05f) {
            double loss=lastHealth-hp;
            addImpact(client,Math.min(1.0,0.16+loss/12.0));
            lastDamageAt=System.currentTimeMillis();
        }
        lastHealth=hp;

        long now=System.currentTimeMillis();
        if(now-lastDamageAt>=300_000L && SurvMegaState.armorIntegrity(client.player)>50) {
            boolean changed=false;
            Iterator<Impact> it=IMPACTS.iterator();
            while(it.hasNext()) {
                Impact i=it.next();
                i.severity-=0.0025;
                if(i.severity<=0.02)it.remove();
                changed=true;
            }
            if(changed && now-lastRepairSound>10_000L) {
                lastRepairSound=now;
                save();
            }
        }
    }

    private static void addImpact(MinecraftClient client,double severity) {
        Impact i=new Impact();
        i.severity=severity;
        i.created=System.currentTimeMillis();

        Entity attacker=client.player.getAttacker();
        if(attacker!=null) {
            Vec3d to=new Vec3d(attacker.getX(),attacker.getY(),attacker.getZ()).subtract(new Vec3d(client.player.getX(),client.player.getY(),client.player.getZ()));
            double angle=Math.toDegrees(Math.atan2(to.z,to.x))-client.player.getYaw();
            while(angle>180)angle-=360;
            while(angle<-180)angle+=360;

            if(Math.abs(angle)<45) {i.edge="TOP";i.anchor=0.50;lastDirection="FRONT";}
            else if(angle>=45&&angle<135){i.edge="LEFT";i.anchor=0.48;lastDirection="LEFT";}
            else if(angle<=-45&&angle>-135){i.edge="RIGHT";i.anchor=0.48;lastDirection="RIGHT";}
            else {i.edge="BOTTOM";i.anchor=0.50;lastDirection="REAR";}
        } else {
            int n=IMPACTS.size()%4;
            i.edge=switch(n){case 0->"LEFT";case 1->"RIGHT";case 2->"TOP";default->"BOTTOM";};
            i.anchor=0.25+((IMPACTS.size()*37)%50)/100.0;
            lastDirection="IMPACT";
        }

        directionUntil=System.currentTimeMillis()+2200;
        IMPACTS.add(i);
        while(IMPACTS.size()>12)IMPACTS.remove(0);
        save();
    }

    public static void render(DrawContext ctx,MinecraftClient client) {
        if(!ExoLinkData.SETTINGS.visorCracks||IMPACTS.isEmpty())return;
        int w=ctx.getScaledWindowWidth(),h=ctx.getScaledWindowHeight();

        int idx=0;
        for(Impact impact:IMPACTS) {
            int[] p=anchorPoint(impact,w,h);
            int depth=(int)Math.round(38+impact.severity*130);
            int endX=p[0],endY=p[1];
            switch(impact.edge) {
                case "LEFT"->endX+=depth;
                case "RIGHT"->endX-=depth;
                case "TOP"->endY+=depth;
                case "BOTTOM"->endY-=depth;
            }

            branch(ctx,p[0],p[1],endX,endY,idx,0xD7D9F5F5);
            int mx=(p[0]+endX)/2,my=(p[1]+endY)/2;
            branch(ctx,mx,my,mx+(idx%2==0?28:-31),my+(idx%3==0?36:-24),idx+7,0xB6BFE3E7);
            if(impact.severity>0.42) branch(ctx,mx,my,mx+(idx%2==0?-22:26),my+31,idx+13,0xAEB7DADF);
            if(impact.severity>0.70) branch(ctx,endX,endY,endX+(idx%2==0?36:-34),endY-28,idx+19,0x99ABCED3);
            idx++;
        }
    }

    public static void renderDirection(DrawContext ctx,MinecraftClient client) {
        if(!ExoLinkData.SETTINGS.damageDirection||System.currentTimeMillis()>directionUntil||lastDirection.isBlank())return;
        int w=ctx.getScaledWindowWidth(),h=ctx.getScaledWindowHeight();
        String s="IMPACT // "+lastDirection;
        int tw=client.textRenderer.getWidth(s);
        int x=(w-tw)/2,y=h/2+44;
        ctx.fill(x-7,y-4,x+tw+7,y+12,0xB90B1114);
        ctx.fill(x-7,y+12,x+tw+7,y+14,0xFFFF4F4F);
        ctx.drawTextWithShadow(client.textRenderer,s,x,y,0xFFFF7777);
    }

    private static int[] anchorPoint(Impact i,int w,int h) {
        int x=(int)(i.anchor*w), y=(int)(i.anchor*h);
        return switch(i.edge) {
            case "LEFT"->new int[]{2,(int)(i.anchor*h)};
            case "RIGHT"->new int[]{w-3,(int)(i.anchor*h)};
            case "TOP"->new int[]{(int)(i.anchor*w),2};
            default->new int[]{(int)(i.anchor*w),h-3};
        };
    }

    private static void branch(DrawContext ctx,int x1,int y1,int x2,int y2,int seed,int color) {
        int segments=10;
        int px=x1,py=y1;
        for(int n=1;n<=segments;n++) {
            double f=n/(double)segments;
            int jitter=((seed*31+n*17)%11)-5;
            int nx=(int)Math.round(x1+(x2-x1)*f+(n%2==0?jitter:-jitter));
            int ny=(int)Math.round(y1+(y2-y1)*f+(n%3==0?jitter:0));
            line(ctx,px,py,nx,ny,color);
            px=nx;py=ny;
        }
    }

    private static void line(DrawContext ctx,int x1,int y1,int x2,int y2,int color) {
        int steps=Math.max(1,Math.max(Math.abs(x2-x1),Math.abs(y2-y1)));
        for(int i=0;i<=steps;i++) {
            double f=i/(double)steps;
            int x=(int)Math.round(x1+(x2-x1)*f);
            int y=(int)Math.round(y1+(y2-y1)*f);
            ctx.fill(x,y,x+1,y+1,color);
        }
    }

    public static int damagePercent() {
        double sum=0;
        for(Impact i:IMPACTS)sum+=i.severity;
        return Math.min(100,(int)Math.round(sum*22));
    }

    public static void clear(){IMPACTS.clear();save();}

    private static void ensureLoaded() {
        if(loaded)return;loaded=true;
        try{
            Path p=ExoLinkData.root().resolve("visor-damage.json");
            if(Files.exists(p)) {
                Impact[] arr=GSON.fromJson(Files.readString(p,StandardCharsets.UTF_8),Impact[].class);
                if(arr!=null) for(Impact i:arr)IMPACTS.add(i);
            }
        }catch(Exception ignored){}
    }

    private static void save() {
        try{
            Path p=ExoLinkData.root().resolve("visor-damage.json");
            Files.writeString(p,GSON.toJson(IMPACTS),StandardCharsets.UTF_8);
        }catch(Exception ignored){}
    }
}
