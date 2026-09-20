package com.survos;

import com.k2fsa.sherpa.onnx.*;
import net.fabricmc.loader.api.FabricLoader;

import javax.sound.sampled.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.*;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TtsService {
    private record Speech(String text, String style) {}

    private static final String MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2";

    private final BlockingQueue<Speech> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean booting = new AtomicBoolean(false);

    private volatile boolean enabled = true;
    private volatile String status = "PREPARING";
    private volatile String style = "CINEMATIC";
    private volatile boolean ready;
    private volatile OfflineTts tts;
    private volatile SourceDataLine activeLine;

    public TtsService() {
        Thread.ofVirtual().name("SURV-TTS-Boot").start(this::ensureReady);
        Thread.ofVirtual().name("SURV-TTS").start(this::loop);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) stopAll();
        else ensureReady();
    }

    public boolean enabled() { return enabled; }
    public boolean ready() { return ready; }
    public String status() { return enabled ? status : "OFF"; }
    public String style() { return style; }
    public boolean isSpeaking() { return "SPEAKING".equals(status); }

    public void setStyle(String newStyle) {
        if (newStyle == null) return;
        style = switch (newStyle.toUpperCase(Locale.ROOT)) {
            case "TACTICAL" -> "TACTICAL";
            case "MINIMAL" -> "MINIMAL";
            case "NORMAL" -> "NORMAL";
            default -> "CINEMATIC";
        };
    }

    public String cycleStyle() {
        style = switch (style) {
            case "CINEMATIC" -> "TACTICAL";
            case "TACTICAL" -> "NORMAL";
            case "NORMAL" -> "MINIMAL";
            default -> "CINEMATIC";
        };
        return style;
    }

    public void speak(String text) { speak(text, style); }

    public void speak(String text, String speechStyle) {
        if (!enabled || text == null || text.isBlank()) return;
        String clean = sanitize(text);
        if (clean.isBlank()) return;
        if (clean.length() > 260) clean = clean.substring(0, 260);
        ensureReady();
        queue.offer(new Speech(clean, speechStyle == null ? style : speechStyle));
    }

    public void stopAll() {
        queue.clear();
        try {
            if (activeLine != null) {
                activeLine.stop();
                activeLine.flush();
                activeLine.close();
            }
        } catch (Throwable ignored) {}
        activeLine = null;
    }

    private void ensureReady() {
        if (ready || !enabled || booting.getAndSet(true)) return;
        try {
            status = "PREPARING VOICE";
            Path root = FabricLoader.getInstance().getGameDir().resolve("surv-ai").resolve("tts");
            Path modelDir = root.resolve("kokoro-en-v0_19");
            Path model = modelDir.resolve("model.onnx");
            Path voices = modelDir.resolve("voices.bin");
            Path tokens = modelDir.resolve("tokens.txt");
            Path dataDir = modelDir.resolve("espeak-ng-data");

            Files.createDirectories(root);

            if (!Files.exists(model) || !Files.exists(voices) || !Files.exists(tokens) || !Files.isDirectory(dataDir)) {
                status = "DOWNLOADING VOICE";
                Path archive = root.resolve("kokoro-en-v0_19.tar.bz2");
                download(MODEL_URL, archive);
                extractWithWindowsTar(archive, root);
                Files.deleteIfExists(archive);
            }

            if (!Files.exists(model)) throw new FileNotFoundException("Kokoro model missing after extraction");

            OfflineTtsKokoroModelConfig kokoro = OfflineTtsKokoroModelConfig.builder()
                    .setModel(model.toAbsolutePath().toString())
                    .setVoices(voices.toAbsolutePath().toString())
                    .setTokens(tokens.toAbsolutePath().toString())
                    .setDataDir(dataDir.toAbsolutePath().toString())
                    .build();

            OfflineTtsModelConfig modelConfig = OfflineTtsModelConfig.builder()
                    .setKokoro(kokoro)
                    .setNumThreads(Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)))
                    .setDebug(false)
                    .build();

            OfflineTtsConfig config = OfflineTtsConfig.builder()
                    .setModel(modelConfig)
                    .build();

            OfflineTts old = tts;
            tts = new OfflineTts(config);
            if (old != null) try { old.release(); } catch (Throwable ignored) {}

            ready = true;
            status = "READY";
        } catch (Throwable t) {
            ready = false;
            status = "VOICE ERROR";
        } finally {
            booting.set(false);
        }
    }

    private void loop() {
        while (true) {
            try {
                Speech speech = queue.take();
                if (!enabled) continue;

                while (enabled && !ready) {
                    ensureReady();
                    Thread.sleep(150);
                    if ("VOICE ERROR".equals(status)) break;
                }

                if (!enabled || !ready || tts == null) continue;
                generateAndPlay(speech);
            } catch (InterruptedException ignored) {
            } catch (Throwable t) {
                status = ready ? "READY" : "VOICE ERROR";
            }
        }
    }

    private void generateAndPlay(Speech speech) throws Exception {
        status = "SPEAKING";

        GenerationConfig cfg = new GenerationConfig();
        cfg.setSid(voiceIdFor(speech.style()));
        cfg.setSpeed(speedFor(speech.style()));
        cfg.setSilenceScale(0.12f);

        GeneratedAudio audio = tts.generateWithConfigAndCallback(
                speech.text(),
                cfg,
                samples -> {}
        );

        if (audio == null || audio.getSamples() == null || audio.getSamples().length == 0) {
            status = "READY";
            return;
        }

        play(audio.getSamples(), audio.getSampleRate());
        status = "READY";
    }

    private static int voiceIdFor(String style) {
        return switch (style == null ? "CINEMATIC" : style.toUpperCase(Locale.ROOT)) {
            case "TACTICAL" -> 5;   // am_adam
            case "NORMAL" -> 6;     // am_michael
            case "MINIMAL" -> 10;   // bm_lewis
            default -> 9;           // bm_george
        };
    }

    private static float speedFor(String style) {
        return switch (style == null ? "CINEMATIC" : style.toUpperCase(Locale.ROOT)) {
            case "TACTICAL" -> 1.08f;
            case "MINIMAL" -> 1.12f;
            case "NORMAL" -> 1.03f;
            default -> 1.00f;
        };
    }

    private void play(float[] samples, int sampleRate) throws Exception {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        SourceDataLine line = AudioSystem.getSourceDataLine(format);
        activeLine = line;
        line.open(format, Math.max(4096, sampleRate / 2));
        line.start();

        byte[] pcm = new byte[Math.min(samples.length, 4096) * 2];
        int index = 0;
        while (index < samples.length && enabled) {
            int n = Math.min(4096, samples.length - index);
            if (pcm.length < n * 2) pcm = new byte[n * 2];

            for (int i = 0; i < n; i++) {
                float f = Math.max(-1f, Math.min(1f, samples[index + i]));
                short s = (short)Math.round(f * 32767f);
                pcm[i * 2] = (byte)(s & 0xff);
                pcm[i * 2 + 1] = (byte)((s >>> 8) & 0xff);
            }

            line.write(pcm, 0, n * 2);
            index += n;
        }

        if (enabled) line.drain();
        line.stop();
        line.close();
        activeLine = null;
    }

    private static String sanitize(String text) {
        String out = text
                .replaceAll("(?is)<think>.*?</think>", "")
                .replaceAll("(?is)<analysis>.*?</analysis>", "")
                .replaceAll("(?i)^(analysis|reasoning|thought process)\\s*:\\s*", "")
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();

        if (out.startsWith("{") && out.endsWith("}")) return "";
        return out;
    }

    private static void download(String url, Path out) throws Exception {
        Path tmp = out.resolveSibling(out.getFileName() + ".part");
        HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
        con.setInstanceFollowRedirects(true);
        con.setConnectTimeout(15000);
        con.setReadTimeout(60000);
        con.setRequestProperty("User-Agent", "SURV-OS/4.1");

        int code = con.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("Voice download HTTP " + code);

        try (InputStream in = con.getInputStream();
             OutputStream os = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[262144];
            int n;
            while ((n = in.read(buffer)) > 0) os.write(buffer, 0, n);
        } finally {
            con.disconnect();
        }

        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void extractWithWindowsTar(Path archive, Path destination) throws Exception {
        Process p = new ProcessBuilder(
                "tar.exe",
                "-xf",
                archive.toAbsolutePath().toString(),
                "-C",
                destination.toAbsolutePath().toString()
        ).redirectErrorStream(true).start();

        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            while (r.readLine() != null) {}
        }

        if (p.waitFor() != 0) throw new IOException("Could not extract Kokoro voice model");
    }
}
