package com.example.solosystem.client;

import com.example.solosystem.SoloConfig;
import com.example.solosystem.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Small cooldown indicator for the Intelligence ability "Dominion". */
public final class AbilityHud {
	private AbilityHud() {}

	/** {x, y, w, h, minX, maxX, minY, maxY} for a panel of width w - shared with the drag editor. */
	public static int[] rect(int gw, int gh, SoloConfig.Data cfg, int w) {
		int minX = 6, maxX = Math.max(minX, gw - w - 6), minY = 6, maxY = Math.max(minY, gh - 14 - 6);
		int x = minX + (int) Math.round((maxX - minX) * cfg.abilityX / 1000.0);
		int y = minY + (int) Math.round((maxY - minY) * cfg.abilityY / 1000.0);
		return new int[]{x, y, w, 14, minX, maxX, minY, maxY};
	}

	public static void render(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		var d = ClientState.data;
		if (mc.player == null || mc.level == null || !SoloConfig.get().enableDominion || !d.has(Stat.INTELLIGENCE, 2)) return;

		long left = d.domReady - mc.level.getGameTime();
		boolean ready = left <= 0;
		String text = ready ? "Dominion: READY" : String.format("Dominion: %.1fs", left / 20.0);
		int w = mc.font.width(text) + 10;
		SoloConfig.Data cfg = SoloConfig.get();
		int[] rc = rect(g.guiWidth(), g.guiHeight(), cfg, w);
		int x = rc[0], y = rc[1];
		int accent = ready ? 0xFFB45CFF : 0xFF5A5E68;

		g.fill(x, y, x + w, y + 14, 0xC0101828);
		g.fill(x, y, x + 2, y + 14, accent);
		g.text(mc.font, Component.literal(text).withStyle(ready ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY), x + 6, y + 3, 0xFFFFFFFF, true);
	}
}
