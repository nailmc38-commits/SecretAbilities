package com.survivalutils;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.*;

public final class SurvMegaHubScreen extends Screen {
    private static final List<String> BUILTIN = List.of(
            "HOME","ADVISOR","EXO","ECHO","MOB CONTROL","SCRIPT","MACROS","AURA",
            "TRACKER","TRAP","COMPANION","SATELLITE","ESPANOL","LIBRARY","WASTELAND",
            "LOG","UTILITIES","TOOLS"
    );

    private String tab;
    private int sidebarScroll;
    private int contentScroll;
    private int bookPage;
    private int spanishPage;

    public SurvMegaHubScreen() { this("HOME"); }
    public SurvMegaHubScreen(String tab) {
        super(Text.literal("SURV // OS"));
        this.tab = tab == null ? "HOME" : tab;
    }

    @Override
    protected void init() {
        SurvMegaState.ensureLoaded();
        clearChildren();

        int left = panelLeft();
        int top = 48;
        int sidebarW = 124;
        int visible = Math.max(6, (height - 78) / 27);
        List<String> tabs = allTabs();

        sidebarScroll = Math.max(0, Math.min(sidebarScroll, Math.max(0, tabs.size() - visible)));
        for (int i=0;i<visible && i+sidebarScroll<tabs.size();i++) {
            String name=tabs.get(i+sidebarScroll);
            boolean active=name.equals(tab);
            addDrawableChild(ButtonWidget.builder(Text.literal((active?"◆ ":"  ")+displayName(name)), b -> {
                tab=name;
                contentScroll=0;
                rebuild();
            }).dimensions(left+10, top+i*27, sidebarW-18, 22).build());
        }

        buildTab(panelLeft()+sidebarW+8, 56, panelRight()-panelLeft()-sidebarW-18);
    }

    private void rebuild() {
        clearChildren();
        init();
    }

    private List<String> allTabs() {
        ArrayList<String> out=new ArrayList<>(BUILTIN);
        for (SurvMegaState.ScriptTab t:SurvMegaState.scriptTabs()) out.add("SCRIPT:"+t.name());
        return out;
    }

    private String displayName(String name) {
        if(name.startsWith("SCRIPT:"))return name.substring(7);
        return name;
    }

    private void buildTab(int x,int y,int w) {
        switch(tab) {
            case "HOME" -> home(x,y,w);
            case "ADVISOR" -> advisor(x,y,w);
            case "EXO" -> exo(x,y,w);
            case "ECHO" -> echo(x,y,w);
            case "MOB CONTROL" -> mob(x,y,w);
            case "SCRIPT" -> scripts(x,y,w);
            case "MACROS" -> macros(x,y,w);
            case "AURA" -> aura(x,y,w);
            case "TRACKER" -> tracker(x,y,w);
            case "TRAP" -> trap(x,y,w);
            case "COMPANION" -> companion(x,y,w);
            case "SATELLITE" -> satellite(x,y,w);
            case "ESPANOL" -> spanish(x,y,w);
            case "LIBRARY" -> library(x,y,w);
            case "WASTELAND" -> wasteland(x,y,w);
            case "LOG" -> logTab(x,y,w);
            case "UTILITIES" -> utilities(x,y,w);
            case "TOOLS" -> tools(x,y,w);
            default -> {
                if(tab.startsWith("SCRIPT:")) scriptCustom(x,y,w,tab.substring(7));
            }
        }
    }

    private void home(int x,int y,int w) {
        toggle(x,y,w/2-4,"ADVISOR",()->SurvMegaState.SETTINGS.advisor,v->SurvMegaState.SETTINGS.advisor=v);
        toggle(x+w/2+4,y,w/2-4,"EXO",()->SurvMegaState.SETTINGS.exo,v->SurvMegaState.SETTINGS.exo=v);
        y+=32;
        toggle(x,y,w/2-4,"VISOR DAMAGE",()->SurvMegaState.SETTINGS.visorDamage,v->SurvMegaState.SETTINGS.visorDamage=v);
        toggle(x+w/2+4,y,w/2-4,"IDENTIFY",()->SurvMegaState.SETTINGS.identify,v->SurvMegaState.SETTINGS.identify=v);
        y+=32;
        toggle(x,y,w/2-4,"TRACKER",()->SurvMegaState.SETTINGS.tracker,v->SurvMegaState.SETTINGS.tracker=v);
        toggle(x+w/2+4,y,w/2-4,"SATELLITE",()->SurvMegaState.SETTINGS.satellite,v->SurvMegaState.SETTINGS.satellite=v);
        y+=32;
        toggle(x,y,w/2-4,"COMPANION",()->SurvMegaState.SETTINGS.companion,v->SurvMegaState.SETTINGS.companion=v);
        toggle(x+w/2+4,y,w/2-4,"AURA",()->SurvMegaState.SETTINGS.aura,v->SurvMegaState.SETTINGS.aura=v);
        y+=44;
        button(x,y,w/2-4,"OPEN ECHO 3D",()->client.setScreen(new Echo3DScreen(this)));
        button(x+w/2+4,y,w/2-4,"PLAY WASTELAND 3D",()->client.setScreen(new Wasteland3DScreen(this)));
        y+=32;
        button(x,y,w/2-4,"ORIGINAL UTILITIES",()->client.setScreen(new SurvivalUtilsScreen()));
        button(x+w/2+4,y,w/2-4,"SAVE EVERYTHING",()->{
            SurvMegaState.save();
            SurvivalUtilsClient.CONFIG.save();
        });
    }

