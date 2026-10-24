package com.osucombo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.Minecraft;

/**
 * Состояние комбо. Всё живёт в потоке клиента, синхронизация не нужна.
 */
public final class ComboTracker {
	/** Одна вылетающая цифра. */
	public record Popup(int value, boolean crit, long startNanos, float angle) {
	}

	/** Сколько секунд после обрыва показывать «сломанное» комбо (если оно было не меньше BREAK_MIN). */
	public static final float BREAK_SECONDS = 0.4f;
	private static final int BREAK_MIN = 3;
	private static final int MAX_POPUPS = 32;

	public static final List<Popup> POPUPS = new ArrayList<>();

	public static int combo;
	public static boolean lastHitCrit;
	public static long lastHitNanos;
	public static int breakValue;
	public static long breakNanos;

	/** Игрок кликнул атаку в этом тике, но удара по сущности ещё не было. */
	static boolean swingPending;
	private static int idleTicks;

	private ComboTracker() {
	}

	/** Удар по цели. {@code crit} — был ли это критический удар. */
	public static void onHit(boolean crit) {
		swingPending = false;
		combo++;
		// -1, потому что в конце этого же тика счётчик простоя ещё раз прибавит единицу.
		idleTicks = -1;
		lastHitCrit = crit;
		lastHitNanos = System.nanoTime();
		breakValue = 0;

		if (OsuComboClient.CONFIG.showFlyingNumbers) {
			// Чётные удары летят вправо, нечётные влево, чтобы цифры не слипались.
			float side = (combo % 2 == 0) ? 1f : -1f;
			float spread = (float) Math.toRadians(18 + ThreadLocalRandom.current().nextDouble() * 47);
			POPUPS.add(new Popup(combo, crit, lastHitNanos, side * spread));
			while (POPUPS.size() > MAX_POPUPS) {
				POPUPS.remove(0);
			}
		}
	}

	/** Комбо оборвалось (промах или таймаут). */
	public static void reset() {
		if (combo >= BREAK_MIN) {
			breakValue = combo;
			breakNanos = System.nanoTime();
		}
		combo = 0;
		idleTicks = 0;
	}

	/** Вызывается в конце каждого тика клиента. */
	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			combo = 0;
			idleTicks = 0;
			swingPending = false;
			breakValue = 0;
			POPUPS.clear();
			return;
		}

		if (mc.isPaused()) {
			return;
		}

		// Был клик атаки, а удара по сущности не случилось: это промах.
		if (swingPending) {
			swingPending = false;
			if (combo > 0) {
				reset();
			}
			return;
		}

		if (combo > 0) {
			idleTicks++;
			int limit = (int) Math.round(OsuComboClient.CONFIG.resetSeconds * 20.0);
			if (idleTicks >= limit) {
				reset();
			}
		}
	}
}
