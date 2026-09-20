package com.survos;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class VoiceService {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Process process;
    private volatile TargetDataLine micLine;
    private volatile String status = "OFFLINE";
    private volatile String lastHeard = "";
    private Consumer<String> listener = s -> {};

    public void setListener(Consumer<String> listener) { this.listener = listener == null ? s -> {} : listener; }
    public boolean isRunning() { return running.get(); }
    public String status() { return status; }
    public String lastHeard() { return lastHeard; }

    public List<String> microphones() {
        ArrayList<String> result = new ArrayList<>();
        result.add("System Default");
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, new AudioFormat(16000f, 16, 1, true, false));
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(mi);
                if (mixer.isLineSupported(info)) result.add(mi.getName());
            } catch (Exception ignored) {}
        }
        return result;
    }

    public synchronized void start(SurvConfig cfg) {
        if (running.get()) return;
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            status = "WINDOWS ONLY";
            return;
        }
        running.set(true);
        status = "STARTING";
        Thread.ofVirtual().name("SURV-Voice-Startup").start(() -> runVoice(cfg));
    }

    public synchronized void stop() {
        running.set(false);
        status = "OFFLINE";
        try { if (micLine != null) { micLine.stop(); micLine.close(); } } catch (Exception ignored) {}
        try { if (process != null) process.destroyForcibly(); } catch (Exception ignored) {}
        micLine = null; process = null;
    }

    public void toggle(SurvConfig cfg) { if (isRunning()) stop(); else start(cfg); }

    private void runVoice(SurvConfig cfg) {
        Path script = null;
        try {
            int sampleRate = 16000;
            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
            DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);
            TargetDataLine line = openMic(cfg.microphone, lineInfo, format);
            line.open(format); line.start(); micLine = line;
            script = Files.createTempFile("surv-voice-", ".ps1");
            Files.writeString(script, powershellScript(), StandardCharsets.UTF_8);
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                    script.toAbsolutePath().toString(), Integer.toString(sampleRate), Double.toString(cfg.voiceConfidence));
            pb.redirectErrorStream(true);
            Process p = pb.start(); process = p;
            Thread.ofVirtual().name("SURV-Voice-Reader").start(() -> readOutput(p));
            status = "LISTENING";
            byte[] buffer = new byte[4096];
            OutputStream out = p.getOutputStream();
            while (running.get() && p.isAlive()) {
                int n = line.read(buffer, 0, buffer.length);
                if (n > 0) { out.write(buffer, 0, n); out.flush(); }
            }
        } catch (Throwable t) {
            status = "VOICE ERROR";
        } finally {
            stop();
            if (script != null) try { Files.deleteIfExists(script); } catch (Exception ignored) {}
        }
    }

    private TargetDataLine openMic(String wanted, DataLine.Info info, AudioFormat format) throws LineUnavailableException {
        if (wanted != null && !wanted.isBlank() && !"System Default".equalsIgnoreCase(wanted)) {
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                if (mi.getName().equalsIgnoreCase(wanted)) {
                    Mixer m = AudioSystem.getMixer(mi);
                    if (m.isLineSupported(info)) return (TargetDataLine)m.getLine(info);
                }
            }
        }
        return AudioSystem.getTargetDataLine(format);
    }

    private void readOutput(Process p) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (running.get() && (line = br.readLine()) != null) {
                if (line.equals("__READY__")) status = "LISTENING";
                else if (line.startsWith("HEARD:")) {
                    String heard = line.substring(6).trim();
                    if (!heard.isBlank()) { lastHeard = heard; listener.accept(heard); }
                }
            }
        } catch (Exception ignored) {}
    }

    private String powershellScript() {
        return String.join("\n",
                "Add-Type -AssemblyName System.Speech",
                "$sampleRate = [int]$args[0]",
                "$confidence = [double]::Parse($args[1], [Globalization.CultureInfo]::InvariantCulture)",
                "$recognizer = New-Object System.Speech.Recognition.SpeechRecognitionEngine",
                "$bits = [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen",
                "$channels = [System.Speech.AudioFormat.AudioChannel]::Mono",
                "$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo($sampleRate, $bits, $channels)",
                "$recognizer.SetInputToAudioStream([Console]::OpenStandardInput(), $format)",
                "$grammar = New-Object System.Speech.Recognition.DictationGrammar",
                "$recognizer.LoadGrammar($grammar)",
                "Write-Output '__READY__'",
                "[Console]::Out.Flush()",
                "while ($true) {",
                "  try {",
                "    $result = $recognizer.Recognize([TimeSpan]::FromMilliseconds(1100))",
                "    if ($null -ne $result -and $result.Confidence -ge $confidence) {",
                "      Write-Output ('HEARD:' + $result.Text)",
                "      [Console]::Out.Flush()",
                "    }",
                "  } catch { Start-Sleep -Milliseconds 80 }",
                "}"
        );
    }
}
