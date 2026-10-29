package com.example.solosystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;

/**
 * Measures quest progress from the game's own statistics. Because these statistics exist for EVERY registered block,
 * item and entity, quests automatically work for things added by other mods, and for any way of doing something
 * (single click, Shift+Click ...).
 *
 * Filters (comma separated): "modid:thing" exact id, "@suffix:_pickaxe", "@prefix:cooked_", "@contains:ore",
 * and groups "@ores", "@logs", "@stone", "@crops" (blocks), "@food" (items), "@hostile", "@animals" (entities).
 */
public final class QuestTracker {
	private static final Map<String, List<Block>> BLOCKS = new HashMap<>();
	private static final Map<String, List<Item>> ITEMS = new HashMap<>();
	private static final Map<String, List<EntityType<?>>> ENTITIES = new HashMap<>();

	private static final List<TagKey<Block>> ORE_TAGS = List.of(
		SoloTags.C_ORES,
		SoloTags.tag("minecraft", "coal_ores"), SoloTags.tag("minecraft", "copper_ores"), SoloTags.tag("minecraft", "iron_ores"),
		SoloTags.tag("minecraft", "gold_ores"), SoloTags.tag("minecraft", "redstone_ores"), SoloTags.tag("minecraft", "lapis_ores"),
		SoloTags.DIAMOND, SoloTags.EMERALD);

	private QuestTracker() {}

	// ---------------------------------------------------------------- groups

	public static boolean looksLikeOre(String path) {
		return path.endsWith("_ore") || path.contains("_ore_") || path.equals("ore") || path.endsWith("_ores");
	}

	public static boolean isOre(Block b) {
		if (b == Blocks.NETHER_QUARTZ_ORE || b == Blocks.ANCIENT_DEBRIS) return true;
		var s = b.defaultBlockState();
		for (TagKey<Block> t : ORE_TAGS) if (s.is(t)) return true;
		String id = BuiltInRegistries.BLOCK.getKey(b).toString();
		return !id.startsWith("minecraft:") && looksLikeOre(id.substring(id.indexOf(':') + 1));
	}

	private static boolean isStone(Block b) {
		var s = b.defaultBlockState();
		return s.is(SoloTags.STONE_OVERWORLD) || s.is(SoloTags.STONE_NETHER) || b == Blocks.COBBLESTONE || b == Blocks.COBBLED_DEEPSLATE;
	}

	private static boolean isCrop(Block b) { return b.defaultBlockState().is(SoloTags.CROPS) || b instanceof CropBlock; }

	private static boolean tokenMatch(String tok, String id) {
		String path = id.substring(id.indexOf(':') + 1);
		if (tok.startsWith("@suffix:")) return path.endsWith(tok.substring(8));
		if (tok.startsWith("@prefix:")) return path.startsWith(tok.substring(8));
		if (tok.startsWith("@contains:")) return path.contains(tok.substring(10));
		return tok.equals(id);
	}

	// ---------------------------------------------------------------- resolving filters (cached)

	public static List<Block> blocks(String filter) {
		return BLOCKS.computeIfAbsent(filter, f -> {
			String[] toks = f.split(",");
			List<Block> out = new ArrayList<>();
			for (Block b : BuiltInRegistries.BLOCK) {
				String id = BuiltInRegistries.BLOCK.getKey(b).toString();
				for (String raw : toks) {
					String t = raw.trim();
					boolean m = switch (t) {
						case "@ores" -> isOre(b);
						case "@logs" -> b.defaultBlockState().is(BlockTags.LOGS);
						case "@stone" -> isStone(b);
						case "@crops" -> isCrop(b);
						default -> tokenMatch(t, id);
					};
					if (m) { out.add(b); break; }
				}
			}
			return out;
		});
	}

