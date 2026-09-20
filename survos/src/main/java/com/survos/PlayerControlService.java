package com.survos;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import java.util.Comparator;
import java.util.Locale;

public final class PlayerControlService {
    private enum Motion { NONE, FORWARD, BACK, LEFT, RIGHT }

    private Motion motion = Motion.NONE;
    private int motionTicks;
    private int useTicks;
    private boolean jumpOnce;
    private boolean ownsMovement;
    private boolean ownsJump;
    private boolean ownsUse;

    public String status() {
        if (motion != Motion.NONE) return "MOVING " + motion;
        if (useTicks > 0) return "USING ITEM";
        return "IDLE";
    }

    public void move(String direction, double seconds) {
        motion = switch (direction == null ? "" : direction.toLowerCase(Locale.ROOT)) {
            case "back", "backward", "backwards" -> Motion.BACK;
            case "left" -> Motion.LEFT;
            case "right" -> Motion.RIGHT;
            default -> Motion.FORWARD;
        };
        motionTicks = Math.max(1, Math.min(200, (int)Math.round(seconds * 20.0)));
        ownsMovement = true;
    }

    public void stop(MinecraftClient c) {
        motion = Motion.NONE;
        motionTicks = 0;
        useTicks = 0;
        jumpOnce = false;

        if (c != null && c.options != null) {
            if (ownsMovement) {
                c.options.forwardKey.setPressed(false);
                c.options.backKey.setPressed(false);
                c.options.leftKey.setPressed(false);
                c.options.rightKey.setPressed(false);
            }
            if (ownsJump) c.options.jumpKey.setPressed(false);
            if (ownsUse) c.options.useKey.setPressed(false);
        }

        ownsMovement = false;
        ownsJump = false;
        ownsUse = false;
    }

    public void jump() {
        jumpOnce = true;
        ownsJump = true;
    }

    public void useItem(int ticks) {
        useTicks = Math.max(1, Math.min(80, ticks));
        ownsUse = true;
    }

    public void turn(MinecraftClient c, float degrees) {
        if (c.player == null) return;
        c.player.setYaw(MathHelper.wrapDegrees(c.player.getYaw() + degrees));
    }

    public boolean lookAtNearestHostile(MinecraftClient c, double range) {
        if (c.player == null || c.world == null) return false;
        HostileEntity mob = c.world.getEntitiesByClass(
                        HostileEntity.class,
                        c.player.getBoundingBox().expand(Math.max(2, Math.min(24, range))),
                        e -> e.isAlive())
                .stream()
                .min(Comparator.comparingDouble(c.player::squaredDistanceTo))
                .orElse(null);
        if (mob == null) return false;
        LocalNavigator.lookAt(c.player, mob.getBoundingBox().getCenter());
        return true;
    }

    public boolean attackNearestHostile(MinecraftClient c, double range) {
        if (c.player == null || c.world == null || c.interactionManager == null) return false;
        HostileEntity mob = c.world.getEntitiesByClass(
                        HostileEntity.class,
                        c.player.getBoundingBox().expand(Math.max(2, Math.min(8, range))),
                        e -> e.isAlive())
                .stream()
                .min(Comparator.comparingDouble(c.player::squaredDistanceTo))
                .orElse(null);
        if (mob == null) return false;
        LocalNavigator.lookAt(c.player, mob.getBoundingBox().getCenter());
        if (c.player.getAttackCooldownProgress(0f) >= 0.85f) {
            c.interactionManager.attackEntity(c.player, mob);
            c.player.swingHand(Hand.MAIN_HAND);
        }
        return true;
    }

    public boolean selectItem(MinecraftClient c, String item) {
        return InventoryManager.selectItemByKeyword(
                c,
                item,
                c.player == null ? 0 : c.player.getInventory().getSelectedSlot()
        );
    }

    public boolean eat(MinecraftClient c) {
        if (c.player == null) return false;
        if (!InventoryManager.selectFood(c, true)) return false;
        useItem(45);
        return true;
    }

    public void tick(MinecraftClient c) {
        if (c == null || c.player == null || c.options == null) return;

        // Do not touch the player's normal keys unless SURV currently owns that input.
        if (ownsMovement) {
            c.options.forwardKey.setPressed(false);
            c.options.backKey.setPressed(false);
            c.options.leftKey.setPressed(false);
            c.options.rightKey.setPressed(false);

            if (motionTicks > 0) {
                switch (motion) {
                    case FORWARD -> c.options.forwardKey.setPressed(true);
                    case BACK -> c.options.backKey.setPressed(true);
                    case LEFT -> c.options.leftKey.setPressed(true);
                    case RIGHT -> c.options.rightKey.setPressed(true);
                    default -> {}
                }
                motionTicks--;
            }

            if (motionTicks <= 0) {
                c.options.forwardKey.setPressed(false);
                c.options.backKey.setPressed(false);
                c.options.leftKey.setPressed(false);
                c.options.rightKey.setPressed(false);
                motion = Motion.NONE;
                ownsMovement = false;
            }
        }

        if (jumpOnce) {
            c.options.jumpKey.setPressed(true);
            jumpOnce = false;
            ownsJump = true;
        } else if (ownsJump) {
            c.options.jumpKey.setPressed(false);
            ownsJump = false;
        }

        if (useTicks > 0) {
            c.options.useKey.setPressed(true);
            ownsUse = true;
            useTicks--;
        } else if (ownsUse) {
            c.options.useKey.setPressed(false);
            ownsUse = false;
        }
    }
}
