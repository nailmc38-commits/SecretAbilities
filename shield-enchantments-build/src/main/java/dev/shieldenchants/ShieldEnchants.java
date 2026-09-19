package dev.shieldenchants;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

    private static final Identifier AEGIS = id("aegis");
    private static final Identifier DEFLECTION = id("deflection");
    private static final Identifier REPULSE = id("repulse");
    private static final Identifier BASTION = id("bastion");
    private static final Identifier GUARDIAN_STEP = id("guardian_step");
    private static final Identifier FLAME_WARD = id("flame_ward");
    private static final Identifier WARDHEART = id("wardheart");
    private static final Identifier RESTORATION = id("restoration");

    private static final String AEGIS_DISPLAY_TAG = MOD_ID + ".aegis_display";
    private static final long AEGIS_SUMMON_COOLDOWN = 80L; // 4 seconds

    private static final Map<UUID, List<Display.ItemDisplay>> AEGIS_DISPLAYS = new HashMap<>();
    private static final Map<UUID, Integer> AEGIS_CHARGES = new HashMap<>();
    private static final Map<UUID, Long> AEGIS_COOLDOWN_UNTIL = new HashMap<>();
    private static final Map<UUID, ItemStack> AEGIS_SOURCE_SHIELD = new HashMap<>();
    private static final Map<UUID, Long> REPULSE_COOLDOWN_UNTIL = new HashMap<>();
    private static long ticks;

    @Override
    public void onInitialize() {
        DefaultItemComponentEvents.MODIFY.register(context ->
                context.modify(Items.SHIELD, builder ->
                        builder.set(DataComponents.ENCHANTABLE, new Enchantable(14))));

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            int aegisLevel = enchantmentLevel(stack, AEGIS);

            if (!stack.is(Items.SHIELD) || aegisLevel <= 0 || !player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }

            if (world instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
                summonAegisShield(serverPlayer, stack, aegisLevel);
            }

            // Shift + right-click is reserved for Aegis, so it does not start normal blocking.
            return InteractionResult.SUCCESS;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player)) {
                return true;
            }

            int charges = AEGIS_CHARGES.getOrDefault(player.getUUID(), 0);
            if (charges <= 0) {
                return true;
            }

            Entity attackOrigin = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
            if (attackOrigin == null || attackOrigin == player) {
                return true;
            }

            ItemStack sourceShield = AEGIS_SOURCE_SHIELD.getOrDefault(player.getUUID(), ItemStack.EMPTY);
            handleSuccessfulBlock(player, source, sourceShield);
            consumeAegisCharge(player, attackOrigin);
            return false;
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (!blocked || !(entity instanceof ServerPlayer player) || !isActivelyBlockingWithShield(player)) {
                return;
            }

            ItemStack shield = player.getUseItem();
            handleSuccessfulBlock(player, source, shield);
        });

        ServerTickEvents.END_SERVER_TICK.register(ShieldEnchants::serverTick);
        LOGGER.info("Shield Enchantments loaded for Minecraft 1.21.11");
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void summonAegisShield(ServerPlayer player, ItemStack shield, int aegisLevel) {
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        UUID uuid = player.getUUID();

        int current = AEGIS_CHARGES.getOrDefault(uuid, 0);
        int max = Math.min(3, aegisLevel);

        if (current >= max || now < AEGIS_COOLDOWN_UNTIL.getOrDefault(uuid, 0L)) {
            return;
        }

        int newCount = current + 1;
        AEGIS_CHARGES.put(uuid, newCount);
        AEGIS_COOLDOWN_UNTIL.put(uuid, now + AEGIS_SUMMON_COOLDOWN);
        AEGIS_SOURCE_SHIELD.put(uuid, shield.copy());

        rebuildAegisDisplays(player, shield, newCount);
    }

    private static void handleSuccessfulBlock(ServerPlayer player, DamageSource source, ItemStack shield) {
        if (shield.isEmpty()) {
            return;
        }

        int deflectionLevel = enchantmentLevel(shield, DEFLECTION);
        if (deflectionLevel > 0 && source.getDirectEntity() instanceof Projectile projectile) {
            float chance = switch (deflectionLevel) {
                case 1 -> 0.35F;
                case 2 -> 0.60F;
                default -> 0.85F;
            };

            if (player.getRandom().nextFloat() < chance) {
                Vec3 reflected = projectile.getDeltaMovement().scale(-1.15D).add(0.0D, 0.08D, 0.0D);
                projectile.setOwner(player);
                projectile.setDeltaMovement(reflected);
            }
        }

        int repulseLevel = enchantmentLevel(shield, REPULSE);
        Entity attacker = source.getEntity();
        if (repulseLevel > 0 && attacker instanceof LivingEntity livingAttacker
                && !(source.getDirectEntity() instanceof Projectile)) {
            long now = ((ServerLevel) player.level()).getGameTime();
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

        int bastionLevel = enchantmentLevel(shield, BASTION);
        if (bastionLevel > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 + (bastionLevel * 10), 0));
        }

        int stepLevel = enchantmentLevel(shield, GUARDIAN_STEP);
        if (stepLevel > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40 + (stepLevel * 10), stepLevel - 1));
        }

        int flameLevel = enchantmentLevel(shield, FLAME_WARD);
        if (flameLevel > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60 + (flameLevel * 60), 0));
        }

        int wardheartLevel = enchantmentLevel(shield, WARDHEART);
        if (wardheartLevel > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 40 + (wardheartLevel * 40), 0));
        }
    }

    private static boolean isActivelyBlockingWithShield(ServerPlayer player) {
        return player.isBlocking() && player.isUsingItem() && player.getUseItem().is(Items.SHIELD);
    }

    private static int enchantmentLevel(ItemStack stack, Identifier id) {
        if (stack.isEmpty()) {
            return 0;
        }

        for (Object2IntMap.Entry<net.minecraft.core.Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(id)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    private static void serverTick(MinecraftServer server) {
        ticks++;
        Set<UUID> onlinePlayers = new HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            onlinePlayers.add(uuid);

            if (!player.isAlive()) {
                clearAegis(uuid);
                continue;
            }

            int charges = AEGIS_CHARGES.getOrDefault(uuid, 0);
            if (charges > 0) {
                ItemStack sourceShield = AEGIS_SOURCE_SHIELD.getOrDefault(uuid, ItemStack.EMPTY);
                List<Display.ItemDisplay> displays = AEGIS_DISPLAYS.get(uuid);

                if (!validDisplaySet(displays, charges, (ServerLevel) player.level())) {
                    rebuildAegisDisplays(player, sourceShield.isEmpty() ? new ItemStack(Items.SHIELD) : sourceShield, charges);
                    displays = AEGIS_DISPLAYS.get(uuid);
                }

                updateAegisDisplayPositions(player, displays);
            } else {
                removeDisplays(uuid);
            }

            tickRestoration(player.getMainHandItem());
            tickRestoration(player.getOffhandItem());
        }

        for (UUID uuid : new ArrayList<>(AEGIS_DISPLAYS.keySet())) {
            if (!onlinePlayers.contains(uuid)) {
                clearAegis(uuid);
            }
        }

        if (ticks % 100L == 0L) {
            cleanupOrphanedDisplays(server);
        }
    }

    private static void tickRestoration(ItemStack shield) {
        if (!shield.is(Items.SHIELD) || !shield.isDamaged()) {
            return;
        }

        int level = enchantmentLevel(shield, RESTORATION);
        if (level <= 0) {
            return;
        }

        long interval = switch (level) {
            case 1 -> 160L;
            case 2 -> 120L;
            default -> 80L;
        };

        if (ticks % interval == 0L) {
            shield.setDamageValue(Math.max(0, shield.getDamageValue() - 1));
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

    private static void rebuildAegisDisplays(ServerPlayer player, ItemStack shield, int count) {
        removeDisplays(player.getUUID());

        List<Display.ItemDisplay> displays = new ArrayList<>(count);
        ServerLevel level = (ServerLevel) player.level();

        for (int i = 0; i < count; i++) {
            Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
            ItemStack visualStack = shield.copy();
            visualStack.setCount(1);

            display.getSlot(0).set(visualStack);
            display.setNoGravity(true);
            display.setInvulnerable(true);
            display.addTag(AEGIS_DISPLAY_TAG);
            display.setPos(player.getX(), player.getY() + 1.0D, player.getZ());
            level.addFreshEntity(display);
            displays.add(display);
        }

        AEGIS_DISPLAYS.put(player.getUUID(), displays);
        updateAegisDisplayPositions(player, displays);
    }

    private static void updateAegisDisplayPositions(ServerPlayer player, List<Display.ItemDisplay> displays) {
        if (displays == null || displays.isEmpty()) {
            return;
        }

        double radius = 0.82D;
        double y = player.getY() + 1.05D;
        int count = displays.size();
        double base = Math.toRadians(player.getYRot());

        for (int i = 0; i < count; i++) {
            double offset;
            if (count == 1) {
                offset = Math.PI;
            } else if (count == 2) {
                offset = (i == 0 ? Math.toRadians(120.0D) : Math.toRadians(240.0D));
            } else {
                offset = Math.toRadians(i * 120.0D);
            }

            double angle = base + offset;
            Display.ItemDisplay display = displays.get(i);
            double x = player.getX() - Math.sin(angle) * radius;
            double z = player.getZ() + Math.cos(angle) * radius;

            display.setPos(x, y, z);
            display.setYRot((float) Math.toDegrees(angle));
            display.setXRot(0.0F);
        }
    }

    private static void consumeAegisCharge(ServerPlayer player, Entity attackOrigin) {
        UUID uuid = player.getUUID();
        List<Display.ItemDisplay> displays = AEGIS_DISPLAYS.get(uuid);

        if (displays != null && !displays.isEmpty()) {
            Display.ItemDisplay closest = displays.get(0);
            double closestDistance = closest.distanceToSqr(attackOrigin);

            for (Display.ItemDisplay display : displays) {
                double distance = display.distanceToSqr(attackOrigin);
                if (distance < closestDistance) {
                    closest = display;
                    closestDistance = distance;
                }
            }

            closest.discard();
            displays.remove(closest);
        }

        int remaining = Math.max(0, AEGIS_CHARGES.getOrDefault(uuid, 0) - 1);
        if (remaining == 0) {
            clearAegis(uuid);
        } else {
            AEGIS_CHARGES.put(uuid, remaining);
        }
    }

    private static void clearAegis(UUID playerId) {
        AEGIS_CHARGES.remove(playerId);
        AEGIS_SOURCE_SHIELD.remove(playerId);
        removeDisplays(playerId);
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
