package com.example.solosystem;

import com.example.solosystem.net.SyncPayload;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

public final class SystemLogic {
	private static final Set<UUID> DIRTY = new HashSet<>();
	private static final Map<UUID, long[]> LAST = new HashMap<>();
	private static final Map<UUID, Integer> LAST_FOOD = new HashMap<>();
	private static final Map<UUID, Integer> LAST_XP = new HashMap<>();

	private SystemLogic() {}

	public static PlayerData data(ServerPlayer p) { return p.getAttachedOrCreate(SoloSystemMod.DATA); }
	public static void markDirty(ServerPlayer p) { DIRTY.add(p.getUUID()); }
	public static void sync(ServerPlayer p) { ServerPlayNetworking.send(p, new SyncPayload(data(p).copy())); }

	public static void onJoin(ServerPlayer p) {
		LAST.put(p.getUUID(), snapshot(p));
		applyAll(p, data(p));
		QuestLogic.ensure(p, data(p));
		sync(p);
		msg(p, "Player detected. Press Y to open the System window.", ChatFormatting.AQUA);
	}

	public static void onLeave(ServerPlayer p) {
		LAST.remove(p.getUUID());
		LAST_FOOD.remove(p.getUUID());
		LAST_XP.remove(p.getUUID());
		DIRTY.remove(p.getUUID());
	}

	// ---------------------------------------------------------------- EXP / levels

	/** EXP from game actions: scaled by config and your multipliers. */
	public static void addExp(ServerPlayer p, double base) {
		if (base <= 0) return;
		PlayerData d = data(p);
		double mult = SoloConfig.get().expMultiplier
			* (1.0 + 0.01 * d.stat(Stat.INTELLIGENCE) + 0.10 * d.rank(PassiveSkill.MONARCH_DOMAIN) + 0.05 * d.rank(PassiveSkill.SAGES_MIND));
		grant(p, d, base * mult);
	}

	/** EXP with no multipliers (quest rewards). */
	public static void addExpRaw(ServerPlayer p, double amount) {
		if (amount <= 0) return;
		grant(p, data(p), amount);
	}

	private static void grant(ServerPlayer p, PlayerData d, double amount) {
		d.exp += amount;
		boolean leveled = false;
		while (d.exp >= d.expToNext()) {
			d.exp -= d.expToNext();
			d.level++;
			d.points += 3;
			leveled = true;
			msg(p, "LEVEL UP!  Lv." + d.level + "  (+3 stat points)", ChatFormatting.GOLD);
		}
		if (leveled) {
			applyAll(p, d);
			sync(p);
		} else {
			markDirty(p);
		}
	}

	public static void allocate(ServerPlayer p, String statId, int amount) {
		boolean test = SoloConfig.get().testMode;
		PlayerData d = data(p);

		if ("__reset".equals(statId)) {            // test mode: wipe all stat points
			if (!test) return;
			d.stats.clear();
			applyAll(p, d);
			sync(p);
			msg(p, "[TEST MODE] All stats reset.", ChatFormatting.RED);
			return;
		}

		Stat s = Stat.byId(statId);
		if (s == null) return;

		if (amount < 0) {                           // test mode: take points back out
			if (!test) return;
			int take = Math.min(Math.min(100, -amount), d.stat(s));
			if (take <= 0) return;
			d.stats.merge(s.id(), -take, Integer::sum);
			applyAll(p, d);
			sync(p);
			return;
		}

		if (!test && d.points <= 0) return;
		int n = test ? Math.max(1, Math.min(amount, 100)) : Math.max(1, Math.min(amount, Math.min(100, d.points)));
		int before = d.stat(s);
		if (!test) d.points -= n;                   // test mode = unlimited points
		d.stats.merge(s.id(), n, Integer::sum);
		int after = d.stat(s);
		for (Ability a : s.abilities) {
			if (before < a.need() && after >= a.need())
				msg(p, "Ability unlocked [" + s.label + "]: " + a.name(), ChatFormatting.GOLD);
		}
		applyAll(p, d);
		sync(p);
	}

	// ---------------------------------------------------------------- skills

	/** Roll for a passive skill. rarityBoost shifts odds toward rarer skills. */
	public static void rollSkill(ServerPlayer p, double chance, double rarityBoost) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		if (rnd.nextDouble() >= Math.min(1.0, chance * SoloConfig.get().skillDropMultiplier * (1.0 + 0.5 * data(p).rank(PassiveSkill.SOVEREIGNS_FORTUNE)))) return;
		PlayerData d = data(p);

