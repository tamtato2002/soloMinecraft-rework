package com.example.solosystem;

/** One-click difficulty presets for the rate settings (EXP, skill drops, loot ...). */
public final class Presets {
	public record Preset(String name, String blurb, double exp, double skill, double loot, double questExp, double orb,
						 int quests, int resistCap, boolean testMode) {}

	public static final Preset[] ALL = {
		new Preset("Challenging", "Slow progress, rare drops", 0.5, 0.5, 0.5, 0.75, 0.25, 2, 60, false),
		new Preset("Relaxed", "Default balance", 1.0, 1.0, 1.0, 1.0, 0.5, 3, 75, false),
		new Preset("Easy", "A bit faster", 1.5, 1.5, 1.5, 1.5, 0.75, 3, 80, false),
		new Preset("Very Easy", "Fast levels, frequent loot", 2.5, 2.5, 2.5, 2.0, 1.0, 4, 85, false),
		new Preset("Starting to Cheat", "Everything x4", 4.0, 4.0, 4.0, 3.0, 2.0, 4, 90, false),
		new Preset("Overpowered", "Everything x7", 7.0, 7.0, 7.0, 5.0, 4.0, 5, 95, false),
		new Preset("Fully Cheated", "Maxed out + test mode (unlimited stat points)", 10.0, 10.0, 10.0, 10.0, 10.0, 5, 95, true)
	};

	private Presets() {}

	public static void apply(Preset p) {
		SoloConfig.Data c = SoloConfig.get();
		c.expMultiplier = p.exp();
		c.skillDropMultiplier = p.skill();
		c.lootMultiplier = p.loot();
		c.questExpMultiplier = p.questExp();
		c.orbExpFactor = p.orb();
		c.questsPerDay = p.quests();
		c.resistMaxPercent = p.resistCap();
		c.testMode = p.testMode();
		SoloConfig.save();
	}

	private static boolean eq(double a, double b) { return Math.abs(a - b) < 1e-6; }

	/** Index of the preset that matches the current settings, or -1 for "Custom". */
	public static int current() {
		SoloConfig.Data c = SoloConfig.get();
		for (int i = 0; i < ALL.length; i++) {
			Preset p = ALL[i];
			if (eq(c.expMultiplier, p.exp()) && eq(c.skillDropMultiplier, p.skill()) && eq(c.lootMultiplier, p.loot())
				&& eq(c.questExpMultiplier, p.questExp()) && eq(c.orbExpFactor, p.orb()) && c.questsPerDay == p.quests()
				&& c.resistMaxPercent == p.resistCap() && c.testMode == p.testMode()) return i;
		}
		return -1;
	}
}
