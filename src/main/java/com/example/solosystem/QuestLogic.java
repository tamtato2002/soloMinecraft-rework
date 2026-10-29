package com.example.solosystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Daily quests. They reset every 24000 game ticks (1 Minecraft day, ~20 real minutes).
 * Quests come from QuestCatalog (230+ built-in + automatic ones for other mods), are balanced by the time they take
 * (see QuestCatalog), and rewards are random per quest - including items from other mods.
 * Rewards are collected with the CLAIM button; unclaimed ones are claimed automatically at the reset.
 */
public final class QuestLogic {
	public static final long DAY_TICKS = 24000L;

	/** seconds of work a quest of each tier should take (see QuestCatalog.sec) */
	private static final double[] BUDGET = {100, 210, 360, 560, 840};
	private static final int[] TIER_WEIGHT = {40, 28, 18, 10, 4};

	private static final int[] EXP_BASE = {60, 160, 400, 900, 2000};
	private static final int[] PTS_MIN = {0, 0, 1, 2, 4};
	private static final int[] PTS_MAX = {1, 2, 3, 5, 8};
	private static final double[] SK_MIN = {0.05, 0.20, 0.50, 0.80, 1.0};
	private static final double[] SK_MAX = {0.20, 0.50, 0.85, 1.00, 1.0};
	private static final double[] SK_BOOST = {0, 0.5, 1.5, 3, 6};

	private record Reward(String id, int min, int max) {}

	private static Reward r(String id, int a, int b) { return new Reward(id, a, b); }

	private static final Reward[][] BUILTIN = {
		{ r("minecraft:iron_ingot", 4, 10), r("minecraft:cooked_beef", 6, 12), r("minecraft:coal", 8, 16), r("minecraft:bread", 8, 16),
		  r("minecraft:copper_ingot", 6, 12), r("minecraft:arrow", 16, 32), r("minecraft:torch", 16, 32), r("minecraft:bone_meal", 8, 16) },
		{ r("minecraft:gold_ingot", 6, 14), r("minecraft:diamond", 1, 3), r("minecraft:lapis_lazuli", 8, 16), r("minecraft:redstone", 10, 24),
		  r("minecraft:ender_pearl", 1, 3), r("minecraft:golden_carrot", 8, 16), r("minecraft:experience_bottle", 4, 10), r("minecraft:quartz", 8, 16) },
		{ r("minecraft:diamond", 3, 8), r("minecraft:emerald", 4, 10), r("minecraft:experience_bottle", 10, 24), r("minecraft:golden_apple", 1, 3),
		  r("minecraft:ender_pearl", 4, 8), r("minecraft:blaze_rod", 3, 8), r("minecraft:name_tag", 1, 2), r("minecraft:saddle", 1, 1) },
		{ r("minecraft:diamond", 8, 20), r("minecraft:netherite_scrap", 2, 5), r("minecraft:enchanted_golden_apple", 1, 2), r("minecraft:totem_of_undying", 1, 1),
		  r("minecraft:emerald", 12, 24), r("minecraft:experience_bottle", 24, 48), r("minecraft:shulker_shell", 2, 4), r("minecraft:heart_of_the_sea", 1, 1) },
		{ r("minecraft:netherite_ingot", 2, 5), r("minecraft:enchanted_golden_apple", 2, 4), r("minecraft:totem_of_undying", 1, 2), r("minecraft:nether_star", 1, 1),
		  r("minecraft:experience_bottle", 48, 64), r("minecraft:diamond_block", 2, 5), r("minecraft:beacon", 1, 1) }
	};

	private QuestLogic() {}

	public static SkillRarity tierRarity(int tier) { return SkillRarity.values()[Math.max(0, Math.min(4, tier))]; }

	// ================================================================ reward description (server + client)

	public static List<ItemStack> itemStacks(Quest q) {
		List<ItemStack> out = new ArrayList<>();
		for (String code : q.items) {
			int bar = code.lastIndexOf('|');
			if (bar < 0) continue;
			Item it = ItemIds.item(code.substring(0, bar));
			int count;
			try { count = Integer.parseInt(code.substring(bar + 1)); } catch (NumberFormatException e) { continue; }
			if (it != null && count > 0) out.add(new ItemStack(it, count));
		}
		return out;
	}

	public static String rewardLine1(Quest q) {
		String pts = q.rewardPoints > 0 ? ", +" + q.rewardPoints + " stat pts" : "";
		return q.rewardExp + " EXP" + pts + ", skill " + Math.round(q.skillChance * 100) + "%";
	}