    private void advisor(int x,int y,int w) {
        toggle(x,y,w,"SURV // ADVISOR",()->SurvMegaState.SETTINGS.advisor,v->SurvMegaState.SETTINGS.advisor=v);
    }

    private void exo(int x,int y,int w) {
        toggle(x,y,w/2-4,"EXO HUD",()->SurvMegaState.SETTINGS.exo,v->SurvMegaState.SETTINGS.exo=v);
        toggle(x+w/2+4,y,w/2-4,"VISOR DAMAGE",()->SurvMegaState.SETTINGS.visorDamage,v->SurvMegaState.SETTINGS.visorDamage=v);
        y+=34;
        button(x,y,w/2-4,"CLEAR VISOR DAMAGE",SurvMegaState::clearVisor);
        button(x+w/2+4,y,w/2-4,"SAVE",SurvMegaState::save);
    }

    private void echo(int x,int y,int w) {
        button(x,y,w,"OPEN SURV // ECHO 3D",()->client.setScreen(new Echo3DScreen(this)));
    }

    private void mob(int x,int y,int w) {
        toggle(x,y,w,"MOB CONTROL",()->SurvMegaState.SETTINGS.mobControl,v->SurvMegaState.SETTINGS.mobControl=v);
        y+=34;
        button(x,y,w,"SELECT / RELEASE LOOKED-AT MOB",()->SurvMegaState.selectLookedAtMob(client));
    }

    private void scripts(int x,int y,int w) {
        toggle(x,y,w,"SURV SCRIPT ENGINE",()->SurvMegaState.SETTINGS.scripts,v->SurvMegaState.SETTINGS.scripts=v);
        y+=34;
        button(x,y,w/2-4,"RELOAD .SURV FILES",()->{SurvMegaState.reloadScripts();rebuild();});
        button(x+w/2+4,y,w/2-4,"SAVE",SurvMegaState::save);
    }

    private void macros(int x,int y,int w) {
        toggle(x,y,w,"MACRO SYSTEM",()->SurvMegaState.SETTINGS.macros,v->SurvMegaState.SETTINGS.macros=v);
        y+=34;
        button(x,y,w/2-4,SurvMegaState.macroRecording()?"STOP RECORDING":"RECORD",()->{
            if(SurvMegaState.macroRecording())SurvMegaState.stopMacro(client);else SurvMegaState.startMacroRecording();
            rebuild();
        });
        button(x+w/2+4,y,w/2-4,SurvMegaState.macroPlaying()?"STOP PLAYBACK":"PLAY",()->{
            if(SurvMegaState.macroPlaying())SurvMegaState.stopMacro(client);else SurvMegaState.playMacro();
            rebuild();
        });
        y+=34;
        button(x,y,w,"DELETE RECORDING",()->{SurvMegaState.clearMacro(client);rebuild();});
    }

    private void aura(int x,int y,int w) {
        toggle(x,y,w,"SURV // AURA",()->SurvMegaState.SETTINGS.aura,v->{
            SurvMegaState.SETTINGS.aura=v;
            if(!v)SurvMegaState.stopAura();
        });
        y+=34;
        toggle(x,y,w/2-4,"PLAYER COMBAT",()->SurvMegaState.SETTINGS.auraPlayers,v->SurvMegaState.SETTINGS.auraPlayers=v);
        toggle(x+w/2+4,y,w/2-4,"MOB COMBAT",()->SurvMegaState.SETTINGS.auraMobs,v->SurvMegaState.SETTINGS.auraMobs=v);
        y+=34;
        button(x,y,w,"STOP CURRENT AURA",SurvMegaState::stopAura);
    }

