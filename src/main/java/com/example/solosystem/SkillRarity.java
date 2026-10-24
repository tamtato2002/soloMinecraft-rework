package com.example.solosystem;

import net.minecraft.ChatFormatting;

public enum SkillRarity {
	COMMON(60, ChatFormatting.WHITE),
	UNCOMMON(25, ChatFormatting.GREEN),
	RARE(10, ChatFormatting.AQUA),
	EPIC(4, ChatFormatting.LIGHT_PURPLE),
	LEGENDARY(1, ChatFormatting.GOLD);

	public final int weight;
	public final ChatFormatting color;
	SkillRarity(int weight, ChatFormatting color) { this.weight = weight; this.color = color; }
}
