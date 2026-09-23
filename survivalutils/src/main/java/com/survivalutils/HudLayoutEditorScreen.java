package com.survivalutils;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HudLayoutEditorScreen extends Screen {
    private final Screen parent;
    private String selected="core";
    private String dragging="";
    private double dragOffsetX,dragOffsetY;

    private static final LinkedHashMap<String,int[]> SIZE=new LinkedHashMap<>();
    static{
        SIZE.put("core",new int[]{185,72});
        SIZE.put("advisor",new int[]{225,118});
        SIZE.put("identify",new int[]{155,32});
        SIZE.put("pack",new int[]{230,29});
        SIZE.put("escape",new int[]{250,27});
        SIZE.put("flight",new int[]{230,63});
        SIZE.put("recovery",new int[]{225,20});
        SIZE.put("warning",new int[]{460,24});
    }

    public HudLayoutEditorScreen(Screen parent){
        super(Text.literal("EXO // HUD EDITOR"));
        this.parent=parent;
    }

    @Override
    protected void init(){
        HudLayoutManager.load();
        clearChildren();

        addDrawableChild(ButtonWidget.builder(Text.literal("DONE"),b->{
            HudLayoutManager.save();
            if(client!=null)client.setScreen(parent);
        }).dimensions(width-86,12,74,22).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("RESET"),b->{
            HudLayoutManager.reset();
        }).dimensions(width-166,12,74,22).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("SCALE -"),b->{
            HudLayoutManager.adjustScale(selected,-0.05);
            HudLayoutManager.save();
        }).dimensions(12,height-30,76,20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("SCALE +"),b->{
            HudLayoutManager.adjustScale(selected,0.05);
            HudLayoutManager.save();
        }).dimensions(94,height-30,76,20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("OPACITY -"),b->{
            HudLayoutManager.adjustOpacity(selected,-0.05);
            HudLayoutManager.save();
        }).dimensions(176,height-30,92,20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("OPACITY +"),b->{
            HudLayoutManager.adjustOpacity(selected,0.05);
            HudLayoutManager.save();
        }).dimensions(274,height-30,92,20).build());
    }

    @Override
    public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        ctx.fill(0,0,width,height,0xE805080A);
        ctx.fill(0,0,width,42,0xF0141B1E);
        ctx.drawTextWithShadow(textRenderer,"EXO // HUD EDITOR",12,12,0xFFDDF8FA);
        ctx.drawTextWithShadow(textRenderer,"Drag panels • wheel or buttons resize • opacity controls background",12,25,0xFF8AA0A5);

        for(Map.Entry<String,int[]> e:SIZE.entrySet()){
            drawPanel(ctx,e.getKey(),e.getValue(),mouseX,mouseY);
        }

        HudLayoutManager.Panel p=HudLayoutManager.panel(selected);
        String status="SELECTED // "+selected.toUpperCase()+" // SCALE "+String.format("%.2f",p.scale)+" // OPACITY "+String.format("%.2f",p.opacity);
        ctx.drawTextWithShadow(textRenderer,status,380,height-25,0xFFAADCE2);

        super.render(ctx,mouseX,mouseY,delta);
    }

    private void drawPanel(DrawContext ctx,String key,int[] size,int mouseX,int mouseY){
        HudLayoutManager.Panel p=HudLayoutManager.panel(key);
        int w=(int)Math.round(size[0]*p.scale);
        int h=(int)Math.round(size[1]*p.scale);
        int x=HudLayoutManager.x(key,width,size[0]);
        int y=HudLayoutManager.y(key,height,size[1]);

        boolean sel=key.equals(selected);
        boolean hover=inside(mouseX,mouseY,x,y,w,h);
        int border=sel?0xFF63E2EE:(hover?0xFFC0E9ED:0xFF53676C);
        int bg=HudLayoutManager.alpha(key,sel?0xAA102025:0x82101719);

        ctx.fill(x,y,x+w,y+h,bg);
        ctx.fill(x,y,x+w,y+2,border);
        ctx.fill(x,y,x+2,y+h,border);
        ctx.fill(x+w-2,y,x+w,y+h,border);
        ctx.fill(x,y+h-2,x+w,y+h,border);
        ctx.drawTextWithShadow(textRenderer,key.toUpperCase(),x+6,y+6,sel?0xFFDDF8FA:0xFF9CB3B7);
    }

    @Override
    public boolean mouseClicked(Click click,boolean doubled){
        if(click.button()==GLFW.GLFW_MOUSE_BUTTON_1){
            for(var e:SIZE.entrySet()){
                String key=e.getKey();
                int[] size=e.getValue();
                HudLayoutManager.Panel p=HudLayoutManager.panel(key);
                int w=(int)Math.round(size[0]*p.scale);
                int h=(int)Math.round(size[1]*p.scale);
                int x=HudLayoutManager.x(key,width,size[0]);
                int y=HudLayoutManager.y(key,height,size[1]);
                if(inside(click.x(),click.y(),x,y,w,h)){
                    selected=key;
                    dragging=key;
                    dragOffsetX=click.x()-x;
                    dragOffsetY=click.y()-y;
                    return true;
                }
            }
        }
        return super.mouseClicked(click,doubled);
    }

    @Override
    public boolean mouseDragged(Click click,double offsetX,double offsetY){
        if(!dragging.isBlank()&&click.button()==GLFW.GLFW_MOUSE_BUTTON_1){
            int[] size=SIZE.get(dragging);
            HudLayoutManager.setScreenPosition(
                    dragging,
                    click.x()-dragOffsetX,
                    click.y()-dragOffsetY,
                    width,height,size[0],size[1]
            );
            return true;
        }
        return super.mouseDragged(click,offsetX,offsetY);
    }

    @Override
    public boolean mouseReleased(Click click){
        if(!dragging.isBlank()){
            dragging="";
            HudLayoutManager.save();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical){
        if(!selected.isBlank()&&vertical!=0){
            HudLayoutManager.adjustScale(selected,vertical>0?0.05:-0.05);
            HudLayoutManager.save();
            return true;
        }
        return super.mouseScrolled(mouseX,mouseY,horizontal,vertical);
    }

    private boolean inside(double mx,double my,int x,int y,int w,int h){
        return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;
    }

    @Override
    public boolean keyPressed(KeyInput input){
        if(input.key()==GLFW.GLFW_KEY_ESCAPE){
            HudLayoutManager.save();
            if(client!=null)client.setScreen(parent);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override public boolean shouldPause(){return false;}
}
