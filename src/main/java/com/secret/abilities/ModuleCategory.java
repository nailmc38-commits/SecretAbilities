package com.secret.abilities;

public enum ModuleCategory {
    BOT("Bot"),
    PVP("PvP"),
    MOVEMENT("Movement"),
    RENDER("Render"),
    WORLD("World"),
    MACROS("Macros"),
    PLAYER("Player"),
    HUD("HUD"),
    CLIENT("Client");

    public final String label;

    ModuleCategory(String label) {
        this.label = label;
    }
}
