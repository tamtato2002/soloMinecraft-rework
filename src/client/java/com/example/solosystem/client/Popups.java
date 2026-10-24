package com.example.solosystem.client;

import com.example.solosystem.SoloConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Floating combat text: MISS! / CRIT! near the crosshair. */
public final class Popups {
	private static final int LIFE = 26;

	private static final class P {
		String text;
		int kind, age;
	}

	private static final List<P> LIST = new ArrayList<>();

	private Popups() {}

	public static void push(String text, int kind) {
		if (!SoloConfig.get().notifications) return;
		P p = new P();
		p.text = text;
		p.kind = kind;
		LIST.add(p);
		if (LIST.size() > 6) LIST.remove(0);
	}

	public static void tick() {
		for (int i = LIST.size() - 1; i >= 0; i--) {
			P p = LIST.get(i);
			if (++p.age >= LIFE) LIST.remove(i);
		}
	}

	public static void render(GuiGraphicsExtractor g) {
		if (LIST.isEmpty()) return;
		var font = Minecraft.getInstance().font;
		int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
		int slot = 0;
		for (P p : LIST) {
			int alpha = Math.max(40, 255 - (int) (255.0 * p.age / LIFE));
			int rgb = p.kind == 1 ? 0xFFAA00 : 0x55FFFF;
			Component c = Component.literal(p.text).withStyle(ChatFormatting.BOLD);
			int x = cx - font.width(p.text) / 2 + (p.kind == 1 ? 24 : -24) + slot * 6;
			int y = cy + 14 - p.age / 2 - slot * 10;
			g.text(font, c, x, y, (alpha << 24) | rgb, true);
			slot++;
		}
	}
}
