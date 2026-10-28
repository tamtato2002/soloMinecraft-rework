package com.example.solosystem;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Resolve items by id string ("modid:item"), so items from any mod can be used safely. */
public final class ItemIds {
	private ItemIds() {}

	public static String idOf(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }

	/** Returns null if the item does not exist (e.g. that mod is not installed). */
	public static Item item(String id) {
		try {
			int i = id.indexOf(':');
			String ns = i < 0 ? "minecraft" : id.substring(0, i);
			String path = i < 0 ? id : id.substring(i + 1);
			Identifier ident = Identifier.fromNamespaceAndPath(ns.trim(), path.trim());
			return BuiltInRegistries.ITEM.getOptional(ident).filter(it -> it != Items.AIR).orElse(null);
		} catch (Exception e) {
			return null;
		}
	}

	/** Parse "modid:item,min,max" (min/max optional) into a random stack, or null. */
	public static ItemStack parse(String entry, ThreadLocalRandom rnd) {
		if (entry == null) return null;
		String[] parts = entry.split(",");
		Item it = item(parts[0]);
		if (it == null) return null;
		int min = 1, max = 1;
		try {
			if (parts.length > 1) min = Math.max(1, Integer.parseInt(parts[1].trim()));
			max = parts.length > 2 ? Math.max(min, Integer.parseInt(parts[2].trim())) : min;
		} catch (NumberFormatException ignored) {}
		return new ItemStack(it, min + rnd.nextInt(max - min + 1));
	}
}
