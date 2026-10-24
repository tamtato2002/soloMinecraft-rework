package com.example.solosystem;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Block tags used for mod-compatible ore detection (vanilla + the common "c:" convention tags). */
public final class SoloTags {
	private SoloTags() {}

	public static TagKey<Block> tag(String ns, String name) {
		return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(ns, name));
	}

	public static final TagKey<Block> DIAMOND = tag("minecraft", "diamond_ores");
	public static final TagKey<Block> EMERALD = tag("minecraft", "emerald_ores");
	public static final TagKey<Block> STONE_OVERWORLD = tag("minecraft", "base_stone_overworld");
	public static final TagKey<Block> STONE_NETHER = tag("minecraft", "base_stone_nether");
	public static final TagKey<Block> CROPS = tag("minecraft", "crops");
	public static final TagKey<Block> C_ORES = tag("c", "ores");
	public static final TagKey<Block> C_DIAMOND = tag("c", "ores/diamond");
	public static final TagKey<Block> C_EMERALD = tag("c", "ores/emerald");
	public static final TagKey<Block> C_NETHERITE = tag("c", "ores/netherite_scrap");
}