	public static String rewardLine2(Quest q) {
		StringBuilder sb = new StringBuilder("Items: ");
		boolean first = true;
		for (ItemStack s : itemStacks(q)) {
			if (!first) sb.append(", ");
			sb.append(s.getCount()).append("x ").append(s.getHoverName().getString());
			first = false;
		}
		return sb.toString();
	}

	// ================================================================ items from OTHER MODS as rewards

	private static List<List<Reward>> modded;

	private static final String[] JUNK = {"spawn_egg", "debug", "creative", "command", "barrier", "structure", "test_", "_test", "admin",
		"cheat", "placeholder", "missing", "unused", "wip", "dev_", "_dev", "unobtainable"};

	/** -1 = not a sensible reward, otherwise the natural tier 0..4 of an item that comes from a mod. */
	private static int moddedTier(Item it, String path) {
		for (String j : JUNK) if (path.contains(j)) return -1;
		ItemStack st = new ItemStack(it, 1);
		int max = st.getMaxDamage();
		if (max > 0 && LootLogic.categoryOf(it) >= 0) return max < 300 ? 1 : max < 900 ? 2 : max < 2000 ? 3 : 4; // tools, weapons, armor
		if (max > 0) return 1;

		int tier = -1;
		int rar = st.getRarity().ordinal(); // COMMON, UNCOMMON, RARE, EPIC
		if (rar >= 1) tier = Math.min(4, rar);

		int byName = nameTier(path);
		if (byName >= 0) tier = Math.max(tier, byName);
		if (tier < 0 && st.has(DataComponents.FOOD)) tier = 1;
		if (tier < 0 && it instanceof BlockItem) return -1; // plain building blocks are not rewards
		return tier;
	}

	private static boolean has(String path, String... words) {
		for (String w : words) if (path.contains(w)) return true;
		return false;
	}

	/** Guess how valuable a modded item is from its name (ingot, gem, dust, netherite ...). */
	private static int nameTier(String path) {
		if (has(path, "netherite", "mythril", "adamantite", "orichalcum", "unobtainium", "legend", "relic", "artifact", "nether_star", "dragon", "totem", "eternal", "divine")) return 3;
		if (has(path, "diamond", "emerald", "gem", "crystal", "ruby", "sapphire", "topaz", "onyx", "jade", "opal", "platinum", "titanium", "cobalt", "osmium", "uranium", "tungsten")) return 2;
		if (has(path, "ingot", "pearl", "essence", "alloy") || path.endsWith("_dust") || path.endsWith("_shard") || path.endsWith("_gear") || path.endsWith("_plate")) return 1;
		if (has(path, "nugget", "scrap", "fragment") || path.startsWith("raw_") || path.endsWith("_chunk")) return 0;
		return -1;
	}

	private static synchronized void scanModded() {
		if (modded != null) return;
		modded = new ArrayList<>();
		for (int i = 0; i < 5; i++) modded.add(new ArrayList<>());
		try {
			for (Item it : BuiltInRegistries.ITEM) {
				if (it == Items.AIR) continue;
				String id = ItemIds.idOf(it);
				if (id.startsWith("minecraft:") || id.startsWith("solosystem:")) continue;
				int natural = moddedTier(it, id.substring(id.indexOf(':') + 1));
				if (natural < 0) continue;
				int stack = Math.max(1, new ItemStack(it, 1).getMaxStackSize());
				for (int t = natural; t <= Math.min(4, natural + 1); t++) {
					int[][] range = {{4, 10}, {3, 8}, {2, 5}, {1, 3}, {1, 2}};
					int lo = stack == 1 ? 1 : Math.min(stack, range[t][0]), hi = stack == 1 ? 1 : Math.min(stack, range[t][1]);
					modded.get(t).add(new Reward(id, lo, hi));
				}
			}
		} catch (Exception ignored) {}
	}

	/** Items from other mods that can be a reward on this tier: found automatically + the ones listed in the config. */
	private static List<Reward> modPool(int tier) {
		List<Reward> pool = new ArrayList<>();
		SoloConfig.Data cfg = SoloConfig.get();
		if (cfg.autoModItems) {
			scanModded();
			pool.addAll(modded.get(tier));
		}
		String[] extra = cfg.extraQuestRewards[tier];
		if (extra != null) {
			for (String e : extra) {
				if (e == null) continue;
				String[] parts = e.split(",");
				if (ItemIds.item(parts[0]) == null) continue; // that mod is not installed
				int min = 1, max = 1;
				try {
					if (parts.length > 1) min = Math.max(1, Integer.parseInt(parts[1].trim()));
					max = parts.length > 2 ? Math.max(min, Integer.parseInt(parts[2].trim())) : min;
				} catch (NumberFormatException ignored) {}
				pool.add(new Reward(parts[0].trim(), min, max));
			}
		}
		return pool;
	}

