package com.example.solosystem.client;

import com.example.solosystem.SoloTags;
import java.util.List;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Ore kinds with their Sixth Sense colour. Works for modded ores through the common "c:ores/..." tags. */
public enum OreType {
	COAL("Coal", 0xFF9AA0A6, List.of(SoloTags.tag("minecraft", "coal_ores"), SoloTags.tag("c", "ores/coal")), List.of()),
	COPPER("Copper", 0xFFFF8A3C, List.of(SoloTags.tag("minecraft", "copper_ores"), SoloTags.tag("c", "ores/copper")), List.of()),
	IRON("Iron", 0xFFE8B89A, List.of(SoloTags.tag("minecraft", "iron_ores"), SoloTags.tag("c", "ores/iron")), List.of()),
	GOLD("Gold", 0xFFFFD93D, List.of(SoloTags.tag("minecraft", "gold_ores"), SoloTags.tag("c", "ores/gold")), List.of()),
	REDSTONE("Redstone", 0xFFFF3030, List.of(SoloTags.tag("minecraft", "redstone_ores"), SoloTags.tag("c", "ores/redstone")), List.of()),
	LAPIS("Lapis", 0xFF3F6BFF, List.of(SoloTags.tag("minecraft", "lapis_ores"), SoloTags.tag("c", "ores/lapis")), List.of()),
	DIAMOND("Diamond", 0xFF4FE8F0, List.of(SoloTags.DIAMOND, SoloTags.C_DIAMOND), List.of()),
	EMERALD("Emerald", 0xFF3CE05A, List.of(SoloTags.EMERALD, SoloTags.C_EMERALD), List.of()),
	QUARTZ("Quartz", 0xFFF4F4F4, List.of(SoloTags.tag("c", "ores/quartz")), List.of(Blocks.NETHER_QUARTZ_ORE)),
	DEBRIS("Ancient Debris", 0xFFFF55C8, List.of(SoloTags.C_NETHERITE), List.of(Blocks.ANCIENT_DEBRIS)),
	OTHER("Other ore", 0xFFB0FF70, List.of(), List.of());

	public final String label;
	public final int color;
	private final List<TagKey<Block>> tags;
	private final List<Block> blocks;

	OreType(String label, int color, List<TagKey<Block>> tags, List<Block> blocks) {
		this.label = label; this.color = color; this.tags = tags; this.blocks = blocks;
	}

	/** Which ore is this block? null if it is not an ore. */
	public static OreType classify(BlockState s) {
		for (OreType t : values()) {
			if (t == OTHER) continue;
			for (TagKey<Block> k : t.tags) if (s.is(k)) return t;
			for (Block b : t.blocks) if (s.is(b)) return t;
		}
		return s.is(SoloTags.C_ORES) ? OTHER : null;
	}
}
