package com.example.solosystem;

import com.example.solosystem.net.PopupPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Intelligence ability "Dominion": an attacking monster may turn into your ally for a while
 * (duration scales with Intelligence). When the time is up the ally dies. Has a cooldown.
 */
public final class Dominion {
	private static final Map<Mob, Long> EXPIRY = new HashMap<>();
	private static final Map<Mob, UUID> OWNER = new HashMap<>();

	private Dominion() {}

	public static boolean isAllyOf(Entity e, UUID owner) {
		return e instanceof Mob m && owner.equals(OWNER.get(m));
	}

	public static boolean isCharmed(Entity e) { return e instanceof Mob m && EXPIRY.containsKey(m); }

	private static long now(ServerPlayer p) { return p.level().getServer().overworld().getGameTime(); }

	/** Chance (0..1) that an attacker gets charmed. */
	public static double chance(int in) { return Math.min(0.30, 0.05 + 0.004 * (in - 40)); }
	public static int durationTicks(int in) { return (int) (Math.min(120.0, 10.0 + 0.5 * (in - 40)) * 20); }
	public static int cooldownTicks(int in) { return (int) (Math.max(10.0, 40.0 - 0.2 * (in - 40)) * 20); }

	/** Called when a monster damages the player. Returns true if the monster was turned (the hit is cancelled). */
	public static boolean tryCharm(ServerPlayer sp, PlayerData d, Entity attacker) {
		if (!SoloConfig.get().enableDominion || !d.has(Stat.INTELLIGENCE, 2)) return false;
		if (!(attacker instanceof Mob mob) || !Mobs.isHostile(mob) || EXPIRY.containsKey(mob)) return false;
		if (mob instanceof EnderDragon || mob instanceof WitherBoss || Mobs.isBoss(mob)) return false;
		long now = now(sp);
		if (now < d.domReady) return false;
		int in = d.stat(Stat.INTELLIGENCE);
		if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= chance(in)) return false;

		int dur = durationTicks(in);
		EXPIRY.put(mob, now + dur);
		OWNER.put(mob, sp.getUUID());
		mob.setTarget(null);
		mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, dur, 0, true, false, false));
		mob.setCustomName(Component.literal("[Ally] ").withStyle(ChatFormatting.LIGHT_PURPLE).append(mob.getName()));
		mob.setCustomNameVisible(true);
		d.domReady = now + cooldownTicks(in);
		SystemLogic.markDirty(sp);
		SystemLogic.msg(sp, "DOMINION! The enemy fights for you for " + dur / 20 + "s, then it dies. Ally aura: " + buffFor(mob).label(), ChatFormatting.LIGHT_PURPLE);
		ServerPlayNetworking.send(sp, new PopupPayload("ALLY!", 2));
		return true;
	}

	private record Buff(Holder<MobEffect> effect, String label) {}

	/** Each kind of monster gives its owner a different aura while it fights for you. */
	private static Buff buffFor(Mob m) {
		String p = BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getPath();
		if (p.contains("zombie") || p.contains("husk") || p.contains("drowned") || p.contains("piglin") || p.contains("vindicator") || p.contains("pillager") || p.contains("ravager"))
			return new Buff(MobEffects.STRENGTH, "Strength");
		if (p.contains("skeleton") || p.contains("stray") || p.contains("bogged"))
			return new Buff(MobEffects.SPEED, "Speed");
		if (p.contains("creeper")) return new Buff(MobEffects.RESISTANCE, "Resistance");
		if (p.contains("spider")) return new Buff(MobEffects.JUMP_BOOST, "Jump Boost");
		if (p.contains("enderman") || p.contains("endermite") || p.contains("shulker")) return new Buff(MobEffects.HASTE, "Haste");
		if (p.contains("blaze") || p.contains("magma") || p.contains("ghast") || p.contains("strider")) return new Buff(MobEffects.FIRE_RESISTANCE, "Fire Resistance");
		if (p.contains("witch") || p.contains("evoker") || p.contains("vex")) return new Buff(MobEffects.REGENERATION, "Regeneration");
		if (p.contains("phantom")) return new Buff(MobEffects.SLOW_FALLING, "Slow Falling");
		if (p.contains("guardian")) return new Buff(MobEffects.WATER_BREATHING, "Water Breathing");
		return new Buff(MobEffects.SPEED, "Speed"); // modded / unknown monsters
	}

	private static int buffTimer = 0;

	public static void tick(MinecraftServer server) {
		if (EXPIRY.isEmpty()) return;
		long now = server.overworld().getGameTime();
		buffTimer = (buffTimer + 1) % 4;
		Iterator<Map.Entry<Mob, Long>> it = EXPIRY.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Mob, Long> en = it.next();
			Mob m = en.getKey();
			if (m.isRemoved() || !m.isAlive()) {
				OWNER.remove(m);
				it.remove();
				continue;
			}
			if (now >= en.getValue()) {
				OWNER.remove(m);
				it.remove();
				if (m.level() instanceof ServerLevel level)
					m.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				continue;
			}

			// keep it off its owner and point it at the nearest other monster
			UUID owner = OWNER.get(m);

			Mob best = null;
			double bestD = Double.MAX_VALUE;
			for (Mob other : m.level().getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(16), e -> e != m && Mobs.isHostile(e) && !EXPIRY.containsKey(e) && e.isAlive())) {
				double dd = other.distanceToSqr(m);
				if (dd < bestD) { bestD = dd; best = other; }
			}
			if (best != null) {
				m.setTarget(best);
			} else {
				m.setTarget(null); // never chase its owner
			}
			if (best == null && owner != null && m.level() instanceof ServerLevel level) {
				ServerPlayer sp = level.getServer().getPlayerList().getPlayer(owner);
				if (sp != null && m.distanceToSqr(sp) > 36) m.getNavigation().moveTo(sp, 1.1);
			}

			// aura buff for the owner (every ~1 s), stronger with high Intelligence
			if (buffTimer == 0 && owner != null && m.level() instanceof ServerLevel lv) {
				ServerPlayer sp2 = lv.getServer().getPlayerList().getPlayer(owner);
				if (sp2 != null && m.distanceToSqr(sp2) < 24 * 24) {
					int amp = SystemLogic.data(sp2).stat(Stat.INTELLIGENCE) >= 80 ? 1 : 0;
					sp2.addEffect(new MobEffectInstance(buffFor(m).effect(), 60, amp, true, true, true));
				}
			}
		}
	}
}
