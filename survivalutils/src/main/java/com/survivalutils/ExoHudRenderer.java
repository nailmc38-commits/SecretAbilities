package com.survivalutils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.Locale;

public final class ExoHudRenderer {
    private ExoHudRenderer() {}

    public static void render(DrawContext ctx, MinecraftClient client, WarningManager.Warning warning) {
        if(client==null||client.player==null||client.world==null||client.textRenderer==null)return;
        if(!ExoLinkData.SETTINGS.exoHud)return;

        PlayerEntity p=client.player;
        int w=ctx.getScaledWindowWidth(), h=ctx.getScaledWindowHeight();
        boolean compact=w<900||h<520;
        boolean emergency=ExoLinkData.SETTINGS.autoEmergencyLayout
                && warning!=null
                && (warning.severity()==WarningManager.Severity.DANGER||warning.severity()==WarningManager.Severity.CRITICAL);

        if(ExoLinkData.SETTINGS.powerArmorFrame && !p.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
            renderFrame(ctx,w,h,emergency);
        }

        renderVitals(ctx,client,compact);
        renderTopBar(ctx,client,compact);

        if(!emergency) {
            renderAdvisor(ctx,client,compact);
            if(ExoLinkData.SETTINGS.identify) renderIdentify(ctx,client,compact);
            renderPackMini(ctx,client,compact);
        } else {
            renderEmergency(ctx,client,warning);
        }

        if(ExoLinkData.SETTINGS.escapeVector) renderEscape(ctx,client,compact);
        renderFlight(ctx,client,compact);
        renderRecovery(ctx,client,compact);
        renderWarning(ctx,client,warning,compact);
        ExoScriptEngine.renderHud(ctx,client);
        VisorDamageSystem.render(ctx,client);
        VisorDamageSystem.renderDirection(ctx,client);
        ExoBootRenderer.render(ctx,client);
    }

    private static void renderFrame(DrawContext ctx,int w,int h,boolean emergency) {
        int metal=0xCC1B2022;
        int metal2=0xD52A3032;
        int trim=emergency?0xFFFF4F4F:0xFF71888D;
        int glow=emergency?0xFFFF5C5C:0xFF62D9E8;

        ctx.fill(0,0,w/5,8,metal2);
        ctx.fill(4*w/5,0,w,8,metal2);
        ctx.fill(0,0,10,h,metal);
        ctx.fill(w-10,0,w,h,metal);
        ctx.fill(10,h/5,15,4*h/5,0xB6121719);
        ctx.fill(w-15,h/5,w-10,4*h/5,0xB6121719);

        for(int i=0;i<5;i++) {
            int yy=h/2+i*7;
            ctx.fill(10+i*3,yy,29+i*3,yy+4,metal2);
            ctx.fill(w-29-i*3,yy,w-10-i*3,yy+4,metal2);
        }

        ctx.fill(0,h-12,w/5,h,metal2);
        ctx.fill(4*w/5,h-12,w,h,metal2);
        ctx.fill(w/5-1,h-8,w/5+40,h-5,trim);
        ctx.fill(4*w/5-40,h-8,4*w/5+1,h-5,trim);

        ctx.fill(0,0,w/5,2,glow);
        ctx.fill(4*w/5,0,w,2,glow);
    }

    private static void renderVitals(DrawContext ctx,MinecraftClient c,boolean compact) {
        PlayerEntity p=c.player;
        int width=compact?150:185;
        int height=compact?58:72;
        PanelPos pos=beginPanel(ctx,"core",width,height);
        int x=pos.x(), y=pos.y();
        int row=compact?11:13;

        ctx.fill(x,y,x+width,y+height,HudLayoutManager.alpha("core",0xB20A0E10));
        ctx.fill(x,y,x+3,y+(compact?58:72),0xFF62D9E8);

        line(ctx,c,x+8,y+7,"EXO // CORE",0xFF8BEAF4);
        y+=row+5;

        line(ctx,c,x+8,y,"HP "+String.format(Locale.ROOT,"%.1f/%.1f",p.getHealth(),p.getMaxHealth()),
                p.getHealth()<=6?0xFFFF5555:0xFFD8F3F6); y+=row;
        line(ctx,c,x+8,y,"ARMOR "+p.getArmor()+"/20 // "+SurvMegaState.armorIntegrity(p)+"%",
                SurvMegaState.armorIntegrity(p)<=20?0xFFFF6666:0xFFC7E0E4); y+=row;
        line(ctx,c,x+8,y,"FOOD "+p.getHungerManager().getFoodLevel()+"/20",0xFFE9D18A); y+=row;

        if(!compact) {
            ItemStack held=p.getMainHandStack();
            String tool=held.isEmpty()?"EMPTY":held.getName().getString();
            if(held.isDamageable())tool+=" // "+InventoryUtil.durabilityPercent(held)+"%";
            line(ctx,c,x+8,y,"TOOL "+trim(tool,23),0xFFB9C9CC); y+=row;
            line(ctx,c,x+8,y,"TOTEM "+InventoryUtil.count(p,"totem_of_undying")+" // INV "+InventoryUtil.freeSlots(p)+" FREE",0xFF9FB7BC);
        }
        endPanel(ctx);
    }

