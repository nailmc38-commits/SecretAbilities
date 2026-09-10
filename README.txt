Void Client 2.0 - Fabric 1.21.11

Open GUI:
- Default: F9
- Rebind it in Minecraft Options > Controls > Key Binds.

Tabs:
Bot | PvP | Movement | Render | World | Macros | Player | HUD | Client

Void Client includes 50+ toggleable module entries, persistent module states, rebindable module shortcuts,
a dark/cyan GUI, configurable movement values, custom macros, waypoints, HUD widgets and existing X-Ray/ESP tools.

Macro Editor:
Create your own macros in the Macros tab. Each macro has a name, ON/OFF state, keybind and action sequence.
Separate actions with semicolons.

Supported macro actions:
cmd <command>
chat <message>
wait <milliseconds>
mouse <dx> <dy>
slot <1-9>
forward <ticks>
back <ticks>
left <ticks>
right <ticks>
sneak <ticks>
attack <ticks>
use <ticks>
click left
click right
jump
toggle <module name>
repeat <count>
stop

Example:
cmd home ; wait 250 ; slot 2 ; mouse 30 -10 ; click right

Bot:
Void Client detects a compatible Baritone Fabric 1.21.11 install at runtime.
Auto Mine, Auto Farm and Pathfinder can call Baritone through its API without making the user type commands.
Beat Game has the selectable module, live status and survival-planner framework, but full autonomous crafting,
portal construction and Ender Dragon completion are still experimental/not claimed as complete.

Anti-cheat:
There is no anti-cheat bypass, spoofing or concealment logic.
Movement modules start OFF. Servers can correct or reject movement that their rules do not allow.

Requirements:
- Minecraft Java 1.21.11
- Fabric Loader 0.18.2+
- Fabric API 0.139.4+1.21.11
- Java 21
