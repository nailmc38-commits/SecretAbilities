package com.survivalutils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class SurvAutomationScreen extends Screen {
    private final Screen parent;

    public SurvAutomationScreen(Screen parent){
        super(Text.literal("SURV // AUTOMATION"));
        this.parent=parent;
    }

    @Override
    protected void init(){
        SurvAutomationManager.load();
        clearChildren();

        int left=Math.max(12,width/2-280);
        int top=52;
        int colW=268;
        int gap=12;

        int y=top;
        toggle(left,y,colW,"AUTO EAT",()->SurvAutomationManager.SETTINGS.autoEat,v->SurvAutomationManager.SETTINGS.autoEat=v); y+=28;
        toggle(left,y,colW,"REFILL TOTEM SLOT 8",()->SurvAutomationManager.SETTINGS.refillTotemSlot,v->SurvAutomationManager.SETTINGS.refillTotemSlot=v); y+=28;
        toggle(left,y,colW,"REFILL SHIELD SLOT 9",()->SurvAutomationManager.SETTINGS.refillShieldSlot,v->SurvAutomationManager.SETTINGS.refillShieldSlot=v); y+=28;
        toggle(left,y,colW,"REFILL FOOD SLOT 7",()->SurvAutomationManager.SETTINGS.refillFoodSlot,v->SurvAutomationManager.SETTINGS.refillFoodSlot=v); y+=28;
        toggle(left,y,colW,"REFILL ROCKET SLOT 6",()->SurvAutomationManager.SETTINGS.refillRocketSlot,v->SurvAutomationManager.SETTINGS.refillRocketSlot=v); y+=28;
        toggle(left,y,colW,"REFILL TORCH SLOT 5",()->SurvAutomationManager.SETTINGS.refillTorchSlot,v->SurvAutomationManager.SETTINGS.refillTorchSlot=v); y+=28;
        toggle(left,y,colW,"REFILL BLOCK SLOT 4",()->SurvAutomationManager.SETTINGS.refillBlockSlot,v->SurvAutomationManager.SETTINGS.refillBlockSlot=v); y+=28;
        toggle(left,y,colW,"TOOL SAVER",()->SurvAutomationManager.SETTINGS.toolSaver,v->SurvAutomationManager.SETTINGS.toolSaver=v);

        int x2=left+colW+gap;
        y=top;
        toggle(x2,y,colW,"FIRE WATER RESPONSE",()->SurvAutomationManager.SETTINGS.fireWaterResponse,v->SurvAutomationManager.SETTINGS.fireWaterResponse=v); y+=28;
        toggle(x2,y,colW,"AUTO TORCH",()->SurvAutomationManager.SETTINGS.autoTorch,v->SurvAutomationManager.SETTINGS.autoTorch=v); y+=28;
        toggle(x2,y,colW,"SAFE WALK",()->SurvAutomationManager.SETTINGS.autoSafeWalk,v->SurvAutomationManager.SETTINGS.autoSafeWalk=v); y+=28;
        toggle(x2,y,colW,"AUTO SPRINT",()->SurvAutomationManager.SETTINGS.autoSprint,v->SurvAutomationManager.SETTINGS.autoSprint=v); y+=28;
        toggle(x2,y,colW,"PACK RECALL ON DANGER",()->SurvAutomationManager.SETTINGS.packRecallOnDanger,v->SurvAutomationManager.SETTINGS.packRecallOnDanger=v); y+=28;
        toggle(x2,y,colW,"AUTO SLEEP NEARBY",()->SurvAutomationManager.SETTINGS.autoSleepNearby,v->SurvAutomationManager.SETTINGS.autoSleepNearby=v); y+=28;
        toggle(x2,y,colW,"COMBAT FOCUS",()->SurvAutomationManager.SETTINGS.combatFocus,v->SurvAutomationManager.SETTINGS.combatFocus=v); y+=28;
        toggle(x2,y,colW,"REFILL PEARLS SLOT 2",()->SurvAutomationManager.SETTINGS.refillPearlSlot,v->SurvAutomationManager.SETTINGS.refillPearlSlot=v); y+=28;
        toggle(x2,y,colW,"REFILL GAPPLE SLOT 3",()->SurvAutomationManager.SETTINGS.refillGoldenAppleSlot,v->SurvAutomationManager.SETTINGS.refillGoldenAppleSlot=v); y+=28;
        toggle(x2,y,colW,"REFILL WATER SLOT 1",()->SurvAutomationManager.SETTINGS.refillWaterBucketSlot,v->SurvAutomationManager.SETTINGS.refillWaterBucketSlot=v); y+=28;
        toggle(x2,y,colW,"EMERGENCY SHIELD READY",()->SurvAutomationManager.SETTINGS.emergencyShieldReady,v->SurvAutomationManager.SETTINGS.emergencyShieldReady=v); y+=28;
        toggle(x2,y,colW,"LOW FOOD RESERVE",()->SurvAutomationManager.SETTINGS.lowFoodAutoReserve,v->SurvAutomationManager.SETTINGS.lowFoodAutoReserve=v); y+=28;

        addDrawableChild(ButtonWidget.builder(Text.literal("BACK"),b->client.setScreen(parent))
                .dimensions(left,height-34,100,22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("SAVE"),b->{
            SurvAutomationManager.save();
            client.setScreen(parent);
        }).dimensions(width-112,height-34,100,22).build());
    }

    private interface Get{boolean get();}
    private interface Set{void set(boolean v);}

    private void toggle(int x,int y,int w,String name,Get get,Set set){
        addDrawableChild(ButtonWidget.builder(Text.literal(name+" // "+(get.get()?"ON":"OFF")),b->{
            set.set(!get.get());
            SurvAutomationManager.save();
            clearChildren();
            init();
        }).dimensions(x,y,w,22).build());
    }

    @Override
    public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        ctx.fill(0,0,width,height,0xF4070B0D);
        ctx.drawCenteredTextWithShadow(textRenderer,Text.literal("SURV // AUTOMATION CORE"),width/2,18,0xFF72E7EE);
        ctx.drawCenteredTextWithShadow(textRenderer,Text.literal("REAL ACTIONS // EACH MODULE CAN BE DISABLED"),width/2,32,0xFF71888D);
        super.render(ctx,mouseX,mouseY,delta);
    }

    @Override public boolean shouldPause(){return false;}
}
