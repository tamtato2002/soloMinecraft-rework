package com.example.solosystem;

/** Resistances that are LEARNED by being exposed to something. More exposure = higher % (cap in config, default 75%). */
public enum Resist {
	MELEE("melee", "Melee", 40),
	RANGED("ranged", "Ranged", 40),
	FIRE("fire", "Fire & Lava", 30),
	EXPLOSION("explosion", "Explosion", 15),
	FALL("fall", "Fall", 25),
	DROWNING("drowning", "Drowning", 15),
	COLD("cold", "Freezing", 20),
	LIGHTNING("lightning", "Lightning", 3),
	POISON("poison", "Poison", 8),
	WITHER("wither", "Wither", 6),
	SLOWNESS("slowness", "Slowness", 8),
	WEAKNESS("weakness", "Weakness", 8),
	MINING_FATIGUE("mining_fatigue", "Mining Fatigue", 6),
	HUNGER("hunger", "Hunger", 8),
	BLINDNESS("blindness", "Blindness", 6),
	NAUSEA("nausea", "Nausea", 6),
	DARKNESS("darkness", "Darkness", 5),
	LEVITATION("levitation", "Levitation", 5);

	public final String id, label;
	/** Exposures needed to reach half of the max resistance. */
	public final int half;

	Resist(String id, String label, int half) { this.id = id; this.label = label; this.half = half; }

	public static double max() { return SoloConfig.get().resistMaxPercent / 100.0; }

	/** Learned part only: 0 .. max for a given number of exposures. */
	public double percent(int count) {
		if (count <= 0) return 0;
		return max() * (1.0 - Math.pow(0.5, count / (double) half));
	}

	/** Learned resistance + Endurance "Environmental Resistance" bonus (fire, drowning, poison, wither). */
	public static double total(PlayerData d, Resist r) {
		double p = r.percent(d.resist.getOrDefault(r.id, 0));
		if (d.has(Stat.ENDURANCE, 3) && (r == FIRE || r == DROWNING || r == POISON || r == WITHER))
			p += Math.min(0.5, 0.15 + 0.01 * (d.stat(Stat.ENDURANCE) - 60));
		return Math.min(0.95, p);
	}
}