    private void tracker(int x,int y,int w) {
        toggle(x,y,w,"PLAYER TRACKER / LAST SEEN",()->SurvMegaState.SETTINGS.tracker,v->SurvMegaState.SETTINGS.tracker=v);
        y+=34;
        button(x,y,w,"CLEAR TRACKER HISTORY",()->{SurvMegaState.clearTracker();rebuild();});
    }

    private void trap(int x,int y,int w) {
        toggle(x,y,w,"TRAP ASSIST",()->SurvMegaState.SETTINGS.trap,v->SurvMegaState.SETTINGS.trap=v);
        y+=34;
        button(x,y,w/2-4,"RANGE  "+SurvMegaState.SETTINGS.trapRange,()->{
            SurvMegaState.SETTINGS.trapRange++;
            if(SurvMegaState.SETTINGS.trapRange>8)SurvMegaState.SETTINGS.trapRange=2;
            SurvMegaState.save();rebuild();
        });
        button(x+w/2+4,y,w/2-4,"PACE  "+SurvMegaState.SETTINGS.trapDelayTicks+"t",()->{
            SurvMegaState.SETTINGS.trapDelayTicks+=2;
            if(SurvMegaState.SETTINGS.trapDelayTicks>20)SurvMegaState.SETTINGS.trapDelayTicks=4;
            SurvMegaState.save();rebuild();
        });
    }

    private void companion(int x,int y,int w) {
        toggle(x,y,w,"SURV // COMPANION",()->SurvMegaState.SETTINGS.companion,v->SurvMegaState.SETTINGS.companion=v);
    }

    private void satellite(int x,int y,int w) {
        toggle(x,y,w,"SURV // SATELLITE",()->SurvMegaState.SETTINGS.satellite,v->SurvMegaState.SETTINGS.satellite=v);
        y+=34;
        toggle(x,y,w/2-4,"FOLLOW PLAYERS",()->SurvMegaState.SETTINGS.satellitePlayers,v->SurvMegaState.SETTINGS.satellitePlayers=v);
        toggle(x+w/2+4,y,w/2-4,"FOLLOW HOSTILES",()->SurvMegaState.SETTINGS.satelliteHostiles,v->SurvMegaState.SETTINGS.satelliteHostiles=v);
    }

    private void spanish(int x,int y,int w) {
        button(x,y,w/2-4,"◀ PREVIOUS",()->{spanishPage=Math.max(0,spanishPage-1);});
        button(x+w/2+4,y,w/2-4,"NEXT ▶",()->{spanishPage=Math.min(5,spanishPage+1);});
    }

    private void library(int x,int y,int w) {
        button(x,y,w/2-4,"◀ PAGE",()->bookPage=Math.max(0,bookPage-1));
        button(x+w/2+4,y,w/2-4,"PAGE ▶",()->bookPage++);
    }

    private void wasteland(int x,int y,int w) {
        button(x,y,w,"PLAY SURV // WASTELAND 3D",()->client.setScreen(new Wasteland3DScreen(this)));
    }

    private void logTab(int x,int y,int w) {
        toggle(x,y,w,"EVENT LOGGING",()->SurvMegaState.SETTINGS.logs,v->SurvMegaState.SETTINGS.logs=v);
        y+=34;
        button(x,y,w,"CLEAR ENTIRE LOG",()->{SurvMegaState.clearLogs();rebuild();});
    }

    private void utilities(int x,int y,int w) {
        button(x,y,w,"OPEN SURVIVAL UTILS 2.3 CONTROLS",()->client.setScreen(new SurvivalUtilsScreen()));
    }

    private void tools(int x,int y,int w) {
        button(x,y,w/2-4,"AUTOENCHANTER STATUS",()->{});
        button(x+w/2+4,y,w/2-4,"TRADECYCLER STATUS",()->{});
    }

    private void scriptCustom(int x,int y,int w,String name) {
        // Script tabs are display-only by default. Their text is rendered below.
    }

    private interface BoolGet { boolean get(); }
    private interface BoolSet { void set(boolean v); }

    private void toggle(int x,int y,int w,String label,BoolGet get,BoolSet set) {
        button(x,y,w,label+"  "+(get.get()?"ON":"OFF"),()->{
            set.set(!get.get());
            SurvMegaState.save();
            rebuild();
        });
    }

