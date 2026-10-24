package com.example.solosystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Extra monster drops. Everything is driven by the killer's PER stat:
 *  - higher PER = better item tier, more drops, and better durability on gear.
 * Weapons/armor only drop ENCHANTED, with random durability (near-broken ... brand new).
 */
public final class LootLogic {
	private LootLogic() {}

	private static final int[] TIER_WEIGHT = {55, 25, 12, 6, 2};

	private record Ench(ResourceKey<Enchantment> key, int max) {}
	private record Gear(Item item, int cat) {} // cat: 0 melee, 1 armor, 2 ranged

	private static List<Item[]> oddItems;
	private static List<Gear[]> gear;

	private static void init() {
		if (oddItems != null) return;
		oddItems = List.of(
			new Item[]{Items.STRING, Items.BONE, Items.FEATHER, Items.FLINT, Items.LEATHER, Items.COAL, Items.SPIDER_EYE, Items.STICK},
			new Item[]{Items.IRON_INGOT, Items.GOLD_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI, Items.GUNPOWDER, Items.SLIME_BALL, Items.COPPER_INGOT, Items.QUARTZ},
			new Item[]{Items.DIAMOND, Items.EMERALD, Items.ENDER_PEARL, Items.BLAZE_ROD, Items.GOLDEN_APPLE, Items.EXPERIENCE_BOTTLE, Items.NAME_TAG, Items.SADDLE},
			new Item[]{Items.NETHERITE_SCRAP, Items.TOTEM_OF_UNDYING, Items.SHULKER_SHELL, Items.HEART_OF_THE_SEA, Items.ENDER_EYE, Items.NAUTILUS_SHELL, Items.DIAMOND},
			new Item[]{Items.NETHERITE_INGOT, Items.ENCHANTED_GOLDEN_APPLE, Items.NETHER_STAR, Items.TOTEM_OF_UNDYING, Items.DIAMOND_BLOCK, Items.BEACON}
		);
		gear = List.of(
			new Gear[]{g(Items.WOODEN_SWORD, 0), g(Items.WOODEN_AXE, 0), g(Items.STONE_SWORD, 0), g(Items.STONE_AXE, 0), g(Items.BOW, 2),
				g(Items.LEATHER_HELMET, 1), g(Items.LEATHER_CHESTPLATE, 1), g(Items.LEATHER_LEGGINGS, 1), g(Items.LEATHER_BOOTS, 1)},
			new Gear[]{g(Items.IRON_SWORD, 0), g(Items.IRON_AXE, 0), g(Items.BOW, 2), g(Items.CROSSBOW, 2),
				g(Items.CHAINMAIL_HELMET, 1), g(Items.CHAINMAIL_CHESTPLATE, 1), g(Items.CHAINMAIL_LEGGINGS, 1), g(Items.CHAINMAIL_BOOTS, 1)},
			new Gear[]{g(Items.IRON_SWORD, 0), g(Items.TRIDENT, 0), g(Items.CROSSBOW, 2),
				g(Items.IRON_HELMET, 1), g(Items.IRON_CHESTPLATE, 1), g(Items.IRON_LEGGINGS, 1), g(Items.IRON_BOOTS, 1)},
			new Gear[]{g(Items.DIAMOND_SWORD, 0), g(Items.DIAMOND_AXE, 0), g(Items.TRIDENT, 0), g(Items.CROSSBOW, 2),
				g(Items.DIAMOND_HELMET, 1), g(Items.DIAMOND_CHESTPLATE, 1), g(Items.DIAMOND_LEGGINGS, 1), g(Items.DIAMOND_BOOTS, 1)},
			new Gear[]{g(Items.NETHERITE_SWORD, 0), g(Items.NETHERITE_AXE, 0), g(Items.MACE, 0), g(Items.TRIDENT, 0),
				g(Items.NETHERITE_HELMET, 1), g(Items.NETHERITE_CHESTPLATE, 1), g(Items.NETHERITE_LEGGINGS, 1), g(Items.NETHERITE_BOOTS, 1)}
		);
	}

	private static Gear g(Item i, int cat) { return new Gear(i, cat); }

	private static List<Ench> enchPool(int cat, int tier) {
		List<Ench> l = new ArrayList<>();
		switch (cat) {
			case 0 -> {
				l.add(new Ench(Enchantments.SHARPNESS, 5)); l.add(new Ench(Enchantments.UNBREAKING, 3));
				l.add(new Ench(Enchantments.FIRE_ASPECT, 2)); l.add(new Ench(Enchantments.LOOTING, 3));
				l.add(new Ench(Enchantments.KNOCKBACK, 2));
			}
			case 1 -> {
				l.add(new Ench(Enchantments.PROTECTION, 4)); l.add(new Ench(Enchantments.UNBREAKING, 3));
				l.add(new Ench(Enchantments.THORNS, 3)); l.add(new Ench(Enchantments.FEATHER_FALLING, 4));
			}
			case 3 -> {
				l.add(new Ench(Enchantments.EFFICIENCY, 5)); l.add(new Ench(Enchantments.UNBREAKING, 3));
				l.add(new Ench(Enchantments.FORTUNE, 3));
			}
			default -> {
				l.add(new Ench(Enchantments.POWER, 5)); l.add(new Ench(Enchantments.UNBREAKING, 3));
				l.add(new Ench(Enchantments.FLAME, 1)); l.add(new Ench(Enchantments.PUNCH, 2));
			}
		}
		if (tier >= 3) l.add(new Ench(Enchantments.MENDING, 1));
		return l;
	}

	public static int effectivePer(PlayerData d) {
		return d.stat(Stat.SENSE) + 4 * d.rank(PassiveSkill.TREASURE_EYE);
	}

