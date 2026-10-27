package com.example.solosystem;

import com.example.solosystem.net.PopupPayload;
import java.util.HashMap;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Combat hooks: Dodge (MISS!), Critical hits (CRIT!) and the learned Resistance system. */
public final class ResistLogic {
	private static final ThreadLocal<Boolean> REENTRY = ThreadLocal.withInitial(() -> false);
	private static final Map<String, Integer> LAST_COUNT_TICK = new HashMap<>();
	private record Hit(LivingEntity attacker, int tick) {}
	private static final Map<UUID, Hit> LAST_HIT = new HashMap<>();
	private static final Map<UUID, Long> UNDYING_READY = new HashMap<>();

	private ResistLogic() {}

	public static void register() {
		registerUndying();
		registerPlunder();
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (REENTRY.get() || amount <= 0) return true;
			SoloConfig.Data cfg = SoloConfig.get();
			ThreadLocalRandom rnd = ThreadLocalRandom.current();
			double mult = 1.0;

			// ---- attacker side: critical hit (Sense)
			if (cfg.enableCrit && source.getEntity() instanceof ServerPlayer atk && entity != atk) {
				PlayerData cd = SystemLogic.data(atk);
				int sense = cd.stat(Stat.SENSE);
				if (sense > 0 && cd.has(Stat.SENSE, 0) && rnd.nextDouble() < Math.min(0.6, 0.004 * sense)) {
					mult *= Math.min(3.0, 1.5 + 0.01 * sense);
					ServerPlayNetworking.send(atk, new PopupPayload("CRIT!", 1));
				}
			}

			// ---- attacker side: Armor Piercing (Strength 55). Player hits and arrows ignore armor and shields of any mob (modded too):
			// the damage is re-applied as magic damage that is still credited to the player.
			ServerPlayer pierceBy = null;
			if (source.getEntity() instanceof ServerPlayer pa && !(entity instanceof net.minecraft.world.entity.player.Player)
				&& SystemLogic.data(pa).has(Stat.STRENGTH, 3) && !source.is(DamageTypeTags.BYPASSES_ARMOR)
				&& (source.getDirectEntity() == pa || source.is(DamageTypeTags.IS_PROJECTILE))) {
				pierceBy = pa;
			}

			// ---- attacker side: Vampiric Touch (lifesteal) and Executioner
			if (source.getEntity() instanceof ServerPlayer atk2 && entity != atk2) {
				PlayerData ad = SystemLogic.data(atk2);
				int vamp = ad.rank(PassiveSkill.VAMPIRIC_TOUCH);
				if (vamp > 0) atk2.heal((float) (amount * 0.04 * vamp));
				int exe = ad.rank(PassiveSkill.EXECUTIONER);
				if (exe > 0 && entity.getHealth() < entity.getMaxHealth() * 0.3f) mult *= 1.0 + 0.15 * exe;
			}

			// ---- victim side: allies, Dominion, Mirror Ward, dodge (Agility / Sense), Stoneskin and learned resistance
			if (entity instanceof ServerPlayer sp) {
				PlayerData d = SystemLogic.data(sp);

				// a charmed monster never hurts its owner
				if (Dominion.isAllyOf(source.getEntity(), sp.getUUID())) return false;

				if (source.getEntity() instanceof LivingEntity att && att != sp) {
					LAST_HIT.put(sp.getUUID(), new Hit(att, sp.tickCount));
					if (Dominion.tryCharm(sp, d, att)) return false;
					if (tryReflectDamage(sp, d, att, source, amount)) return false;
				}
				int stone = d.rank(PassiveSkill.STONESKIN);
				if (stone > 0) mult *= Math.max(0.1, 1.0 - 0.05 * stone);

				if (cfg.enableDodge && source.getEntity() != null) {
					double dodge = dodgeChance(d, source.is(DamageTypeTags.IS_PROJECTILE));
					if (dodge > 0 && rnd.nextDouble() < dodge) {
						ServerPlayNetworking.send(sp, new PopupPayload("MISS!", 0));
						return false;
					}
				}

				if (cfg.enableResistance) {
					Resist r = classify(sp, source);
					if (r != null) {
						double pct = Resist.total(d, r); // resistance BEFORE this hit
						if (r != Resist.POISON && r != Resist.WITHER) learn(sp, r); // poison/wither are learned when the effect is applied
						if (pct >= 0.005) mult *= (1.0 - pct);
					}
				}
			}

			if (Math.abs(mult - 1.0) < 0.001 && pierceBy == null) return true;

			// re-apply the damage with the modified amount (guarded against recursion)
			REENTRY.set(true);
			try {
				ServerLevel lv = (ServerLevel) entity.level();
				DamageSource applied = pierceBy != null ? lv.damageSources().indirectMagic(pierceBy, pierceBy) : source;
				entity.hurtServer(lv, applied, (float) (amount * mult));
			} finally {
				REENTRY.set(false);
			}
			return false;
		});

		// Status effects: learn from them and shorten their duration
		ServerMobEffectEvents.ALLOW_ADD.register((inst, entity, ctx) -> {
			if (REENTRY.get() || !(entity instanceof ServerPlayer sp) || inst.isAmbient()) return true;
			if (!SoloConfig.get().enableResistance) return true;
			Resist r = forEffect(inst.getEffect());
			if (r == null) return true;

			PlayerData d = SystemLogic.data(sp);
			double pct = Resist.total(d, r);
			learn(sp, r);
			if (tryReflectEffect(sp, d, inst)) return false;
			if (r == Resist.POISON || r == Resist.WITHER || pct < 0.005) return true; // those are reduced via damage instead

			int dur = inst.getDuration();
			if (dur <= 0) return true;
			int shortened = (int) (dur * (1.0 - pct));
			if (shortened <= 0) return false;

			REENTRY.set(true);
			try {
				sp.addEffect(new MobEffectInstance(inst.getEffect(), shortened, inst.getAmplifier(),
					inst.isAmbient(), inst.isVisible(), inst.showIcon()));
			} finally {
				REENTRY.set(false);
			}
			return false;
		});
	}

	/** Strength 75 "Plunder": a mob killed by you drops ALL its gear and carried items, exactly as damaged as they were. */
	private static void registerPlunder() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof net.minecraft.world.entity.player.Player) return true;
			if (!(source.getEntity() instanceof ServerPlayer killer)) return true;
			if (!SystemLogic.data(killer).has(Stat.STRENGTH, 4)) return true;
			if (!(entity.level() instanceof ServerLevel level)) return true;

			for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
				net.minecraft.world.item.ItemStack st = entity.getItemBySlot(slot);
				if (st.isEmpty()) continue;
				spawnLoot(level, entity, st.copy());
				entity.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY); // so the normal death drops do not duplicate it
			}
			// carried items: any mob (vanilla or modded) that exposes getInventory() -> Container (piglins, villagers ...)
			try {
				Object inv = entity.getClass().getMethod("getInventory").invoke(entity);
				if (inv instanceof net.minecraft.world.Container box) {
					for (int i = 0; i < box.getContainerSize(); i++) {
						net.minecraft.world.item.ItemStack it = box.getItem(i);
						if (it.isEmpty()) continue;
						spawnLoot(level, entity, it.copy());
						box.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
					}
				}
			} catch (Exception ignored) {}
			return true;
		});
	}

	private static void spawnLoot(ServerLevel level, LivingEntity from, net.minecraft.world.item.ItemStack stack) {
		level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, from.getX(), from.getY() + 0.5, from.getZ(), stack));
	}

	private static void registerUndying() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer sp)) return true;
			PlayerData d = SystemLogic.data(sp);
			if (d.rank(PassiveSkill.UNDYING_WILL) <= 0) return true;
			long now = sp.level().getServer().overworld().getGameTime();
			Long ready = UNDYING_READY.get(sp.getUUID());
			if (ready != null && now < ready) return true;
			UNDYING_READY.put(sp.getUUID(), now + 12000L); // 10 minutes
			sp.setHealth(sp.getMaxHealth() * 0.3f);
			SystemLogic.msg(sp, "UNDYING WILL! You cheated death. (10 min cooldown)", ChatFormatting.GOLD);
			return false;
		});
	}

	// ---------------------------------------------------------------- Mirror Ward (Intelligence)

	private static double reflectChance(int in) { return Math.min(0.8, 0.2 + 0.01 * (in - 60)); }
	private static double reflectPower(int in) { return Math.min(2.0, 0.5 + 0.015 * (in - 60)); }

	/** Spells / magic / ranged attacks from a living attacker may be thrown back at it. */
	private static boolean tryReflectDamage(ServerPlayer sp, PlayerData d, LivingEntity att, DamageSource src, float amount) {
		if (!SoloConfig.get().enableReflect || !d.has(Stat.INTELLIGENCE, 3)) return false;
		boolean ability = src.getDirectEntity() != att || src.is(DamageTypes.MAGIC) || src.is(DamageTypes.INDIRECT_MAGIC) || src.is(DamageTypes.WITHER);
		if (!ability) return false; // plain melee is not an "ability"
		int in = d.stat(Stat.INTELLIGENCE);
		if (ThreadLocalRandom.current().nextDouble() >= reflectChance(in)) return false;

		REENTRY.set(true);
		try {
			ServerLevel level = (ServerLevel) att.level();
			att.hurtServer(level, level.damageSources().playerAttack(sp), (float) (amount * reflectPower(in)));
		} finally {
			REENTRY.set(false);
		}
		ServerPlayNetworking.send(sp, new PopupPayload("REFLECT!", 3));
		return true;
	}

	/** Harmful effects (poison, wither, slowness...) applied by the last attacker may bounce back at it. */
	private static boolean tryReflectEffect(ServerPlayer sp, PlayerData d, MobEffectInstance inst) {
		if (!SoloConfig.get().enableReflect || !d.has(Stat.INTELLIGENCE, 3)) return false;
		Hit hit = LAST_HIT.get(sp.getUUID());
		if (hit == null || sp.tickCount - hit.tick() > 40 || !hit.attacker().isAlive() || hit.attacker() == sp) return false;
		int dur = inst.getDuration();
		if (dur <= 0) return false;
		int in = d.stat(Stat.INTELLIGENCE);
		if (ThreadLocalRandom.current().nextDouble() >= reflectChance(in)) return false;

		REENTRY.set(true);
		try {
			hit.attacker().addEffect(new MobEffectInstance(inst.getEffect(), (int) (dur * reflectPower(in)),
				inst.getAmplifier() + (in >= 100 ? 1 : 0), false, true, true));
		} finally {
			REENTRY.set(false);
		}
		ServerPlayNetworking.send(sp, new PopupPayload("REFLECT!", 3));
		return true;
	}

	private static double dodgeChance(PlayerData d, boolean projectile) {
		double c = 0;
		if (d.has(Stat.AGILITY, 2)) c += Math.min(0.35, 0.05 + 0.005 * (d.stat(Stat.AGILITY) - 35));
		if (projectile && d.has(Stat.SENSE, 3)) c += Math.min(0.25, 0.05 + 0.005 * (d.stat(Stat.SENSE) - 60));
		return Math.min(0.6, c);
	}

	private static Resist classify(ServerPlayer p, DamageSource s) {
		if (s.is(DamageTypeTags.IS_EXPLOSION)) return Resist.EXPLOSION;
		if (s.is(DamageTypeTags.IS_LIGHTNING)) return Resist.LIGHTNING;
		if (s.is(DamageTypes.WITHER)) return Resist.WITHER;
		if (s.is(DamageTypes.MAGIC) && p.hasEffect(MobEffects.POISON)) return Resist.POISON;
		if (s.is(DamageTypeTags.IS_FIRE)) return Resist.FIRE;
		if (s.is(DamageTypeTags.IS_PROJECTILE)) return Resist.RANGED;
		if (s.is(DamageTypeTags.IS_FALL)) return Resist.FALL;
		if (s.is(DamageTypeTags.IS_DROWNING)) return Resist.DROWNING;
		if (s.is(DamageTypeTags.IS_FREEZING)) return Resist.COLD;
		if (s.getDirectEntity() instanceof LivingEntity) return Resist.MELEE;
		return null;
	}

	private static Resist forEffect(Holder<MobEffect> e) {
		if (e.equals(MobEffects.POISON)) return Resist.POISON;
		if (e.equals(MobEffects.WITHER)) return Resist.WITHER;
		if (e.equals(MobEffects.SLOWNESS)) return Resist.SLOWNESS;
		if (e.equals(MobEffects.WEAKNESS)) return Resist.WEAKNESS;
		if (e.equals(MobEffects.MINING_FATIGUE)) return Resist.MINING_FATIGUE;
		if (e.equals(MobEffects.HUNGER)) return Resist.HUNGER;
		if (e.equals(MobEffects.BLINDNESS)) return Resist.BLINDNESS;
		if (e.equals(MobEffects.NAUSEA)) return Resist.NAUSEA;
		if (e.equals(MobEffects.DARKNESS)) return Resist.DARKNESS;
		if (e.equals(MobEffects.LEVITATION)) return Resist.LEVITATION;
		return null;
	}

	/** Count one exposure (at most once per 10 ticks per type). First time = reveal the name in chat. */
	private static void learn(ServerPlayer sp, Resist r) {
		String key = sp.getUUID() + ":" + r.id;
		Integer last = LAST_COUNT_TICK.get(key);
		if (last != null && sp.tickCount - last < 10) return;
		LAST_COUNT_TICK.put(key, sp.tickCount);

		PlayerData d = SystemLogic.data(sp);
		int now = d.resist.merge(r.id, 1, Integer::sum);
		if (now == 1) {
			SystemLogic.msg(sp, "Resistance discovered: " + r.label + "  (it grows each time you are exposed)", ChatFormatting.LIGHT_PURPLE);
			SystemLogic.sync(sp);
		} else {
			SystemLogic.markDirty(sp);
		}
	}
}
