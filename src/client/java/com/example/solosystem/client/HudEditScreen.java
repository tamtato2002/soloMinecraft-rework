package com.example.solosystem.client;

import com.example.solosystem.SoloConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Drag & drop editor: grab the quest notification, the Sixth Sense map or the Dominion cooldown with the left mouse
 * button and drop it anywhere on the screen. Positions are saved when you let go.
 */
public class HudEditScreen extends Screen {
	private final Screen parent;
	private int dragging = -1, grabX, grabY;
	private int lastMouseX, lastMouseY;

	public HudEditScreen(Screen parent) {
		super(Component.literal("Move HUD"));
		this.parent = parent;
	}

	private static final String[] NAMES = {"Sixth Sense map", "Quest notifications", "Dominion cooldown"};

	/** rect of element i: {x, y, w, h, minX, maxX, minY, maxY} */
	private int[] rect(int i) {
		SoloConfig.Data c = SoloConfig.get();
		return switch (i) {
			case 0 -> SixthSense.radarRect(width, height, c);
			case 1 -> QuestToasts.rect(width, height, c);
			default -> AbilityHud.rect(width, height, c, font.width("Dominion: READY") + 10);
		};
	}

	private void setPos(int i, int[] r, int px, int py) {
		SoloConfig.Data c = SoloConfig.get();
		int fx = r[5] > r[4] ? Math.max(0, Math.min(1000, (int) Math.round((px - r[4]) * 1000.0 / (r[5] - r[4])))) : 0;
		int fy = r[7] > r[6] ? Math.max(0, Math.min(1000, (int) Math.round((py - r[6]) * 1000.0 / (r[7] - r[6])))) : 0;
		switch (i) {
			case 0 -> { c.radarX = fx; c.radarY = fy; }
			case 1 -> { c.toastX = fx; c.toastY = fy; }
			default -> { c.abilityX = fx; c.abilityY = fy; }
		}
	}

	@Override
	protected void init() {
		int cx = width / 2;
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onDone()).bounds(cx - 105, height - 28, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Reset all"), b -> {
			SoloConfig.Data c = SoloConfig.get();
			c.radarX = 1000; c.radarY = 0; c.toastX = 0; c.toastY = 0; c.abilityX = 0; c.abilityY = 1000;
			SoloConfig.save();
		}).bounds(cx + 5, height - 28, 100, 20).build());
	}

	private void onDone() {
		SoloConfig.save();
		Minecraft.getInstance().gui.setScreen(parent);
	}

	private int hit(int mx, int my) {
		for (int i = 2; i >= 0; i--) { // top-most first
			int[] r = rect(i);
			if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) return i;
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int i = hit(lastMouseX, lastMouseY);
		if (i >= 0) { // pick the box up
			int[] r = rect(i);
			dragging = i;
			grabX = lastMouseX - r[0];
			grabY = lastMouseY - r[1];
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) { // drop it and save
		if (dragging >= 0) {
			SoloConfig.save();
			dragging = -1;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);

		lastMouseX = mouseX;
		lastMouseY = mouseY;
		if (dragging >= 0) setPos(dragging, rect(dragging), mouseX - grabX, mouseY - grabY); // follow the mouse while a box is held

		g.text(font, Component.literal("Drag the boxes with the left mouse button").withStyle(ChatFormatting.YELLOW),
			width / 2 - font.width("Drag the boxes with the left mouse button") / 2, 8, 0xFFFFFFFF, true);
		String hint = "Quest notifications step below advancement / other mod toasts when they overlap.";
		g.text(font, Component.literal(hint).withStyle(ChatFormatting.GRAY), width / 2 - font.width(hint) / 2, 20, 0xFFFFFFFF, true);

		for (int i = 0; i < 3; i++) {
			int[] r = rect(i);
			boolean hot = dragging == i || (dragging < 0 && mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]);
			int edge = hot ? 0xFFFFD93D : 0xFF3A9BDC;
			g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xC0101828);
			g.fill(r[0], r[1], r[0] + r[2], r[1] + 1, edge);
			g.fill(r[0], r[1] + r[3] - 1, r[0] + r[2], r[1] + r[3], edge);
			g.fill(r[0], r[1], r[0] + 1, r[1] + r[3], edge);
			g.fill(r[0] + r[2] - 1, r[1], r[0] + r[2], r[1] + r[3], edge);
			g.text(font, Component.literal(NAMES[i]).withStyle(hot ? ChatFormatting.GOLD : ChatFormatting.WHITE), r[0] + 4, r[1] + 3, 0xFFFFFFFF, true);
			if (i == 1) {
				g.text(font, Component.literal("Mine blocks   12/40").withStyle(ChatFormatting.GRAY), r[0] + 4, r[1] + 15, 0xFFFFFFFF, true);
				g.fill(r[0] + 4, r[1] + 27, r[0] + r[2] - 4, r[1] + 30, 0xFF2A3350);
				g.fill(r[0] + 4, r[1] + 27, r[0] + 4 + (r[2] - 8) / 3, r[1] + 30, 0xFF55FF55);
			}
		}
	}
}
