package com.survivalutils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class ExoAudioSettingsScreen extends Screen {
    private final Screen parent;

    public ExoAudioSettingsScreen(Screen parent){
        super(Text.literal("EXO // AUDIO"));
        this.parent=parent;
    }

    @Override
    protected void init(){
        clearChildren();
        int w=Math.min(620,width-24),left=(width-w)/2,half=(w-28)/2,y=58;

        toggle(left+10,y,half,"ALL SOUNDS",()->ExoLinkData.SETTINGS.sounds,v->ExoLinkData.SETTINGS.sounds=v);
        toggle(left+18+half,y,half,"BOOT",()->ExoLinkData.SETTINGS.bootSound,v->ExoLinkData.SETTINGS.bootSound=v); y+=32;
        toggle(left+10,y,half,"WARNINGS",()->ExoLinkData.SETTINGS.warningSound,v->ExoLinkData.SETTINGS.warningSound=v);
        toggle(left+18+half,y,half,"CRITICAL",()->ExoLinkData.SETTINGS.criticalSound,v->ExoLinkData.SETTINGS.criticalSound=v); y+=32;
        toggle(left+10,y,half,"TARGET LOCK",()->ExoLinkData.SETTINGS.targetSound,v->ExoLinkData.SETTINGS.targetSound=v);
        toggle(left+18+half,y,half,"DAMAGE",()->ExoLinkData.SETTINGS.damageSound,v->ExoLinkData.SETTINGS.damageSound=v); y+=32;
        toggle(left+10,y,half,"REPAIR",()->ExoLinkData.SETTINGS.repairSound,v->ExoLinkData.SETTINGS.repairSound=v);
        toggle(left+18+half,y,half,"SATELLITE",()->ExoLinkData.SETTINGS.satelliteSound,v->ExoLinkData.SETTINGS.satelliteSound=v); y+=32;
        toggle(left+10,y,half,"TOOLS / PACK",()->ExoLinkData.SETTINGS.toolSound,v->ExoLinkData.SETTINGS.toolSound=v);
        button(left+18+half,y,half,"TEST BOOT",()->ExoAudioManager.startBoot(client)); y+=40;
        button(left+10,y,w-20,"BACK",()->{if(client!=null)client.setScreen(parent);});
    }

    private interface G{boolean get();}
    private interface S{void set(boolean v);}
    private void toggle(int x,int y,int w,String name,G g,S s){
        button(x,y,w,name+" // "+(g.get()?"ON":"OFF"),()->{s.set(!g.get());ExoLinkData.save();rebuild();});
    }
    private void button(int x,int y,int w,String t,Runnable r){
        addDrawableChild(ButtonWidget.builder(Text.literal(t),b->r.run()).dimensions(x,y,w,23).build());
    }
    private void rebuild(){clearChildren();init();}

    @Override public void render(DrawContext ctx,int mx,int my,float d){
        ctx.fill(0,0,width,height,0xF405080A);
        ctx.drawCenteredTextWithShadow(textRenderer,"EXO // AUDIO BUS",width/2,18,0xFFDDF8FA);
        ctx.drawCenteredTextWithShadow(textRenderer,"Mechanical suit feedback. Channels are independent.",width/2,34,0xFF829A9E);
        super.render(ctx,mx,my,d);
    }
    @Override public boolean keyPressed(KeyInput input){
        if(input.key()==GLFW.GLFW_KEY_ESCAPE){if(client!=null)client.setScreen(parent);return true;}
        return super.keyPressed(input);
    }
    @Override public boolean shouldPause(){return false;}
}
