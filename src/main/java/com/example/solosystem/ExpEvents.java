package com.example.solosystem;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** All the "do anything -> get EXP" hooks. Tune the numbers here. */
public final class ExpEvents {
	private ExpEvents() {}

	private static TagKey<Block> tag(String name) {
		return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", name));
	}
	private static final TagKey<Block> COAL = tag("coal_ores");
	private static final TagKey<Block> COPPER = tag("copper_ores");
	private static final TagKey<Block> IRON = tag("iron_ores");
	private static final TagKey<Block> LAPIS = tag("lapis_ores");
	private static final TagKey<Block> REDSTONE = tag("redstone_ores");
	private static final TagKey<Block> GOLD = tag("gold_ores");
	private static final TagKey<Block> DIAMOND = tag("diamond_ores");
	private static final TagKey<Block> EMERALD = tag("emerald_ores");
	private static final TagKey<Block> CROPS = SoloTags.CROPS;

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (!(player instanceof ServerPlayer sp) || sp.isCreative()) return;
			SystemLogic.addExp(sp, blockExp(state, state.getDestroySpeed(level, pos)));
			QuestLogic.progress(sp, QuestType.MINE, 1);
			boolean stone = state.is(SoloTags.STONE_OVERWORLD) || state.is(SoloTags.STONE_NETHER);
			if (state.is(BlockTags.LOGS)) QuestLogic.progress(sp, QuestType.CHOP, 1);
			if (isOre(state)) QuestLogic.progress(sp, QuestType.ORE, 1);
			if (stone) QuestLogic.progress(sp, QuestType.STONE, 1);
			if (state.is(CROPS)) QuestLogic.progress(sp, QuestType.HARVEST, 1);
			blockSkillRoll(sp, state, stone);
			QolSkills.onBlockBroken(sp, level, pos, state, be);
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(source.getEntity() instanceof ServerPlayer sp) || entity instanceof ServerPlayer) return;
			onKill(sp, entity);
		});
	}

	/** Passive skills can be found by mining stone / ores, chopping trees and farming (always random). */
	private static void blockSkillRoll(ServerPlayer sp, BlockState s, boolean stone) {
		double chance = 0, boost = 0;
		if (s.is(Blocks.ANCIENT_DEBRIS)) { chance = 0.02; boost = 3; }
		else if (s.is(EMERALD)) { chance = 0.010; boost = 1.5; }
		else if (s.is(DIAMOND)) { chance = 0.008; boost = 1.0; }
		else if (s.is(GOLD)) { chance = 0.003; boost = 0.5; }
		else if (s.is(IRON) || s.is(LAPIS) || s.is(REDSTONE)) chance = 0.002;
		else if (s.is(COAL) || s.is(COPPER) || s.is(SoloTags.C_ORES)) chance = 0.0015;
		else if (s.is(BlockTags.LOGS)) chance = 0.0006;
		else if (s.is(CROPS)) chance = 0.0008;
		else if (stone) chance = 0.0004;
		if (chance > 0) SystemLogic.rollSkill(sp, chance, boost);
	}

	private static boolean isOre(BlockState s) {
		return s.is(SoloTags.C_ORES) || s.is(COAL) || s.is(COPPER) || s.is(IRON) || s.is(LAPIS) || s.is(REDSTONE)
			|| s.is(GOLD) || s.is(DIAMOND) || s.is(EMERALD) || s.is(Blocks.ANCIENT_DEBRIS);
	}

	private static double blockExp(BlockState s, float hardness) {
		double exp = hardness <= 0 ? 0.2 : 0.4 + Math.min(hardness, 10f) * 0.6;
		if (s.is(BlockTags.LOGS)) exp += 0.5;
		if (s.is(CROPS)) exp += 1.5; // harvesting crops
		if (s.is(COAL) || s.is(COPPER)) exp += 3;
		else if (s.is(IRON) || s.is(LAPIS) || s.is(REDSTONE)) exp += 5;
		else if (s.is(GOLD)) exp += 8;
		else if (s.is(DIAMOND)) exp += 20;
		else if (s.is(EMERALD)) exp += 25;
		else if (s.is(Blocks.ANCIENT_DEBRIS)) exp += 40;
		else if (s.is(SoloTags.C_ORES)) exp += 4; // ores added by other mods
		return exp;
	}

	private static void onKill(ServerPlayer sp, LivingEntity victim) {
		double hp = victim.getMaxHealth();
		boolean boss = victim instanceof EnderDragon || victim instanceof WitherBoss || Mobs.isBoss(victim);
		boolean hostile = Mobs.isHostile(victim);

		double exp = hp * (hostile ? 0.8 : 0.3) + (hostile ? 4 : 0);
		if (boss) exp += 1000;
		SystemLogic.addExp(sp, exp);
		if (hostile) QuestLogic.progress(sp, QuestType.KILL, 1);
		else QuestLogic.progress(sp, QuestType.HUNT, 1);

		// skill drop from monsters
		double chance = boss ? 1.0 : hostile ? Math.min(0.5, 0.02 + hp * 0.002) : 0.005;
		double boost = boss ? 4.0 : hostile ? Math.min(2.0, hp / 40.0) : 0;
		SystemLogic.rollSkill(sp, chance, boost);

		// odd items + enchanted gear, driven by PER
		LootLogic.onKill(sp, victim, hostile, boss);
	}

	/**
	 * A player took a craft / smelt / stonecutter result (click or Shift+Click): {@code amount} items.
	 * Furnace-type menus (including modded ones with "furnace" / "smoker" in the name) count as smelting, the rest as crafting.
	 */
	public static void onResultTaken(ServerPlayer sp, ItemStack stack, int amount) {
		if (stack.isEmpty() || sp.isCreative()) return;
		String menu = sp.containerMenu == null ? "" : sp.containerMenu.getClass().getSimpleName().toLowerCase();
		if (menu.contains("furnace") || menu.contains("smoker")) onSmelt(sp, stack, amount);
		else onCraft(sp, stack, amount);
	}

	private static void onCraft(ServerPlayer sp, ItemStack stack, int amount) {
		if (SoloConfig.get().enableMasterwork && amount == 1) Masterwork.tryApply(sp, stack); // only a single crafted item is the real result stack
		int tier = stack.getRarity().ordinal(); // COMMON, UNCOMMON, RARE, EPIC
		double exp = 1.0 + amount * 0.5 + tier * 5.0;
		SystemLogic.addExp(sp, exp);
		QuestLogic.progress(sp, QuestType.CRAFT, amount);
		SystemLogic.rollSkill(sp, 0.004 + tier * 0.01, tier * 0.5);
	}

	private static void onSmelt(ServerPlayer sp, ItemStack stack, int amount) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		double per = 1.2;
		if (path.contains("ingot") || path.contains("netherite") || path.equals("diamond") || path.equals("emerald")) per += 3.0;
		else if (path.contains("cooked") || path.contains("baked") || path.contains("steak")) per += 1.0;
		SystemLogic.addExp(sp, per * amount);
		QuestLogic.progress(sp, QuestType.SMELT, amount);
		SystemLogic.rollSkill(sp, 0.002 * amount, 0);
	}
}
