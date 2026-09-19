package dev.shieldenchants;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ShieldEnchants implements ModInitializer {
    public static final String MOD_ID = "shield_enchants";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Identifier AEGIS = Identifier.fromNamespaceAndPath(MOD_ID, "aegis");
    private static final Identifier DEFLECTION = Identifier.fromNamespaceAndPath(MOD_ID, "deflection");
    private static final Identifier REPULSE = Identifier.fromNamespaceAndPath(MOD_ID, "repulse");
    private static final String AEGIS_DISPLAY_TAG = MOD_ID + ".aegis_display";

    private static final Map<UUID, List<Display.ItemDisplay>> AEGIS_DISPLAYS = new HashMap<>();
    private static final Map<UUID, Long> REPULSE_COOLDOWN_UNTIL = new HashMap<>();
    private static long ticks;

    @Override
    public void onInitialize() {
        DefaultItemComponentEvents.MODIFY.register(context ->
                context.modify(Items.SHIELD, builder ->
                        builder.set(DataComponents.ENCHANTABLE, new Enchantable(14))));

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player) || !isActivelyBlockingWithShield(player)) {
                return true;
            }

            ItemStack shield = player.getUseItem();
            int level = enchantmentLevel(shield, AEGIS);
            if (level <= 0) {
                return true;
            }

            Entity attackOrigin = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
            if (attackOrigin == null || attackOrigin == player) {
                return true;
            }

            return !isInsideAegisExtraCoverage(player, attackOrigin.position(), level);
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (!blocked || !(entity instanceof ServerPlayer player) || !isActivelyBlockingWithShield(player)) {
                return;
            }

            ItemStack shield = player.getUseItem();

            int deflectionLevel = enchantmentLevel(shield, DEFLECTION);
            if (deflectionLevel > 0 && source.getDirectEntity() instanceof Projectile projectile) {
                float chance = switch (deflectionLevel) {
                    case 1 -> 0.35F;
                    case 2 -> 0.60F;
                    default -> 0.85F;
                };

                if (player.getRandom().nextFloat() < chance) {
                    Vec3 oldVelocity = projectile.getDeltaMovement();
                    Vec3 reflected = oldVelocity.scale(-1.15D).add(0.0D, 0.08D, 0.0D);
                    projectile.setOwner(player);
                    projectile.setDeltaMovement(reflected);
                    projectile.hasImpulse = true;
                }
            }

            int repulseLevel = enchantmentLevel(shield, REPULSE);
            Entity attacker = source.getEntity();
            if (repulseLevel > 0 && attacker instanceof LivingEntity livingAttacker
                    && !(source.getDirectEntity() instanceof Projectile)) {
                long now = player.serverLevel().getGameTime();
                long readyAt = REPULSE_COOLDOWN_UNTIL.getOrDefault(player.getUUID(), 0L);
                if (now >= readyAt) {
                    double strength = 0.65D + (0.35D * repulseLevel);
                    double x = attacker.getX() - player.getX();
                    double z = attacker.getZ() - player.getZ();
                    livingAttacker.knockback(strength, x, z);

                    long cooldown = switch (repulseLevel) {
                        case 1 -> 60L;
                        case 2 -> 40L;
                        default -> 25L;
                    };
                    REPULSE_COOLDOWN_UNTIL.put(player.getUUID(), now + cooldown);
                }
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(ShieldEnchants::tickAegisDisplays);
        LOGGER.info("Shield Enchantments loaded for Minecraft 1.21.11");
    }

    private static boolean isActivelyBlockingWithShield(ServerPlayer player) {
        return player.isBlocking() && player.isUsingItem() && player.getUseItem().is(Items.SHIELD);
    }

    private static int enchantmentLevel(ItemStack stack, Identifier id) {
        for (Object2IntMap.Entry<net.minecraft.core.Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(id)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    private static boolean isInsideAegisExtraCoverage(ServerPlayer player, Vec3 sourcePosition, int level) {
        Vec3 toSource = sourcePosition.subtract(player.position());
        Vec3 horizontal = new Vec3(toSource.x, 0.0D, toSource.z);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            return true;
        }

        Vec3 look = player.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z).normalize();
        Vec3 direction = horizontal.normalize();
        double dot = Math.max(-1.0D, Math.min(1.0D, horizontalLook.dot(direction)));
        double angleDegrees = Math.toDegrees(Math.acos(dot));

        if (angleDegrees <= 90.0D) {
            return false;
        }

        double maxHalfAngle = switch (level) {
            case 1 -> 120.0D;
            case 2 -> 150.0D;
            default -> 180.0D;
        };
        return angleDegrees <= maxHalfAngle;
    }

    private static void tickAegisDisplays(MinecraftServer server) {
        ticks++;
        Set<UUID> activePlayers = new HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isActivelyBlockingWithShield(player)) {
                removeDisplays(player.getUUID());
                continue;
            }

            ItemStack shield = player.getUseItem();
            int level = enchantmentLevel(shield, AEGIS);
            if (level <= 0) {
                removeDisplays(player.getUUID());
                continue;
            }

            activePlayers.add(player.getUUID());
            int count = 3 + level;
            List<Display.ItemDisplay> displays = AEGIS_DISPLAYS.get(player.getUUID());

            if (!validDisplaySet(displays, count, player.serverLevel())) {
                removeDisplays(player.getUUID());
                displays = createDisplays(player, shield, count);
                AEGIS_DISPLAYS.put(player.getUUID(), displays);
            }

            updateDisplayPositions(player, displays, level);
        }

        for (UUID uuid : new ArrayList<>(AEGIS_DISPLAYS.keySet())) {
            if (!activePlayers.contains(uuid)) {
                removeDisplays(uuid);
            }
        }

        if (ticks % 100L == 0L) {
            cleanupOrphanedDisplays(server);
        }
    }

    private static boolean validDisplaySet(List<Display.ItemDisplay> displays, int wanted, ServerLevel level) {
        if (displays == null || displays.size() != wanted) {
            return false;
        }
        for (Display.ItemDisplay display : displays) {
            if (display.isRemoved() || display.level() != level) {
                return false;
            }
        }
        return true;
    }

    private static List<Display.ItemDisplay> createDisplays(ServerPlayer player, ItemStack enchantedShield, int count) {
        List<Display.ItemDisplay> displays = new ArrayList<>(count);
        ServerLevel level = player.serverLevel();

        for (int i = 0; i < count; i++) {
            Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
            ItemStack visualStack = enchantedShield.copy();
            visualStack.setCount(1);

            display.getSlot(0).set(visualStack);
            display.setNoGravity(true);
            display.setInvulnerable(true);
            display.addTag(AEGIS_DISPLAY_TAG);
            display.setPos(player.getX(), player.getY() + 1.0D, player.getZ());
            level.addFreshEntity(display);
            displays.add(display);
        }

        return displays;
    }

    private static void updateDisplayPositions(ServerPlayer player, List<Display.ItemDisplay> displays, int level) {
        double radius = 1.15D + (0.10D * level);
        double rotation = ticks * 0.045D;
        double y = player.getY() + 1.05D;
        int count = displays.size();

        for (int i = 0; i < count; i++) {
            Display.ItemDisplay display = displays.get(i);
            double angle = rotation + (Math.PI * 2.0D * i / count);
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            display.setPos(x, y, z);
            display.setYRot((float) (-Math.toDegrees(angle) + 90.0D));
        }
    }

    private static void removeDisplays(UUID playerId) {
        List<Display.ItemDisplay> displays = AEGIS_DISPLAYS.remove(playerId);
        if (displays != null) {
            for (Display.ItemDisplay display : displays) {
                if (!display.isRemoved()) {
                    display.discard();
                }
            }
        }
    }

    private static void cleanupOrphanedDisplays(MinecraftServer server) {
        Set<UUID> liveDisplayIds = new HashSet<>();
        for (List<Display.ItemDisplay> displays : AEGIS_DISPLAYS.values()) {
            for (Display.ItemDisplay display : displays) {
                liveDisplayIds.add(display.getUUID());
            }
        }

        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> remove = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity.getTags().contains(AEGIS_DISPLAY_TAG) && !liveDisplayIds.contains(entity.getUUID())) {
                    remove.add(entity);
                }
            }
            remove.forEach(Entity::discard);
        }
    }
}
