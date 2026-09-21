package com.survos;

import com.k2fsa.sherpa.onnx.*;
import net.fabricmc.loader.api.FabricLoader;

import javax.sound.sampled.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class VoiceService {
    private static final int SAMPLE_RATE = 16000;
    private static final int WINDOW_SIZE = 512;
    private static final String ASR_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-tiny-en-int8.tar.bz2";
    private static final String VAD_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile TargetDataLine micLine;
    private volatile String status = "OFFLINE";
    private volatile String lastHeard = "";
    private volatile String lastError = "";
    private volatile float micLevel;
    private Consumer<String> listener = s -> {};

    public void setListener(Consumer<String> listener) {
        this.listener = listener == null ? s -> {} : listener;
    }

    public boolean isRunning() { return running.get(); }
    public String status() { return status; }
    public String lastHeard() { return lastHeard; }
    public String lastError() { return lastError; }
    public float micLevel() { return micLevel; }

    public List<String> microphones() {
        ArrayList<String> result = new ArrayList<>();
        result.add("System Default");
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(mi);
                if (mixer.isLineSupported(info)) result.add(mi.getName());
            } catch (Throwable ignored) {}
        }
        return result;
    }

    public synchronized void start(SurvConfig cfg) {
        if (running.get()) return;
        running.set(true);
        lastError = "";
        status = "STARTING MIC";
        Thread.ofVirtual().name("SURV-Voice").start(() -> runVoice(cfg));
    }

    public synchronized void stop() {
        running.set(false);
        status = "OFFLINE";
        micLevel = 0f;
        try {
            if (micLine != null) {
                micLine.stop();
                micLine.flush();
                micLine.close();
            }
        } catch (Throwable ignored) {}
        micLine = null;
    }

    public void toggle(SurvConfig cfg) {
        if (isRunning()) stop();
        else start(cfg);
    }

    private void runVoice(SurvConfig cfg) {
        Vad vad = null;
        OfflineRecognizer recognizer = null;

        try {
            Path root = FabricLoader.getInstance().getGameDir()
                    .resolve("surv-ai")
                    .resolve("speech");
            Path modelDir = root.resolve("sherpa-onnx-moonshine-tiny-en-int8");
            Path vadFile = root.resolve("silero_vad.onnx");

            ensureModels(root, modelDir, vadFile);

            status = "LOADING MIC AI";
            vad = createVad(vadFile);
            recognizer = createRecognizer(modelDir);

            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            TargetDataLine line = openMic(cfg.microphone, info, format);
            line.open(format);
            line.start();
            micLine = line;

            status = "LISTENING";
            byte[] buffer = new byte[WINDOW_SIZE * 2];
            float[] samples = new float[WINDOW_SIZE];

            while (running.get() && line.isOpen()) {
                int n = line.read(buffer, 0, buffer.length);
                if (n < buffer.length) continue;

                double levelSum = 0.0;
                for (int i = 0; i < WINDOW_SIZE; i++) {
                    int lo = buffer[i * 2] & 0xff;
                    int hi = buffer[i * 2 + 1];
                    short sample = (short)((hi << 8) | lo);
                    float value = sample / 32768f;
                    samples[i] = value;
                    levelSum += Math.abs(value);
                }
                micLevel = (float)Math.min(1.0, levelSum / WINDOW_SIZE * 8.0);

                vad.acceptWaveform(samples);

                while (!vad.empty()) {
                    SpeechSegment segment = vad.front();
                    vad.pop();

                    float[] speech = segment.getSamples();
                    if (speech == null || speech.length < SAMPLE_RATE / 4) continue;

                    status = "TRANSCRIBING";
                    OfflineStream stream = recognizer.createStream();
                    try {
                        stream.acceptWaveform(speech, SAMPLE_RATE);
                        recognizer.decode(stream);
                        String text = recognizer.getResult(stream).getText();
                        if (text != null) text = text.trim();

                        if (text != null && !text.isBlank()) {
                            lastHeard = text;
                            listener.accept(text);
                        }
                    } finally {
                        stream.release();
                    }
                    status = "LISTENING";
                }
            }
        } catch (Throwable t) {
            lastError = t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : ": " + t.getMessage());
            status = "MIC ERROR";
        } finally {
            running.set(false);
            micLevel = 0f;

            try {
                if (micLine != null) {
                    micLine.stop();
                    micLine.close();
                }
            } catch (Throwable ignored) {}
            micLine = null;

            try { if (vad != null) vad.release(); } catch (Throwable ignored) {}
            try { if (recognizer != null) recognizer.release(); } catch (Throwable ignored) {}
        }
    }

    private static Vad createVad(Path model) {
        SileroVadModelConfig silero = SileroVadModelConfig.builder()
                .setModel(model.toAbsolutePath().toString())
                .setThreshold(0.45f)
                .setMinSilenceDuration(0.35f)
                .setMinSpeechDuration(0.20f)
                .setWindowSize(WINDOW_SIZE)
                .build();

        VadModelConfig config = VadModelConfig.builder()
                .setSileroVadModelConfig(silero)
                .setSampleRate(SAMPLE_RATE)
                .setNumThreads(1)
                .setDebug(false)
                .setProvider("cpu")
                .build();

        return new Vad(config);
    }

    private static OfflineRecognizer createRecognizer(Path dir) {
        OfflineMoonshineModelConfig moonshine = OfflineMoonshineModelConfig.builder()
                .setPreprocessor(dir.resolve("preprocess.onnx").toAbsolutePath().toString())
                .setEncoder(dir.resolve("encode.int8.onnx").toAbsolutePath().toString())
                .setUncachedDecoder(dir.resolve("uncached_decode.int8.onnx").toAbsolutePath().toString())
                .setCachedDecoder(dir.resolve("cached_decode.int8.onnx").toAbsolutePath().toString())
                .build();

        OfflineModelConfig modelConfig = OfflineModelConfig.builder()
                .setMoonshine(moonshine)
                .setTokens(dir.resolve("tokens.txt").toAbsolutePath().toString())
                .setNumThreads(Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 3)))
                .setDebug(false)
                .build();

        OfflineRecognizerConfig config = OfflineRecognizerConfig.builder()
                .setOfflineModelConfig(modelConfig)
                .setDecodingMethod("greedy_search")
                .build();

        return new OfflineRecognizer(config);
    }

    private TargetDataLine openMic(
            String wanted,
            DataLine.Info info,
            AudioFormat format
    ) throws LineUnavailableException {
        if (wanted != null
                && !wanted.isBlank()
                && !"System Default".equalsIgnoreCase(wanted)) {
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                if (!mi.getName().equalsIgnoreCase(wanted)) continue;
                Mixer mixer = AudioSystem.getMixer(mi);
                if (mixer.isLineSupported(info)) {
                    return (TargetDataLine)mixer.getLine(info);
                }
            }
        }
        return AudioSystem.getTargetDataLine(format);
    }

    private void ensureModels(Path root, Path modelDir, Path vadFile) throws Exception {
        Files.createDirectories(root);

        if (!Files.exists(vadFile)) {
            status = "DOWNLOADING MIC VAD";
            download(VAD_URL, vadFile);
        }

        if (!Files.exists(modelDir.resolve("tokens.txt"))
                || !Files.exists(modelDir.resolve("encode.int8.onnx"))) {
            status = "DOWNLOADING MIC MODEL";
            Path archive = root.resolve("moonshine-asr.tar.bz2");
            download(ASR_URL, archive);
            extractTarBz2(archive, root);
            Files.deleteIfExists(archive);
        }

        if (!Files.exists(modelDir.resolve("tokens.txt"))) {
            throw new FileNotFoundException("Speech model did not extract correctly");
        }
    }

    private static void download(String url, Path out) throws Exception {
        Path tmp = out.resolveSibling(out.getFileName() + ".part");

        HttpURLConnection con = (HttpURLConnection)URI.create(url).toURL().openConnection();
        con.setInstanceFollowRedirects(true);
        con.setConnectTimeout(15000);
        con.setReadTimeout(60000);
        con.setRequestProperty("User-Agent", "SURV-OS/4.3");

        int code = con.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code);
        }

        try (InputStream in = con.getInputStream();
             OutputStream outStream = Files.newOutputStream(
                     tmp,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buf = new byte[262144];
            int n;
            while ((n = in.read(buf)) > 0) outStream.write(buf, 0, n);
        } finally {
            con.disconnect();
        }

        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void extractTarBz2(Path archive, Path destination) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            throw new IOException("Automatic speech model extraction currently requires Windows tar.exe");
        }

        Process p = new ProcessBuilder(
                "tar.exe",
                "-xf",
                archive.toAbsolutePath().toString(),
                "-C",
                destination.toAbsolutePath().toString()
        ).redirectErrorStream(true).start();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream()))) {
            while (br.readLine() != null) {}
        }

        if (p.waitFor() != 0) {
            throw new IOException("tar.exe could not extract speech model");
        }
    }
}
