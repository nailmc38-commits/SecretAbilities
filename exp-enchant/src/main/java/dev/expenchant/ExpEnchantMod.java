package dev.expenchant;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ExpEnchantMod implements ModInitializer {
    public static final String MOD_ID = "exp_enchant";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Identifier EXP = Identifier.fromNamespaceAndPath(MOD_ID, "exp");
    private static final Map<UUID, Long> SHIELD_XP_COOLDOWN = new HashMap<>();
    private static final long SHIELD_COOLDOWN_TICKS = 40L; // 2 seconds

    @Override
    public void onInitialize() {
        // Shields are normally not enchanting-table items, so give them high
        // enchantability to make EXP easy to roll directly on a shield.
        DefaultItemComponentEvents.MODIFY.register(context ->
                context.modify(Items.SHIELD, builder ->
                        builder.set(DataComponents.ENCHANTABLE, new Enchantable(20))));

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            // Shield behavior: a successful block gives bonus XP, with a short
            // cooldown so standing in front of rapid projectiles is not ridiculous.
            if (blocked && entity instanceof ServerPlayer player && player.isUsingItem()) {
                ItemStack shield = player.getUseItem();
                if (shield.is(Items.SHIELD)) {
                    int level = enchantmentLevel(shield, EXP);
                    if (level > 0) {
                        long now = ((ServerLevel) player.level()).getGameTime();
                        long readyAt = SHIELD_XP_COOLDOWN.getOrDefault(player.getUUID(), 0L);
                        if (now >= readyAt) {
                            player.giveExperiencePoints(shieldXp(level));
                            SHIELD_XP_COOLDOWN.put(player.getUUID(), now + SHIELD_COOLDOWN_TICKS);
                        }
                    }
                }
            }

            // Sword behavior: killing a non-player living mob while the enchanted
            // sword is in your main hand awards a large amount of bonus XP.
            if (!blocked
                    && !entity.isAlive()
                    && !(entity instanceof Player)
                    && source.getEntity() instanceof ServerPlayer attacker) {
                ItemStack weapon = attacker.getMainHandItem();
                int level = enchantmentLevel(weapon, EXP);
                if (level > 0) {
                    attacker.giveExperiencePoints(swordXp(level));
                }
            }
        });

        LOGGER.info("EXP Enchantment loaded for Minecraft 1.21.11");
    }

    private static int shieldXp(int level) {
        return switch (level) {
            case 1 -> 4;
            case 2 -> 8;
            case 3 -> 12;
            case 4 -> 16;
            default -> 20;
        };
    }

    private static int swordXp(int level) {
        return switch (level) {
            case 1 -> 10;
            case 2 -> 20;
            case 3 -> 35;
            case 4 -> 55;
            default -> 80;
        };
    }

    private static int enchantmentLevel(ItemStack stack, Identifier id) {
        if (stack.isEmpty()) {
            return 0;
        }

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(id)) {
                return entry.getIntValue();
            }
        }

        return 0;
    }
}
