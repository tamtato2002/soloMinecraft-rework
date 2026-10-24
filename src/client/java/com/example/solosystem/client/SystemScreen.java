package com.example.solosystem.client;

import com.example.solosystem.*;
import com.example.solosystem.net.AllocatePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Status / Skills / Quests / Resistance / Config window. Buttons are real widgets; text and icons are drawn in extractRenderState. */
public class SystemScreen extends Screen {
	private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};
	private static final String[] TABS = {"STATUS", "SKILLS", "QUESTS", "RESISTANCE", "CONFIG"};
	private static final int[] TAB_ICONS = {Icons.STATUS, Icons.SKILLS, Icons.QUESTS, Icons.RESISTANCE, Icons.CONFIG};
	private static final int[] STAT_ICONS = {Icons.STRENGTH, Icons.AGILITY, Icons.ENDURANCE, Icons.INTELLIGENCE, Icons.SENSE};
	private static final String[] CORNERS = {"Top-Right", "Top-Left", "Bottom-Right", "Bottom-Left"};

	private int tab = 0;
	private int selStat = 0;
	private int skillPage = 0;
	private int cfgPage = 0;

	private record Label(Component text, int x, int y) {}
	private record Pic(int icon, int x, int y, int scale) {}
	private record Rect(int x1, int y1, int x2, int y2, int color) {}

	private final List<Label> labels = new ArrayList<>();
	private final List<Pic> pics = new ArrayList<>();
	private final List<Rect> rects = new ArrayList<>();
	private int timerX, timerY;

	public SystemScreen() { super(Component.literal("SYSTEM")); }

	public void refresh() { rebuildWidgets(); }

	/** The menu does not pause the game (single-player) unless enabled in the CONFIG tab. */
	@Override
	public boolean isPauseScreen() { return SoloConfig.get().menuPausesGame; }

	@Override
	protected void init() {
		labels.clear();
		pics.clear();
		rects.clear();
		PlayerData d = ClientState.data;
		int cx = width / 2;
		int top = Math.max(6, height / 2 - 122);

		// ---- tab bar (icon + name)
		int[] w = new int[TABS.length];
		int total = 0;
		for (int i = 0; i < TABS.length; i++) { w[i] = 26 + font.width(TABS[i]) + 6; total += w[i] + 3; }
		int x = cx - total / 2;
		for (int i = 0; i < TABS.length; i++) {
			final int idx = i;
			addRenderableWidget(Button.builder(Component.empty(), b -> { tab = idx; rebuildWidgets(); })
				.bounds(x, top, w[i], 22).build());
			pics.add(new Pic(TAB_ICONS[i], x + 4, top + 3, 1));
			label(Component.literal(TABS[i]).withStyle(tab == i ? ChatFormatting.GOLD : ChatFormatting.WHITE), x + 24, top + 7);
			if (tab == i) rects.add(new Rect(x + 2, top + 20, x + w[i] - 2, top + 22, 0xFFFFD93D));
			x += w[i] + 3;
		}

		int y = top + 30;
		switch (tab) {
			case 0 -> status(d, cx, y);
			case 1 -> skills(d, cx, y);
			case 2 -> quests(d, cx, y);
			case 3 -> resistance(d, cx, y);
			default -> config(cx, y);
		}
	}

	private void label(Component c, int x, int y) { labels.add(new Label(c, x, y)); }

	// ------------------------------------------------------------------ STATUS

	private void status(PlayerData d, int cx, int y) {
		int left = cx - 150;
		label(Component.literal("LEVEL  " + d.level).withStyle(ChatFormatting.GOLD), left, y); y += 12;

		double frac = Math.min(1.0, d.exp / d.expToNext());
		int filled = (int) (frac * 24);
		String bar = "[" + "=".repeat(filled) + "-".repeat(24 - filled) + "]";
		label(Component.literal("EXP " + bar + " " + (int) d.exp + "/" + (int) d.expToNext()).withStyle(ChatFormatting.AQUA), left, y); y += 12;
		label(Component.literal("Available Points: " + d.points).withStyle(ChatFormatting.YELLOW), left, y); y += 14;

		Stat[] stats = Stat.values();
		for (int i = 0; i < stats.length; i++) {
			final int idx = i;
			Stat s = stats[i];
			pics.add(new Pic(STAT_ICONS[i], left, y + 2, 1));
			Button sel = Button.builder(Component.literal((selStat == i ? "> " : "") + s.label + ": " + d.stat(s)), b -> { selStat = idx; rebuildWidgets(); })
				.bounds(left + 20, y, 140, 20).build();
			addRenderableWidget(sel);
			Button b1 = Button.builder(Component.literal("+1"), b -> send(s, 1)).bounds(left + 166, y, 28, 20).build();
			Button b5 = Button.builder(Component.literal("+5"), b -> send(s, 5)).bounds(left + 196, y, 28, 20).build();
			b1.active = b5.active = d.points > 0;
			addRenderableWidget(b1);
			addRenderableWidget(b5);
			label(Component.literal(s.blurb).withStyle(ChatFormatting.DARK_GRAY), left + 230, y + 6);
			y += 22;
		}

		// abilities of the selected stat
		Stat s = stats[selStat];
		y += 2;
		pics.add(new Pic(STAT_ICONS[selStat], left, y - 3, 1));
		label(Component.literal(s.label + " abilities").withStyle(ChatFormatting.GOLD), left + 20, y);
		y += 12;
		for (Ability a : s.abilities) {
			boolean open = d.stat(s) >= a.need();
			Component line = open
				? Component.literal("[UNLOCKED] " + a.name()).withStyle(ChatFormatting.GREEN)
					.append(Component.literal("  " + a.desc()).withStyle(ChatFormatting.GRAY))
				: Component.literal("[needs " + a.need() + "] " + a.name()).withStyle(ChatFormatting.DARK_GRAY);
			label(line, left, y);
			y += 10;
		}
	}

	private void send(Stat s, int n) { ClientPlayNetworking.send(new AllocatePayload(s.id(), n)); }

	// ------------------------------------------------------------------ SKILLS

	private void skills(PlayerData d, int cx, int y) {
		int left = cx - 160;
		PassiveSkill[] all = PassiveSkill.values();
		int found = 0;
		for (PassiveSkill s : all) if (d.rank(s) > 0) found++;
		label(Component.literal("Passive Skills discovered: " + found + "/" + all.length).withStyle(ChatFormatting.YELLOW), left, y);

		final int perPage = 14;
		int pages = (all.length + perPage - 1) / perPage;
		skillPage = Math.max(0, Math.min(pages - 1, skillPage));
		Button prev = Button.builder(Component.literal("<"), b -> { skillPage--; rebuildWidgets(); }).bounds(cx + 90, y - 5, 20, 16).build();
		Button next = Button.builder(Component.literal(">"), b -> { skillPage++; rebuildWidgets(); }).bounds(cx + 112, y - 5, 20, 16).build();
		prev.active = skillPage > 0;
		next.active = skillPage < pages - 1;
		addRenderableWidget(prev);
		addRenderableWidget(next);
		label(Component.literal("Page " + (skillPage + 1) + "/" + pages).withStyle(ChatFormatting.GRAY), cx + 136, y);
		y += 16;

		// rarest first
		int shown = 0, idx = 0;
		for (int i = all.length - 1; i >= 0; i--, idx++) {
			if (idx < skillPage * perPage || shown >= perPage) continue;
			PassiveSkill s = all[i];
			int rank = d.rank(s);
			Component c;
			if (rank > 0) {
				c = Component.literal(s.displayName + " " + ROMAN[rank] + "/" + s.maxRank).withStyle(s.rarity.color)
					.append(Component.literal("  " + s.desc).withStyle(ChatFormatting.GRAY));
			} else {
				c = Component.literal("??? (" + s.rarity.name() + ")").withStyle(ChatFormatting.DARK_GRAY);
			}
			label(c, left, y);
			y += 12;
			shown++;
		}
	}

	// ------------------------------------------------------------------ QUESTS

	private void quests(PlayerData d, int cx, int y) {
		int left = cx - 160;
		pics.add(new Pic(Icons.QUESTS, left, y - 3, 1));
		label(Component.literal("DAILY QUESTS").withStyle(ChatFormatting.GOLD), left + 20, y);
		timerX = left + 120;
		timerY = y;
		y += 16;

		if (d.quests.isEmpty()) {
			label(Component.literal("Waiting for quests...").withStyle(ChatFormatting.GRAY), left, y);
			return;
		}
		for (Quest q : d.quests) {
			SkillRarity r = QuestLogic.tierRarity(q.tier);
			QuestType t = QuestType.byId(q.type);
			String name = t == null ? q.type : t.label;
			Component head = Component.literal("[" + r.name() + "] " + "*".repeat(Math.max(0, Math.min(4, q.tier)) + 1) + "  " + name + "  " + (int) q.progress + "/" + q.target).withStyle(r.color);
			if (q.done) head = head.copy().append(Component.literal("  CLEAR").withStyle(ChatFormatting.GREEN));
			label(head, left, y); y += 11;
			label(Component.literal("Reward: " + QuestLogic.rewardLine1(q)).withStyle(ChatFormatting.GRAY), left + 8, y); y += 10;
			label(Component.literal(QuestLogic.rewardLine2(q)).withStyle(ChatFormatting.DARK_GRAY), left + 8, y); y += 16;
		}
		label(Component.literal("Resets every Minecraft day (24000 ticks). Rewards are random per quest.").withStyle(ChatFormatting.DARK_GRAY), left, y);
	}

	private String timeLeft() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return "--:--";
		long left = Math.max(0, ClientState.data.questExpiry - mc.level.getGameTime());
		long sec = left / 20;
		return String.format("%02d:%02d", sec / 60, sec % 60);
	}

	// ------------------------------------------------------------------ RESISTANCE

	private void resistance(PlayerData d, int cx, int y) {
		int left = cx - 170;
		pics.add(new Pic(Icons.RESISTANCE, left, y - 3, 1));
		label(Component.literal("RESISTANCE").withStyle(ChatFormatting.GOLD)
			.append(Component.literal("   learned by taking hits / effects (cap " + SoloConfig.get().resistMaxPercent + "%)").withStyle(ChatFormatting.GRAY)), left + 20, y);
		y += 18;

		Resist[] all = Resist.values();
		int perCol = (all.length + 1) / 2;
		for (int i = 0; i < all.length; i++) {
			Resist r = all[i];
			int col = i / perCol, row = i % perCol;
			int x = left + col * 175;
			int ry = y + row * 14;
			int count = d.resist.getOrDefault(r.id, 0);
			Component c;
			if (count > 0) {
				double pct = Resist.total(d, r) * 100.0;
				ChatFormatting color = pct >= 50 ? ChatFormatting.GREEN : pct >= 20 ? ChatFormatting.YELLOW : ChatFormatting.WHITE;
				c = Component.literal(r.label + ": " + String.format("%.1f%%", pct)).withStyle(color)
					.append(Component.literal("  (" + count + ")").withStyle(ChatFormatting.DARK_GRAY));
			} else {
				c = Component.literal("?  ???").withStyle(ChatFormatting.DARK_GRAY);
			}
			label(c, x, ry);
		}
	}

	// ------------------------------------------------------------------ CONFIG

	private void toggle(int row, int x, int top, String name, Supplier<Boolean> get, Consumer<Boolean> set) {
		addRenderableWidget(Button.builder(Component.literal(name + ": " + (get.get() ? "ON" : "OFF")), b -> {
			set.accept(!get.get());
			SoloConfig.save();
			rebuildWidgets();
		}).bounds(x, top + row * 21, 180, 20).build());
	}

	private void number(int row, int x, int top, String name, Supplier<Double> get, Consumer<Double> set,
						double step, double min, double max, String fmt) {
		double v = get.get();
		label(Component.literal(name + ": " + String.format(fmt, v)).withStyle(ChatFormatting.WHITE), x, top + row * 21 + 6);
		Button minus = Button.builder(Component.literal("-"), b -> {
			set.accept(Math.max(min, Math.round((get.get() - step) * 100) / 100.0));
			SoloConfig.save();
			rebuildWidgets();
		}).bounds(x + 128, top + row * 21, 22, 20).build();
		Button plus = Button.builder(Component.literal("+"), b -> {
			set.accept(Math.min(max, Math.round((get.get() + step) * 100) / 100.0));
			SoloConfig.save();
			rebuildWidgets();
		}).bounds(x + 152, top + row * 21, 22, 20).build();
		minus.active = v > min + 1e-9;
		plus.active = v < max - 1e-9;
		addRenderableWidget(minus);
		addRenderableWidget(plus);
	}

	private void config(int cx, int y) {
		SoloConfig.Data c = SoloConfig.get();
		int lx = cx - 190, rx = cx + 10;
		pics.add(new Pic(Icons.CONFIG, lx, y - 3, 1));
		label(Component.literal("CONFIG").withStyle(ChatFormatting.GOLD)
			.append(Component.literal("  config/solosystem.json").withStyle(ChatFormatting.GRAY)), lx + 20, y);
		addRenderableWidget(Button.builder(Component.literal("Page: " + (cfgPage == 0 ? "Toggles" : "Numbers") + "  >"), b -> {
			cfgPage = 1 - cfgPage;
			rebuildWidgets();
		}).bounds(cx + 60, y - 5, 130, 16).build());
		y += 18;

		if (cfgPage == 0) {
			toggle(0, lx, y, "Notifications", () -> c.notifications, v -> c.notifications = v);
			toggle(1, lx, y, "HUD Radar", () -> c.radar, v -> c.radar = v);
			addRenderableWidget(Button.builder(Component.literal("Radar corner: " + CORNERS[c.radarCorner]), b -> {
				c.radarCorner = (c.radarCorner + 1) % 4;
				SoloConfig.save();
				rebuildWidgets();
			}).bounds(lx, y + 2 * 21, 180, 20).build());
			toggle(3, lx, y, "Menu pauses game", () -> c.menuPausesGame, v -> c.menuPausesGame = v);
			toggle(4, lx, y, "Dodge (MISS!)", () -> c.enableDodge, v -> c.enableDodge = v);
			toggle(5, lx, y, "Critical hits", () -> c.enableCrit, v -> c.enableCrit = v);

			toggle(0, rx, y, "Resistance system", () -> c.enableResistance, v -> c.enableResistance = v);
			toggle(1, rx, y, "Masterwork crafting", () -> c.enableMasterwork, v -> c.enableMasterwork = v);
			toggle(2, rx, y, "Dominion (ally)", () -> c.enableDominion, v -> c.enableDominion = v);
			toggle(3, rx, y, "Mirror Ward (reflect)", () -> c.enableReflect, v -> c.enableReflect = v);
			toggle(4, rx, y, "Mod items in quests", () -> c.autoModItems, v -> c.autoModItems = v);
		} else {
			number(0, lx, y, "EXP rate", () -> c.expMultiplier, v -> c.expMultiplier = v, 0.25, 0.25, 10, "x%.2f");
			number(1, lx, y, "Skill drop", () -> c.skillDropMultiplier, v -> c.skillDropMultiplier = v, 0.25, 0, 10, "x%.2f");
			number(2, lx, y, "Loot drop", () -> c.lootMultiplier, v -> c.lootMultiplier = v, 0.25, 0, 10, "x%.2f");
			number(3, lx, y, "Quest EXP", () -> c.questExpMultiplier, v -> c.questExpMultiplier = v, 0.25, 0.25, 10, "x%.2f");

			number(0, rx, y, "Quests / day", () -> (double) c.questsPerDay, v -> c.questsPerDay = (int) Math.round(v), 1, 1, 5, "%.0f");
			number(1, rx, y, "Toast time (s)", () -> c.toastSeconds, v -> c.toastSeconds = v, 0.5, 0.5, 10, "%.1f");
			number(2, rx, y, "Radar size", () -> (double) c.radarSize, v -> c.radarSize = (int) Math.round(v), 10, 50, 200, "%.0f");
			number(3, rx, y, "Resist cap %", () -> (double) c.resistMaxPercent, v -> c.resistMaxPercent = (int) Math.round(v), 5, 10, 95, "%.0f");
		}
		label(Component.literal("Gameplay options apply on the server / host. Extra mod items: edit the JSON file.").withStyle(ChatFormatting.DARK_GRAY), lx, y + 6 * 21 + 6);
	}

	// ------------------------------------------------------------------ render

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		for (Rect r : rects) graphics.fill(r.x1(), r.y1(), r.x2(), r.y2(), r.color());
		for (Pic p : pics) Icons.draw(graphics, p.icon(), p.x(), p.y(), p.scale());
		for (Label l : labels) graphics.text(this.font, l.text(), l.x(), l.y(), 0xFFFFFFFF, true);
		if (tab == 2 && !ClientState.data.quests.isEmpty()) {
			graphics.text(this.font, Component.literal("Time left: " + timeLeft()).withStyle(ChatFormatting.RED),
				timerX, timerY, 0xFFFFFFFF, true);
		}
	}
}
