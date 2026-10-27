package com.example.solosystem.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps track of toasts shown by the game (advancements, recipes, system messages) and by OTHER mods:
 * they all go through the vanilla toast manager, which a tiny mixin watches. Quest notifications use
 * this to step aside and stack below them when they would overlap.
 */
public final class ForeignToasts {
	/** vanilla toast: 160 wide, 32 high, stacked at the top-right corner, shown ~5 s */
	public static final int WIDTH = 160, HEIGHT = 32, LIFE_MS = 5600;

	private static final List<Double> ADDED = new ArrayList<>();

	private ForeignToasts() {}

	private static double nowMs() { return System.nanoTime() / 1_000_000.0; }

	/** called by the mixin whenever a toast is added to the game's toast manager */
	public static void onAdded() {
		ADDED.add(nowMs());
		while (ADDED.size() > 8) ADDED.remove(0);
	}

	public static int count() {
		double now = nowMs();
		ADDED.removeIf(t -> now - t > LIFE_MS);
		return Math.min(5, ADDED.size());
	}

	/** pixels of the top-right column currently covered by game / mod toasts */
	public static int occupiedBottom() {
		int n = count();
		return n == 0 ? 0 : n * HEIGHT + 2;
	}
}