	public static List<Item> items(String filter) {
		return ITEMS.computeIfAbsent(filter, f -> {
			String[] toks = f.split(",");
			List<Item> out = new ArrayList<>();
			for (Item it : BuiltInRegistries.ITEM) {
				if (it == Items.AIR) continue;
				String id = BuiltInRegistries.ITEM.getKey(it).toString();
				for (String raw : toks) {
					String t = raw.trim();
					boolean m = t.equals("@food") ? new ItemStack(it, 1).has(DataComponents.FOOD) : tokenMatch(t, id);
					if (m) { out.add(it); break; }
				}
			}
			return out;
		});
	}

	public static List<EntityType<?>> entities(String filter) {
		return ENTITIES.computeIfAbsent(filter, f -> {
			String[] toks = f.split(",");
			List<EntityType<?>> out = new ArrayList<>();
			for (EntityType<?> et : BuiltInRegistries.ENTITY_TYPE) {
				String id = BuiltInRegistries.ENTITY_TYPE.getKey(et).toString();
				for (String raw : toks) {
					String t = raw.trim();
					boolean m = switch (t) {
						case "@hostile" -> et.getCategory() == MobCategory.MONSTER;
						case "@animals" -> et.getCategory() == MobCategory.CREATURE;
						default -> tokenMatch(t, id);
					};
					if (m) { out.add(et); break; }
				}
			}
			return out;
		});
	}

	private static Identifier ident(String id) {
		int i = id.indexOf(':');
		return Identifier.fromNamespaceAndPath(i < 0 ? "minecraft" : id.substring(0, i).trim(), i < 0 ? id.trim() : id.substring(i + 1).trim());
	}

	/** Is this quest definition usable in this game (its blocks / items / mobs / statistics exist)? */
	public static boolean valid(QuestCatalog.Def d) {
		try {
			return switch (d.kind()) {
				case "block" -> !blocks(d.filter()).isEmpty();
				case "craft", "use", "pickup" -> !items(d.filter()).isEmpty();
				case "kill" -> !entities(d.filter()).isEmpty();
				case "stat" -> {
					for (String s : d.filter().split(","))
						if (BuiltInRegistries.CUSTOM_STAT.getOptional(ident(s)).isPresent()) yield true;
					yield false;
				}
				default -> true;
			};
		} catch (Exception e) {
			return false;
		}
	}

	// ---------------------------------------------------------------- measuring

	private static long stat(ServerPlayer p, Stat<?> s) { return p.getStats().getValue(s); }

	/** Current total of the measured statistic, already multiplied by the quest's scale. */
	public static double current(ServerPlayer p, Quest q) {
		double sum = 0;
		switch (q.kind) {
			case "block" -> { for (Block b : blocks(q.filter)) sum += stat(p, Stats.BLOCK_MINED.get(b)); }
			case "craft" -> { for (Item it : items(q.filter)) sum += stat(p, Stats.ITEM_CRAFTED.get(it)); }
			case "use" -> { for (Item it : items(q.filter)) sum += stat(p, Stats.ITEM_USED.get(it)); }
			case "pickup" -> { for (Item it : items(q.filter)) sum += stat(p, Stats.ITEM_PICKED_UP.get(it)); }
			case "kill" -> { for (EntityType<?> et : entities(q.filter)) sum += stat(p, Stats.ENTITY_KILLED.get(et)); }
			case "stat" -> { for (String s : q.filter.split(",")) sum += stat(p, Stats.CUSTOM.get(ident(s))); }
			case "xp" -> sum = p.totalExperience;
			default -> { }
		}
		return sum * q.scale;
	}

	/** Called every half second: update progress of every open quest. */
	public static void tick(ServerPlayer p, PlayerData d) {
		boolean changed = false;
		for (Quest q : d.quests) {
			if (q.done || q.kind == null || q.kind.isEmpty()) continue;
			double prog;
			if (q.kind.equals("active")) prog = Math.min(q.target, q.progress + 0.5);   // seconds spent playing
			else prog = Math.max(0, Math.min(q.target, current(p, q) - q.base));
			if (prog > q.progress + 1e-9) {
				q.progress = prog;
				changed = true;
				if (q.progress >= q.target) QuestLogic.finish(p, d, q);
			}
		}
		if (changed) SystemLogic.markDirty(p);
	}
}
