package com.osucombo;

import java.util.Iterator;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * Рисует большой счётчик сверху по центру и вылетающие цифры.
 *
 * <p>Цифры берутся из текстур assets/osucombo/textures/gui/combo/:
 * n0..n9, nx — обычные, c0..c9, cx — для критов. Чтобы поменять стиль, подмени эти PNG
 * (ресурспаком или прямо в проекте). Размер каждой цифры — 96x168, фон прозрачный.
 */
public final class ComboHud {
	/** Размер одной текстуры цифры. */
	private static final int CELL_W = 96;
	private static final int CELL_H = 168;
	/** Шаг между цифрами в пикселях текстуры (цифры стоят чуть теснее ячейки). */
	private static final float ADVANCE = 60f;

	// Вылетающие цифры
	private static final float POPUP_LIFE = 0.9f;
	private static final float POPUP_SPEED = 210f;
	private static final float POPUP_DRAG = 150f;
	private static final float POPUP_GRAVITY = 90f;

	/** [0] — обычные, [1] — криты; индекс 10 — буква «x». */
	private static final Identifier[][] TEXTURES = new Identifier[2][11];

	static {
		String[] prefixes = {"n", "c"};
		for (int style = 0; style < 2; style++) {
			for (int i = 0; i < 11; i++) {
				String name = prefixes[style] + (i == 10 ? "x" : String.valueOf(i));
				TEXTURES[style][i] = Identifier.fromNamespaceAndPath(
						OsuComboClient.MOD_ID, "textures/gui/combo/" + name + ".png");
			}
		}
	}

	private ComboHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(OsuComboClient.MOD_ID, "combo_hud"),
				ComboHud::render
		);
	}

	private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		ComboConfig config = OsuComboClient.CONFIG;
		if (!config.enabled) {
			return;
		}

		long now = System.nanoTime();
		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();

		if (config.showFlyingNumbers) {
			renderPopups(graphics, config, now, screenWidth, screenHeight);
		}
		if (config.showCounter) {
			renderCounter(graphics, config, now, screenWidth);
		}
	}

	private static void renderCounter(GuiGraphics graphics, ComboConfig config, long now, int screenWidth) {
		float centerX = screenWidth / 2f;
		float centerY = config.counterTop + config.counterHeight / 2f;

		if (ComboTracker.combo >= config.minCombo) {
			float t = (now - ComboTracker.lastHitNanos) / 1_000_000_000f;

			// «Пульс» как в osu!: на каждый удар счётчик подпрыгивает и плавно возвращается.
			float scale = 1f;
			if (config.pulse) {
				float strength = ComboTracker.lastHitCrit ? 0.45f : 0.30f;
				scale += strength * (float) Math.exp(-t * 9.0);
			}

			// Крит отличается только размером: пульс сильнее. Цвет такой же, как у обычного удара.
			drawNumber(graphics, ComboTracker.combo + "x", false, centerX, centerY, config.counterHeight, scale);
		} else if (ComboTracker.breakValue > 0) {
			// Комбо оборвалось: счётчик проваливается вниз и уменьшается.
			float t = (now - ComboTracker.breakNanos) / 1_000_000_000f;
			if (t < ComboTracker.BREAK_SECONDS) {
				float k = 1f - t / ComboTracker.BREAK_SECONDS;
				float drop = (1f - k) * config.counterHeight * 0.8f;
				drawNumber(graphics, ComboTracker.breakValue + "x", false, centerX, centerY + drop,
						config.counterHeight, k * k);
			}
		}
	}

	private static void renderPopups(GuiGraphics graphics, ComboConfig config, long now,
			int screenWidth, int screenHeight) {
		Iterator<ComboTracker.Popup> iterator = ComboTracker.POPUPS.iterator();
		while (iterator.hasNext()) {
			ComboTracker.Popup popup = iterator.next();
			float t = (now - popup.startNanos()) / 1_000_000_000f;
			if (t >= POPUP_LIFE) {
				iterator.remove();
				continue;
			}

			// Цифра вылетает из нижней части экрана вверх и в сторону, замедляясь.
			float distance = POPUP_SPEED * t - POPUP_DRAG * t * t * 0.5f;
			float x = screenWidth / 2f + (float) Math.sin(popup.angle()) * distance;
			float y = screenHeight * config.popupOriginY
					- (float) Math.cos(popup.angle()) * distance
					+ POPUP_GRAVITY * t * t * 0.5f;

			// Появляется с «отскоком», в конце уменьшается до нуля.
			float scale;
			if (t < 0.12f) {
				scale = easeOutBack(t / 0.12f);
			} else if (t < 0.55f) {
				scale = 1f;
			} else {
				scale = 1f - (t - 0.55f) / (POPUP_LIFE - 0.55f);
			}
			if (popup.crit()) {
				scale *= 1.4f;
			}

			// Цвет всегда обычный (бледно-серый), крит виден только по размеру.
			drawNumber(graphics, String.valueOf(popup.value()), false, x, y, config.popupHeight, scale);
		}
	}

	/**
	 * Рисует строку из цифр и «x» с центром в (centerX, centerY).
	 *
	 * @param height высота цифры в пикселях интерфейса при scaleMul = 1
	 */
	private static void drawNumber(GuiGraphics graphics, String text, boolean crit,
			float centerX, float centerY, float height, float scaleMul) {
		if (scaleMul <= 0.01f) {
			return;
		}

		float s = height * scaleMul / CELL_H; // пиксели текстуры -> пиксели интерфейса
		float advance = ADVANCE * s;
		float cellW = CELL_W * s;
		float cellH = CELL_H * s;
		float left = centerX - advance * text.length() / 2f + (advance - cellW) / 2f;
		float top = centerY - cellH / 2f;

		Identifier[] set = TEXTURES[crit ? 1 : 0];
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			int index = (c == 'x') ? 10 : (c - '0');
			if (index < 0 || index > 10) {
				continue;
			}

			graphics.pose().pushMatrix();
			graphics.pose().translate(left + i * advance, top);
			graphics.pose().scale(s);
			graphics.blit(RenderPipelines.GUI_TEXTURED, set[index], 0, 0, 0f, 0f, CELL_W, CELL_H, CELL_W, CELL_H);
			graphics.pose().popMatrix();
		}
	}

	private static float easeOutBack(float x) {
		float c1 = 1.70158f;
		float c3 = c1 + 1f;
		float p = x - 1f;
		return 1f + c3 * p * p * p + c1 * p * p;
	}
}
