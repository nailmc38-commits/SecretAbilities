package com.survivalutils;

import com.secret.autoenchanter.AutoEnchanterClient;
import com.secret.tradecycler.TradeCyclerClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ExoLinkScreen extends Screen {
    private static final List<String> TABS=List.of("STATUS","VISOR","INTEL","COMBAT","PACK","TOOLS","SYSTEM");

    private String tab="STATUS";
    private int warningPage;

    public ExoLinkScreen() {
        super(Text.literal("EXO // LINK"));
    }

    @Override
    protected void init() {
        ExoLinkData.load();
        clearChildren();

        int left=Math.max(8,(width-Math.min(820,width-16))/2);
        int right=Math.min(width-8,left+Math.min(820,width-16));
        int top=48;
        int tabW=Math.max(82,(right-left-20)/TABS.size());

        for(int i=0;i<TABS.size();i++) {
            String name=TABS.get(i);
            int x=left+10+i*tabW;
            addDrawableChild(ButtonWidget.builder(Text.literal((name.equals(tab)?"◆ ":"")+name),b->{
                tab=name;
                warningPage=0;
                rebuild();
            }).dimensions(x,top,tabW-4,22).build());
        }

        int x=left+18;
        int y=88;
        int w=right-left-36;

        switch(tab) {
            case "STATUS"->buildStatus(x,y,w);
            case "VISOR"->buildVisor(x,y,w);
            case "INTEL"->buildIntel(x,y,w);
            case "COMBAT"->buildCombat(x,y,w);
            case "PACK"->buildPack(x,y,w);
            case "TOOLS"->buildTools(x,y,w);
            case "SYSTEM"->buildSystem(x,y,w);
        }
    }

    private void buildStatus(int x,int y,int w) {
        int half=(w-8)/2;
        toggle(x,y,half,"EXO HUD",()->ExoLinkData.SETTINGS.exoHud,v->ExoLinkData.SETTINGS.exoHud=v);
        toggle(x+half+8,y,half,"EMERGENCY MODE",()->ExoLinkData.SETTINGS.emergencyMode,v->ExoLinkData.SETTINGS.emergencyMode=v);
        y+=34;
        toggle(x,y,half,"ADVISOR",()->ExoLinkData.SETTINGS.advisor,v->ExoLinkData.SETTINGS.advisor=v);
        toggle(x+half+8,y,half,"ESCAPE VECTOR",()->ExoLinkData.SETTINGS.escapeVector,v->ExoLinkData.SETTINGS.escapeVector=v);
    }

    private void buildVisor(int x,int y,int w) {
        int half=(w-8)/2;
        toggle(x,y,half,"POWER ARMOR FRAME",()->ExoLinkData.SETTINGS.powerArmorFrame,v->ExoLinkData.SETTINGS.powerArmorFrame=v);
        toggle(x+half+8,y,half,"VISOR CRACKS",()->ExoLinkData.SETTINGS.visorCracks,v->ExoLinkData.SETTINGS.visorCracks=v);
        y+=34;
        toggle(x,y,half,"IDENTIFY",()->ExoLinkData.SETTINGS.identify,v->ExoLinkData.SETTINGS.identify=v);
        toggle(x+half+8,y,half,"DAMAGE DIRECTION",()->ExoLinkData.SETTINGS.damageDirection,v->ExoLinkData.SETTINGS.damageDirection=v);
        y+=34;
        toggle(x,y,half,"UI ANIMATIONS",()->ExoLinkData.SETTINGS.hudAnimations,v->ExoLinkData.SETTINGS.hudAnimations=v);
        button(x+half+8,y,half,"CLEAR VISOR DAMAGE",VisorDamageSystem::clear);
        y+=34;
        button(x,y,half,"VISOR MODE // "+ExoSuitSystems.configuredMode(),()->{
            ExoSuitSystems.cycleMode();
            rebuild();
        });
        button(x+half+8,y,half,"EDIT HUD LAYOUT",()->client.setScreen(new HudLayoutEditorScreen(this)));
    }

    private void buildIntel(int x,int y,int w) {
        int half=(w-8)/2;
        toggle(x,y,half,"THREAT MEMORY",()->ExoLinkData.SETTINGS.threatMemory,v->ExoLinkData.SETTINGS.threatMemory=v);
        toggle(x+half+8,y,half,"ECHO 3D",()->ExoLinkData.SETTINGS.echo,v->ExoLinkData.SETTINGS.echo=v);
        y+=34;
        toggle(x,y,half,"SATELLITE",()->ExoLinkData.SETTINGS.satellite,v->ExoLinkData.SETTINGS.satellite=v);
        toggle(x+half+8,y,half,"COMPANION",()->ExoLinkData.SETTINGS.companion,v->ExoLinkData.SETTINGS.companion=v);
        y+=34;
        if(client!=null&&client.targetedEntity instanceof PlayerEntity p) {
            button(x,y,half,"SAT TRACK // "+p.getGameProfile().name(),()->{ExoDroneSystem.track(p,client);rebuild();});
            button(x+half+8,y,half,(ExoDroneSystem.ignored(p)?"UNIGNORE // ":"IGNORE // ")+p.getGameProfile().name(),()->{ExoDroneSystem.toggleIgnore(p);rebuild();});
        } else {
            button(x,y,half,"CLEAR SAT TARGET",()->{ExoDroneSystem.clearTarget();rebuild();});
            disabled(x+half+8,y,half,"LOOK AT PLAYER TO IGNORE");
        }
        y+=34;
        button(x,y,half,"THREAT MEMORY // "+ThreatMemoryManager.all().size(),()->client.setScreen(new ExoThreatMemoryScreen(this)));
        if(ExoLinkData.SETTINGS.echo) button(x+half+8,y,half,"OPEN ECHO",()->client.setScreen(new Echo3DScreen(this)));
    }

    private void buildCombat(int x,int y,int w) {
        int half=(w-8)/2;
        toggle(x,y,half,"ADVISOR",()->ExoLinkData.SETTINGS.advisor,v->ExoLinkData.SETTINGS.advisor=v);
        toggle(x+half+8,y,half,"ESCAPE VECTOR",()->ExoLinkData.SETTINGS.escapeVector,v->ExoLinkData.SETTINGS.escapeVector=v);
        y+=34;

        if(client!=null&&client.targetedEntity instanceof PlayerEntity p) {
            ThreatMemoryManager.Contact c=ThreatMemoryManager.get(p);
            String tag=c==null?"NEUTRAL":c.tag;
            button(x,y,half,"TAG "+p.getGameProfile().name()+" // "+tag,()->{
                ThreatMemoryManager.cycleTag(p);
                rebuild();
            });
            button(x+half+8,y,half,"LOCK THREAT // "+p.getGameProfile().name(),()->{
                ExoSuitSystems.lockThreat(p);
                rebuild();
            });
        } else {
            disabled(x,y,half,"LOOK AT PLAYER TO TAG");
            button(x+half+8,y,half,"CLEAR THREAT LOCK",()->{
                ExoSuitSystems.clearThreatLock();
                rebuild();
            });
        }
    }

    private void buildPack(int x,int y,int w) {
        int third=(w-16)/3;
        button(x,y,third,"SCAN MY DOGS",()->{PackManager.scan(client);rebuild();});
        button(x+third+8,y,third,"SIT ALL",()->PackManager.issue(client,PackManager.Order.SIT));
        button(x+2*(third+8),y,third,"FOLLOW / RECALL",()->PackManager.issue(client,PackManager.Order.FOLLOW));
        y+=34;
        button(x,y,w,"CLEAR PACK",()->{PackManager.clear(client);rebuild();});
    }

    private void buildTools(int x,int y,int w) {
        int half=(w-8)/2;
        button(x,y,half,"AUTO ENCHANTER",()->AutoEnchanterClient.openBuilder(client));
        button(x+half+8,y,half,"VILLAGER CYCLER SETTINGS",()->TradeCyclerClient.openSettings(client,this));
        y+=34;
        button(x,y,half,"START / STOP VILLAGER CYCLER",()->{TradeCyclerClient.toggle(client);rebuild();});
        button(x+half+8,y,half,"SEED CRACKER",SeedCrackerShortcut::runNow);
    }

    private void buildSystem(int x,int y,int w) {
        int half=(w-8)/2;
        button(x,y,half,"WARNING MATRIX",()->client.setScreen(new ExoWarningSettingsScreen(this)));
        button(x+half+8,y,half,"AUDIO BUS",()->client.setScreen(new ExoAudioSettingsScreen(this)));
        y+=34;
        button(x,y,half,"DATA CENTER",()->client.setScreen(new ExoDataCenterScreen(this)));
        button(x+half+8,y,half,"SYSTEM TEST",()->client.setScreen(new ExoSystemTestScreen(this)));
        y+=34;
        toggle(x,y,half,"EXO SCRIPT",()->ExoLinkData.SETTINGS.exoScript,v->ExoLinkData.SETTINGS.exoScript=v);
        button(x+half+8,y,half,"RELOAD EXO SCRIPTS",()->{ExoScriptEngine.reload();rebuild();});
        y+=34;
        toggle(x,y,half,"PER-SERVER PROFILES",()->ExoLinkData.SETTINGS.perServerProfiles,v->ExoLinkData.SETTINGS.perServerProfiles=v);
        button(x+half+8,y,half,"SAVE ALL",()->{
            ExoLinkData.save();
            HudLayoutManager.save();
            ThreatMemoryManager.save();
            ServerProfileManager.saveCurrent();
        });
    }

    @Override
    public void render(DrawContext ctx,int mouseX,int mouseY,float delta) {
        int left=Math.max(8,(width-Math.min(820,width-16))/2);
        int right=Math.min(width-8,left+Math.min(820,width-16));

        ctx.fill(0,0,width,height,0xF4050708);
        ctx.fill(left,10,right,height-10,0xF20C1113);
        ctx.fill(left,10,right,14,0xFF65787D);
        ctx.fill(left+3,14,left+6,height-13,0xFF263236);
        ctx.fill(right-6,14,right-3,height-13,0xFF263236);

        ctx.drawTextWithShadow(textRenderer,"EXO // LINK",left+16,23,0xFFDDF8FA);
        ctx.drawTextWithShadow(textRenderer,"POWER ARMOR SURVIVAL SYSTEM",left+16,35,0xFF71888D);

        drawStatusReadout(ctx,left+18,250,right-left-36);
        super.render(ctx,mouseX,mouseY,delta);
    }

    private void drawStatusReadout(DrawContext ctx,int x,int y,int w) {
        if(client==null||client.player==null)return;
        switch(tab) {
            case "STATUS"->{
                CombatAdvisor.Snapshot a=CombatAdvisor.current();
                line(ctx,x,y,"ADVISOR // "+a.recommendation()+" // "+a.opponent(),0xFF72DFEB); y+=15;
                line(ctx,x,y,"ESCAPE // "+a.escape().direction()+" // "+a.escape().clearBlocks()+"m CLEAR",0xFFA8C9CE); y+=15;
                WarningManager.Warning warn=SurvivalUtilsClient.WARNINGS.active();
                line(ctx,x,y,"WARNING // "+(warn==null?"NONE":warn.severity()+" // "+warn.text()),warn==null?0xFF7FE4A2:0xFFFF7777);
            }
            case "VISOR"->{
                line(ctx,x,y,"VISOR DAMAGE // "+VisorDamageSystem.damagePercent()+"%",0xFF7DE1EB); y+=15;
                line(ctx,x,y,"MODE // "+ExoSuitSystems.effectiveMode(client)+" // CONFIG "+ExoSuitSystems.configuredMode(),0xFF9BC3C8); y+=15;
                line(ctx,x,y,"ARMOR // "+SurvMegaState.armorName(client.player)+" // "+SurvMegaState.armorIntegrity(client.player)+"%",0xFFAAC5C9);
            }
            case "INTEL"->{
                line(ctx,x,y,"THREAT MEMORY // "+ThreatMemoryManager.all().size()+" CONTACTS",0xFF7DE1EB); y+=15;
                ExoDroneSystem.Status drone=ExoDroneSystem.status(client);
                line(ctx,x,y,"SATELLITE // "+drone.satelliteState()+" // TARGET "+drone.target(),0xFF9DDDE5); y+=15;
                line(ctx,x,y,"COMPANION // "+(drone.companionAlert()?"ALERT":"STABLE")+" // IGNORED "+drone.ignored(),0xFFA9C3C7); y+=15;
                line(ctx,x,y,"Only observed equipment/items are stored. Hidden inventory is never guessed.",0xFF829A9E);
            }
            case "COMBAT"->{
                CombatAdvisor.Snapshot a=CombatAdvisor.current();
                line(ctx,x,y,"TARGET // "+a.opponent()+" // "+a.opponentType(),0xFF7DE1EB); y+=15;
                if(a.estimatedWinPercent()>=0) {
                    line(ctx,x,y,"WIN ESTIMATE // "+a.estimatedWinPercent()+"% // "+a.confidence()+" CONFIDENCE",0xFFFFD07A); y+=15;
                }
                line(ctx,x,y,"THREAT LOCK // "+(ExoSuitSystems.hasThreatLock()?"ACTIVE":"NONE"),0xFF9BC3C8); y+=15;
                int shown=0;
                for(String s:a.details()) {
                    if(shown++>=5)break;
                    line(ctx,x,y,s,0xFF9CB4B8); y+=14;
                }
            }
            case "PACK"->{
                PackManager.Status s=PackManager.status(client);
                line(ctx,x,y,"KNOWN "+s.known()+" // LOADED "+s.loaded()+" // REACHABLE "+s.reachable(),0xFF7DE1EB); y+=15;
                line(ctx,x,y,"SITTING "+s.sitting()+" // FOLLOWING "+s.standing()+" // ORDER "+s.activeOrder(),0xFFA7C2C7);
            }
            case "TOOLS"->{
                line(ctx,x,y,"ENCHANTER // "+AutoEnchanterClient.status(),0xFF7DE1EB); y+=15;
                line(ctx,x,y,"VILLAGER CYCLER // "+TradeCyclerClient.status(),0xFFA7C2C7);
            }
            case "SYSTEM"->{
                line(ctx,x,y,"DATA RETENTION // "+ExoLinkData.SETTINGS.dataRetentionDays+" DAYS",0xFF7DE1EB); y+=15;
                line(ctx,x,y,"THREAT CONTACTS // "+ThreatMemoryManager.all().size(),0xFFA7C2C7); y+=15;
                line(ctx,x,y,"COMBAT RECORDS // "+ExoTelemetry.combats().size()+" // ROUTE POINTS "+ExoTelemetry.route().size(),0xFFA7C2C7); y+=15;
                line(ctx,x,y,"PORTAL LINKS // "+ExoTelemetry.portals().size()+" // RECOVERY "+(ExoTelemetry.recovery()==null?"NONE":"READY"),0xFFA7C2C7); y+=15;
                line(ctx,x,y,"PROFILE // "+ServerProfileManager.currentKey(),0xFF9FCAD0); y+=15;
                line(ctx,x,y,"EXO SCRIPT // "+ExoScriptEngine.ruleCount()+" RULES // "+ExoScriptEngine.errorCount()+" ERRORS",ExoScriptEngine.errorCount()==0?0xFF7FE4A2:0xFFFF8C72); y+=15;
                var diag=ExoTelemetry.diagnostics(client);
                line(ctx,x,y,"DIAGNOSTICS // "+diag.entrySet().stream().filter(e->!"ONLINE".equals(e.getValue())).count()+" ATTENTION",0xFF829A9E);
            }
        }
    }

    private interface BoolGet{boolean get();}
    private interface BoolSet{void set(boolean value);}

    private void toggle(int x,int y,int w,String label,BoolGet g,BoolSet s) {
        button(x,y,w,label+" // "+onOff(g.get()),()->{
            s.set(!g.get());
            ExoLinkData.save();
            rebuild();
        });
    }

    private void button(int x,int y,int w,String label,Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label),b->r.run()).dimensions(x,y,w,24).build());
    }

    private void disabled(int x,int y,int w,String label) {
        ButtonWidget b=ButtonWidget.builder(Text.literal(label),x2->{}).dimensions(x,y,w,24).build();
        b.active=false;
        addDrawableChild(b);
    }

    private int enabledWarnings() {
        int n=0;
        for(boolean v:ExoLinkData.SETTINGS.warnings.values())if(v)n++;
        return n;
    }

    private String pretty(String key){return key.replace('_',' ').toUpperCase(Locale.ROOT);}
    private String onOff(boolean v){return v?"ON":"OFF";}

    private void line(DrawContext ctx,int x,int y,String s,int color) {
        if(y<height-20)ctx.drawTextWithShadow(textRenderer,s,x,y,color);
    }

    private void rebuild(){clearChildren();init();}

    @Override
    public boolean keyPressed(KeyInput input) {
        if(input.key()==GLFW.GLFW_KEY_ESCAPE||input.key()==GLFW.GLFW_KEY_F9){close();return true;}
        return super.keyPressed(input);
    }

    @Override public boolean shouldPause(){return false;}
}
