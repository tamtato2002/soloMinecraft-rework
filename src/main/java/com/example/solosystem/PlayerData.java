package com.example.solosystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

public class PlayerData {
	public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("level", 1).forGetter(d -> d.level),
		Codec.DOUBLE.optionalFieldOf("exp", 0.0).forGetter(d -> d.exp),
		Codec.INT.optionalFieldOf("points", 0).forGetter(d -> d.points),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("stats", Map.of()).forGetter(d -> d.stats),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("skills", Map.of()).forGetter(d -> d.skills),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("resist", Map.of()).forGetter(d -> d.resist),
		Codec.LONG.optionalFieldOf("questExpiry", 0L).forGetter(d -> d.questExpiry),
		Quest.CODEC.listOf().optionalFieldOf("quests", List.of()).forGetter(d -> d.quests),
		Codec.STRING.listOf().optionalFieldOf("lastQuestTypes", List.of()).forGetter(d -> d.lastQuestTypes),
		Codec.LONG.optionalFieldOf("domReady", 0L).forGetter(d -> d.domReady),
		Codec.STRING.listOf().optionalFieldOf("disabled", List.of()).forGetter(d -> new ArrayList<>(d.disabled))
	).apply(i, PlayerData::new));

	public int level = 1;
	public double exp = 0;
	public int points = 0;
	public final Map<String, Integer> stats = new HashMap<>();
	public final Map<String, Integer> skills = new HashMap<>();
	/** resistance id -> number of exposures */
	public final Map<String, Integer> resist = new HashMap<>();
	/** game tick (overworld game time) when the current daily quests expire */
	public long questExpiry = 0;
	public final List<Quest> quests = new ArrayList<>();
	/** quest types used in the previous set (so the next day is different) */
	public final List<String> lastQuestTypes = new ArrayList<>();
	/** game tick when the Dominion ability is ready again */
	public long domReady = 0;
	/** passive skills ("light_feet") and stat abilities ("strength:3") the player switched OFF */
	public final Set<String> disabled = new HashSet<>();

	public PlayerData() {}

	public PlayerData(int level, double exp, int points, Map<String, Integer> stats, Map<String, Integer> skills,
					  Map<String, Integer> resist, long questExpiry, List<Quest> quests,
					  List<String> lastQuestTypes, long domReady, List<String> disabled) {
		this.level = level; this.exp = exp; this.points = points;
		this.stats.putAll(stats); this.skills.putAll(skills); this.resist.putAll(resist);
		migrateStatIds(this.stats);
		this.questExpiry = questExpiry;
		for (Quest q : quests) this.quests.add(q.copy());
		this.lastQuestTypes.addAll(lastQuestTypes);
		this.domReady = domReady;
		this.disabled.addAll(disabled);
	}

	/** Old saves used str/agi/vit/int/per. */
	private static void migrateStatIds(Map<String, Integer> m) {
		String[][] ren = {{"str", "strength"}, {"agi", "agility"}, {"vit", "endurance"}, {"int", "intelligence"}, {"per", "sense"}};
		for (String[] r : ren) {
			Integer v = m.remove(r[0]);
			if (v != null) m.merge(r[1], v, Integer::sum);
		}
	}

	/** Stat reached the level needed for ability {@code idx} (regardless of the ON/OFF switch). */
	public boolean unlocked(Stat s, int idx) { return idx < s.abilities.length && stat(s) >= s.abilities[idx].need(); }

	public static String abilityKey(Stat s, int idx) { return s.id + ":" + idx; }

	/** Unlocked AND switched on: this is what every game effect checks. */
	public boolean has(Stat s, int idx) { return unlocked(s, idx) && !disabled.contains(abilityKey(s, idx)); }

	public int stat(Stat s) { return stats.getOrDefault(s.id(), 0); }
	/** Rank you own, even if the skill is switched off. */
	public int rawRank(PassiveSkill s) { return skills.getOrDefault(s.id(), 0); }

	/** Rank that counts for game effects: 0 while the skill is switched off. */
	public int rank(PassiveSkill s) { return disabled.contains(s.id()) ? 0 : rawRank(s); }

	/** 40 + 18L + 4L^2 (Lv1->2 = 62, Lv10 = 620, Lv50 = 10940). */
	public static double expToNext(int level) { return 40 + 18.0 * level + 4.0 * level * level; }
	public double expToNext() { return expToNext(level); }

	public PlayerData copy() { return new PlayerData(level, exp, points, stats, skills, resist, questExpiry, quests, lastQuestTypes, domReady, new ArrayList<>(disabled)); }
}
