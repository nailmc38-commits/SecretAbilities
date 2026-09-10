package com.secret.abilities;

import java.util.UUID;

public final class ModState {
    private ModState() {}

    public static volatile boolean walkOnWater = false;
    public static volatile boolean xray = false;
    public static volatile boolean playerEsp = false;
    public static volatile boolean statsHud = true;
    public static volatile boolean waypointHud = true;
    public static volatile UUID localPlayerUuid = null;
}
