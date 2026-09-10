package com.secret.abilities;

public final class ClientModule {
    public final String id;
    public final String name;
    public final ModuleCategory category;
    public final String description;
    public final boolean defaultEnabled;
    private boolean enabled;

    public ClientModule(String id, String name, ModuleCategory category, String description, boolean defaultEnabled) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.defaultEnabled = defaultEnabled;
        this.enabled = defaultEnabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    void setEnabledRaw(boolean enabled) {
        this.enabled = enabled;
    }
}