    private static void renderTopBar(DrawContext ctx,MinecraftClient c,boolean compact) {
        int w=ctx.getScaledWindowWidth();
        String left="EXO // "+ExoSuitSystems.effectiveMode(c);
        ExoSuitSystems.Readiness ready=ExoSuitSystems.readiness(c);
        String right="READY "+ready.percent()+"% // "+c.player.getBlockX()+" "+c.player.getBlockY()+" "+c.player.getBlockZ()
                +" // "+c.world.getRegistryKey().getValue().getPath().toUpperCase(Locale.ROOT);
        int barW=compact?240:330;
        int x=(w-barW)/2;
        ctx.fill(x,5,x+barW,20,0xB60A0F11);
        ctx.fill(x,5,x+barW,7,0xFF62D9E8);
        line(ctx,c,x+7,10,left,0xFFDDF8FA);
        int rw=c.textRenderer.getWidth(right);
        line(ctx,c,x+barW-rw-7,10,right,0xFF9FB7BC);
    }

    private static void renderAdvisor(DrawContext ctx,MinecraftClient c,boolean compact) {
        if(!ExoLinkData.SETTINGS.advisor)return;
        CombatAdvisor.Snapshot a=CombatAdvisor.current();
        int width=compact?175:225;
        int height=a.opponentType().equals("PLAYER")?(compact?82:118):(compact?58:78);
        PanelPos pos=beginPanel(ctx,"advisor",width,height);
        int x=pos.x(), y=pos.y();

        int accent=switch(a.recommendation()){
            case "DISENGAGE"->0xFFFF5C5C;
            case "ENGAGE"->0xFF78E9A0;
            case "CAUTION"->0xFFFFC866;
            default->0xFF62D9E8;
        };

        ctx.fill(x,y,x+width,y+height,HudLayoutManager.alpha("advisor",0xB20A0E10));
        ctx.fill(x+width-3,y,x+width,y+height,accent);
        line(ctx,c,x+8,y+7,"ADVISOR // "+a.recommendation(),accent);
        line(ctx,c,x+8,y+21,"TARGET "+trim(a.opponent(),18),0xFFDCEBED);

        int yy=y+34;
        if(a.estimatedWinPercent()>=0) {
            line(ctx,c,x+8,yy,"WIN EST "+a.estimatedWinPercent()+"% // "+a.confidence(),0xFFFFD27A); yy+=13;
        }

        if(!compact) {
            int shown=0;
            for(String s:a.details()) {
                if(shown++>=5)break;
                line(ctx,c,x+8,yy,trim(s,30),0xFF9FB7BC);
                yy+=13;
            }
        }
        endPanel(ctx);
    }

    private static void renderIdentify(DrawContext ctx,MinecraftClient c,boolean compact) {
        HitResult hit=c.crosshairTarget;
        if(hit==null||hit.getType()==HitResult.Type.MISS)return;

        String title="";
        String sub="";
        if(hit instanceof EntityHitResult ehr) {
            var e=ehr.getEntity();
            title=e.getName().getString().toUpperCase(Locale.ROOT);
            sub=String.format(Locale.ROOT,"%.1fm",c.player.distanceTo(e));
            if(e instanceof PlayerEntity pe) {
                ThreatMemoryManager.Contact mem=ThreatMemoryManager.get(pe);
                if(mem!=null) sub+=" // "+mem.tag;
            }
        } else if(hit instanceof BlockHitResult bhr) {
            var state=c.world.getBlockState(bhr.getBlockPos());
            title=net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).getPath().toUpperCase(Locale.ROOT);
            sub=bhr.getBlockPos().getX()+" "+bhr.getBlockPos().getY()+" "+bhr.getBlockPos().getZ();
        }

        if(title.isBlank())return;

