package com.survos;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class ConversationMemory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Turn {
        public String role;
        public String text;
        public long time;
        public Turn() {}
        public Turn(String role, String text) {
            this.role = role;
            this.text = text;
            this.time = System.currentTimeMillis();
        }
    }

    private static final class Data {
        List<Turn> turns = new ArrayList<>();
        List<String> notes = new ArrayList<>();
    }

    private Data data = new Data();
    private String key = "";

    public void tick(MinecraftClient client) {
        if (client == null) return;
        String next = worldKey(client);
        if (!next.equals(key)) {
            key = next;
            load();
        }
    }

    public void addTurn(String role, String text) {
        if (text == null || text.isBlank()) return;
        data.turns.add(new Turn(role, compact(text, 240)));
        while (data.turns.size() > 30) data.turns.remove(0);
        save();
    }

    public void remember(String note) {
        if (note == null || note.isBlank()) return;
        String n = compact(note, 180);
        data.notes.removeIf(x -> x.equalsIgnoreCase(n));
        data.notes.add(n);
        while (data.notes.size() > 20) data.notes.remove(0);
        save();
    }

    public String recall(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<String> hits = new ArrayList<>();
        for (int i = data.notes.size() - 1; i >= 0; i--) {
            String n = data.notes.get(i);
            if (q.isBlank() || n.toLowerCase(Locale.ROOT).contains(q)) hits.add(n);
            if (hits.size() >= 5) break;
        }
        if (hits.isEmpty() && !data.notes.isEmpty()) {
            for (int i = data.notes.size() - 1; i >= 0 && hits.size() < 5; i--) hits.add(data.notes.get(i));
        }
        return hits.isEmpty() ? "nothing remembered" : String.join("; ", hits);
    }

    public String context() {
        StringBuilder out = new StringBuilder();
        if (!data.notes.isEmpty()) {
            out.append("MEMORY NOTES: ");
            int start = Math.max(0, data.notes.size() - 8);
            for (int i = start; i < data.notes.size(); i++) {
                if (i > start) out.append(" | ");
                out.append(data.notes.get(i));
            }
            out.append("\n");
        }

        if (!data.turns.isEmpty()) {
            out.append("RECENT CHAT: ");
            int start = Math.max(0, data.turns.size() - 8);
            for (int i = start; i < data.turns.size(); i++) {
                Turn t = data.turns.get(i);
                if (i > start) out.append(" | ");
                out.append(t.role).append(": ").append(t.text);
            }
        }
        return out.toString();
    }

    public int noteCount() { return data.notes.size(); }
    public int turnCount() { return data.turns.size(); }

    private void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Data loaded = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class);
                data = loaded == null ? new Data() : loaded;
                if (data.turns == null) data.turns = new ArrayList<>();
                if (data.notes == null) data.notes = new ArrayList<>();
            } else {
                data = new Data();
            }
        } catch (Exception e) {
            data = new Data();
        }
    }

    private void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    private Path file() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("surv-os")
                .resolve("ai-memory-" + (key.isBlank() ? "default" : key) + ".json");
    }

    private static String worldKey(MinecraftClient c) {
        String server = c.getCurrentServerEntry() != null ? c.getCurrentServerEntry().address : "singleplayer";
        return clean(server);
    }

    private static String clean(String s) {
        return (s == null ? "default" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_"));
    }

    private static String compact(String s, int max) {
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() <= max ? one : one.substring(0, max);
    }
}
