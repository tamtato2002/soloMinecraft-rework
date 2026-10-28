package com.example.solosystem.client;

import com.example.solosystem.SoloConfig;
import java.util.HashMap;
import java.util.Map;

/** Ore colour lookup: config overrides ("#RRGGBB"), cached, plus an automatic colour for unknown modded ores. */
public final class OreColors {
	public static final int[] PALETTE = {
		0xFFFFFF, 0xC0C0C0, 0xFF3030, 0xFF8A3C, 0xFFD93D, 0xB0FF70, 0x3CE05A, 0x00FFA0,
		0x4FE8F0, 0x3F6BFF, 0xA85CE8, 0xFF55C8, 0xFF77A0, 0xC08040, 0x7DFF00, 0x00B8FF};

	private static final Map<String, Integer> CACHE = new HashMap<>();
	private static int cacheVersion = -1;

	private OreColors() {}

	/** RGB override for this key, or null. */
	public static Integer override(String key) {
		if (cacheVersion != SoloConfig.version) {
			CACHE.clear();
			cacheVersion = SoloConfig.version;
		}
		Integer c = CACHE.get(key);
		if (c == null) {
			String hex = SoloConfig.get().oreColors.get(key);
			c = hex == null ? -1 : parse(hex);
			CACHE.put(key, c);
		}
		return c < 0 ? null : c;
	}

	public static int parse(String hex) {
		try {
			String h = hex.trim();
			if (h.startsWith("#")) h = h.substring(1);
			return Integer.parseInt(h, 16) & 0xFFFFFF;
		} catch (Exception e) {
			return -1;
		}
	}

	public static String toHex(int rgb) { return String.format("#%06X", rgb & 0xFFFFFF); }

	/** Next colour in the palette after {@code current}. */
	public static int next(int current) {
		for (int i = 0; i < PALETTE.length; i++) if (PALETTE[i] == (current & 0xFFFFFF)) return PALETTE[(i + 1) % PALETTE.length];
		return PALETTE[0];
	}

	/** Stable, vivid colour derived from a name (used for ores from mods that have no colour set). */
	public static int auto(String key) {
		int h = key.hashCode() * 0x9E3779B1;
		double hue = ((h >>> 8) & 0xFFFF) / 65535.0;
		return hsv(hue, 0.75, 1.0);
	}

	private static int hsv(double h, double s, double v) {
		double r, g, b;
		int i = (int) Math.floor(h * 6);
		double f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
		switch (((i % 6) + 6) % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
	}
}
