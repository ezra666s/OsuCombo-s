package com.osucombo;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

public class OsuComboClient implements ClientModInitializer {
	public static final String MOD_ID = "osucombo";
	public static ComboConfig CONFIG = new ComboConfig();

	@Override
	public void onInitializeClient() {
		CONFIG = ComboConfig.load();

		// Клик атаки: если дальше не будет удара по сущности, в конце тика это засчитается как промах.
		ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
			if (CONFIG.enabled && clickCount > 0) {
				ComboTracker.swingPending = true;
			}
			return false; // ничего не отменяем
		});

		// Удар по сущности. Событие вызывается и на клиенте, и на встроенном сервере, берём только клиент.
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!CONFIG.enabled || !level.isClientSide()) {
				return InteractionResult.PASS;
			}
			if (player != Minecraft.getInstance().player) {
				return InteractionResult.PASS;
			}

			// Клик дошёл до сущности, значит это не промах (даже если цель мы не считаем).
			ComboTracker.swingPending = false;

			if (CONFIG.playersOnly && !(entity instanceof Player)) {
				return InteractionResult.PASS;
			}

			ComboTracker.onHit(isCrit(player, entity));
			return InteractionResult.PASS;
		});

		ClientTickEvents.END_CLIENT_TICK.register(ComboTracker::tick);

		ComboHud.register();
	}

	/**
	 * Условия критического удара из ванильной игры (Java Edition):
	 * падаем, не на земле, не в воде, не на лестнице, нет слепоты, не едем верхом,
	 * не бежим, шкала атаки заряжена больше чем на 90%.
	 */
	private static boolean isCrit(Player player, Entity target) {
		return player.getAttackStrengthScale(0.5F) > 0.9F
				&& player.fallDistance > 0.0
				&& !player.onGround()
				&& !player.onClimbable()
				&& !player.isInWater()
				&& !player.hasEffect(MobEffects.BLINDNESS)
				&& !player.isPassenger()
				&& target instanceof LivingEntity
				&& !player.isSprinting();
	}
}
