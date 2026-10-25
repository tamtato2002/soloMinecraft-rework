package com.example.solosystem;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Quality-of-life passive skills: farming, fishing, animal husbandry and forestry. */
public final class QolSkills {
	private QolSkills() {}

	private static final Item[] FISH = {Items.COD, Items.SALMON, Items.TROPICAL_FISH};
	private static final Item[] TREASURE = {Items.NAME_TAG, Items.SADDLE, Items.NAUTILUS_SHELL, Items.LILY_PAD, Items.INK_SAC};
	private static final Item[] SAPLINGS = {Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.BIRCH_SAPLING, Items.JUNGLE_SAPLING,
		Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING, Items.APPLE, Items.STICK};

	// ---------------------------------------------------------------- blocks: farming + forestry

	public static void onBlockBroken(ServerPlayer sp, Level level, BlockPos pos, BlockState state, BlockEntity be) {
		PlayerData d = SystemLogic.data(sp);
		ThreadLocalRandom r = ThreadLocalRandom.current();

		if (state.getBlock() instanceof CropBlock cb && cb.isMaxAge(state)) {
			int bounty = d.rank(PassiveSkill.HARVEST_BOUNTY);
			if (bounty > 0 && r.nextDouble() < 0.15 * bounty) extraDrops(level, pos, state, be);
			int replant = d.rank(PassiveSkill.AUTO_REPLANT);
			if (replant > 0 && r.nextDouble() < 0.30 * replant) level.setBlock(pos, cb.defaultBlockState(), 3);
		}

		if (state.is(BlockTags.LOGS)) {
			int ff = d.rank(PassiveSkill.FORESTERS_FORTUNE);
			if (ff > 0 && r.nextDouble() < 0.12 * ff) extraDrops(level, pos, state, be);
			int fell = d.rank(PassiveSkill.TREE_FELLER);
			if (fell > 0 && !sp.isShiftKeyDown() && holdingAxe(sp)) fellTree(sp, level, pos, 12 + 18 * fell);
		} else if (state.is(BlockTags.LEAVES)) {
			int ff = d.rank(PassiveSkill.FORESTERS_FORTUNE);
			if (ff > 0 && r.nextDouble() < 0.05 * ff) Block.popResource(level, pos, new ItemStack(SAPLINGS[r.nextInt(SAPLINGS.length)], 1));
		}
	}

	private static void extraDrops(Level level, BlockPos pos, BlockState state, BlockEntity be) {
		if (!(level instanceof ServerLevel sl)) return;
		for (ItemStack s : Block.getDrops(state, sl, pos, be)) Block.popResource(level, pos, s.copy());
	}

	private static boolean holdingAxe(ServerPlayer sp) {
		String p = ItemIds.idOf(sp.getMainHandItem().getItem());
		p = p.substring(p.indexOf(':') + 1);
		return p.endsWith("axe") && !p.contains("pickaxe");
	}

	/** Break every log connected to the one just chopped (diagonals included, so branches come down too). */
	private static void fellTree(ServerPlayer sp, Level level, BlockPos origin, int limit) {
		Deque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		seen.add(origin);
		queue.add(origin);
		int broken = 0;
		while (!queue.isEmpty() && broken < limit) {
			BlockPos c = queue.poll();
			for (int dx = -1; dx <= 1 && broken < limit; dx++)
				for (int dy = -1; dy <= 1 && broken < limit; dy++)
					for (int dz = -1; dz <= 1 && broken < limit; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) continue;
						BlockPos n = c.offset(dx, dy, dz);
						if (!seen.add(n) || !level.getBlockState(n).is(BlockTags.LOGS)) continue;
						level.destroyBlock(n, true, sp);
						broken++;
						queue.add(n);
					}
		}
	}

	// ---------------------------------------------------------------- fishing / breeding (from statistics)

	public static void onFish(ServerPlayer sp, long n) {
		PlayerData d = SystemLogic.data(sp);
		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (long i = 0; i < n; i++) {
			int bountiful = d.rank(PassiveSkill.BOUNTIFUL_CATCH);
			if (bountiful > 0 && r.nextDouble() < 0.15 * bountiful)
				QuestLogic.give(sp, new ItemStack(FISH[r.nextInt(FISH.length)], 1));
			int lucky = d.rank(PassiveSkill.LUCKY_LINE);
			if (lucky > 0 && r.nextDouble() < 0.08 * lucky)
				QuestLogic.give(sp, new ItemStack(TREASURE[r.nextInt(TREASURE.length)], 1));
			int deep = d.rank(PassiveSkill.DEEP_SEA_HAUL);
			if (deep > 0 && r.nextDouble() < 0.10 * deep) {
				double roll = r.nextDouble();
				if (roll < 0.04 * deep) QuestLogic.give(sp, new ItemStack(Items.DIAMOND, 1));
				else if (roll < 0.5) QuestLogic.give(sp, new ItemStack(Items.EMERALD, 1 + r.nextInt(3)));
				else QuestLogic.give(sp, new ItemStack(Items.GOLD_INGOT, 2 + r.nextInt(4)));
			}
		}
	}

	public static void onBreed(ServerPlayer sp, long n) {
		int rank = SystemLogic.data(sp).rank(PassiveSkill.BREEDERS_BOUNTY);
		if (rank <= 0) return;
		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (long i = 0; i < n; i++)
			if (r.nextDouble() < 0.20 * rank) sp.giveExperiencePoints(4 + 2 * rank);
	}

	// ---------------------------------------------------------------- auras (called from the server tick)

	/** Caretaker's Aura: animals around you slowly regenerate. */
	public static void caretaker(ServerPlayer p, PlayerData d) {
		int rank = d.rank(PassiveSkill.CARETAKERS_AURA);
		if (rank <= 0) return;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6 + 2 * rank), x -> x.isAlive() && Mobs.isAnimal(x)))
			e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, rank - 1, true, false, false));
	}

	/** Verdant Aura: crops near you grow a stage now and then. */
	public static void verdant(ServerPlayer p, PlayerData d) {
		int rank = d.rank(PassiveSkill.VERDANT_AURA);
		if (rank <= 0 || !(p.level() instanceof ServerLevel sl)) return;
		int r = 3 + 2 * rank;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		BlockPos c = p.blockPosition();
		for (int x = -r; x <= r; x++)
			for (int z = -r; z <= r; z++)
				for (int y = -2; y <= 2; y++) {
					BlockPos pos = c.offset(x, y, z);
					BlockState st = sl.getBlockState(pos);
					if (!(st.getBlock() instanceof CropBlock cb) || cb.isMaxAge(st) || rnd.nextDouble() > 0.25) continue;
					try {
						for (int age = 0; age < 7; age++) {
							if (cb.getStateForAge(age) == st) {
								sl.setBlock(pos, cb.getStateForAge(age + 1), 2);
								break;
							}
						}
					} catch (Exception ignored) {} // crops with fewer stages
				}
	}
}