		SkillRarity rarity = rollRarity(rnd, rarityBoost);
		List<PassiveSkill> pool = new ArrayList<>();
		for (PassiveSkill s : PassiveSkill.values())
			if (s.rarity == rarity && d.rank(s) < s.maxRank) pool.add(s);
		if (pool.isEmpty()) {
			for (PassiveSkill s : PassiveSkill.values())
				if (d.rank(s) < s.maxRank) pool.add(s);
		}
		if (pool.isEmpty()) return;

		PassiveSkill s = pool.get(rnd.nextInt(pool.size()));
		int rank = d.rank(s) + 1;
		d.skills.put(s.id(), rank);
		p.sendSystemMessage(Component.literal("[SYSTEM] ").withStyle(ChatFormatting.AQUA)
			.append(Component.literal("Passive skill acquired: " + s.displayName + " (Rank " + rank + "/" + s.maxRank + ") ["
				+ s.rarity.name() + "]").withStyle(s.rarity.color)));
		applyAll(p, d);
		sync(p);
	}

	private static SkillRarity rollRarity(ThreadLocalRandom rnd, double boost) {
		SkillRarity[] all = SkillRarity.values();
		double[] w = new double[all.length];
		double total = 0;
		for (int i = 0; i < all.length; i++) {
			w[i] = all[i].weight * (1.0 + boost * i);
			total += w[i];
		}
		double r = rnd.nextDouble() * total;
		for (int i = 0; i < all.length; i++) {
			r -= w[i];
			if (r <= 0) return all[i];
		}
		return SkillRarity.COMMON;
	}

	// ---------------------------------------------------------------- tick

	public static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % 5 == 0) Dominion.tick(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!p.isAlive()) continue;
			PlayerData d = data(p);
			orbs(p);
			if (t % 4 == 0) magnet(p, d);
			if (t % 10 == 0) hungerEfficiency(p, d);
			if (t % 40 == 0) regen(p, d);
			if (t % 80 == 0) QolSkills.caretaker(p, d);
			if (t % 100 == 0) QolSkills.verdant(p, d);
			if (t % 10 == 0) secondWind(p, d, t);
			if (t % 20 == 0) {
				applyAll(p, d);
				pollStats(p);
				QuestLogic.ensure(p, d);
			}
			if (t % 200 == 0) stomach(p, d);
			if (t % 2 == 0 && DIRTY.remove(p.getUUID())) sync(p);
		}
	}

	private static long stat(ServerPlayer p, Identifier id) { return p.getStats().getValue(Stats.CUSTOM.get(id)); }

	private static long totalCm(ServerPlayer p) {
		return stat(p, Stats.WALK_ONE_CM) + stat(p, Stats.SPRINT_ONE_CM) + stat(p, Stats.SWIM_ONE_CM);
	}

	private static long[] snapshot(ServerPlayer p) {
		return new long[]{totalCm(p), stat(p, Stats.FISH_CAUGHT), stat(p, Stats.ANIMALS_BRED),
			stat(p, Stats.TRADED_WITH_VILLAGER), stat(p, Stats.ENCHANT_ITEM)};
	}

	/** Actions that have no event hook: read vanilla statistics and award EXP for the change. */
	private static void pollStats(ServerPlayer p) {
		long[] cur = snapshot(p);
		long[] last = LAST.put(p.getUUID(), cur);
		if (last == null) return;

		long dCm = cur[0] - last[0];
		if (dCm > 0) {
			double blocks = dCm / 100.0;
			addExp(p, blocks * 0.02); // 0.02 EXP per block
			QuestLogic.progress(p, QuestType.WALK, blocks);
			rollSkill(p, Math.min(0.5, blocks * 0.0001), 0); // exploring can teach a skill
		}
		long fish = cur[1] - last[1];
		if (fish > 0) {
			addExp(p, fish * 12.0);
			QuestLogic.progress(p, QuestType.FISH, fish);
			QolSkills.onFish(p, fish);
			rollSkill(p, Math.min(1.0, 0.04 * fish), 0.5);   // fishing
		}
		long bred = cur[2] - last[2];
		if (bred > 0) {
			addExp(p, bred * 8.0);
			QuestLogic.progress(p, QuestType.BREED, bred);
			QolSkills.onBreed(p, bred);
			rollSkill(p, Math.min(1.0, 0.03 * bred), 0);    // farming / ranching
		}
		long trade = cur[3] - last[3];
		if (trade > 0) {
			addExp(p, trade * 10.0);
			QuestLogic.progress(p, QuestType.TRADE, trade);
			rollSkill(p, Math.min(1.0, 0.04 * trade), 0.5);
		}
		long ench = cur[4] - last[4];
		if (ench > 0) {
			addExp(p, ench * 20.0);
			QuestLogic.progress(p, QuestType.ENCHANT, ench);
			rollSkill(p, Math.min(1.0, 0.1 * ench), 1.0);
		}
	}

	/** Picking up Experience Orbs (vanilla or modded) also gives System EXP. */
	private static void orbs(ServerPlayer p) {
		int cur = p.totalExperience;
		Integer last = LAST_XP.put(p.getUUID(), cur);
		if (last != null && cur > last) addExp(p, (cur - last) * SoloConfig.get().orbExpFactor);
	}

	private static final Map<UUID, Integer> SECOND_WIND_READY = new HashMap<>();

	/** Epic skill: when health drops under 25% you get a burst of regeneration (cooldown). */
	private static void secondWind(ServerPlayer p, PlayerData d, int tick) {
		int rank = d.rank(PassiveSkill.SECOND_WIND);
		if (rank <= 0 || p.getHealth() >= p.getMaxHealth() * 0.25f) return;
		Integer ready = SECOND_WIND_READY.get(p.getUUID());
		if (ready != null && tick < ready) return;
		SECOND_WIND_READY.put(p.getUUID(), tick + (60 - 15 * rank) * 20);
		p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1 + rank, false, true, true));
		msg(p, "SECOND WIND!", ChatFormatting.GREEN);
	}

	private static void regen(ServerPlayer p, PlayerData d) {
		int end = d.stat(Stat.ENDURANCE);
		if (end > 0 && p.getHealth() < p.getMaxHealth()) p.heal((float) (0.02 * end));
	}

	/** Endurance ability: hunger that just dropped may be refunded. */
	private static void hungerEfficiency(ServerPlayer p, PlayerData d) {
		int food = p.getFoodData().getFoodLevel();
		Integer last = LAST_FOOD.put(p.getUUID(), food);
		if (last == null || food >= last || !d.has(Stat.ENDURANCE, 1)) return;
		double chance = Math.min(0.7, 0.1 + 0.01 * (d.stat(Stat.ENDURANCE) - 15));
		if (ThreadLocalRandom.current().nextDouble() < chance) {
			int restored = Math.min(20, last);
			p.getFoodData().setFoodLevel(restored);
			LAST_FOOD.put(p.getUUID(), restored);
		}
	}

	private static void stomach(ServerPlayer p, PlayerData d) {
		int rank = d.rank(PassiveSkill.IRON_STOMACH);
		if (rank <= 0) return;
		int food = p.getFoodData().getFoodLevel();
		if (food < 20) p.getFoodData().setFoodLevel(Math.min(20, food + rank));
	}

	private static void magnet(ServerPlayer p, PlayerData d) {
		int rank = d.rank(PassiveSkill.MAGNET);
		if (rank <= 0) return;
		double radius = rank == 1 ? 3 : rank == 2 ? 5 : 8;
		List<ItemEntity> items = p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(radius));
		for (ItemEntity it : items) {
			if (it.hasPickUpDelay()) continue;
			Vec3 dir = p.position().add(0, 0.5, 0).subtract(it.position());
			if (dir.lengthSqr() < 0.25) continue;
			it.setDeltaMovement(dir.normalize().scale(0.35));
		}
	}

	// ---------------------------------------------------------------- stats + passives -> attributes/effects

	public static void applyAll(ServerPlayer p, PlayerData d) {
		int str = d.stat(Stat.STRENGTH), agi = d.stat(Stat.AGILITY), end = d.stat(Stat.ENDURANCE);

		// ---- STRENGTH
		mod(p, Attributes.ATTACK_DAMAGE, "attack", 0.3 * str + 0.5 * d.rank(PassiveSkill.HUNTERS_INSTINCT) + 1.0 * d.rank(PassiveSkill.BERSERKER), Operation.ADD_VALUE);
		mod(p, Attributes.BLOCK_BREAK_SPEED, "break_speed", (d.has(Stat.STRENGTH, 1) ? Math.min(1.0, 0.15 + 0.01 * (str - 20)) : 0) + 0.25 * d.rank(PassiveSkill.WORLD_BREAKER), Operation.ADD_MULTIPLIED_BASE);
		mod(p, Attributes.KNOCKBACK_RESISTANCE, "kb_resist", d.has(Stat.STRENGTH, 2) ? Math.min(0.9, 0.1 + 0.01 * (str - 45)) : 0, Operation.ADD_VALUE);

		// ---- AGILITY
		mod(p, Attributes.MOVEMENT_SPEED, "speed", 0.004 * agi + 0.05 * d.rank(PassiveSkill.SWIFT_STEP) + 0.08 * d.rank(PassiveSkill.WINDWALKER) + 0.15 * d.rank(PassiveSkill.SHADOW_HASTE), Operation.ADD_MULTIPLIED_BASE);
		double jump = d.has(Stat.AGILITY, 1) ? Math.min(0.25, 0.03 + 0.0035 * (agi - 15)) : 0;
		mod(p, Attributes.JUMP_STRENGTH, "jump", jump + 0.08 * d.rank(PassiveSkill.SPRINGS), Operation.ADD_VALUE);
		double fall = d.has(Stat.AGILITY, 3) ? Math.min(0.8, 0.2 + 0.01 * (agi - 60)) : 0;
		mod(p, Attributes.FALL_DAMAGE_MULTIPLIER, "fall", -Math.min(0.95, fall + 0.25 * d.rank(PassiveSkill.FEATHER_FALL)), Operation.ADD_VALUE);
		mod(p, Attributes.WATER_MOVEMENT_EFFICIENCY, "swim", 0.006 * agi + 0.15 * d.rank(PassiveSkill.GILLS), Operation.ADD_VALUE);

		// ---- ENDURANCE
		mod(p, Attributes.MAX_HEALTH, "health", 1.0 * end + 2.0 * d.rank(PassiveSkill.HEARTY) + 4.0 * d.rank(PassiveSkill.TITAN_FRAME), Operation.ADD_VALUE);
		boolean nat = d.has(Stat.ENDURANCE, 2);
		mod(p, Attributes.ARMOR, "armor", 2.0 * d.rank(PassiveSkill.IRON_SKIN) + 5.0 * d.rank(PassiveSkill.MONARCH_AEGIS) + (nat ? 1.0 + 0.2 * (end - 35) : 0), Operation.ADD_VALUE);
		mod(p, Attributes.ARMOR_TOUGHNESS, "toughness", 1.0 * d.rank(PassiveSkill.THICK_SKIN) + 1.0 * d.rank(PassiveSkill.TITAN_FRAME)
			+ 2.0 * d.rank(PassiveSkill.MONARCH_AEGIS) + (nat ? 0.1 * (end - 35) : 0), Operation.ADD_VALUE);
		mod(p, Attributes.OXYGEN_BONUS, "oxygen", 3.0 * d.rank(PassiveSkill.GILLS) + (d.has(Stat.ENDURANCE, 3) ? 1.0 + 0.1 * (end - 60) : 0), Operation.ADD_VALUE);

		// ---- passive skills / misc
		mod(p, Attributes.LUCK, "luck", 1.0 * d.rank(PassiveSkill.LUCKY) + 0.5 * d.rank(PassiveSkill.ANGLERS_LUCK), Operation.ADD_VALUE);
		int sov = d.rank(PassiveSkill.SOVEREIGN_REACH);
		mod(p, Attributes.BLOCK_INTERACTION_RANGE, "block_reach", 2.0 * sov, Operation.ADD_VALUE);
		mod(p, Attributes.ENTITY_INTERACTION_RANGE, "entity_reach", 1.5 * sov + 0.75 * d.rank(PassiveSkill.SHEPHERDS_REACH), Operation.ADD_VALUE);
		mod(p, Attributes.STEP_HEIGHT, "step", 0.4 * d.rank(PassiveSkill.LIGHT_FEET), Operation.ADD_VALUE);
		mod(p, Attributes.MINING_EFFICIENCY, "mining", 1.0 * d.rank(PassiveSkill.MINERS_HANDS) + 0.5 * d.rank(PassiveSkill.QUICK_HANDS) + 2.0 * d.rank(PassiveSkill.WORLD_BREAKER), Operation.ADD_VALUE);
		mod(p, Attributes.ATTACK_SPEED, "atkspeed", 0.10 * d.rank(PassiveSkill.SHADOW_HASTE), Operation.ADD_MULTIPLIED_BASE);

		if (d.rank(PassiveSkill.CAT_EYES) > 0)
			p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));
		if (d.rank(PassiveSkill.HEAT_WARD) > 0)
			p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100, 0, true, false, false));
		int regen = d.rank(PassiveSkill.REGEN);
		if (regen > 0)
			p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, regen - 1, true, false, false));
	}

	private static void mod(ServerPlayer p, Holder<Attribute> attr, String key, double amount, Operation op) {
		AttributeInstance inst = p.getAttribute(attr);
		if (inst == null) return;
		Identifier id = SoloSystemMod.id(key);
		if (amount == 0) {
			if (inst.getModifier(id) != null) inst.removeModifier(id);
			return;
		}
		AttributeModifier cur = inst.getModifier(id);
		if (cur != null && cur.amount() == amount) return;
		inst.addOrReplacePermanentModifier(new AttributeModifier(id, amount, op));
	}

	public static void msg(ServerPlayer p, String text, ChatFormatting color) {
		p.sendSystemMessage(Component.literal("[SYSTEM] ").withStyle(ChatFormatting.AQUA)
			.append(Component.literal(text).withStyle(color)));
	}
}
