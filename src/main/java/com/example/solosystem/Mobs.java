package com.example.solosystem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/** One place that decides what counts as an enemy, so monsters from other mods work everywhere. */
public final class Mobs {
	private Mobs() {}

	public static String idOf(Entity e) { return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(); }

	private static boolean listed(String[] list, String id) {
		if (list == null) return false;
		for (String s : list) if (s != null && s.trim().equals(id)) return true;
		return false;
	}

	/** Enemy interface (vanilla + most mods), MONSTER spawn category, or listed in config extraEnemies. */
	public static boolean isHostile(Entity e) {
		if (e == null || e instanceof Player) return false;
		SoloConfig.Data c = SoloConfig.get();
		String id = idOf(e);
		if (listed(c.ignoredEnemies, id)) return false;
		if (e instanceof Enemy || listed(c.extraEnemies, id)) return true;
		return e.getType().getCategory() == MobCategory.MONSTER;
	}

	/** Peaceful animals (cows, sheep, pigs, horses and modded ones): the CREATURE spawn category. */
	public static boolean isAnimal(Entity e) {
		if (e == null || e instanceof Player || isHostile(e)) return false;
		return e.getType().getCategory() == MobCategory.CREATURE;
	}

	/** Dragons, withers and modded bosses: very high health. */
	public static boolean isBoss(net.minecraft.world.entity.LivingEntity e) { return e.getMaxHealth() >= 150f; }
}
