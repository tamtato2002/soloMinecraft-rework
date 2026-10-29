package com.example.solosystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

/**
 * One daily quest. Progress is measured from the game's own statistics (see QuestTracker): the stat value now minus
 * {@code base} (the value when the quest was handed out), times {@code scale}. That is why it counts every way of
 * doing the thing (clicking, Shift+Click, items from other mods ...).
 */
public class Quest {
	public static final Codec<Quest> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("type").forGetter(q -> q.type),
		Codec.INT.fieldOf("tier").forGetter(q -> q.tier),
		Codec.INT.fieldOf("target").forGetter(q -> q.target),
		Codec.DOUBLE.optionalFieldOf("progress", 0.0).forGetter(q -> q.progress),
		Codec.BOOL.optionalFieldOf("done", false).forGetter(q -> q.done),
		Codec.INT.optionalFieldOf("rewardExp", 0).forGetter(q -> q.rewardExp),
		Codec.INT.optionalFieldOf("rewardPoints", 0).forGetter(q -> q.rewardPoints),
		Codec.DOUBLE.optionalFieldOf("skillChance", 0.0).forGetter(q -> q.skillChance),
		Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(q -> q.items),
		Codec.BOOL.optionalFieldOf("claimed", false).forGetter(q -> q.claimed),
		Codec.STRING.optionalFieldOf("label", "").forGetter(q -> q.label),
		Codec.STRING.optionalFieldOf("kind", "").forGetter(q -> q.kind),
		Codec.STRING.optionalFieldOf("filter", "").forGetter(q -> q.filter),
		Codec.DOUBLE.optionalFieldOf("scale", 1.0).forGetter(q -> q.scale),
		Codec.DOUBLE.optionalFieldOf("base", 0.0).forGetter(q -> q.base)
	).apply(i, Quest::new));

	/** catalog id, e.g. "ore_iron" or "mod_mob_somemod_dragon" */
	public final String type;
	public final int tier;    // 0 = COMMON ... 4 = LEGENDARY
	public final int target;
	public double progress;
	public boolean done;
	public final int rewardExp;
	public final int rewardPoints;
	public final double skillChance;
	/** each entry = "modid:item|count" */
	public final List<String> items = new ArrayList<>();
	public boolean claimed;
	/** text shown to the player, e.g. "Mine Iron Ore" */
	public final String label;
	/** how it is measured: block, craft, use, pickup, kill, stat, xp, active */
	public final String kind;
	/** what is measured: ids / groups such as "@ores" (see QuestTracker) */
	public final String filter;
	public final double scale;
	public double base;

	public Quest(String type, int tier, int target, double progress, boolean done, int rewardExp, int rewardPoints,
				 double skillChance, List<String> items, boolean claimed, String label, String kind, String filter,
				 double scale, double base) {
		this.type = type; this.tier = tier; this.target = target;
		this.progress = progress; this.done = done;
		this.rewardExp = rewardExp; this.rewardPoints = rewardPoints; this.skillChance = skillChance;
		this.items.addAll(items);
		this.claimed = claimed;
		this.label = label; this.kind = kind; this.filter = filter; this.scale = scale; this.base = base;
	}

	public String title() { return label == null || label.isEmpty() ? type : label; }

	public Quest copy() {
		return new Quest(type, tier, target, progress, done, rewardExp, rewardPoints, skillChance, items, claimed, label, kind, filter, scale, base);
	}
}
