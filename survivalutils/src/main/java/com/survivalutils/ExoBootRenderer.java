package com.survivalutils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class ExoBootRenderer {
    private ExoBootRenderer(){}

    public static void render(DrawContext ctx,MinecraftClient client){
        if(!ExoAudioManager.bootActive()||client==null||client.textRenderer==null)return;

        long age=ExoAudioManager.bootAge();
        int w=ctx.getScaledWindowWidth(),h=ctx.getScaledWindowHeight();
        int boxW=Math.min(370,w-40);
        int boxH=112;
        int x=(w-boxW)/2;
        int y=(h-boxH)/2;

        int alpha=age>2200?(int)Math.max(0,220-(age-2200)*220/500):220;
        int bg=(alpha<<24)|0x081012;

        ctx.fill(x,y,x+boxW,y+boxH,bg);
        ctx.fill(x,y,x+boxW,y+3,0xFF65787D);
        ctx.fill(x,y,x+3,y+boxH,0xFF2D3B3F);
        ctx.fill(x+boxW-3,y,x+boxW,y+boxH,0xFF2D3B3F);

        text(ctx,client,x+14,y+12,"EXO // LINK",0xFFDDF8FA);
        text(ctx,client,x+14,y+26,"POWER ARMOR STARTUP",0xFF83999E);

        stage(ctx,client,x+14,y+48,"VISOR LINK",age>=300);
        stage(ctx,client,x+14,y+62,"THREAT SENSOR",age>=750);
        stage(ctx,client,x+14,y+76,"ADVISOR / ESCAPE VECTOR",age>=1200);
        stage(ctx,client,x+14,y+90,"DATA BUS",age>=1650);

        if(age>=1900){
            String online="EXO // LINK ONLINE";
            int tw=client.textRenderer.getWidth(online);
            text(ctx,client,x+boxW-tw-14,y+90,online,0xFF75EFA6);
        }
    }

    private static void stage(DrawContext ctx,MinecraftClient c,int x,int y,String name,boolean ready){
        text(ctx,c,x,y,name,ready?0xFFCFEAED:0xFF516268);
        String state=ready?"OK":"...";
        int sw=c.textRenderer.getWidth(state);
        text(ctx,c,x+260-sw,y,state,ready?0xFF75EFA6:0xFFFFC868);
    }

    private static void text(DrawContext ctx,MinecraftClient c,int x,int y,String s,int color){
        ctx.drawTextWithShadow(c.textRenderer,s,x,y,color);
    }
}