	private static List<String> rollItems(int tier, ThreadLocalRandom rnd) {
		List<Reward> vanilla = new ArrayList<>(List.of(BUILTIN[tier]));
		Collections.shuffle(vanilla);
		List<Reward> mod = modPool(tier);
		int n = tier <= 1 ? 2 : 3;
		List<String> codes = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		// most quests get at least one item from another mod when any are installed; high tiers sometimes two
		if (!mod.isEmpty() && rnd.nextDouble() < 0.75) {
			int slots = (tier >= 3 && mod.size() > 1 && rnd.nextDouble() < 0.4) ? 2 : 1;
			for (int k = 0; k < slots; k++) {
				Reward rw = mod.get(rnd.nextInt(mod.size()));
				if (seen.add(rw.id())) codes.add(rw.id() + "|" + (rw.min() + rnd.nextInt(Math.max(1, rw.max() - rw.min() + 1))));
			}
		}
		for (Reward rw : vanilla) {
			if (codes.size() >= n) break;
			if (seen.add(rw.id())) codes.add(rw.id() + "|" + (rw.min() + rnd.nextInt(Math.max(1, rw.max() - rw.min() + 1))));
		}
		return codes;
	}

	// ================================================================ generation

	public static long gameTime(ServerPlayer p) { return p.level().getServer().overworld().getGameTime(); }

	public static void ensure(ServerPlayer p, PlayerData d) {
		long now = gameTime(p);
		if (d.quests.isEmpty() || now >= d.questExpiry || d.questExpiry - now > DAY_TICKS) generate(p, d, now);
	}

	private static int rollTier(ThreadLocalRandom rnd) {
		int total = 0;
		for (int w : TIER_WEIGHT) total += w;
		int r = rnd.nextInt(total);
		for (int i = 0; i < TIER_WEIGHT.length; i++) {
			r -= TIER_WEIGHT[i];
			if (r < 0) return i;
		}
		return 0;
	}

	/** How many to do: about BUDGET[tier] seconds of work, scaled by level and the difficulty setting, kept inside the quest's own limits. */
	private static int targetFor(QuestCatalog.Def def, int tier, double scale, ThreadLocalRandom rnd) {
		double units = BUDGET[tier] / Math.max(0.05, def.sec());
		int target = (int) Math.round(units * scale * (0.85 + rnd.nextDouble() * 0.30));
		target = Math.max(def.min(), Math.min(def.max(), target));
		if (target >= 1000) target = Math.round(target / 50f) * 50;
		else if (target >= 200) target = Math.round(target / 10f) * 10;
		else if (target >= 50) target = Math.round(target / 5f) * 5;
		return Math.max(def.min(), Math.min(def.max(), target));
	}

	private static QuestCatalog.Def pick(List<QuestCatalog.Def> all, int tier, Set<String> families, Set<String> picked,
										  List<String> yesterday, ThreadLocalRandom rnd) {
		for (int relax = 0; relax < 3; relax++) {
			List<QuestCatalog.Def> cand = new ArrayList<>();
			for (QuestCatalog.Def d : all) {
				if (d.minTier() > tier || picked.contains(d.id())) continue;
				if (relax < 2 && yesterday.contains(d.id())) continue;   // not the same quest two days in a row
				if (relax < 1 && families.contains(d.family())) continue; // different kinds of work on the same day
				cand.add(d);
			}
			if (!cand.isEmpty()) return cand.get(rnd.nextInt(cand.size()));
		}
		return null;
	}

