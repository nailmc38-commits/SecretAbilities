package com.survivalutils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class ExoSystemTestScreen extends Screen {
    private final Screen parent;

    public ExoSystemTestScreen(Screen parent){
        super(Text.literal("EXO // SYSTEM TEST"));
        this.parent=parent;
    }

    @Override protected void init(){
        clearChildren();
        int w=Math.min(600,width-24),left=(width-w)/2,half=(w-28)/2,y=62;
        button(left+10,y,half,"TEST INFO",()->SurvivalUtilsClient.WARNINGS.test(WarningManager.Severity.INFO));
        button(left+18+half,y,half,"TEST CAUTION",()->SurvivalUtilsClient.WARNINGS.test(WarningManager.Severity.CAUTION)); y+=32;
        button(left+10,y,half,"TEST DANGER",()->SurvivalUtilsClient.WARNINGS.test(WarningManager.Severity.DANGER));
        button(left+18+half,y,half,"TEST CRITICAL",()->SurvivalUtilsClient.WARNINGS.test(WarningManager.Severity.CRITICAL)); y+=32;
        button(left+10,y,half,"TEST VISOR IMPACT",()->VisorDamageSystem.testImpact(client));
        button(left+18+half,y,half,"TEST BOOT SEQUENCE",()->ExoAudioManager.startBoot(client)); y+=32;
        button(left+10,y,half,"CLEAR VISOR DAMAGE",VisorDamageSystem::clear);
        button(left+18+half,y,half,"RELOAD EXO SCRIPT",ExoScriptEngine::reload); y+=40;
        button(left+10,y,w-20,"BACK",()->{if(client!=null)client.setScreen(parent);});
    }

    private void button(int x,int y,int w,String t,Runnable r){
        addDrawableChild(ButtonWidget.builder(Text.literal(t),b->r.run()).dimensions(x,y,w,23).build());
    }

    @Override public void render(DrawContext ctx,int mx,int my,float d){
        ctx.fill(0,0,width,height,0xF405080A);
        ctx.drawCenteredTextWithShadow(textRenderer,"EXO // SYSTEM TEST",width/2,18,0xFFDDF8FA);
        ctx.drawCenteredTextWithShadow(textRenderer,"Preview suit behavior without needing a real emergency.",width/2,34,0xFF829A9E);
        super.render(ctx,mx,my,d);
    }

    @Override public boolean keyPressed(KeyInput input){
        if(input.key()==GLFW.GLFW_KEY_ESCAPE){if(client!=null)client.setScreen(parent);return true;}
        return super.keyPressed(input);
    }
    @Override public boolean shouldPause(){return false;}
}
