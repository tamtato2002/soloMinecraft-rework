package com.example.solosystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

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
		Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(q -> q.items)
	).apply(i, Quest::new));

	public final String type;
	public final int tier;    // 0 = COMMON ... 4 = LEGENDARY
	public final int target;
	public double progress;
	public boolean done;
	// randomly rolled reward (fixed when the quest is created)
	public final int rewardExp;
	public final int rewardPoints;
	public final double skillChance;
	/** each entry = "modid:item|count" */
	public final List<String> items = new ArrayList<>();

	public Quest(String type, int tier, int target, double progress, boolean done,
				 int rewardExp, int rewardPoints, double skillChance, List<String> items) {
		this.type = type; this.tier = tier; this.target = target;
		this.progress = progress; this.done = done;
		this.rewardExp = rewardExp; this.rewardPoints = rewardPoints; this.skillChance = skillChance;
		this.items.addAll(items);
	}

	public Quest copy() { return new Quest(type, tier, target, progress, done, rewardExp, rewardPoints, skillChance, items); }
}
