package com.survos;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class TtsService {
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private volatile boolean enabled = true;
    private volatile String status = "READY";

    public TtsService() {
        Thread.ofVirtual().name("SURV-TTS").start(this::loop);
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean enabled() { return enabled; }
    public String status() { return status; }

    public void speak(String text) {
        if (!enabled || text == null || text.isBlank()) return;
        String clean = text.replaceAll("[\\r\\n]+", " ").trim();
        if (clean.length() > 450) clean = clean.substring(0, 450);
        queue.offer(clean);
    }

    public void stopAll() {
        queue.clear();
    }

    private void loop() {
        while (true) {
            try {
                String text = queue.take();
                if (!enabled) continue;
                speakWindows(text);
            } catch (InterruptedException ignored) {
            } catch (Throwable t) {
                status = "ERROR";
            }
        }
    }

    private void speakWindows(String text) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            status = "WINDOWS ONLY";
            return;
        }
        status = "SPEAKING";
        String script = "Add-Type -AssemblyName System.Speech; " +
                "$t=[Console]::In.ReadToEnd(); " +
                "$s=New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                "$s.Rate=1; $s.Volume=90; $s.Speak($t)";
        Process p = new ProcessBuilder("powershell.exe","-NoProfile","-ExecutionPolicy","Bypass","-Command",script)
                .redirectErrorStream(true).start();
        try (OutputStream out = p.getOutputStream()) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        p.waitFor();
        status = "READY";
    }
}
