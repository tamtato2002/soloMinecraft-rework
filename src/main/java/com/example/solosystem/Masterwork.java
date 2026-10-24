package com.example.solosystem;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Intelligence ability "Enchanting & Crafting Luck": crafted gear can come out Masterwork or Legendary. */
public final class Masterwork {
	private Masterwork() {}

	public static void tryApply(ServerPlayer sp, ItemStack stack) {
		if (stack.isEmpty() || stack.getMaxDamage() <= 0) return;
		PlayerData d = SystemLogic.data(sp);
		if (!d.has(Stat.INTELLIGENCE, 1)) return;
		int in = d.stat(Stat.INTELLIGENCE);

		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		if (rnd.nextDouble() >= Math.min(0.5, 0.02 + 0.006 * (in - 20))) return;
		int cat = LootLogic.categoryOf(stack.getItem());
		if (cat < 0) return;

		boolean legendary = rnd.nextDouble() < Math.min(0.5, 0.10 + 0.004 * in);
		LootLogic.enchant(sp.level(), stack, cat, legendary ? 4 : 2, rnd);
		Component prefix = Component.literal(legendary ? "Legendary " : "Masterwork ")
			.withStyle(legendary ? ChatFormatting.GOLD : ChatFormatting.AQUA);
		stack.set(DataComponents.CUSTOM_NAME, prefix.copy().append(stack.getHoverName()));
		SystemLogic.msg(sp, (legendary ? "LEGENDARY" : "MASTERWORK") + " item crafted!", legendary ? ChatFormatting.GOLD : ChatFormatting.AQUA);
	}
}
