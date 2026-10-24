package com.osucombo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Настройки мода. Файл: config/osucombo.json (создаётся при первом запуске).
 */
public final class ComboConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("osucombo");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Мод включён. */
	public boolean enabled = true;
	/** Через сколько секунд без ударов комбо сбрасывается. */
	public double resetSeconds = 2.0;
	/** Считать только удары по игрокам. Поставь false, чтобы потестировать на мобах. */
	public boolean playersOnly = true;
	/** Большой счётчик комбо сверху по центру. */
	public boolean showCounter = true;
	/** Цифры, которые вылетают при каждом ударе. */
	public boolean showFlyingNumbers = true;
	/** Анимация «пульса» счётчика на каждый удар. */
	public boolean pulse = true;
	/** С какого комбо показывать большой счётчик. */
	public int minCombo = 1;
	/** Высота большого счётчика в пикселях интерфейса. */
	public float counterHeight = 34f;
	/** Отступ счётчика от верха экрана. */
	public float counterTop = 6f;
	/** Высота вылетающих цифр в пикселях интерфейса. */
	public float popupHeight = 26f;
	/** Откуда вылетают цифры по высоте экрана: 0 = верх, 1 = низ. */
	public float popupOriginY = 0.6f;

	public static ComboConfig load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("osucombo.json");
		ComboConfig config = new ComboConfig();

		if (Files.exists(path)) {
			try {
				ComboConfig read = GSON.fromJson(Files.readString(path), ComboConfig.class);
				if (read != null) {
					config = read;
				}
			} catch (IOException | RuntimeException e) {
				LOGGER.warn("Не удалось прочитать {}, использую значения по умолчанию", path, e);
			}
		}

		config.sanitize();

		// Перезаписываем файл, чтобы в нём всегда были все актуальные поля.
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(config));
		} catch (IOException e) {
			LOGGER.warn("Не удалось сохранить {}", path, e);
		}

		return config;
	}

	private void sanitize() {
		if (resetSeconds < 0.5) resetSeconds = 0.5;
		if (minCombo < 1) minCombo = 1;
		if (counterHeight < 8f) counterHeight = 8f;
		if (popupHeight < 6f) popupHeight = 6f;
		if (popupOriginY < 0f) popupOriginY = 0f;
		if (popupOriginY > 1f) popupOriginY = 1f;
	}
}
