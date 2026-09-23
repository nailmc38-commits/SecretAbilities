package com.survivalutils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class ExoDataCenterScreen extends Screen {
    private final Screen parent;

    public ExoDataCenterScreen(Screen parent){
        super(Text.literal("EXO // DATA CENTER"));
        this.parent=parent;
    }

    @Override protected void init(){
        clearChildren();
        int w=Math.min(650,width-24),left=(width-w)/2,half=(w-28)/2,y=66;

        button(left+10,y,half,"CLEAR THREAT MEMORY",ThreatMemoryManager::clear);
        button(left+18+half,y,half,"CLEAR VISOR DAMAGE",VisorDamageSystem::clear); y+=32;
        button(left+10,y,half,"CLEAR BLACKBOX",ExoTelemetry::clearBlackbox);
        button(left+18+half,y,half,"CLEAR COMBAT HISTORY",ExoTelemetry::clearCombats); y+=32;
        button(left+10,y,half,"CLEAR ROUTE MEMORY",ExoTelemetry::clearRoute);
        button(left+18+half,y,half,"CLEAR PORTAL LINKS",ExoTelemetry::clearPortals); y+=32;
        button(left+10,y,half,"CLEAR RECOVERY TARGET",ExoTelemetry::clearRecovery);
        button(left+18+half,y,half,"RETENTION // "+ExoLinkData.SETTINGS.dataRetentionDays+" DAYS",()->{
            int d=ExoLinkData.SETTINGS.dataRetentionDays;
            ExoLinkData.SETTINGS.dataRetentionDays=d<7?7:d<30?30:d<90?90:365;
            ExoLinkData.save();rebuild();
        }); y+=40;
        button(left+10,y,w-20,"BACK",()->{if(client!=null)client.setScreen(parent);});
    }

    private void button(int x,int y,int w,String t,Runnable r){
        addDrawableChild(ButtonWidget.builder(Text.literal(t),b->{r.run();}).dimensions(x,y,w,23).build());
    }
    private void rebuild(){clearChildren();init();}

    @Override public void render(DrawContext ctx,int mx,int my,float d){
        ctx.fill(0,0,width,height,0xF405080A);
        ctx.drawCenteredTextWithShadow(textRenderer,"EXO // DATA CENTER",width/2,18,0xFFDDF8FA);
        ctx.drawCenteredTextWithShadow(textRenderer,"Stored locally. Every category has a clear control.",width/2,34,0xFF829A9E);
        int x=Math.max(12,width/2-280),y=230;
        ctx.drawTextWithShadow(textRenderer,"THREAT CONTACTS // "+ThreatMemoryManager.all().size(),x,y,0xFF9FCAD0);y+=14;
        ctx.drawTextWithShadow(textRenderer,"COMBAT RECORDS // "+ExoTelemetry.combats().size(),x,y,0xFF9FCAD0);y+=14;
        ctx.drawTextWithShadow(textRenderer,"ROUTE POINTS // "+ExoTelemetry.route().size(),x,y,0xFF9FCAD0);y+=14;
        ctx.drawTextWithShadow(textRenderer,"PORTAL LINKS // "+ExoTelemetry.portals().size(),x,y,0xFF9FCAD0);y+=14;
        ctx.drawTextWithShadow(textRenderer,"RECOVERY // "+(ExoTelemetry.recovery()==null?"NONE":"READY"),x,y,0xFF9FCAD0);
        super.render(ctx,mx,my,d);
    }
    @Override public boolean keyPressed(KeyInput input){
        if(input.key()==GLFW.GLFW_KEY_ESCAPE){if(client!=null)client.setScreen(parent);return true;}
        return super.keyPressed(input);
    }
    @Override public boolean shouldPause(){return false;}
}
