package com.example.solosystem;

public enum QuestType {
	MINE("mine", "Mine blocks", 40),
	CHOP("chop", "Chop logs", 16),
	ORE("ore", "Mine ores", 6),
	KILL("kill", "Defeat monsters", 8),
	CRAFT("craft", "Craft items", 12),
	SMELT("smelt", "Smelt / cook items", 12),
	WALK("walk", "Travel on foot (blocks)", 300),
	FISH("fish", "Catch fish", 3),
	BREED("breed", "Breed animals", 2),
	STONE("stone", "Mine stone", 64),
	HARVEST("harvest", "Harvest crops", 24),
	TRADE("trade", "Trade with villagers", 3),
	ENCHANT("enchant", "Enchant items", 2),
	HUNT("hunt", "Hunt animals", 6);

	public final String id, label;
	public final int base;
	QuestType(String id, String label, int base) { this.id = id; this.label = label; this.base = base; }

	public static QuestType byId(String s) {
		for (QuestType t : values()) if (t.id.equals(s)) return t;
		return null;
	}
}
