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

        String rate = switch (speech.style().toUpperCase(Locale.ROOT)) {
            case "TACTICAL" -> "+6%";
            case "MINIMAL" -> "+10%";
            case "NORMAL" -> "0%";
            default -> "-5%";
        };
        String pitch = switch (speech.style().toUpperCase(Locale.ROOT)) {
            case "TACTICAL" -> "-5%";
            case "MINIMAL" -> "-3%";
            case "NORMAL" -> "-2%";
            default -> "-8%";
        };
        int volume = "MINIMAL".equalsIgnoreCase(speech.style()) ? 84 : 95;

        status = "SPEAKING";
        String script =
                "Add-Type -AssemblyName System.Speech; " +
                "$t=[Console]::In.ReadToEnd(); " +
                "$s=New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                "try {$s.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::Male,[System.Speech.Synthesis.VoiceAge]::Adult)} catch {}; " +
                "$s.Volume=" + volume + "; " +
                "$safe=[System.Security.SecurityElement]::Escape($t); " +
                "$ssml=\"<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'><prosody rate='" + rate + "' pitch='" + pitch + "'>\"+$safe+\"</prosody></speak>\"; " +
                "try {$s.SpeakSsml($ssml)} catch {$s.Rate=0; $s.Speak($t)}";

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