	/** Roll an item tier 0..4. Higher PER shifts weight toward higher tiers. */
	private static int rollTier(ThreadLocalRandom rnd, int per, double extra) {
		double[] w = new double[5];
		double total = 0;
		for (int i = 0; i < 5; i++) {
			w[i] = TIER_WEIGHT[i] * (1.0 + per * 0.05 * i * (1.0 + extra));
			total += w[i];
		}
		double r = rnd.nextDouble() * total;
		for (int i = 0; i < 5; i++) {
			r -= w[i];
			if (r <= 0) return i;
		}
		return 0;
	}

	/** Called when a player kills a mob. */
	public static void onKill(ServerPlayer p, LivingEntity victim, boolean hostile, boolean boss) {
		init();
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		PlayerData d = SystemLogic.data(p);
		int per = effectivePer(d);
		double extra = boss ? 3.0 : 0.0;

		// odd items
		int drops = 0;
		if (boss) drops = 3;
		else {
			double lm = SoloConfig.get().lootMultiplier;
			double chance = (hostile ? Math.min(0.85, 0.12 + per * 0.012) : Math.min(0.2, 0.02 + per * 0.002)) * lm;
			if (rnd.nextDouble() < chance) drops = 1;
			if (drops == 1 && rnd.nextDouble() < Math.min(0.4, per * 0.004)) drops = 2;
		}
		for (int i = 0; i < drops; i++) dropOdd(p, victim, rollTier(rnd, per, extra), rnd);

		// enchanted gear (hostile / boss only)
		double gearChance = boss ? 1.0 : hostile ? Math.min(0.35, (0.02 + per * 0.004) * SoloConfig.get().lootMultiplier) : 0;
		if (rnd.nextDouble() < gearChance) dropGear(p, victim, rollTier(rnd, per, extra), per, rnd);
	}

	private static void dropOdd(ServerPlayer p, LivingEntity victim, int tier, ThreadLocalRandom rnd) {
		String[] extra = SoloConfig.get().extraLoot[tier];
		if (extra != null && extra.length > 0 && rnd.nextDouble() < 0.25) {
			ItemStack mod = ItemIds.parse(extra[rnd.nextInt(extra.length)], rnd);
			if (mod != null) {
				spawn(victim, mod);
				if (tier >= 2) announce(p, mod, tier);
				return;
			}
		}
		Item[] pool = oddItems.get(tier);
		Item item = pool[rnd.nextInt(pool.length)];
		int count = tier == 0 ? 1 + rnd.nextInt(4) : tier == 1 ? 1 + rnd.nextInt(3) : tier == 2 ? 1 + rnd.nextInt(2) : tier == 3 ? 1 + rnd.nextInt(3) : 1;
		if (item == Items.EXPERIENCE_BOTTLE) count *= 4;
		ItemStack stack = new ItemStack(item, count);
		spawn(victim, stack);
		if (tier >= 2) announce(p, stack, tier);
	}

	private static void dropGear(ServerPlayer p, LivingEntity victim, int tier, int per, ThreadLocalRandom rnd) {
		Gear[] pool = gear.get(tier);
		Gear gr = pool[rnd.nextInt(pool.length)];
		ItemStack stack = new ItemStack(gr.item(), 1);

		enchant(victim.level(), stack, gr.cat(), tier, rnd);

		// durability: random from nearly broken to brand new; PER pushes it toward new
		int max = stack.getMaxDamage();
		if (max > 0) {
			double q = Math.pow(rnd.nextDouble(), 1.0 / (1.0 + per * 0.04)); // 0..1, higher PER -> closer to 1
			int damage = (int) ((1.0 - q) * max);
			stack.setDamageValue(Math.max(0, Math.min(max - 1, damage)));
		}
		spawn(victim, stack);
		announce(p, stack, tier);
	}

	/** Add random enchantments (count and levels grow with tier 0..4). Shared with Masterwork crafting. */
	public static void enchant(net.minecraft.world.level.Level level, ItemStack stack, int cat, int tier, ThreadLocalRandom rnd) {
		List<Ench> list = enchPool(cat, tier);
		Collections.shuffle(list);
		int n = Math.min(list.size(), 1 + tier);
		var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		for (int i = 0; i < n; i++) {
			Ench e = list.get(i);
			int lvl = Math.min(e.max(), 1 + rnd.nextInt(tier + 2));
			stack.enchant(registry.getOrThrow(e.key()), lvl);
		}
	}

	/** Guess an enchant category from the item id (works for modded gear too): 0 melee, 1 armor, 2 ranged, 3 tool, -1 none. */
	public static int categoryOf(Item item) {
		String p = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
		if (p.contains("helmet") || p.contains("chestplate") || p.contains("leggings") || p.contains("boots")) return 1;
		if (p.contains("bow")) return 2;
		if (p.contains("pickaxe") || p.contains("shovel") || p.contains("hoe") || p.endsWith("axe")) return 3;
		if (p.contains("sword") || p.contains("trident") || p.contains("mace") || p.contains("spear")) return 0;
		return -1;
	}

	private static void spawn(LivingEntity victim, ItemStack stack) {
		victim.level().addFreshEntity(new ItemEntity(victim.level(), victim.getX(), victim.getY() + 0.5, victim.getZ(), stack));
	}

	private static void announce(ServerPlayer p, ItemStack stack, int tier) {
		SkillRarity r = SkillRarity.values()[tier];
		p.sendSystemMessage(Component.literal("[SYSTEM] ").withStyle(ChatFormatting.AQUA)
			.append(Component.literal("Loot dropped [" + r.name() + "]: ").withStyle(r.color))
			.append(stack.getHoverName()));
	}
}
