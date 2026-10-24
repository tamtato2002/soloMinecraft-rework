package com.example.solosystem;

import com.example.solosystem.net.PopupPayload;
import java.util.HashMap;
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

	private ResistLogic() {}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (REENTRY.get() || amount <= 0) return true;
			SoloConfig.Data cfg = SoloConfig.get();
			ThreadLocalRandom rnd = ThreadLocalRandom.current();
			double mult = 1.0;

			// ---- attacker side: critical hit (Sense)
			if (cfg.enableCrit && source.getEntity() instanceof ServerPlayer atk && entity != atk) {
				int sense = SystemLogic.data(atk).stat(Stat.SENSE);
				if (sense > 0 && rnd.nextDouble() < Math.min(0.6, 0.004 * sense)) {
					mult *= Math.min(3.0, 1.5 + 0.01 * sense);
					ServerPlayNetworking.send(atk, new PopupPayload("CRIT!", 1));
				}
			}

			// ---- victim side: dodge (Agility / Sense) and learned resistance
			if (entity instanceof ServerPlayer sp) {
				PlayerData d = SystemLogic.data(sp);

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

			if (Math.abs(mult - 1.0) < 0.001) return true;

			// re-apply the damage with the modified amount (guarded against recursion)
			REENTRY.set(true);
			try {
				entity.hurtServer((ServerLevel) entity.level(), source, (float) (amount * mult));
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
