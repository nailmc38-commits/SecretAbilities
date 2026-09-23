package com.survivalutils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Locale;

public final class ExoWarningSettingsScreen extends Screen {
    private final Screen parent;
    private int page;

    public ExoWarningSettingsScreen(Screen parent){
        super(Text.literal("EXO // WARNINGS"));
        this.parent=parent;
    }

    @Override
    protected void init(){
        ExoLinkData.load();
        clearChildren();
        ArrayList<String> keys=new ArrayList<>(ExoLinkData.SETTINGS.warnings.keySet());
        int per=12;
        int pages=Math.max(1,(keys.size()+per-1)/per);
        page=Math.max(0,Math.min(page,pages-1));

        int panelW=Math.min(720,width-24);
        int left=(width-panelW)/2;
        int half=(panelW-28)/2;
        int start=page*per;
        int end=Math.min(keys.size(),start+per);

        for(int i=start;i<end;i++){
            String key=keys.get(i);
            int n=i-start;
            int col=n%2,row=n/2;
            int x=left+10+col*(half+8);
            int y=58+row*30;
            addDrawableChild(ButtonWidget.builder(
                    Text.literal(pretty(key)+" // "+(ExoLinkData.warning(key)?"ON":"OFF")),
                    b->{ExoLinkData.toggleWarning(key);rebuild();}
            ).dimensions(x,y,half,23).build());
        }

        int navY=Math.min(height-36,58+6*30+8);
        addDrawableChild(ButtonWidget.builder(Text.literal("< PAGE"),b->{page=Math.max(0,page-1);rebuild();})
                .dimensions(left+10,navY,88,22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("PAGE "+(page+1)+"/"+pages),b->{})
                .dimensions(left+106,navY,100,22).build()).active=false;
        addDrawableChild(ButtonWidget.builder(Text.literal("PAGE >"),b->{page=Math.min(pages-1,page+1);rebuild();})
                .dimensions(left+214,navY,88,22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("ALL ON"),b->{
            for(String k:keys)ExoLinkData.SETTINGS.warnings.put(k,true);
            ExoLinkData.save();rebuild();
        }).dimensions(left+panelW-206,navY,88,22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("BACK"),b->{if(client!=null)client.setScreen(parent);})
                .dimensions(left+panelW-110,navY,100,22).build());
    }

    @Override
    public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        ctx.fill(0,0,width,height,0xF405080A);
        ctx.drawCenteredTextWithShadow(textRenderer,"EXO // WARNING MATRIX",width/2,18,0xFFDDF8FA);
        ctx.drawCenteredTextWithShadow(textRenderer,"Every detector can be disabled separately.",width/2,34,0xFF829A9E);
        super.render(ctx,mouseX,mouseY,delta);
    }

    private String pretty(String s){return s.replace('_',' ').toUpperCase(Locale.ROOT);}
    private void rebuild(){clearChildren();init();}

    @Override public boolean keyPressed(KeyInput input){
        if(input.key()==GLFW.GLFW_KEY_ESCAPE){if(client!=null)client.setScreen(parent);return true;}
        return super.keyPressed(input);
    }
    @Override public boolean shouldPause(){return false;}
}