    private void button(int x,int y,int w,String label,Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label),b->action.run()).dimensions(x,y,w,24).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX,double mouseY,double hAmount,double vAmount) {
        int left=panelLeft();
        if(mouseX>=left&&mouseX<=left+124) {
            List<String> tabs=allTabs();
            int visible=Math.max(6,(height-78)/27);
            int max=Math.max(0,tabs.size()-visible);
            int old=sidebarScroll;
            sidebarScroll=Math.max(0,Math.min(max,sidebarScroll-(int)Math.round(vAmount)));
            if(old!=sidebarScroll){rebuild();return true;}
        } else {
            int old=contentScroll;
            contentScroll=Math.max(0,contentScroll-(int)Math.round(vAmount*18));
            if(old!=contentScroll)return true;
        }
        return super.mouseScrolled(mouseX,mouseY,hAmount,vAmount);
    }

    @Override
    public void render(DrawContext ctx,int mouseX,int mouseY,float delta) {
        int l=panelLeft(),r=panelRight();
        ctx.fill(0,0,width,height,0xEE05080C);
        ctx.fill(l,10,r,height-10,0xF20A1118);
        ctx.fill(l,10,r,13,0xFF55EEE7);
        ctx.fill(l+124,42,l+125,height-18,0x554CE8E2);

        ctx.drawTextWithShadow(textRenderer,Text.literal("SURV // OS").formatted(Formatting.AQUA,Formatting.BOLD),l+14,20,0xFFEAFBFF);
        String status="ADVISOR "+(SurvMegaState.SETTINGS.advisor?"ON":"OFF")
                +"  •  EXO "+(SurvMegaState.SETTINGS.exo?"ON":"OFF")
                +"  •  SCRIPT "+SurvMegaState.scriptTabs().size();
        ctx.drawTextWithShadow(textRenderer,status,r-textRenderer.getWidth(status)-14,21,0xFF839AA6);

        int cx=l+138;
        int cy=48-contentScroll;
        String title=displayName(tab);
        ctx.drawTextWithShadow(textRenderer,Text.literal(title).formatted(Formatting.WHITE,Formatting.BOLD),cx,42,0xFFFFFFFF);
        drawContent(ctx,cx,cy,r-cx-12);

        if(FabricLoader.getInstance().isModLoaded("autoenchanter")) {
            ctx.drawTextWithShadow(textRenderer,"AE",r-32,height-24,0xFF74F0A6);
        }
        if(FabricLoader.getInstance().isModLoaded("tradecycler")) {
            ctx.drawTextWithShadow(textRenderer,"TC",r-16,height-24,0xFF74F0A6);
        }
        super.render(ctx,mouseX,mouseY,delta);
    }

    private void drawContent(DrawContext ctx,int x,int y,int w) {
        int yy=y+150;
        switch(tab) {
            case "HOME" -> {
                line(ctx,x,yy,"One clean hub. Every major system is independently toggleable.",0xFF9FB4C0); yy+=14;
                line(ctx,x,yy,"Recommendation: "+SurvMegaState.recommendation(),0xFF7DE3FF); yy+=14;
                line(ctx,x,yy,"Mob units: "+SurvMegaState.mobUnitCount()+"   Macro frames: "+SurvMegaState.macroFrames(),0xFFB7C7D6); yy+=14;
                line(ctx,x,yy,"Visor damage: "+(int)Math.round(SurvMegaState.visorDamage()*100)+"%",0xFFCFEFFF);
            }
            case "ADVISOR" -> {
                line(ctx,x,yy,"CURRENT // "+SurvMegaState.recommendation(),0xFF74F0A6);yy+=16;
                wrap(ctx,x,yy,w,SurvMegaState.recommendationWhy(),0xFFB7C7D6);
            }
            case "EXO" -> {
                if(client!=null&&client.player!=null) {
                    line(ctx,x,yy,"ARMOR TYPE // "+SurvMegaState.armorName(client.player),0xFF8EEAFF);yy+=14;
                    line(ctx,x,yy,"INTEGRITY // "+SurvMegaState.armorIntegrity(client.player)+"%",0xFFEAFBFF);yy+=14;
                }
                line(ctx,x,yy,"Visor self-repair requires 5 minutes without damage and armor above 50%.",0xFF9FB4C0);
            }
            case "ECHO" -> {
                line(ctx,x,yy,"Interactive 3D client-world reconstruction.",0xFF8EEAFF);yy+=14;
                line(ctx,x,yy,"Your real player does not move while ECHO is open.",0xFF9FB4C0);
            }
            case "MOB CONTROL" -> {
                line(ctx,x,yy,"Selected units // "+SurvMegaState.mobUnitCount(),0xFF8EEAFF);yy+=14;
                line(ctx,x,yy,"Selection and squad tracking are local. Server AI still controls mob targeting.",0xFF9FB4C0);
            }
            case "SCRIPT" -> {
                line(ctx,x,yy,"Folder // config/surv-os/scripts",0xFF8EEAFF);yy+=14;
                line(ctx,x,yy,"Scripts can create their own tabs with TAB and TEXT commands.",0xFF9FB4C0);yy+=14;
                line(ctx,x,yy,"They can also append to Library books with APPEND_BOOK.",0xFF9FB4C0);
            }
            case "MACROS" -> {
                line(ctx,x,yy,"State // "+(SurvMegaState.macroRecording()?"RECORDING":SurvMegaState.macroPlaying()?"PLAYING":"IDLE"),0xFF8EEAFF);yy+=14;
                line(ctx,x,yy,"Frames // "+SurvMegaState.macroFrames(),0xFFB7C7D6);
            }
            case "AURA" -> {
                line(ctx,x,yy,"Place your local track at:",0xFF8EEAFF);yy+=14;
                line(ctx,x,yy,"config/surv-os/aura/"+SurvMegaState.SETTINGS.auraFile,0xFFEAFBFF);yy+=14;
                line(ctx,x,yy,"This build uses Java local audio; WAV is the reliable format.",0xFF9FB4C0);
            }
            case "TRACKER" -> {
                int shown=0;
                for(SurvMegaState.TrackedPlayer t:SurvMegaState.tracked()) {
                    if(shown++>=10)break;
                    line(ctx,x,yy,t.name()+" // "+(System.currentTimeMillis()-t.seenAt()<3000?"LIVE":"LAST SEEN")
                            +" // "+(int)t.x()+" "+(int)t.y()+" "+(int)t.z(),t.spectator()?0xFFFF8C8C:0xFFBFE8FF);
                    yy+=13;
                }
                if(shown==0)line(ctx,x,yy,"No player locations observed yet.",0xFF718894);
            }
            case "TRAP" -> {
                line(ctx,x,yy,"Manual/proximity settings live here. Progressive placement only.",0xFF9FB4C0);yy+=14;
                line(ctx,x,yy,"Range // "+SurvMegaState.SETTINGS.trapRange+" blocks   Pace // "+SurvMegaState.SETTINGS.trapDelayTicks+" ticks",0xFFBFE8FF);
            }
            case "COMPANION" -> {
                line(ctx,x,yy,"Client-side SURV companion display.",0xFF8EEAFF);yy+=14;
                line(ctx,x,yy,"Cosmetic + status only; other players do not see it.",0xFF9FB4C0);
            }
            case "SATELLITE" -> {
                var target=SurvMegaState.satelliteTarget(client);
                line(ctx,x,yy,"Target // "+(target==null?"NONE":target.getName().getString()),0xFF8EEAFF);yy+=14;
                line(ctx,x,yy,"You choose whether it follows players or hostile mobs.",0xFF9FB4C0);
            }
            case "ESPANOL" -> drawSpanish(ctx,x,yy,w);
            case "LIBRARY" -> drawBook(ctx,x,yy,w);
            case "WASTELAND" -> {
                line(ctx,x,yy,"Open-world survival RPG module with local saving.",0xFF8EEAFF);yy+=14;
                line(ctx,x,yy,"Story is optional; you can explore, scavenge, visit town and maintain power armor.",0xFF9FB4C0);
            }
            case "LOG" -> {
                List<SurvMegaState.LogEntry> logs=SurvMegaState.logs();
                int start=Math.max(0,logs.size()-12);
                for(int i=start;i<logs.size();i++) {
                    var e=logs.get(i);
                    line(ctx,x,yy,SurvMegaState.time(e.time())+"  "+e.category()+"  "+e.text(),0xFFB7C7D6);yy+=13;
                }
                if(logs.isEmpty())line(ctx,x,yy,"Log is empty.",0xFF718894);
            }
            case "UTILITIES" -> {
                line(ctx,x,yy,"The original Survival Utils 2.3 controls remain intact.",0xFF8EEAFF);yy+=14;
                line(ctx,x,yy,"HUD, threat warnings, stats, water clutch and SeedCrackerX stay available.",0xFF9FB4C0);
            }
            case "TOOLS" -> {
                boolean ae=FabricLoader.getInstance().isModLoaded("autoenchanter");
                boolean tc=FabricLoader.getInstance().isModLoaded("tradecycler");
                line(ctx,x,yy,"AutoEnchanter // "+(ae?"INSTALLED":"NOT LOADED"),ae?0xFF74F0A6:0xFFFF8C8C);yy+=14;
                line(ctx,x,yy,"TradeCycler // "+(tc?"INSTALLED":"NOT LOADED"),tc?0xFF74F0A6:0xFFFF8C8C);yy+=14;
                line(ctx,x,yy,"The final combined JAR can bundle both so their normal keybinds still work.",0xFF9FB4C0);
            }
            default -> {
                if(tab.startsWith("SCRIPT:")) {
                    String n=tab.substring(7);
                    SurvMegaState.ScriptTab st=SurvMegaState.scriptTabs().stream().filter(s->s.name().equals(n)).findFirst().orElse(null);
                    if(st!=null) {
                        line(ctx,x,yy,"SCRIPT // "+st.sourceFile(),0xFF8EEAFF);yy+=18;
                        for(String s:st.lines()){wrap(ctx,x,yy,w,s,0xFFB7C7D6);yy+=22;}
                    }
                }
            }
        }
    }

    private void drawSpanish(DrawContext ctx,int x,int y,int w) {
        String[][] p={
                {"A1 // BASICS","Hola = Hello","¿Cómo estás? = How are you?","Estoy bien = I am good"},
                {"A1 // MINECRAFT","espada = sword","cofre = chest","pico = pickaxe","aldea = village"},
                {"A1 // USEFUL","No entiendo = I don't understand","Necesito ayuda = I need help","¿Dónde está...? = Where is...?"},
                {"A1 // VERBS","tener = to have","querer = to want","ir = to go","hacer = to do/make"},
                {"A1 // PRACTICE","Yo tengo trece años.","Me gusta Minecraft.","Vivo en Texas."},
                {"A2 PREVIEW","Ayer jugué con mis amigos.","Voy a aprender más español.","Quiero mejorar poco a poco."}
        };
        int i=Math.max(0,Math.min(spanishPage,p.length-1));
        for(String s:p[i]){line(ctx,x,y,s,y==y?0xFF8EEAFF:0xFFB7C7D6);y+=15;}
        line(ctx,x,y+8,"Lesson "+(i+1)+" / "+p.length,0xFF718894);
    }

    private void drawBook(DrawContext ctx,int x,int y,int w) {
        List<String> lines=SurvMegaState.readBook("under_the_bridge.txt");
        int per=Math.max(8,(height-y-35)/13);
        int pages=Math.max(1,(lines.size()+per-1)/per);
        bookPage=Math.max(0,Math.min(bookPage,pages-1));
        int start=bookPage*per,end=Math.min(lines.size(),start+per);
        for(int i=start;i<end;i++) {
            String s=lines.get(i);
            int col=(s.startsWith("CHAPTER")||s.equals("UNDER THE BRIDGE"))?0xFF8EEAFF:0xFFD9E7EC;
            line(ctx,x,y,s,col);y+=13;
        }
        line(ctx,x,y+5,"Page "+(bookPage+1)+" / "+pages+"  •  file: under_the_bridge.txt",0xFF718894);
    }

    private void line(DrawContext ctx,int x,int y,String s,int color) {
        if(y<53||y>height-24)return;
        ctx.drawTextWithShadow(textRenderer,s,x,y,color);
    }

    private void wrap(DrawContext ctx,int x,int y,int w,String s,int color) {
        int max=Math.max(20,w/7);
        String rem=s;
        int yy=y;
        while(rem.length()>max&&yy<height-25) {
            int cut=rem.lastIndexOf(' ',max);
            if(cut<10)cut=max;
            line(ctx,x,yy,rem.substring(0,cut),color);
            rem=rem.substring(cut).trim();
            yy+=13;
        }
        line(ctx,x,yy,rem,color);
    }

    private int panelLeft(){return Math.max(6,(width-Math.min(760,width-12))/2);}
    private int panelRight(){return Math.min(width-6,panelLeft()+Math.min(760,width-12));}

    @Override
    public boolean keyPressed(KeyInput input) {
        int k=input.key();
        if(k==GLFW.GLFW_KEY_ESCAPE||k==GLFW.GLFW_KEY_F9){close();return true;}
        return super.keyPressed(input);
    }

    @Override public boolean shouldPause(){return false;}
}