	private static void generate(ServerPlayer p, PlayerData d, long now) {
		SoloConfig.Data cfg = SoloConfig.get();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		boolean reset = !d.quests.isEmpty();
		for (Quest old : d.quests) if (old.done && !old.claimed) grant(p, d, old, true); // expired before claiming: claim automatically
		d.quests.clear();
		d.questExpiry = now + DAY_TICKS;

		List<QuestCatalog.Def> all = QuestCatalog.available();
		int count = Math.max(1, Math.min(5, cfg.questsPerDay));
		double scale = (1.0 + Math.min(0.5, d.level * 0.01)) * cfg.questDifficulty; // a little harder as you level
		Set<String> families = new HashSet<>(), picked = new HashSet<>();
		int[] tierUse = new int[5];
		List<String> used = new ArrayList<>();

		for (int i = 0; i < count; i++) {
			int tier = rollTier(rnd);
			for (int tries = 0; tries < 4 && tierUse[tier] >= 1 + (i / 2); tries++) tier = rollTier(rnd); // spread the tiers
			tierUse[tier]++;
			QuestCatalog.Def def = pick(all, tier, families, picked, d.lastQuestTypes, rnd);
			if (def == null) break;
			families.add(def.family());
			picked.add(def.id());
			used.add(def.id());

			int target = targetFor(def, tier, scale, rnd);
			double expScale = (1.0 + 0.05 * d.level) * cfg.questExpMultiplier;
			int exp = (int) Math.round(EXP_BASE[tier] * (0.8 + rnd.nextDouble() * 0.5) * expScale);
			int pts = PTS_MIN[tier] + rnd.nextInt(PTS_MAX[tier] - PTS_MIN[tier] + 1);
			double sk = SK_MIN[tier] + rnd.nextDouble() * (SK_MAX[tier] - SK_MIN[tier]);
			sk = Math.round(sk * 100) / 100.0;

			Quest q = new Quest(def.id(), tier, target, 0, false, exp, pts, sk, rollItems(tier, rnd), false,
				def.label(), def.kind(), def.filter(), def.scale(), 0);
			q.base = QuestTracker.current(p, q); // progress counts from now
			d.quests.add(q);
		}
		d.lastQuestTypes.clear();
		d.lastQuestTypes.addAll(used);
		SystemLogic.msg(p, reset ? "A new day begins. Daily quests have reset!" : "[Daily Quest] has arrived!", ChatFormatting.YELLOW);
		SystemLogic.sync(p);
	}

	// ================================================================ completion + claiming

	/** Quest finished: the reward waits until the player presses CLAIM in the Daily Quest page. */
	public static void finish(ServerPlayer p, PlayerData d, Quest q) {
		q.done = true;
		q.progress = q.target;
		int t = Math.max(0, Math.min(4, q.tier));
		SystemLogic.msg(p, "QUEST CLEAR [" + tierRarity(t).name() + "]: " + q.title() + " x" + q.target, tierRarity(t).color);
		SystemLogic.msg(p, "Open the System menu (Y) > QUESTS and press CLAIM to collect your reward.", ChatFormatting.GREEN);
		SystemLogic.sync(p);
	}

	/** Player pressed CLAIM on quest number {@code index}. */
	public static void claim(ServerPlayer p, int index) {
		PlayerData d = SystemLogic.data(p);
		if (index < 0 || index >= d.quests.size()) return;
		Quest q = d.quests.get(index);
		if (!q.done || q.claimed) return;
		grant(p, d, q, false);
		SystemLogic.sync(p);
	}

	/** Give the reward of a finished quest: EXP, stat points, a chance of a skill, and the items. */
	private static void grant(ServerPlayer p, PlayerData d, Quest q, boolean auto) {
		q.claimed = true;
		int t = Math.max(0, Math.min(4, q.tier));
		SystemLogic.msg(p, (auto ? "Daily quests reset - reward auto-claimed: " : "Reward claimed: ") + q.title() + " [" + tierRarity(t).name() + "]", ChatFormatting.GREEN);
		SystemLogic.msg(p, rewardLine1(q), ChatFormatting.GREEN);

		d.points += q.rewardPoints;
		SystemLogic.addExpRaw(p, q.rewardExp);
		SystemLogic.rollSkill(p, q.skillChance, SK_BOOST[t]);
		for (ItemStack s : itemStacks(q)) {
			int cat = LootLogic.categoryOf(s.getItem());
			if (cat >= 0 && s.getMaxDamage() > 0) LootLogic.enchant(p.level(), s, cat, t, ThreadLocalRandom.current()); // gear rewards come enchanted
			give(p, s);
		}
		SystemLogic.msg(p, rewardLine2(q), ChatFormatting.GREEN);
	}

	/** Spawn an item at the player's feet (picked up automatically). */
	public static void give(ServerPlayer p, ItemStack stack) {
		p.level().addFreshEntity(new ItemEntity(p.level(), p.getX(), p.getY() + 0.3, p.getZ(), stack));
	}
}