        int boxW=compact?130:155;
        PanelPos pos=beginPanel(ctx,"identify",boxW,32);
        int x=pos.x(), y=pos.y();
        ctx.fill(x-14,y-12,x-1,y-10,0xAA62D9E8);
        ctx.fill(x,y,x+boxW,y+32,HudLayoutManager.alpha("identify",0xB10A0E10));
        ctx.fill(x,y,x+2,y+32,0xFF62D9E8);
        line(ctx,c,x+7,y+6,trim(title,18),0xFFDDF8FA);
        line(ctx,c,x+7,y+18,trim(sub,22),0xFF9FB7BC);
        endPanel(ctx);
    }

    private static void renderEscape(DrawContext ctx,MinecraftClient c,boolean compact) {
        EscapeVector.Result e=CombatAdvisor.current().escape();
        if(e==null||"NONE".equals(e.direction()))return;

        String text="ESCAPE VECTOR // "+e.direction()+" // "+e.clearBlocks()+"m CLEAR";
        int tw=c.textRenderer.getWidth(text);
        PanelPos pos=beginPanel(ctx,"escape",tw+16,29);
        int x=pos.x()+8;
        int y=pos.y()+4;
        ctx.fill(x-8,y-4,x+tw+8,y+12,HudLayoutManager.alpha("escape",0xB20A0E10));
        ctx.fill(x-8,y+12,x+tw+8,y+14,0xFF62D9E8);
        line(ctx,c,x,y,text,0xFFDDF8FA);

        String arrow=arrow(e.direction());
        int aw=c.textRenderer.getWidth(arrow);
        line(ctx,c,x+(tw-aw)/2,y-13,arrow,0xFF75ECF3);
        endPanel(ctx);
    }

    private static void renderPackMini(DrawContext ctx,MinecraftClient c,boolean compact) {
        if(!ExoLinkData.SETTINGS.pack)return;
        PackManager.Status s=PackManager.status(c);
        if(s.known()==0)return;

        int panelW=compact?185:230;
        PanelPos pos=beginPanel(ctx,"pack",panelW,29);
        int x=pos.x(), y=pos.y();
        String line1="PACK // "+s.loaded()+" LOADED // "+s.sitting()+" SIT // "+s.standing()+" FOLLOW";
        ctx.fill(x,y,x+panelW,y+29,HudLayoutManager.alpha("pack",0xA80A0E10));
        line(ctx,c,x+7,y+6,trim(line1,compact?28:36),0xFFC7E4E7);
        line(ctx,c,x+7,y+18,"REACHABLE "+s.reachable()+" // "+s.activeOrder(),0xFF829A9E);
        endPanel(ctx);
    }

    private static void renderEmergency(DrawContext ctx,MinecraftClient c,WarningManager.Warning warning) {
        if(warning==null)return;
        int w=ctx.getScaledWindowWidth(),h=ctx.getScaledWindowHeight();
        CombatAdvisor.Snapshot a=CombatAdvisor.current();

        int width=Math.min(360,w-50);
        int x=(w-width)/2;
        int y=h/2-48;
        ctx.fill(x,y,x+width,y+96,0xD10A0B0D);
        ctx.fill(x,y,x+width,y+4,0xFFFF4F4F);
        ctx.fill(x,y,x+4,y+96,0xFFFF4F4F);
        ctx.fill(x+width-4,y,x+width,y+96,0xFFFF4F4F);

        line(ctx,c,x+12,y+12,"EMERGENCY MODE",0xFFFF6262);
        line(ctx,c,x+12,y+30,trim(warning.text(),43),0xFFFFFFFF);
        line(ctx,c,x+12,y+48,"ADVISOR // "+a.recommendation()+" // "+trim(a.opponent(),18),0xFFFFD07A);
        line(ctx,c,x+12,y+65,"ESCAPE // "+a.escape().direction()+" // "+a.escape().clearBlocks()+"m CLEAR",0xFF82EFF4);
        line(ctx,c,x+12,y+80,"HP "+String.format(Locale.ROOT,"%.1f",c.player.getHealth())+" // ARMOR "+c.player.getArmor()+"/20",0xFFD9E8EA);
    }

    private static void renderFlight(DrawContext ctx,MinecraftClient c,boolean compact) {
        if(ExoSuitSystems.effectiveMode(c)!=ExoSuitSystems.VisorMode.FLIGHT)return;
        ExoSuitSystems.Flight f=ExoSuitSystems.flight(c);
        int boxW=compact?190:230;
        int boxH=compact?50:63;
        PanelPos pos=beginPanel(ctx,"flight",boxW,boxH);
        int x=pos.x(), y=pos.y();
        ctx.fill(x,y,x+boxW,y+boxH,HudLayoutManager.alpha("flight",0xAF0A0E10));
        ctx.fill(x+boxW-3,y,x+boxW,y+(compact?50:63),f.pullUp()?0xFFFF4F4F:0xFF62D9E8);
        line(ctx,c,x+8,y+7,"FLIGHT COMPUTER",0xFF8BEAF4);
        line(ctx,c,x+8,y+20,String.format(Locale.ROOT,"SPD %.1f b/s // ALT %d",f.speed(),f.altitude()),0xFFC9DEE1);
        line(ctx,c,x+8,y+33,"ROCKETS "+f.rockets()+" // ELYTRA "+f.elytraDurability()+"%",0xFFAAC3C7);
        if(!compact&&f.pullUp())line(ctx,c,x+8,y+46,"PULL UP",0xFFFF5C5C);
        endPanel(ctx);
    }

    private static void renderRecovery(DrawContext ctx,MinecraftClient c,boolean compact) {
        ExoTelemetry.Recovery r=ExoTelemetry.recovery();
        if(r==null)return;
        if(!r.dimension().equals(c.world.getRegistryKey().getValue().getPath()))return;
        double dx=r.x()-c.player.getX(),dy=r.y()-c.player.getY(),dz=r.z()-c.player.getZ();
        double d=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(d<5)return;
        String text="RECOVERY // "+r.x()+" "+r.y()+" "+r.z()+" // "+String.format(Locale.ROOT,"%.0fm",d);
        int tw=c.textRenderer.getWidth(text);
        PanelPos pos=beginPanel(ctx,"recovery",tw+12,20);
        int x=pos.x(), y=pos.y();
        ctx.fill(x,y,x+tw+12,y+20,HudLayoutManager.alpha("recovery",0xA90A0E10));
        ctx.fill(x,y,x+3,y+20,0xFFFFB35A);
        line(ctx,c,x+7,y+6,text,0xFFFFCE8A);
        endPanel(ctx);
    }

    private static void renderWarning(DrawContext ctx,MinecraftClient c,WarningManager.Warning warning,boolean compact) {
        if(warning==null)return;
        int w=ctx.getScaledWindowWidth();
        int color=switch(warning.severity()){
            case INFO->0xFFFFE17A;
            case CAUTION->0xFFFFB35A;
            case DANGER->0xFFFF5B5B;
            case CRITICAL->0xFFFF3434;
        };

        String prefix=warning.severity()==WarningManager.Severity.CRITICAL?"CRITICAL // ":"";
        String text=prefix+warning.text();
        int maxW=Math.min(w-80,compact?470:650);
        int tw=Math.min(maxW,c.textRenderer.getWidth(text)+22);
        PanelPos pos=beginPanel(ctx,"warning",tw,24);
        int x=pos.x(), y=pos.y();

        ctx.fill(x,y,x+tw,y+24,HudLayoutManager.alpha("warning",0xD80A0C0E));
        ctx.fill(x,y,x+tw,y+3,color);
        ctx.fill(x,y,x+3,y+24,color);
        ctx.fill(x+tw-3,y,x+tw,y+24,color);

        String shown=trimToWidth(c,text,tw-14);
        int sw=c.textRenderer.getWidth(shown);
        line(ctx,c,x+(tw-sw)/2,y+8,shown,color);
        endPanel(ctx);
    }

    private record PanelPos(int x,int y,float scale) {}

    private static PanelPos beginPanel(DrawContext ctx,String key,int width,int height) {
        HudLayoutManager.Panel p=HudLayoutManager.panel(key);
        float scale=(float)p.scale;
        int px=HudLayoutManager.x(key,ctx.getScaledWindowWidth(),width);
        int py=HudLayoutManager.y(key,ctx.getScaledWindowHeight(),height);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(scale,scale);
        return new PanelPos((int)Math.round(px/scale),(int)Math.round(py/scale),scale);
    }

    private static void endPanel(DrawContext ctx) {
        ctx.getMatrices().popMatrix();
    }

    private static String arrow(String dir) {
        return switch(dir){
            case "N"->"↑";
            case "NE"->"↗";
            case "E"->"→";
            case "SE"->"↘";
            case "S"->"↓";
            case "SW"->"↙";
            case "W"->"←";
            case "NW"->"↖";
            default->"•";
        };
    }

    private static void line(DrawContext ctx,MinecraftClient c,int x,int y,String s,int color) {
        ctx.drawTextWithShadow(c.textRenderer,s,x,y,color);
    }

    private static String trim(String s,int max) {
        if(s==null)return "";
        return s.length()<=max?s:s.substring(0,Math.max(0,max-1))+"…";
    }

    private static String trimToWidth(MinecraftClient c,String s,int px) {
        if(c.textRenderer.getWidth(s)<=px)return s;
        String out=s;
        while(out.length()>3&&c.textRenderer.getWidth(out+"…")>px)out=out.substring(0,out.length()-1);
        return out+"…";
    }
}
