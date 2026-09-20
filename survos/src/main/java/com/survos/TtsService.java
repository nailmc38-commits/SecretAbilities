package com.survos;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class TtsService {
    private record Speech(String text, String style) {}

    private final BlockingQueue<Speech> queue = new LinkedBlockingQueue<>();
    private volatile boolean enabled = true;
    private volatile String status = "READY";
    private volatile String style = "CINEMATIC";

    public TtsService() {
        Thread.ofVirtual().name("SURV-TTS").start(this::loop);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) stopAll();
    }

    public boolean enabled() { return enabled; }
    public String status() { return status; }
    public String style() { return style; }

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

    public void speak(String text) {
        speak(text, style);
    }

    public void speak(String text, String speechStyle) {
        if (!enabled || text == null || text.isBlank()) return;
        String clean = text.replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (clean.length() > 520) clean = clean.substring(0, 520);
        queue.offer(new Speech(clean, speechStyle == null ? style : speechStyle));
    }

    public void stopAll() {
        queue.clear();
    }

    private void loop() {
        while (true) {
            try {
                Speech s = queue.take();
                if (!enabled) continue;
                speakWindows(s);
            } catch (InterruptedException ignored) {
            } catch (Throwable t) {
                status = "ERROR";
            }
        }
    }

    private void speakWindows(Speech speech) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            status = "WINDOWS ONLY";
            return;
        }

        int rate = switch (speech.style().toUpperCase(Locale.ROOT)) {
            case "TACTICAL" -> 1;
            case "MINIMAL" -> 2;
            case "NORMAL" -> 0;
            default -> -1;
        };
        int volume = "MINIMAL".equalsIgnoreCase(speech.style()) ? 82 : 94;

        status = "SPEAKING";
        String script =
                "Add-Type -AssemblyName System.Speech; " +
                "$t=[Console]::In.ReadToEnd(); " +
                "$s=New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                "try {$s.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::Male,[System.Speech.Synthesis.VoiceAge]::Adult)} catch {}; " +
                "$s.Rate=" + rate + "; $s.Volume=" + volume + "; " +
                "$s.Speak($t)";

        Process p = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script)
                .redirectErrorStream(true)
                .start();

        try (OutputStream out = p.getOutputStream()) {
            out.write(speech.text().getBytes(StandardCharsets.UTF_8));
        }
        p.waitFor();
        status = "READY";
    }
}
