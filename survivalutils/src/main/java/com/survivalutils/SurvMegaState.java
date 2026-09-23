package com.survivalutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class SurvMegaState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("surv-os");
    private static final Path STATE_PATH = ROOT.resolve("mega-state.json");
    private static final Path SCRIPT_DIR = ROOT.resolve("scripts");
    private static final Path LIBRARY_DIR = ROOT.resolve("library");
    private static final Path AURA_DIR = ROOT.resolve("aura");

    public static final Settings SETTINGS = new Settings();
    private static final ArrayList<LogEntry> LOGS = new ArrayList<>();
    private static final LinkedHashMap<String, TrackedPlayer> TRACKED = new LinkedHashMap<>();
    private static final LinkedHashSet<UUID> MOB_UNITS = new LinkedHashSet<>();
    private static final ArrayList<ScriptTab> SCRIPT_TABS = new ArrayList<>();
    private static final ArrayList<MacroFrame> MACRO = new ArrayList<>();

    private static boolean loaded;
    private static int ticks;
    private static float lastHealth = -1f;
    private static long lastDamageAt;
    private static long combatUntil;
    private static double visorDamage;
    private static String recommendation = "STABLE";
    private static String recommendationWhy = "No immediate action needed.";
    private static boolean macroRecording;
    private static boolean macroPlaying;
    private static int macroIndex;
    private static Clip auraClip;
    private static boolean auraPlaying;
    private static int trapTicker;
    private static int trapPhase;
    private static String lastDimension = "";
    private static long lastSaveAt;

    private SurvMegaState() {}

    public static final class Settings {
        public boolean advisor = true;
        public boolean exo = true;
        public boolean visorDamage = true;
        public boolean identify = true;
        public boolean tracker = true;
        public boolean logs = true;
        public boolean aura = false;
        public boolean macros = true;
        public boolean scripts = true;
        public boolean mobControl = true;
        public boolean trap = false;
        public boolean trapWeb = true;
        public boolean trapBox = true;
        public boolean companion = true;
        public boolean satellite = true;
        public boolean satellitePlayers = true;
        public boolean satelliteHostiles = false;
        public boolean satelliteFriends = false;
        public boolean adaptiveHud = true;
        public int trapRange = 4;
        public int trapDelayTicks = 8;
        public int identifyDelayMs = 350;
        public String auraFile = "combat.wav";
        public boolean auraPlayers = true;
        public boolean auraMobs = true;
        public int auraStopDelaySeconds = 8;
    }

    public record LogEntry(long time, String category, String text) {}
    public record TrackedPlayer(String name, double x, double y, double z, String dimension, long seenAt, boolean spectator) {}
    public record ScriptTab(String sourceFile, String name, List<String> lines) {}
    private record MacroFrame(boolean f, boolean b, boolean l, boolean r, boolean j, boolean sneak, boolean use, boolean attack, int slot) {}

    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Files.createDirectories(ROOT);
            Files.createDirectories(SCRIPT_DIR);
            Files.createDirectories(LIBRARY_DIR);
            Files.createDirectories(AURA_DIR);
            if (Files.exists(STATE_PATH)) {
                SaveData data = GSON.fromJson(Files.readString(STATE_PATH), SaveData.class);
                if (data != null) {
                    if (data.settings != null) copySettings(data.settings, SETTINGS);
                    if (data.logs != null) {
                        LOGS.clear();
                        LOGS.addAll(data.logs);
                    }
                    if (data.tracked != null) {
                        TRACKED.clear();
                        for (TrackedPlayer t : data.tracked) if (t != null && t.name() != null) TRACKED.put(t.name().toLowerCase(Locale.ROOT), t);
                    }
                    visorDamage = Math.max(0, Math.min(1, data.visorDamage));
                }
            }
            ensureBook();
            ensureExampleScript();
            reloadScripts();
        } catch (Exception e) {
            System.err.println("[SURV OS] load failed: " + e);
        }
    }

    private static void copySettings(Settings src, Settings dst) {
        dst.advisor = src.advisor;
        dst.exo = src.exo;
        dst.visorDamage = src.visorDamage;
        dst.identify = src.identify;
        dst.tracker = src.tracker;
        dst.logs = src.logs;
        dst.aura = src.aura;
        dst.macros = src.macros;
        dst.scripts = src.scripts;
        dst.mobControl = src.mobControl;
        dst.trap = src.trap;
        dst.trapWeb = src.trapWeb;
        dst.trapBox = src.trapBox;
        dst.companion = src.companion;
        dst.satellite = src.satellite;
        dst.satellitePlayers = src.satellitePlayers;
        dst.satelliteHostiles = src.satelliteHostiles;
        dst.satelliteFriends = src.satelliteFriends;
        dst.adaptiveHud = src.adaptiveHud;
        dst.trapRange = Math.max(2, Math.min(8, src.trapRange));
        dst.trapDelayTicks = Math.max(2, Math.min(30, src.trapDelayTicks));
        dst.identifyDelayMs = Math.max(0, Math.min(2000, src.identifyDelayMs));
        if (src.auraFile != null && !src.auraFile.isBlank()) dst.auraFile = src.auraFile;
        dst.auraPlayers = src.auraPlayers;
        dst.auraMobs = src.auraMobs;
        dst.auraStopDelaySeconds = Math.max(1, Math.min(60, src.auraStopDelaySeconds));
    }

    private static final class SaveData {
        Settings settings;
        List<LogEntry> logs;
        List<TrackedPlayer> tracked;
        double visorDamage;
    }

    public static void save() {
        ensureLoaded();
        try {
            Files.createDirectories(ROOT);
            SaveData d = new SaveData();
            d.settings = SETTINGS;
            d.logs = new ArrayList<>(LOGS);
            d.tracked = new ArrayList<>(TRACKED.values());
            d.visorDamage = visorDamage;
            Files.writeString(STATE_PATH, GSON.toJson(d), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            lastSaveAt = System.currentTimeMillis();
        } catch (Exception e) {
            System.err.println("[SURV OS] save failed: " + e);
        }
    }

    public static void tick(MinecraftClient c) {
        ensureLoaded();
        ticks++;
        if (c == null || c.player == null || c.world == null) {
            stopOwnedKeys(c);
            lastHealth = -1f;
            return;
        }

        float hp = c.player.getHealth();
        if (lastHealth >= 0 && hp + 0.01f < lastHealth) {
            float lost = lastHealth - hp;
            lastDamageAt = System.currentTimeMillis();
            combatUntil = Math.max(combatUntil, lastDamageAt + 8000L);
            visorDamage = Math.min(1.0, visorDamage + 0.08 + lost * 0.025);
            log("COMBAT", "Impact detected // -" + String.format(Locale.ROOT, "%.1f", lost) + " HP");
        }
        lastHealth = hp;

        if (c.options != null && c.options.attackKey.isPressed() && c.crosshairTarget instanceof EntityHitResult ehr) {
            if (ehr.getEntity() instanceof LivingEntity) combatUntil = System.currentTimeMillis() + 8000L;
        }

        if (SETTINGS.visorDamage && visorDamage > 0.0
                && System.currentTimeMillis() - lastDamageAt >= 300_000L
                && armorIntegrity(c.player) > 50) {
            visorDamage = Math.max(0.0, visorDamage - 0.0018);
        }

        if (SETTINGS.tracker && ticks % 10 == 0) updateTracker(c);
        if (SETTINGS.advisor && ticks % 4 == 0) updateRecommendation(c);
        if (SETTINGS.macros) tickMacro(c);
        if (SETTINGS.aura) tickAura(c);
        if (SETTINGS.trap) tickTrap(c);

        String dim = c.world.getRegistryKey().getValue().toString();
        if (!dim.equals(lastDimension)) {
            if (!lastDimension.isBlank()) log("WORLD", "Dimension // " + dim);
            lastDimension = dim;
        }


        if (System.currentTimeMillis() - lastSaveAt > 20_000L) save();
    }

    private static void updateTracker(MinecraftClient c) {
        if (c.player == null || c.world == null) return;
        for (PlayerEntity p : c.world.getPlayers()) {
            if (p == c.player) continue;
            boolean spec = false;
            if (c.getNetworkHandler() != null) {
                var entry = c.getNetworkHandler().getPlayerListEntry(p.getUuid());
                spec = entry != null && entry.getGameMode() == GameMode.SPECTATOR;
            }
            TrackedPlayer old = TRACKED.get(p.getGameProfile().name().toLowerCase(Locale.ROOT));
            TrackedPlayer next = new TrackedPlayer(
                    p.getGameProfile().name(), p.getX(), p.getY(), p.getZ(),
                    c.world.getRegistryKey().getValue().toString(),
                    System.currentTimeMillis(), spec);
            TRACKED.put(next.name().toLowerCase(Locale.ROOT), next);
            if (old == null) log("TRACKER", "Contact acquired // " + next.name());
        }
    }

    private static void updateRecommendation(MinecraftClient c) {
        if (c.player == null) return;
        ThreatAnalyzer.Snapshot t = ThreatAnalyzer.analyze(c);
        if (c.player.getHealth() <= 6f && t.hostileCount() > 0) {
            setRecommendation("DISENGAGE", "Low health with active threats.");
            return;
        }
        if (c.player.getHungerManager().getFoodLevel() <= 5 && t.hostileCount() == 0) {
            setRecommendation("EAT", "Food level is low and the area is currently calmer.");
            return;
        }
        if (armorIntegrity(c.player) <= 25) {
            setRecommendation("REPAIR ARMOR", "Average equipped armor durability is below 25%.");
            return;
        }
        if (c.crosshairTarget instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity living
                && living.isAlive()
                && c.player.squaredDistanceTo(living) <= 20.25) {
            float cooldown = c.player.getAttackCooldownProgress(0.5f);
            if (cooldown >= 0.92f) {
                setRecommendation("ATTACK", "Target is in close range and your attack cooldown is ready.");
            } else {
                setRecommendation("WAIT", "Attack cooldown is still recovering.");
            }
            return;
        }
        if (t.level().ordinal() >= ThreatAnalyzer.Level.HIGH.ordinal()) {
            setRecommendation(t.recommendation(), t.reasons().isEmpty() ? "Threat analysis is elevated." : String.join(" • ", t.reasons()));
            return;
        }
        setRecommendation("STABLE", "No immediate action needed.");
    }

    private static void setRecommendation(String rec, String why) {
        if (!Objects.equals(recommendation, rec)) log("ADVISOR", rec + " // " + why);
        recommendation = rec;
        recommendationWhy = why;
    }

    private static void tickAura(MinecraftClient c) {
        boolean combat = System.currentTimeMillis() < combatUntil;
        if (combat && !auraPlaying) {
            boolean playerFight = nearbyPlayer(c, 8.0) != null;
            boolean mobFight = nearbyHostile(c, 8.0) != null;
            if ((playerFight && SETTINGS.auraPlayers) || (mobFight && SETTINGS.auraMobs)) playAura();
        }
        if (!combat && auraPlaying && System.currentTimeMillis() > combatUntil + SETTINGS.auraStopDelaySeconds * 1000L) stopAura();
    }

    private static void playAura() {
        stopAura();
        try {
            Path file = AURA_DIR.resolve(SETTINGS.auraFile).normalize();
            if (!file.startsWith(AURA_DIR) || !Files.exists(file)) return;
            AudioInputStream stream = AudioSystem.getAudioInputStream(file.toFile());
            auraClip = AudioSystem.getClip();
            auraClip.open(stream);
            auraClip.loop(Clip.LOOP_CONTINUOUSLY);
            auraClip.start();
            auraPlaying = true;
            log("AURA", "Playing // " + SETTINGS.auraFile);
        } catch (Exception e) {
            auraPlaying = false;
            log("AURA", "Could not play " + SETTINGS.auraFile + " // WAV works best");
        }
    }

    public static void stopAura() {
        try {
            if (auraClip != null) {
                auraClip.stop();
                auraClip.close();
            }
        } catch (Exception ignored) {}
        auraClip = null;
        auraPlaying = false;
    }

    public static void render(DrawContext ctx, MinecraftClient c) {
        ensureLoaded();
        if (c == null || c.player == null || c.world == null) return;
        if (SETTINGS.visorDamage) renderVisorDamage(ctx);
        if (SETTINGS.advisor) renderAdvisor(ctx, c);
        if (SETTINGS.exo) renderExo(ctx, c);
        if (SETTINGS.identify) renderIdentify(ctx, c);
        if (SETTINGS.companion) renderCompanion(ctx, c);
        if (SETTINGS.satellite) renderSatellite(ctx, c);
    }

    private static void renderAdvisor(DrawContext ctx, MinecraftClient c) {
        int w = ctx.getScaledWindowWidth();
        int x = Math.max(6, w - 205);
        int y = 8;
        int col = recommendation.equals("DISENGAGE") ? 0xFFFF6666
                : recommendation.equals("ATTACK") ? 0xFF74F0A6
                : recommendation.equals("WAIT") ? 0xFFFFD36A : 0xFF7DE3FF;
        ctx.fill(x, y, w - 8, y + 34, 0xB30A1118);
        ctx.fill(x, y, x + 3, y + 34, col);
        ctx.drawTextWithShadow(c.textRenderer, "SURV // ADVISOR", x + 8, y + 6, 0xFF9FB4C0);
        ctx.drawTextWithShadow(c.textRenderer, recommendation, x + 8, y + 18, col);
    }

    private static void renderExo(DrawContext ctx, MinecraftClient c) {
        if (c.player == null) return;
        int integrity = armorIntegrity(c.player);
        if (integrity >= 100 && c.player.getArmor() == 0) return;
        int x = 8;
        int y = 8;
        ctx.fill(x, y, x + 154, y + 34, 0xB30A1118);
        ctx.fill(x, y, x + 154, y + 2, 0xFF4CE8E2);
        ctx.drawTextWithShadow(c.textRenderer, "SURV // EXO", x + 7, y + 6, 0xFF8EEAFF);
        ctx.drawTextWithShadow(c.textRenderer, armorName(c.player) + " // " + integrity + "%", x + 7, y + 18,
                integrity <= 25 ? 0xFFFF6666 : 0xFFEAFBFF);
    }

    private static void renderIdentify(DrawContext ctx, MinecraftClient c) {
        HitResult hit = c.crosshairTarget;
        if (hit == null || hit.getType() == HitResult.Type.MISS) return;
        String title;
        ArrayList<String> lines = new ArrayList<>();
        if (hit instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            title = e.getName().getString().toUpperCase(Locale.ROOT);
            lines.add("DIST  " + String.format(Locale.ROOT, "%.1fm", c.player.distanceTo(e)));
            if (e instanceof LivingEntity le) lines.add("HEALTH  " + String.format(Locale.ROOT, "%.1f", le.getHealth()));
            if (e instanceof PlayerEntity pe) {
                lines.add("PLAYER  " + pe.getGameProfile().name());
                TrackedPlayer t = TRACKED.get(pe.getGameProfile().name().toLowerCase(Locale.ROOT));
                if (t != null) lines.add("TRACK  LIVE");
            } else if (e instanceof HostileEntity) {
                lines.add("STATE  HOSTILE");
            }
        } else if (hit instanceof BlockHitResult bhr) {
            BlockPos pos = bhr.getBlockPos();
            var state = c.world.getBlockState(pos);
            title = Registries.BLOCK.getId(state.getBlock()).getPath().toUpperCase(Locale.ROOT);
            lines.add("POS  " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
            lines.add("DIST  " + String.format(Locale.ROOT, "%.1fm", Math.sqrt(c.player.squaredDistanceTo(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5))));
        } else return;

        int boxW = 155;
        int boxH = 19 + lines.size() * 11;
        int x = Math.min(ctx.getScaledWindowWidth() - boxW - 8, ctx.getScaledWindowWidth()/2 + 36);
        int y = Math.max(46, ctx.getScaledWindowHeight()/2 - boxH/2);
        int anchorX = ctx.getScaledWindowWidth()/2 + 8;
        int anchorY = ctx.getScaledWindowHeight()/2;
        ctx.fill(anchorX, anchorY, x, anchorY + 1, 0xAA55EEE7);
        ctx.fill(x, y, x + boxW, y + boxH, 0xC90A1118);
        ctx.fill(x, y, x + 3, y + boxH, 0xFF55EEE7);
        ctx.drawTextWithShadow(c.textRenderer, title, x + 8, y + 5, 0xFFEAFBFF);
        int ly = y + 16;
        for (String line : lines) {
            ctx.drawTextWithShadow(c.textRenderer, line, x + 8, ly, 0xFF9FB4C0);
            ly += 11;
        }
    }

    private static void renderVisorDamage(DrawContext ctx) {
        if (visorDamage <= 0.015) return;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int alpha = Math.min(210, 70 + (int)(visorDamage * 130));
        int c = (alpha << 24) | 0x00C9F7FF;
        int count = 4 + (int)Math.round(visorDamage * 18);
        int[][] rays = {
                {0, h/5, w/5, h/3}, {0, h/5, w/6, h/2}, {w, h/4, w-w/5, h/3},
                {w, h/4, w-w/7, h/2}, {w/4, 0, w/3, h/4}, {3*w/4, 0, 2*w/3, h/4},
                {0, 4*h/5, w/5, 2*h/3}, {w, 4*h/5, w-w/5, 2*h/3},
                {w/8, h, w/4, 3*h/4}, {7*w/8, h, 3*w/4, 3*h/4}
        };
        for (int i=0;i<count;i++) {
            int[] r = rays[i % rays.length];
            drawSegmentedLine(ctx, r[0],r[1],r[2],r[3],c);
            int mx=(r[0]+r[2])/2, my=(r[1]+r[3])/2;
            drawSegmentedLine(ctx,mx,my,mx+(i%2==0?22:-18),my+26,c);
        }
        int shadeAlpha = Math.min(70, (int)(visorDamage * 75));
        if (shadeAlpha > 0) {
            int edge=(shadeAlpha<<24)|0x00101820;
            ctx.fill(0,0,w,5,edge); ctx.fill(0,h-5,w,h,edge); ctx.fill(0,0,5,h,edge); ctx.fill(w-5,0,w,h,edge);
        }
    }

    private static void drawSegmentedLine(DrawContext ctx,int x1,int y1,int x2,int y2,int color) {
        int steps = Math.max(1, Math.max(Math.abs(x2-x1),Math.abs(y2-y1))/5);
        for(int i=0;i<steps;i++) {
            double t=i/(double)steps;
            int x=(int)Math.round(x1+(x2-x1)*t);
            int y=(int)Math.round(y1+(y2-y1)*t);
            int nx=(int)Math.round(x1+(x2-x1)*Math.min(1,(i+1)/(double)steps));
            int ny=(int)Math.round(y1+(y2-y1)*Math.min(1,(i+1)/(double)steps));
            ctx.fill(Math.min(x,nx),Math.min(y,ny),Math.max(x,nx)+2,Math.max(y,ny)+2,color);
        }
    }

    private static void renderCompanion(DrawContext ctx, MinecraftClient c) {
        int x=18, y=ctx.getScaledWindowHeight()-44;
        long phase=(System.currentTimeMillis()/180)%8;
        ctx.fill(x+6,y+6,x+20,y+20,0xCC0D1E28);
        ctx.fill(x+4,y+10,x+22,y+16,0xCC183642);
        ctx.fill(x+10,y+4,x+16,y+22,0xCC183642);
        int eye=phase<4?0xFF55EEE7:0xFF8EEAFF;
        ctx.fill(x+11,y+10,x+15,y+14,eye);
        ctx.drawTextWithShadow(c.textRenderer,"COMPANION",x+28,y+9,0xFF718894);
    }

    private static void renderSatellite(DrawContext ctx, MinecraftClient c) {
        Entity target = satelliteTarget(c);
        if (target == null) return;
        int w=ctx.getScaledWindowWidth();
        int x=w/2-46,y=18;
        ctx.fill(x,y,x+92,y+22,0xAA071017);
        ctx.fill(x,y,x+92,y+1,0xFF55EEE7);
        ctx.drawTextWithShadow(c.textRenderer,"SAT // "+target.getName().getString(),x+6,y+6,0xFFBFFBFF);
    }

    public static Entity satelliteTarget(MinecraftClient c) {
        if (c == null || c.player == null || c.world == null) return null;
        Entity best=null; double bd=Double.MAX_VALUE;
        if (SETTINGS.satellitePlayers) {
            for (PlayerEntity p:c.world.getPlayers()) {
                if (p==c.player) continue;
                double d=c.player.squaredDistanceTo(p);
                if (d<bd) {bd=d;best=p;}
            }
        }
        if (SETTINGS.satelliteHostiles) {
            HostileEntity h=nearbyHostile(c,64);
            if (h!=null) {
                double d=c.player.squaredDistanceTo(h);
                if(d<bd) best=h;
            }
        }
        return best;
    }

    public static int armorIntegrity(PlayerEntity p) {
        int sum=0,count=0;
        for (net.minecraft.entity.EquipmentSlot slot : new net.minecraft.entity.EquipmentSlot[]{
                net.minecraft.entity.EquipmentSlot.HEAD,
                net.minecraft.entity.EquipmentSlot.CHEST,
                net.minecraft.entity.EquipmentSlot.LEGS,
                net.minecraft.entity.EquipmentSlot.FEET}) {
            ItemStack s = p.getEquippedStack(slot);
            if (s == null || s.isEmpty()) continue;
            count++;
            sum += InventoryUtil.durabilityPercent(s);
        }
        return count==0?100:(int)Math.round(sum/(double)count);
    }

    public static String armorName(PlayerEntity p) {
        String best="UNARMORED";
        for (net.minecraft.entity.EquipmentSlot slot : new net.minecraft.entity.EquipmentSlot[]{
                net.minecraft.entity.EquipmentSlot.HEAD,
                net.minecraft.entity.EquipmentSlot.CHEST,
                net.minecraft.entity.EquipmentSlot.LEGS,
                net.minecraft.entity.EquipmentSlot.FEET}) {
            ItemStack s = p.getEquippedStack(slot);
            if (s==null||s.isEmpty()) continue;
            String id=InventoryUtil.id(s);
            if(id.contains("netherite")) return "NETHERITE";
            if(id.contains("diamond")) best="DIAMOND";
            else if(best.equals("UNARMORED")&&id.contains("iron")) best="IRON";
            else if(best.equals("UNARMORED")&&id.contains("gold")) best="GOLD";
            else if(best.equals("UNARMORED")&&id.contains("chainmail")) best="CHAIN";
            else if(best.equals("UNARMORED")&&id.contains("leather")) best="LEATHER";
        }
        return best;
    }

    public static PlayerEntity nearbyPlayer(MinecraftClient c,double range) {
        if(c.player==null||c.world==null)return null;
        return c.world.getPlayers().stream()
                .filter(p->p!=c.player&&p.isAlive()&&p.squaredDistanceTo(c.player)<=range*range)
                .min(Comparator.comparingDouble(c.player::squaredDistanceTo)).orElse(null);
    }

    public static HostileEntity nearbyHostile(MinecraftClient c,double range) {
        if(c.player==null||c.world==null)return null;
        return c.world.getEntitiesByClass(HostileEntity.class,c.player.getBoundingBox().expand(range),Entity::isAlive)
                .stream().min(Comparator.comparingDouble(c.player::squaredDistanceTo)).orElse(null);
    }

    public static void selectLookedAtMob(MinecraftClient c) {
        if (!(c.crosshairTarget instanceof EntityHitResult ehr) || !(ehr.getEntity() instanceof LivingEntity)) {
            log("MOB", "No living entity selected.");
            return;
        }
        UUID id=ehr.getEntity().getUuid();
        if(MOB_UNITS.contains(id)) {
            MOB_UNITS.remove(id);
            log("MOB","Released // "+ehr.getEntity().getName().getString());
        } else {
            MOB_UNITS.add(id);
            log("MOB","Selected // "+ehr.getEntity().getName().getString());
        }
    }

    public static int mobUnitCount(){ return MOB_UNITS.size(); }

    public static void startMacroRecording() {
        MACRO.clear();
        macroRecording=true;
        macroPlaying=false;
        macroIndex=0;
        log("MACRO","Recording started.");
    }
    public static void stopMacro(MinecraftClient c) {
        macroRecording=false;
        macroPlaying=false;
        macroIndex=0;
        stopOwnedKeys(c);
        log("MACRO","Stopped // "+MACRO.size()+" frames.");
    }
    public static void playMacro() {
        if(MACRO.isEmpty()) { log("MACRO","Nothing recorded."); return; }
        macroRecording=false;
        macroPlaying=true;
        macroIndex=0;
        log("MACRO","Playback started.");
    }
    public static void clearMacro(MinecraftClient c){ stopMacro(c); MACRO.clear(); log("MACRO","Recording deleted."); }
    public static boolean macroRecording(){return macroRecording;}
    public static boolean macroPlaying(){return macroPlaying;}
    public static int macroFrames(){return MACRO.size();}

    private static void tickMacro(MinecraftClient c) {
        if(c.options==null||c.player==null)return;
        if(macroRecording) {
            MACRO.add(new MacroFrame(
                    c.options.forwardKey.isPressed(),c.options.backKey.isPressed(),c.options.leftKey.isPressed(),c.options.rightKey.isPressed(),
                    c.options.jumpKey.isPressed(),c.options.sneakKey.isPressed(),c.options.useKey.isPressed(),c.options.attackKey.isPressed(),
                    c.player.getInventory().getSelectedSlot()));
            if(MACRO.size()>20*60*10) { macroRecording=false; log("MACRO","Recording capped at 10 minutes."); }
        } else if(macroPlaying) {
            if(macroIndex>=MACRO.size()) {
                stopMacro(c);
                log("MACRO","Playback complete.");
                return;
            }
            MacroFrame f=MACRO.get(macroIndex++);
            c.options.forwardKey.setPressed(f.f());
            c.options.backKey.setPressed(f.b());
            c.options.leftKey.setPressed(f.l());
            c.options.rightKey.setPressed(f.r());
            c.options.jumpKey.setPressed(f.j());
            c.options.sneakKey.setPressed(f.sneak());
            c.options.useKey.setPressed(f.use());
            c.options.attackKey.setPressed(f.attack());
            if(f.slot()>=0&&f.slot()<9)c.player.getInventory().setSelectedSlot(f.slot());
        }
    }

    private static void stopOwnedKeys(MinecraftClient c) {
        if(c==null||c.options==null)return;
        c.options.forwardKey.setPressed(false);c.options.backKey.setPressed(false);c.options.leftKey.setPressed(false);c.options.rightKey.setPressed(false);
        c.options.jumpKey.setPressed(false);c.options.sneakKey.setPressed(false);c.options.useKey.setPressed(false);c.options.attackKey.setPressed(false);
    }


    private static void tickTrap(MinecraftClient c) {
        if (c.player == null || c.world == null || c.interactionManager == null || c.currentScreen != null) return;
        PlayerEntity target = nearbyPlayer(c, SETTINGS.trapRange);
        if (target == null) {
            trapTicker = 0;
            trapPhase = 0;
            return;
        }

        trapTicker++;
        if (trapTicker < Math.max(2, SETTINGS.trapDelayTicks)) return;
        trapTicker = 0;

        BlockPos base = BlockPos.ofFloored(target.getX(), target.getY(), target.getZ());
        BlockPos desired;
        String item;

        if (SETTINGS.trapWeb && trapPhase == 0) {
            desired = base;
            item = "cobweb";
        } else {
            if (!SETTINGS.trapBox) {
                trapPhase = 0;
                return;
            }
            int phase = SETTINGS.trapWeb ? trapPhase - 1 : trapPhase;
            desired = switch (Math.floorMod(phase, 5)) {
                case 0 -> base.east();
                case 1 -> base.west();
                case 2 -> base.north();
                case 3 -> base.south();
                default -> base.up(2);
            };
            item = "obsidian";
        }

        trapPhase = (trapPhase + 1) % (SETTINGS.trapWeb && SETTINGS.trapBox ? 6 : (SETTINGS.trapWeb ? 1 : 5));
        if (!c.world.getBlockState(desired).isAir()) return;

        int slot = findHotbarSlot(c.player, item);
        if (slot < 0) return;

        Direction attach = null;
        BlockPos neighbor = null;
        for (Direction d : Direction.values()) {
            BlockPos n = desired.offset(d);
            var state = c.world.getBlockState(n);
            if (!state.isAir() && !state.getCollisionShape(c.world, n).isEmpty()) {
                neighbor = n;
                attach = d.getOpposite();
                break;
            }
        }
        if (neighbor == null || attach == null) return;

        int old = c.player.getInventory().getSelectedSlot();
        c.player.getInventory().setSelectedSlot(slot);

        Vec3d hitPos = Vec3d.ofCenter(neighbor).add(
                attach.getOffsetX() * 0.5,
                attach.getOffsetY() * 0.5,
                attach.getOffsetZ() * 0.5
        );
        BlockHitResult hit = new BlockHitResult(hitPos, attach, neighbor, false);
        c.interactionManager.interactBlock(c.player, Hand.MAIN_HAND, hit);
        c.player.swingHand(Hand.MAIN_HAND);
        c.player.getInventory().setSelectedSlot(old);
    }

    private static int findHotbarSlot(PlayerEntity player, String keyword) {
        String q = keyword.toLowerCase(Locale.ROOT);
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && InventoryUtil.id(stack).contains(q)) return i;
        }
        return -1;
    }

    public static void reloadScripts() {
        SCRIPT_TABS.clear();
        try {
            Files.createDirectories(SCRIPT_DIR);
            try(var stream=Files.list(SCRIPT_DIR)) {
                stream.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".surv")).sorted().forEach(SurvMegaState::parseScript);
            }
            log("SCRIPT","Loaded "+SCRIPT_TABS.size()+" custom tabs.");
        } catch(Exception e) {
            log("SCRIPT","Reload error // "+e.getClass().getSimpleName());
        }
    }

    private static void parseScript(Path p) {
        try {
            String name=p.getFileName().toString().replaceFirst("\\.surv$","");
            ArrayList<String> lines=new ArrayList<>();
            for(String raw:Files.readAllLines(p)) {
                String s=raw.trim();
                if(s.isBlank()||s.startsWith("#"))continue;
                if(s.toUpperCase(Locale.ROOT).startsWith("TAB ")) name=stripQuotes(s.substring(4).trim());
                else if(s.toUpperCase(Locale.ROOT).startsWith("TEXT ")) lines.add(stripQuotes(s.substring(5).trim()));
                else if(s.toUpperCase(Locale.ROOT).startsWith("APPEND_BOOK ")) {
                    // Exposed to scripts intentionally; format: APPEND_BOOK filename.txt | text
                    int bar=s.indexOf('|');
                    if(bar>12) appendBook(stripQuotes(s.substring(12,bar).trim()),stripQuotes(s.substring(bar+1).trim()));
                }
            }
            SCRIPT_TABS.add(new ScriptTab(p.getFileName().toString(),name,List.copyOf(lines)));
        } catch(Exception ignored){}
    }

    private static String stripQuotes(String s) {
        String x=s.trim();
        if(x.length()>=2&&((x.startsWith("\"")&&x.endsWith("\""))||(x.startsWith("'")&&x.endsWith("'")))) return x.substring(1,x.length()-1);
        return x;
    }

    public static List<ScriptTab> scriptTabs(){return List.copyOf(SCRIPT_TABS);}
    public static List<LogEntry> logs(){return List.copyOf(LOGS);}
    public static List<TrackedPlayer> tracked(){
        ArrayList<TrackedPlayer> l=new ArrayList<>(TRACKED.values());
        l.sort(Comparator.comparingLong(TrackedPlayer::seenAt).reversed());
        return l;
    }
    public static String recommendation(){return recommendation;}
    public static String recommendationWhy(){return recommendationWhy;}
    public static double visorDamage(){return visorDamage;}
    public static boolean auraPlaying(){return auraPlaying;}
    public static long combatRemainingMs(){return Math.max(0,combatUntil-System.currentTimeMillis());}

    public static void clearLogs(){LOGS.clear();save();}
    public static void clearTracker(){TRACKED.clear();log("TRACKER","History cleared.");save();}
    public static void clearVisor(){visorDamage=0;log("EXO","Visor damage cleared.");save();}

    public static void log(String category,String text) {
        ensureLoaded();
        if(!SETTINGS.logs && !"SCRIPT".equals(category))return;
        LOGS.add(new LogEntry(System.currentTimeMillis(),category,text));
        while(LOGS.size()>400)LOGS.remove(0);
    }

    public static String time(long t) {
        return LocalTime.ofNanoOfDay((t%(24L*60*60*1000))*1_000_000L).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    public static Path libraryDir(){ensureLoaded();return LIBRARY_DIR;}
    public static Path auraDir(){ensureLoaded();return AURA_DIR;}
    public static Path scriptDir(){ensureLoaded();return SCRIPT_DIR;}

    public static List<String> readBook(String file) {
        ensureLoaded();
        try {
            Path p=LIBRARY_DIR.resolve(file).normalize();
            if(!p.startsWith(LIBRARY_DIR)||!Files.exists(p))return List.of("Book not found.");
            return Files.readAllLines(p);
        }catch(Exception e){return List.of("Could not read book.");}
    }

    public static void appendBook(String file,String text) {
        try {
            Files.createDirectories(LIBRARY_DIR);
            Path p=LIBRARY_DIR.resolve(file).normalize();
            if(!p.startsWith(LIBRARY_DIR))return;
            Files.writeString(p,System.lineSeparator()+text,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            log("LIBRARY","Updated // "+file);
        }catch(Exception ignored){}
    }

    private static void ensureBook() throws Exception {
        Path p=LIBRARY_DIR.resolve("under_the_bridge.txt");
        if(Files.exists(p))return;
        String book = """
UNDER THE BRIDGE
An original wasteland story for SURV // LIBRARY

CHAPTER 1 — CONCRETE ROOF

Eli Mercer was fifteen and had learned to sleep through the groan of old highway steel.

The bridge above his shelter had not carried traffic in years. Wind moved through the cracked lanes and made loose signs knock against their posts. Underneath, hidden behind a wall of sheet metal and salvaged boards, Eli kept everything he owned: two blankets, three cans of food, a dented cooking pot, a hand-crank radio, a map, and a photograph of his parents.

They had been gone for almost two years.

The settlements east of the river called this stretch of ruins the Commonwealth. Eli mostly called it home.

Every morning began the same way. Check the water traps. Count food. Inspect the bridge supports. Listen before stepping into the open. Nothing in the wasteland cared that he was young, so he had stopped expecting it to.

That morning, the radio clicked on by itself.

Three bursts of static. A pause. Then a repeating signal from somewhere beyond the collapsed interchange.

Eli packed water, a pry bar, bandages, and the last good batteries he had. He told himself he was only going to look.

Two hours later, inside the service bay of an abandoned maintenance depot, he found the armor.

It stood against the far wall under a torn tarp: a heavy powered frame wrapped in scarred metal plates. Dust filled the joints. One shoulder was missing. The visor was cracked. A faded warning label near the chest read POWER CELL REQUIRED.

Eli stared at it for a long time.

Finding power armor was one thing.

Finding a fusion core was going to be the hard part.

CHAPTER 2 — THE LIST

Eli did not try to move the suit. He was realistic enough to know that dragging several hundred pounds of dead machinery across broken concrete was a good way to get hurt.

Instead he made a list.

Fusion core. Wiring. Hydraulic seal. Clean cloth. Food. Water. A safer route back to the bridge.

The suit could wait.

Survival could not.
""";
        Files.writeString(p,book,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }

    private static void ensureExampleScript() throws Exception {
        Path p=SCRIPT_DIR.resolve("example-tab.surv");
        if(Files.exists(p))return;
        Files.writeString(p,
                "# SURV Script example\nTAB \"My Module\"\nTEXT \"Custom tabs can be created by .surv files.\"\nTEXT \"Ask ChatGPT for another .surv module later.\"\n",
                StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
}
