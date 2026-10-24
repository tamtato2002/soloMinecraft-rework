package com.example.solosystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

/**
 * Daily quests: random quests (COMMON..LEGENDARY). They reset every 24000 game ticks
 * (= 1 Minecraft day, ~20 real minutes; sleeping does not skip it). Rewards are rolled randomly per quest
 * and can include items from other mods (see extraQuestRewards in config/solosystem.json).
 */
public final class QuestLogic {
	public static final long DAY_TICKS = 24000L;

	private static final double[] MULT = {1, 2, 3.5, 6, 10};
	private static final int[] TIER_WEIGHT = {40, 28, 18, 10, 4};

	private static final int[] EXP_BASE = {80, 200, 500, 1200, 3000};
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

	// ---------------------------------------------------------------- reward description (server + client)

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

	// ---------------------------------------------------------------- generation

	public static long gameTime(ServerPlayer p) { return p.level().getServer().overworld().getGameTime(); }

	public static void ensure(ServerPlayer p, PlayerData d) {
		long now = gameTime(p);
		if (d.quests.isEmpty() || now >= d.questExpiry || d.questExpiry - now > DAY_TICKS) generate(p, d, now);
	}

	private static List<List<Reward>> modded;

	/** Find items added by other mods and sort them into tiers by durability / item rarity. Done once, lazily. */
	private static void scanModded() {
		if (modded != null) return;
		modded = new ArrayList<>();
		for (int i = 0; i < 5; i++) modded.add(new ArrayList<>());
		try {
			for (Item it : BuiltInRegistries.ITEM) {
				if (it == Items.AIR) continue;
				String id = ItemIds.idOf(it);
				if (id.startsWith("minecraft:") || id.startsWith("solosystem:")) continue;
				ItemStack st = new ItemStack(it, 1);
				int max = st.getMaxDamage();
				int cat = LootLogic.categoryOf(it);
				int natural;
				if (cat >= 0 && max > 0) {
					natural = max < 300 ? 1 : max < 900 ? 2 : max < 2000 ? 3 : 4; // modded weapons / armor / tools
				} else {
					int rar = st.getRarity().ordinal(); // COMMON, UNCOMMON, RARE, EPIC
					if (rar <= 0) continue;               // skip plain items (blocks, junk)
					natural = rar == 1 ? 1 : rar == 2 ? 2 : 3;
				}
				for (int t = natural; t <= Math.min(4, natural + 1); t++)
					modded.get(t).add(new Reward(id, 1, (cat >= 0 && max > 0) ? 1 : 2));
			}
		} catch (Exception ignored) {}
	}

