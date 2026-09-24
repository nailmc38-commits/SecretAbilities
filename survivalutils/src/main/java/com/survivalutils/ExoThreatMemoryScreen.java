package com.survivalutils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ExoThreatMemoryScreen extends Screen {
    private final Screen parent;
    private int index;

    public ExoThreatMemoryScreen(Screen parent){
        super(Text.literal("EXO // THREAT MEMORY"));
        this.parent=parent;
    }

    @Override
    protected void init(){
        clearChildren();
        List<ThreatMemoryManager.Contact> list=contacts();
        if(index>=list.size())index=Math.max(0,list.size()-1);

        int y=height-34;
        addDrawableChild(ButtonWidget.builder(Text.literal("BACK"),b->client.setScreen(parent))
                .dimensions(10,y,70,22).build());

        if(!list.isEmpty()){
            ThreatMemoryManager.Contact c=list.get(index);
            addDrawableChild(ButtonWidget.builder(Text.literal("< PREV"),b->{index=Math.max(0,index-1);rebuild();})
                    .dimensions(90,y,72,22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("NEXT >"),b->{index=Math.min(contacts().size()-1,index+1);rebuild();})
                    .dimensions(168,y,72,22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("TAG // "+c.tag),b->{
                ThreatMemoryManager.cycleTag(c.uuid);
                rebuild();
            }).dimensions(250,y,112,22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(c.starred?"UNSTAR":"STAR"),b->{
                ThreatMemoryManager.toggleStar(c.uuid);
                rebuild();
            }).dimensions(368,y,74,22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("FORGET"),b->{
                ThreatMemoryManager.remove(c.uuid);
                if(index>0)index--;
                rebuild();
            }).dimensions(448,y,82,22).build());
        }
    }

    private List<ThreatMemoryManager.Contact> contacts(){
        ArrayList<ThreatMemoryManager.Contact> list=new ArrayList<>(ThreatMemoryManager.all());
        list.sort(Comparator.comparingLong((ThreatMemoryManager.Contact c)->c.lastSeen).reversed());
        return list;
    }

    @Override
    public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        ctx.fill(0,0,width,height,0xF405080A);
        ctx.fill(8,8,width-8,height-44,0xE70C1113);
        ctx.fill(8,8,width-8,12,0xFF62D9E8);

        ctx.drawTextWithShadow(textRenderer,"EXO // THREAT MEMORY",18,20,0xFFDDF8FA);
        ctx.drawTextWithShadow(textRenderer,"OBSERVED DATA ONLY // EXO never invents hidden inventory",18,34,0xFF71888D);

        List<ThreatMemoryManager.Contact> list=contacts();
        if(list.isEmpty()){
            ctx.drawCenteredTextWithShadow(textRenderer,Text.literal("NO CONTACTS RECORDED"),width/2,height/2,0xFF8AA0A5);
            super.render(ctx,mouseX,mouseY,delta);
            return;
        }

        index=Math.max(0,Math.min(index,list.size()-1));
        ThreatMemoryManager.Contact c=list.get(index);

        int left=20;
        int mid=Math.max(285,width/2);
        int y=58;

        line(ctx,left,y,c.name.toUpperCase()+" // "+c.tag+(c.starred?" // STARRED":""),tagColor(c.tag)); y+=17;
        line(ctx,left,y,"CONTACT "+(index+1)+" / "+list.size(),0xFF71888D); y+=20;

        line(ctx,left,y,"LAST SEEN // "+ago(c.lastSeen),0xFFA9C4C8); y+=14;
        line(ctx,left,y,"FIRST SEEN // "+ago(c.firstSeen),0xFF8FA6AA); y+=14;
        line(ctx,left,y,"ENCOUNTERS // "+c.encounters+" // COMBAT "+c.combatEncounters,0xFFA9C4C8); y+=14;
        line(ctx,left,y,"LAST HP SEEN // "+(c.lastHealth<0?"UNKNOWN":String.format("%.1f",c.lastHealth)),0xFFA9C4C8); y+=14;
        line(ctx,left,y,"MOVEMENT // "+c.movement,0xFFA9C4C8); y+=18;

        line(ctx,left,y,"LAST POSITION",0xFF62D9E8); y+=14;
        line(ctx,left,y,c.x+"  "+c.y+"  "+c.z,0xFFD8E9EC); y+=14;
        line(ctx,left,y,"DIM // "+safe(c.dimension),0xFF9FB7BC); y+=14;
        line(ctx,left,y,"BIOME // "+safe(c.biome),0xFF9FB7BC);

        int yy=58;
        line(ctx,mid,yy,"VISIBLE / LAST OBSERVED GEAR",0xFF62D9E8); yy+=17;
        yy=gear(ctx,mid,yy,"HELMET",c.helmet);
        yy=gear(ctx,mid,yy,"CHEST",c.chest);
        yy=gear(ctx,mid,yy,"LEGS",c.legs);
        yy=gear(ctx,mid,yy,"BOOTS",c.boots);
        yy=gear(ctx,mid,yy,"MAIN HAND",c.mainHand);
        yy=gear(ctx,mid,yy,"OFF HAND",c.offHand);
        yy+=7;

        line(ctx,mid,yy,"OBSERVED INVENTORY HISTORY // "+c.observedItems.size(),0xFF62D9E8); yy+=16;

        List<Map.Entry<String,Long>> items=new ArrayList<>(c.observedItems.entrySet());
        items.sort(Map.Entry.<String,Long>comparingByValue().reversed());

        int shown=0;
        for(Map.Entry<String,Long> e:items){
            if(shown++>=12||yy>height-68)break;
            line(ctx,mid,yy,pretty(e.getKey())+" // "+ago(e.getValue()),0xFFB7C9CC);
            yy+=13;
        }

        if(items.size()>shown){
            line(ctx,mid,yy,"+"+(items.size()-shown)+" more observed item types",0xFF71888D);
        }

        super.render(ctx,mouseX,mouseY,delta);
    }

    private int gear(DrawContext ctx,int x,int y,String slot,String item){
        line(ctx,x,y,slot+" // "+pretty(item),item==null||item.isBlank()||"empty".equals(item)?0xFF64767A:0xFFD7EAED);
        return y+14;
    }

    private void line(DrawContext ctx,int x,int y,String text,int color){
        if(y<height-48)ctx.drawTextWithShadow(textRenderer,text,x,y,color);
    }

    private String safe(String s){return s==null||s.isBlank()?"UNKNOWN":s.toUpperCase();}

    private String pretty(String s){
        if(s==null||s.isBlank()||"empty".equals(s))return "EMPTY";
        return s.replace('_',' ').toUpperCase();
    }

    private String ago(long time){
        if(time<=0)return "UNKNOWN";
        long sec=Math.max(0,Duration.ofMillis(System.currentTimeMillis()-time).toSeconds());
        if(sec<60)return sec+"s ago";
        long min=sec/60;
        if(min<60)return min+"m ago";
        long hr=min/60;
        if(hr<24)return hr+"h ago";
        return (hr/24)+"d ago";
    }

    private int tagColor(String tag){
        return switch(tag){
            case "FRIEND"->0xFF78E9A0;
            case "WATCH"->0xFFFFC866;
            case "HOSTILE"->0xFFFF6262;
            default->0xFFB8CDD0;
        };
    }

    private void rebuild(){clearChildren();init();}

    @Override
    public boolean keyPressed(KeyInput input){
        if(input.key()==GLFW.GLFW_KEY_ESCAPE||input.key()==GLFW.GLFW_KEY_F9){
            if(client!=null)client.setScreen(parent);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override public boolean shouldPause(){return false;}
}