	private static List<Reward> pool(int tier, ThreadLocalRandom rnd) {
		List<Reward> pool = new ArrayList<>(List.of(BUILTIN[tier]));
		if (SoloConfig.get().autoModItems) {
			scanModded();
			List<Reward> m = modded.get(tier);
			for (int i = 0; i < 2 && !m.isEmpty(); i++) pool.add(m.get(rnd.nextInt(m.size())));
		}
		String[] extra = SoloConfig.get().extraQuestRewards[tier];
		if (extra != null) {
			for (String e : extra) {
				if (e == null) continue;
				String[] parts = e.split(",");
				if (ItemIds.item(parts[0]) == null) continue; // other mod not installed -> skip
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

	private static void generate(ServerPlayer p, PlayerData d, long now) {
		SoloConfig.Data cfg = SoloConfig.get();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		boolean reset = !d.quests.isEmpty();
		d.quests.clear();
		d.questExpiry = now + DAY_TICKS;

		List<QuestType> types = new ArrayList<>(List.of(QuestType.values()));
		Collections.shuffle(types);
		int count = Math.max(1, Math.min(types.size(), cfg.questsPerDay));
		// variety: avoid the types used yesterday when there are enough others
		List<QuestType> fresh = new ArrayList<>();
		for (QuestType t : types) if (!d.lastQuestTypes.contains(t.id)) fresh.add(t);
		if (fresh.size() >= count) types = fresh;
		List<String> used = new ArrayList<>();
		int[] tierUse = new int[5];
		for (int i = 0; i < count; i++) {
			QuestType t = types.get(i);
			used.add(t.id);
			int tier = rollTier(rnd);
			for (int tries = 0; tries < 4 && tierUse[tier] >= 1 + (i / 2); tries++) tier = rollTier(rnd); // spread the tiers
			tierUse[tier]++;
			double jitter = 0.85 + rnd.nextDouble() * 0.35;
			int target = Math.max(1, (int) Math.round(t.base * MULT[tier] * jitter));

			// random reward, rolled now and fixed for this quest
			int exp = (int) Math.round(EXP_BASE[tier] * (0.7 + rnd.nextDouble() * 0.8) * cfg.questExpMultiplier);
			int pts = PTS_MIN[tier] + rnd.nextInt(PTS_MAX[tier] - PTS_MIN[tier] + 1);
			double sk = SK_MIN[tier] + rnd.nextDouble() * (SK_MAX[tier] - SK_MIN[tier]);
			sk = Math.round(sk * 100) / 100.0;

			List<Reward> pool = pool(tier, rnd);
			Collections.shuffle(pool);
			int n = Math.min(pool.size(), tier <= 1 ? 2 : 3);
			List<String> codes = new ArrayList<>();
			for (int k = 0; k < n; k++) {
				Reward rw = pool.get(k);
				int amount = rw.min() + rnd.nextInt(Math.max(1, rw.max() - rw.min() + 1));
				codes.add(rw.id() + "|" + amount);
			}
			d.quests.add(new Quest(t.id, tier, target, 0, false, exp, pts, sk, codes));
		}
		d.lastQuestTypes.clear();
		d.lastQuestTypes.addAll(used);
		SystemLogic.msg(p, reset ? "A new day begins. Daily quests have reset!" : "[Daily Quest] has arrived!", ChatFormatting.YELLOW);
		SystemLogic.sync(p);
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

	// ---------------------------------------------------------------- progress + completion

	public static void progress(ServerPlayer p, QuestType type, double amount) {
		if (amount <= 0) return;
		PlayerData d = SystemLogic.data(p);
		boolean changed = false;
		for (Quest q : d.quests) {
			if (q.done || !q.type.equals(type.id)) continue;
			q.progress = Math.min(q.target, q.progress + amount);
			changed = true;
			if (q.progress >= q.target) complete(p, d, q);
		}
		if (changed) SystemLogic.markDirty(p);
	}

	private static void complete(ServerPlayer p, PlayerData d, Quest q) {
		q.done = true;
		q.progress = q.target;
		int t = Math.max(0, Math.min(4, q.tier));
		QuestType type = QuestType.byId(q.type);
		String label = type == null ? q.type : type.label;
		SystemLogic.msg(p, "QUEST CLEAR [" + tierRarity(t).name() + "]: " + label + " x" + q.target, tierRarity(t).color);
		SystemLogic.msg(p, "Reward: " + rewardLine1(q), ChatFormatting.GREEN);

		d.points += q.rewardPoints;
		SystemLogic.addExpRaw(p, q.rewardExp);
		SystemLogic.rollSkill(p, q.skillChance, SK_BOOST[t]);
		for (ItemStack s : itemStacks(q)) {
			int cat = LootLogic.categoryOf(s.getItem());
			if (cat >= 0 && s.getMaxDamage() > 0) LootLogic.enchant(p.level(), s, cat, t, ThreadLocalRandom.current()); // gear rewards come enchanted
			give(p, s);
		}
		SystemLogic.msg(p, rewardLine2(q), ChatFormatting.GREEN);
		SystemLogic.sync(p);
	}

	/** Spawn an item at the player's feet (picked up automatically). */
	public static void give(ServerPlayer p, ItemStack stack) {
		p.level().addFreshEntity(new ItemEntity(p.level(), p.getX(), p.getY() + 0.3, p.getZ(), stack));
	}
}
