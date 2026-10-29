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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Status / Skills / Quests / Resistance / Config window. Buttons are real widgets; text and icons are drawn in extractRenderState. */
public class SystemScreen extends Screen {
	private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};
	private static final String[] TABS = {"STATUS", "SKILLS", "QUESTS", "RESISTANCE", "CONFIG"};
	private static final int[] TAB_ICONS = {Icons.STATUS, Icons.SKILLS, Icons.QUESTS, Icons.RESISTANCE, Icons.CONFIG};
	private static final int[] STAT_ICONS = {Icons.STRENGTH, Icons.AGILITY, Icons.ENDURANCE, Icons.INTELLIGENCE, Icons.SENSE};
	private static final String[] CFG_PAGES = {"Presets", "Rates", "General", "Sixth Sense", "Colors", "HUD layout", "Mods"};
	private static final String[] FOV_MODES = {"Vanilla", "Reduced", "Off"};
	private static final ChatFormatting[] PRESET_COLORS = {ChatFormatting.AQUA, ChatFormatting.GREEN, ChatFormatting.DARK_GREEN,
		ChatFormatting.YELLOW, ChatFormatting.GOLD, ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE};

	private int tab = 0;
	private int selStat = 0;
	private int skillTier = 4; // 0 COMMON .. 4 LEGENDARY
	private int cfgPage = 0;
	// Mods page (editable lists for other mods)
	private static final String[] MOD_LISTS = {"Extra enemies", "Ignored enemies", "Quest reward items", "Mob loot items",
		"Ore colours", "Pickaxe keywords", "Weapon keywords", "Animal item keywords"};
	private int modCat = 0, modTier = 0, modPage = 0;
	private String modText = "";
	private Component modStatus = Component.empty();
	private int modStatusX, modStatusY;
	private int hudStep = 50; // permille moved per click in the HUD layout page

	private record Label(Component text, int x, int y) {}
	private record Pic(int icon, int x, int y, int scale) {}
	private record Rect(int x1, int y1, int x2, int y2, int color) {}

	private final List<Label> labels = new ArrayList<>();
	private final List<Pic> pics = new ArrayList<>();
	private final List<Rect> rects = new ArrayList<>();
	private record Click(int x1, int y1, int x2, int y2, String key) {}
	private final List<Click> clicks = new ArrayList<>();
	private int lastMouseX, lastMouseY; // updated every frame; used by the click handler
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
		clicks.clear();
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
			if (i == 2) { // red dot on QUESTS while a finished quest is waiting to be claimed
				for (Quest q : d.quests) if (q.done && !q.claimed) { rects.add(new Rect(x + w[i] - 8, top + 2, x + w[i] - 2, top + 8, 0xFFFF3030)); break; }
			}
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

	/** A name you can click to switch a skill / ability ON or OFF (key = "light_feet" or "strength:3"). */
	private void toggleLabel(String key, boolean on, String name, int rgbOn, int x, int y) {
		String plain = (on ? "[ON] " : "[OFF] ") + name;
		Component c = on ? Component.literal(plain).withStyle(ChatFormatting.GREEN)
			: Component.literal(plain).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH);
		labels.add(new Label(c, x, y));
		clicks.add(new Click(x - 1, y - 1, x + font.width(plain) + 1, y + 9, key));
	}

	private void tick(int x, int y) { // pixel-art green check mark
		int[][] px = {{0, 3}, {1, 4}, {2, 5}, {3, 4}, {4, 3}, {5, 2}, {6, 1}, {7, 0}};
		for (int[] p : px) rects.add(new Rect(x + p[0] * 2, y + p[1] * 2, x + p[0] * 2 + 3, y + p[1] * 2 + 3, 0xFF55FF55));
	}

	// ------------------------------------------------------------------ STATUS

	private void status(PlayerData d, int cx, int y) {
		boolean test = SoloConfig.get().testMode;
		int left = cx - 150;
		label(Component.literal("LEVEL  " + d.level).withStyle(ChatFormatting.GOLD), left, y); y += 12;

		double frac = Math.min(1.0, d.exp / d.expToNext());
		int filled = (int) (frac * 24);
		String bar = "[" + "=".repeat(filled) + "-".repeat(24 - filled) + "]";
		label(Component.literal("EXP " + bar + " " + (int) d.exp + "/" + (int) d.expToNext()).withStyle(ChatFormatting.AQUA), left, y); y += 12;

		if (test) {
			label(Component.literal("TEST MODE: unlimited stat points").withStyle(ChatFormatting.RED), left, y);
			addRenderableWidget(Button.builder(Component.literal("Reset stats"), b -> ClientPlayNetworking.send(new AllocatePayload("__reset", 0)))
				.bounds(left + 200, y - 3, 90, 14).build());
		} else {
			label(Component.literal("Available Points: " + d.points).withStyle(ChatFormatting.YELLOW), left, y);
		}
		y += 14;

		Stat[] stats = Stat.values();
		for (int i = 0; i < stats.length; i++) {
			final int idx = i;
			Stat s = stats[i];
			pics.add(new Pic(STAT_ICONS[i], left, y + 2, 1));
			addRenderableWidget(Button.builder(Component.literal((selStat == i ? "> " : "") + s.label + ": " + d.stat(s)), b -> { selStat = idx; rebuildWidgets(); })
				.bounds(left + 20, y, 120, 20).build());

			int bx = left + 144;
			if (test) {
				addRenderableWidget(Button.builder(Component.literal("-5"), b -> send(s, -5)).bounds(bx, y, 26, 20).build());
				addRenderableWidget(Button.builder(Component.literal("-1"), b -> send(s, -1)).bounds(bx + 28, y, 26, 20).build());
				bx += 56;
			}
			Button b1 = Button.builder(Component.literal("+1"), b -> send(s, 1)).bounds(bx, y, 26, 20).build();
			Button b5 = Button.builder(Component.literal("+5"), b -> send(s, 5)).bounds(bx + 28, y, 26, 20).build();
			b1.active = b5.active = test || d.points > 0;
			addRenderableWidget(b1);
			addRenderableWidget(b5);
			if (!test) label(Component.literal(s.blurb).withStyle(ChatFormatting.DARK_GRAY), bx + 60, y + 6);
			y += 22;
		}

		// abilities of the selected stat
		Stat s = stats[selStat];
		y += 2;
		pics.add(new Pic(STAT_ICONS[selStat], left, y - 3, 1));
		label(Component.literal(s.label + " abilities").withStyle(ChatFormatting.GOLD), left + 20, y);
		y += 12;
		label(Component.literal("click a name to switch it ON / OFF").withStyle(ChatFormatting.DARK_GRAY), left + 150, y - 12);
		for (int i = 0; i < s.abilities.length; i++) {
			Ability a = s.abilities[i];
			if (d.unlocked(s, i)) {
				boolean on = d.has(s, i);
				toggleLabel(PlayerData.abilityKey(s, i), on, a.name(), 0, left, y);
				label(Component.literal(on ? a.desc() : "(off)").withStyle(ChatFormatting.GRAY), left + font.width((on ? "[ON] " : "[OFF] ") + a.name()) + 8, y);
			} else {
				label(Component.literal("[needs " + a.need() + "] " + a.name()).withStyle(ChatFormatting.DARK_GRAY), left, y);
			}
			y += 10;
		}
	}

	private void send(Stat s, int n) { ClientPlayNetworking.send(new AllocatePayload(s.id(), n)); }

	// ------------------------------------------------------------------ SKILLS

	private static String trim(String t, int n) { return t.length() <= n ? t : t.substring(0, n - 2) + ".."; }

	/** Skills are grouped by tier; pick a tier and its skills are listed in aligned columns. */
	private void skills(PlayerData d, int cx, int y) {
		int left = cx - 205;
		PassiveSkill[] all = PassiveSkill.values();
		int found = 0;
		for (PassiveSkill s : all) if (d.rawRank(s) > 0) found++;
		label(Component.literal("Passive Skills discovered: " + found + "/" + all.length).withStyle(ChatFormatting.YELLOW), left, y);
		y += 14;

		// tier selector (highest first)
		SkillRarity[] tiers = SkillRarity.values();
		for (int k = tiers.length - 1; k >= 0; k--) {
			final int tier = k;
			int total = 0, own = 0;
			for (PassiveSkill s : all) if (s.rarity == tiers[k]) { total++; if (d.rawRank(s) > 0) own++; }
			String name = tiers[k].name() + " " + own + "/" + total;
			Component text = Component.literal((skillTier == k ? "[" + name + "]" : name)).withStyle(tiers[k].color);
			addRenderableWidget(Button.builder(text, b -> { skillTier = tier; rebuildWidgets(); })
				.bounds(left + (tiers.length - 1 - k) * 80, y, 78, 18).build());
		}
		y += 24;

		// aligned table: SKILL | RANK | EFFECT
		int colRank = left + 152, colDesc = left + 196;
		label(Component.literal("SKILL  (click a name: ON / OFF)").withStyle(ChatFormatting.DARK_GRAY), left, y);
		label(Component.literal("RANK").withStyle(ChatFormatting.DARK_GRAY), colRank, y);
		label(Component.literal("EFFECT").withStyle(ChatFormatting.DARK_GRAY), colDesc, y);
		rects.add(new Rect(left, y + 10, left + 400, y + 11, 0x60FFFFFF));
		y += 14;

		SkillRarity tr = tiers[skillTier];
		for (PassiveSkill s : all) {
			if (s.rarity != tr) continue;
			int rank = d.rawRank(s);
			if (rank > 0) {
				boolean on = !d.disabled.contains(s.id());
				String plain = (on ? "[ON] " : "[OFF] ") + s.displayName;
				// active = tier colour, inactive = grey + struck through
				Component name = on ? Component.literal(plain).withStyle(tr.color)
					: Component.literal(plain).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH);
				labels.add(new Label(name, left, y));
				clicks.add(new Click(left - 1, y - 1, left + font.width(plain) + 1, y + 9, s.id()));
				label(Component.literal(ROMAN[rank] + " / " + ROMAN[s.maxRank]).withStyle(rank >= s.maxRank ? ChatFormatting.GOLD : ChatFormatting.WHITE), colRank, y);
				label(Component.literal(trim(on ? s.desc : "(switched off)", 36)).withStyle(ChatFormatting.GRAY), colDesc, y);
			} else {
				label(Component.literal("???").withStyle(ChatFormatting.DARK_GRAY), left, y);
				label(Component.literal("- / " + ROMAN[s.maxRank]).withStyle(ChatFormatting.DARK_GRAY), colRank, y);
				label(Component.literal("Not discovered yet").withStyle(ChatFormatting.DARK_GRAY), colDesc, y);
			}
			y += 12;
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
		int index = 0;
		for (Quest q : d.quests) {
			final int qi = index++;
			SkillRarity r = QuestLogic.tierRarity(q.tier);
			String name = q.title();
			Component head = Component.literal("[" + r.name() + "] " + "*".repeat(Math.max(0, Math.min(4, q.tier)) + 1) + "  " + name + "  " + (int) q.progress + "/" + q.target).withStyle(r.color);
			if (q.claimed) head = head.copy().append(Component.literal("  CLAIMED").withStyle(ChatFormatting.GREEN));
			else if (q.done) head = head.copy().append(Component.literal("  COMPLETE").withStyle(ChatFormatting.YELLOW));
			label(head, left, y);
			int lineY = y + 11;
			label(Component.literal("Reward: " + QuestLogic.rewardLine1(q)).withStyle(ChatFormatting.GRAY), left + 8, lineY);

			// CLAIM button once the quest is complete; a green check mark after the reward was collected
			if (q.claimed) {
				tick(left + 300, y + 2);
				label(Component.literal("Claimed").withStyle(ChatFormatting.GREEN), left + 320, y + 6);
			} else if (q.done) {
				addRenderableWidget(Button.builder(Component.literal("CLAIM").withStyle(ChatFormatting.GREEN), b -> ClientPlayNetworking.send(new AllocatePayload("__claim", qi)))
					.bounds(left + 296, y - 1, 64, 18).build());
			}
			y += 21;
			label(Component.literal(QuestLogic.rewardLine2(q)).withStyle(ChatFormatting.DARK_GRAY), left + 8, y); y += 16;
		}
		label(Component.literal("Resets every Minecraft day. Rewards must be claimed - unclaimed ones are auto-claimed at the reset.").withStyle(ChatFormatting.DARK_GRAY), left, y);
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

	/** One ore-colour row: swatch + button. Click = next palette colour (saved to config). */
	private void colorRow(int row, int x, int top, String label, String key, int defaultRgb) {
		SoloConfig.Data c = SoloConfig.get();
		int cur = OreColors.override(key) != null ? OreColors.override(key) : defaultRgb;
		int y = top + row * 21;
		rects.add(new Rect(x, y + 3, x + 14, y + 17, 0xFF000000));
		rects.add(new Rect(x + 1, y + 4, x + 13, y + 16, 0xFF000000 | cur));
		addRenderableWidget(Button.builder(Component.literal(label), b -> {
			c.oreColors.put(key, OreColors.toHex(OreColors.next(cur)));
			SoloConfig.save();
			rebuildWidgets();
		}).bounds(x + 18, y, 152, 20).build());
	}

	/** One HUD-position row: < > ^ v move it, Reset puts it back. */
	private void moveRow(int row, int x, int top, String name, int[] pos, int defX, int defY, Runnable save) {
		int y = top + row * 26;
		label(Component.literal(name).withStyle(ChatFormatting.WHITE), x, y + 3);
		label(Component.literal("x " + pos[0] / 10 + "%   y " + pos[1] / 10 + "%").withStyle(ChatFormatting.GRAY), x, y + 14);
		int bx = x + 160;
		addRenderableWidget(Button.builder(Component.literal("<"), b -> { pos[0] = Math.max(0, pos[0] - hudStep); save.run(); }).bounds(bx, y, 22, 20).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> { pos[0] = Math.min(1000, pos[0] + hudStep); save.run(); }).bounds(bx + 24, y, 22, 20).build());
		addRenderableWidget(Button.builder(Component.literal("^"), b -> { pos[1] = Math.max(0, pos[1] - hudStep); save.run(); }).bounds(bx + 48, y, 22, 20).build());
		addRenderableWidget(Button.builder(Component.literal("v"), b -> { pos[1] = Math.min(1000, pos[1] + hudStep); save.run(); }).bounds(bx + 72, y, 22, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Reset"), b -> { pos[0] = defX; pos[1] = defY; save.run(); }).bounds(bx + 98, y, 50, 20).build());
	}

	/** A rate setting with a coloured bar: blue = harder than default, green = default, yellow..red = cheating. */
	private void rateRow(int row, int cx, int top, String name, Supplier<Double> get, Consumer<Double> set,
						 double step, double min, double max, double def, String fmt) {
		int y = top + row * 22, x = cx - 170;
		double v = get.get();
		label(Component.literal(name).withStyle(ChatFormatting.WHITE), x, y + 6);
		Button minus = Button.builder(Component.literal("-"), b -> {
			set.accept(Math.max(min, Math.round((get.get() - step) * 100) / 100.0));
			SoloConfig.save();
			rebuildWidgets();
		}).bounds(x + 110, y, 20, 20).build();
		int bx = x + 134, bw = 110;
		rects.add(new Rect(bx - 1, y + 4, bx + bw + 1, y + 16, 0xFF000000));
		rects.add(new Rect(bx, y + 5, bx + bw, y + 15, 0xFF202030));
		double frac = max > min ? (v - min) / (max - min) : 0;
		int fw = (int) Math.round(bw * Math.max(0, Math.min(1, frac)));
		double ratio = def > 0 ? v / def : 1;
		int color = v <= 0 ? 0xFF555555 : ratio < 0.9 ? 0xFF4F9BFF : ratio <= 1.1 ? 0xFF4CD964 : ratio <= 2.5 ? 0xFFFFD93D : ratio <= 5 ? 0xFFFF9A3C : 0xFFFF3030;
		if (fw > 0) rects.add(new Rect(bx, y + 5, bx + fw, y + 15, color));
		int dx = bx + (int) Math.round(bw * (def - min) / (max - min));
		rects.add(new Rect(dx, y + 3, dx + 1, y + 17, 0xFFFFFFFF)); // default marker
		Button plus = Button.builder(Component.literal("+"), b -> {
			set.accept(Math.min(max, Math.round((get.get() + step) * 100) / 100.0));
			SoloConfig.save();
			rebuildWidgets();
		}).bounds(bx + bw + 4, y, 20, 20).build();
		minus.active = v > min + 1e-9;
		plus.active = v < max - 1e-9;
		addRenderableWidget(minus);
		addRenderableWidget(plus);
		label(Component.literal(String.format(fmt, v)).withStyle(ChatFormatting.YELLOW), bx + bw + 28, y + 6);
	}

	private void config(int cx, int y) {
		SoloConfig.Data c = SoloConfig.get();
		int lx = cx - 190, rx = cx + 10;
		pics.add(new Pic(Icons.CONFIG, lx, y - 3, 1));
		label(Component.literal("CONFIG").withStyle(ChatFormatting.GOLD), lx + 20, y);
		int n = CFG_PAGES.length;
		addRenderableWidget(Button.builder(Component.literal("<"), b -> { cfgPage = (cfgPage + n - 1) % n; rebuildWidgets(); }).bounds(cx - 10, y - 5, 20, 16).build());
		addRenderableWidget(Button.builder(Component.literal("Page " + (cfgPage + 1) + "/" + n + ": " + CFG_PAGES[cfgPage]), b -> { cfgPage = (cfgPage + 1) % n; rebuildWidgets(); })
			.bounds(cx + 12, y - 5, 140, 16).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> { cfgPage = (cfgPage + 1) % n; rebuildWidgets(); }).bounds(cx + 154, y - 5, 20, 16).build());
		y += 18;

		String footer = "Gameplay options apply on the server / host. Saved to config/solosystem.json.";
		int footY;
		switch (cfgPage) {
			case 0 -> { // ---------------- PRESETS
				int cur = Presets.current();
				label(Component.literal("One click sets EXP, skill drops, loot, quest EXP, XP orbs, quests/day and resist cap.").withStyle(ChatFormatting.GRAY), lx, y);
				y += 14;
				for (int i = 0; i < Presets.ALL.length; i++) {
					final Presets.Preset p = Presets.ALL[i];
					addRenderableWidget(Button.builder(Component.literal((cur == i ? "> " : "") + p.name()).withStyle(PRESET_COLORS[i]), b -> {
						Presets.apply(p);
						rebuildWidgets();
					}).bounds(lx, y + i * 22, 150, 20).build());
					label(Component.literal(String.format("EXP x%.2g  Skills x%.2g  Loot x%.2g", p.exp(), p.skill(), p.loot())).withStyle(ChatFormatting.WHITE), lx + 158, y + i * 22 + 2);
					label(Component.literal(p.blurb()).withStyle(ChatFormatting.DARK_GRAY), lx + 158, y + i * 22 + 11);
				}
				footer = cur >= 0 ? "Current preset: " + Presets.ALL[cur].name() + "  (fine-tune it in the Rates page)"
					: "Current settings: Custom (edited in the Rates page). Pick a preset to reset them.";
				footY = y + Presets.ALL.length * 22 + 6;
			}
			case 1 -> { // ---------------- RATES
				int cur = Presets.current();
				label(Component.literal("Preset: " + (cur >= 0 ? Presets.ALL[cur].name() : "Custom")).withStyle(cur >= 0 ? PRESET_COLORS[cur] : ChatFormatting.WHITE), lx, y);
				label(Component.literal("white tick = default").withStyle(ChatFormatting.DARK_GRAY), cx + 40, y);
				y += 12;
				rateRow(0, cx, y, "EXP rate", () -> c.expMultiplier, v -> c.expMultiplier = v, 0.25, 0.25, 10, 1.0, "x%.2f");
				rateRow(1, cx, y, "Skill drops", () -> c.skillDropMultiplier, v -> c.skillDropMultiplier = v, 0.25, 0, 10, 1.0, "x%.2f");
				rateRow(2, cx, y, "Loot drops", () -> c.lootMultiplier, v -> c.lootMultiplier = v, 0.25, 0, 10, 1.0, "x%.2f");
				rateRow(3, cx, y, "Quest EXP", () -> c.questExpMultiplier, v -> c.questExpMultiplier = v, 0.25, 0.25, 10, 1.0, "x%.2f");
				rateRow(4, cx, y, "XP orb -> EXP", () -> c.orbExpFactor, v -> c.orbExpFactor = v, 0.25, 0, 10, 0.5, "x%.2f");
				rateRow(5, cx, y, "Quests / day", () -> (double) c.questsPerDay, v -> c.questsPerDay = (int) Math.round(v), 1, 1, 5, 3, "%.0f");
				rateRow(6, cx, y, "Resist cap %", () -> (double) c.resistMaxPercent, v -> c.resistMaxPercent = (int) Math.round(v), 5, 10, 95, 75, "%.0f");
				rateRow(7, cx, y, "Quest difficulty", () -> c.questDifficulty, v -> c.questDifficulty = v, 0.25, 0.25, 3, 1.0, "x%.2f");
				footY = y + 8 * 22 + 6;
			}
			case 2 -> { // ---------------- GENERAL
				toggle(0, lx, y, "Notifications", () -> c.notifications, v -> c.notifications = v);
				toggle(1, lx, y, "Menu pauses game", () -> c.menuPausesGame, v -> c.menuPausesGame = v);
				toggle(2, lx, y, "Dodge (MISS!)", () -> c.enableDodge, v -> c.enableDodge = v);
				toggle(3, lx, y, "Critical hits", () -> c.enableCrit, v -> c.enableCrit = v);
				addRenderableWidget(Button.builder(Component.literal("Speed FOV stretch: " + FOV_MODES[c.fovMode]), b -> {
					c.fovMode = c.fovMode == 2 ? 1 : c.fovMode == 1 ? 0 : 2; // Off -> Reduced -> Vanilla -> Off
					SoloConfig.save();
					rebuildWidgets();
				}).bounds(lx, y + 4 * 21, 180, 20).build());
				toggle(5, lx, y, "Toasts avoid overlap", () -> c.toastAvoidOverlap, v -> c.toastAvoidOverlap = v);
				number(6, lx, y, "Toast time (s)", () -> c.toastSeconds, v -> c.toastSeconds = v, 0.5, 0.5, 10, "%.1f");

				toggle(0, rx, y, "Resistance system", () -> c.enableResistance, v -> c.enableResistance = v);
				toggle(1, rx, y, "Masterwork crafting", () -> c.enableMasterwork, v -> c.enableMasterwork = v);
				toggle(2, rx, y, "Dominion (ally)", () -> c.enableDominion, v -> c.enableDominion = v);
				toggle(3, rx, y, "Mirror Ward (reflect)", () -> c.enableReflect, v -> c.enableReflect = v);
				toggle(4, rx, y, "Mod items in quests", () -> c.autoModItems, v -> c.autoModItems = v);
				toggle(5, rx, y, "TEST MODE (inf. points)", () -> c.testMode, v -> c.testMode = v);
				footer = c.testMode ? "TEST MODE is ON: change stats freely in the STATUS tab (host/server setting)."
					: "Speed FOV stretch: 'Off' stops the screen widening when Agility makes you fast (uses the FOV Effects slider).";
				footY = y + 7 * 21 + 6;
			}
			case 3 -> { // ---------------- SIXTH SENSE
				toggle(0, lx, y, "Sixth Sense HUD (key V)", () -> c.radar, v -> c.radar = v);
				toggle(1, lx, y, "World overlay", () -> c.senseEsp, v -> c.senseEsp = v);
				toggle(2, lx, y, "Show ores (pickaxe)", () -> c.showOres, v -> c.showOres = v);
				toggle(3, lx, y, "Show enemies (weapon)", () -> c.showEnemies, v -> c.showEnemies = v);
				toggle(4, lx, y, "Show animals (feed/tools)", () -> c.showAnimals, v -> c.showAnimals = v);
				toggle(5, lx, y, "Show chests / traps", () -> c.showSecrets, v -> c.showSecrets = v);
				toggle(6, lx, y, "Threat alert (red map)", () -> c.threatAlert, v -> c.threatAlert = v);

				number(0, rx, y, "Map size", () -> (double) c.radarSize, v -> c.radarSize = (int) Math.round(v), 10, 50, 200, "%.0f");
				number(1, rx, y, "Map opacity %", () -> (double) c.radarOpacity, v -> c.radarOpacity = (int) Math.round(v), 10, 10, 100, "%.0f");
				number(2, rx, y, "Overlay range", () -> (double) c.espRange, v -> c.espRange = (int) Math.round(v), 4, 8, 64, "%.0f");
				number(3, rx, y, "Threat range", () -> (double) c.threatRange, v -> c.threatRange = (int) Math.round(v), 2, 6, 48, "%.0f");
				footer = "Green = animal items, yellow = pickaxe, orange = weapon, RED = enemy close. Other mods: see the JSON file.";
				footY = y + 7 * 21 + 6;
			}
			case 4 -> { // ---------------- COLORS
				label(Component.literal("Click a colour to cycle it:").withStyle(ChatFormatting.GRAY), lx, y + 3);
				addRenderableWidget(Button.builder(Component.literal("Reset all colours"), b -> {
					c.oreColors.clear();
					SoloConfig.save();
					rebuildWidgets();
				}).bounds(rx + 30, y - 1, 140, 18).build());
				OreType[] types = OreType.values();
				for (int i = 0; i < types.length; i++) {
					int row = 1 + (i % 6), x = i < 6 ? lx : rx;
					colorRow(row, x, y, types[i].label, types[i].name(), types[i].color & 0xFFFFFF);
				}
				List<String[]> modded = SixthSense.moddedOres();
				label(Component.literal(modded.isEmpty() ? "No modded ores seen yet (hold a pickaxe near some)."
					: "Detected ores from other mods:").withStyle(ChatFormatting.GRAY), lx, y + 7 * 21 + 5);
				for (int i = 0; i < Math.min(2, modded.size()); i++) {
					String[] m = modded.get(i);
					colorRow(8, i == 0 ? lx : rx, y, m[1], m[0], OreColors.auto(m[0]));
				}
				footer = "Custom ore colours: \"modid:block\" or \"#modid:tag\" -> \"#RRGGBB\" in oreColors (JSON).";
				footY = y + 9 * 21 + 6;
			}
			case 5 -> { // ---------------- HUD LAYOUT
				addRenderableWidget(Button.builder(Component.literal("Move step: " + (hudStep / 10.0) + "%  (click to change)"), b -> {
					hudStep = hudStep == 10 ? 25 : hudStep == 25 ? 50 : hudStep == 50 ? 100 : 10;
					rebuildWidgets();
				}).bounds(lx, y - 2, 220, 18).build());
				addRenderableWidget(Button.builder(Component.literal("Drag & drop HUD..."), b -> Minecraft.getInstance().gui.setScreen(new HudEditScreen(this)))
					.bounds(lx + 226, y - 2, 140, 18).build());
				y += 22;
				Runnable save = SoloConfig::save;
				int[] radar = {c.radarX, c.radarY}, toast = {c.toastX, c.toastY}, abil = {c.abilityX, c.abilityY};
				moveRow(0, lx, y, "Sixth Sense map", radar, 1000, 0, () -> { c.radarX = radar[0]; c.radarY = radar[1]; save.run(); rebuildWidgets(); });
				moveRow(1, lx, y, "Quest notifications", toast, 0, 0, () -> { c.toastX = toast[0]; c.toastY = toast[1]; save.run(); rebuildWidgets(); });
				moveRow(2, lx, y, "Dominion cooldown", abil, 0, 1000, () -> { c.abilityX = abil[0]; c.abilityY = abil[1]; save.run(); rebuildWidgets(); });
				footer = "The HUD behind this window moves live. Open Controls to rebind the keys (Y menu, V Sixth Sense).";
				footY = y + 3 * 26 + 6;
			}
			default -> { // ---------------- MODS: edit the lists that make other mods work with Solo System
				boolean tiered = modCat == 2 || modCat == 3;
				addRenderableWidget(Button.builder(Component.literal("List: " + MOD_LISTS[modCat] + "  >"), b -> {
					modCat = (modCat + 1) % MOD_LISTS.length;
					modPage = 0;
					modText = "";
					rebuildWidgets();
				}).bounds(lx, y - 2, 220, 18).build());
				if (tiered) {
					SkillRarity tr = SkillRarity.values()[modTier];
					addRenderableWidget(Button.builder(Component.literal("Tier: " + tr.name()).withStyle(tr.color), b -> {
						modTier = (modTier + 1) % 5;
						modPage = 0;
						rebuildWidgets();
					}).bounds(lx + 226, y - 2, 140, 18).build());
				}
				y += 22;

				EditBox box = new EditBox(font, lx, y, 220, 18, Component.literal("entry"));
				box.setMaxLength(120);
				box.setHint(Component.literal(modHint()));
				box.setValue(modText);
				box.setResponder(v -> { modText = v; modStatus = modStatusFor(v); });
				addRenderableWidget(box);
				addRenderableWidget(Button.builder(Component.literal("Add"), b -> { addModEntry(); rebuildWidgets(); }).bounds(lx + 226, y - 1, 60, 20).build());
				modStatus = modStatusFor(modText);
				modStatusX = lx;
				modStatusY = y + 22;
				y += 36;

				// current entries of this list, with remove buttons
				List<String> entries = modEntries();
				int perPage = 6, pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
				modPage = Math.max(0, Math.min(pages - 1, modPage));
				label(Component.literal("Entries: " + entries.size() + "   (page " + (modPage + 1) + "/" + pages + ")").withStyle(ChatFormatting.GRAY), lx, y - 12);
				Button prev = Button.builder(Component.literal("<"), b -> { modPage--; rebuildWidgets(); }).bounds(lx + 300, y - 15, 20, 14).build();
				Button next = Button.builder(Component.literal(">"), b -> { modPage++; rebuildWidgets(); }).bounds(lx + 322, y - 15, 20, 14).build();
				prev.active = modPage > 0;
				next.active = modPage < pages - 1;
				addRenderableWidget(prev);
				addRenderableWidget(next);
				for (int i = modPage * perPage, row = 0; i < Math.min(entries.size(), (modPage + 1) * perPage); i++, row++) {
					final String key = entries.get(i);
					int ry = y + row * 17;
					label(Component.literal(trim(key, 44)).withStyle(ChatFormatting.WHITE), lx + 4, ry + 3);
					if (modCat == 4) {
						Integer col = OreColors.override(key);
						int cur = col == null ? 0xFFFFFF : col;
						rects.add(new Rect(lx + 268, ry + 1, lx + 280, ry + 13, 0xFF000000));
						rects.add(new Rect(lx + 269, ry + 2, lx + 279, ry + 12, 0xFF000000 | cur));
						addRenderableWidget(Button.builder(Component.literal("~"), b -> {
							SoloConfig.get().oreColors.put(key, OreColors.toHex(OreColors.next(cur)));
							SoloConfig.save();
							rebuildWidgets();
						}).bounds(lx + 284, ry, 18, 15).build());
					}
					addRenderableWidget(Button.builder(Component.literal("x"), b -> { removeModEntry(key); rebuildWidgets(); }).bounds(lx + 306, ry, 18, 15).build());
				}

				// which other mods are installed
				StringBuilder sb = new StringBuilder();
				int count = 0;
				for (net.fabricmc.loader.api.ModContainer mc : FabricLoader.getInstance().getAllMods()) {
					String id = mc.getMetadata().getId();
					if (id.equals("minecraft") || id.equals("java") || id.equals("fabricloader") || id.equals("fabric") || id.equals("solosystem")
						|| id.startsWith("fabric-") || id.startsWith("mixinextras") || id.startsWith("fabric_")) continue;
					count++;
					if (sb.length() < 70) sb.append(sb.length() > 0 ? ", " : "").append(id);
				}
				label(Component.literal("Other mods detected (" + count + "): " + (count == 0 ? "none" : trim(sb.toString(), 70))).withStyle(ChatFormatting.AQUA), lx, y + 6 * 17 + 6);
				footer = "Saved to config/solosystem.json. Items: modid:item,min,max  -  anything you add also works for mods loaded on the server.";
				footY = y + 6 * 17 + 20;
			}
		}
		label(Component.literal(footer).withStyle(c.testMode && cfgPage == 2 ? ChatFormatting.RED : ChatFormatting.DARK_GRAY), lx, footY);
	}

	// ------------------------------------------------------------------ Mods page helpers

	private String modHint() {
		return switch (modCat) {
			case 0, 1 -> "modid:entity_id";
			case 2, 3 -> "modid:item,min,max";
			case 4 -> "modid:block  or  #modid:tag";
			default -> "keyword";
		};
	}

	private List<String> modEntries() {
		SoloConfig.Data c = SoloConfig.get();
		List<String> out = new ArrayList<>();
		String[] src = switch (modCat) {
			case 0 -> c.extraEnemies;
			case 1 -> c.ignoredEnemies;
			case 2 -> c.extraQuestRewards[modTier];
			case 3 -> c.extraLoot[modTier];
			case 5 -> c.pickKeywords;
			case 6 -> c.weaponKeywords;
			case 7 -> c.animalKeywords;
			default -> null;
		};
		if (modCat == 4) {
			for (String k : c.oreColors.keySet()) {
				boolean builtin = false;
				for (OreType t : OreType.values()) if (t.name().equals(k)) builtin = true;
				if (!builtin) out.add(k);
			}
		} else if (src != null) {
			for (String e : src) if (e != null && !e.isBlank()) out.add(e);
		}
		return out;
	}

	private void setModEntries(List<String> list) {
		SoloConfig.Data c = SoloConfig.get();
		String[] arr = list.toArray(new String[0]);
		switch (modCat) {
			case 0 -> c.extraEnemies = arr;
			case 1 -> c.ignoredEnemies = arr;
			case 2 -> c.extraQuestRewards[modTier] = arr;
			case 3 -> c.extraLoot[modTier] = arr;
			case 5 -> c.pickKeywords = arr;
			case 6 -> c.weaponKeywords = arr;
			case 7 -> c.animalKeywords = arr;
			default -> { }
		}
		SoloConfig.save();
	}

	private void addModEntry() {
		String t = modText.trim();
		if (t.isEmpty()) return;
		SoloConfig.Data c = SoloConfig.get();
		if (modCat == 4) {
			if (!c.oreColors.containsKey(t)) c.oreColors.put(t, OreColors.toHex(OreColors.PALETTE[c.oreColors.size() % OreColors.PALETTE.length]));
			SoloConfig.save();
		} else {
			List<String> list = modEntries();
			if (list.contains(t)) return;
			list.add(t);
			setModEntries(list);
		}
		modText = "";
	}

	private void removeModEntry(String key) {
		if (modCat == 4) {
			SoloConfig.get().oreColors.remove(key);
			SoloConfig.save();
		} else {
			List<String> list = modEntries();
			list.remove(key);
			setModEntries(list);
		}
	}

	private static boolean registryHas(net.minecraft.core.Registry<?> reg, String id) {
		try {
			int i = id.indexOf(':');
			Identifier ident = Identifier.fromNamespaceAndPath(i < 0 ? "minecraft" : id.substring(0, i).trim(), i < 0 ? id.trim() : id.substring(i + 1).trim());
			return reg.getOptional(ident).isPresent();
		} catch (Exception e) {
			return false;
		}
	}

	/** Live feedback under the text box: does what you typed exist in this game? */
	private Component modStatusFor(String v) {
		String t = v == null ? "" : v.trim();
		if (t.isEmpty()) return Component.literal("Type an id and press Add").withStyle(ChatFormatting.DARK_GRAY);
		boolean ok;
		String what;
		switch (modCat) {
			case 0, 1 -> { ok = registryHas(BuiltInRegistries.ENTITY_TYPE, t); what = "entity"; }
			case 2, 3 -> { ok = ItemIds.item(t.split(",")[0].trim()) != null; what = "item"; }
			case 4 -> { ok = t.startsWith("#") || registryHas(BuiltInRegistries.BLOCK, t); what = t.startsWith("#") ? "tag" : "block"; }
			default -> { return Component.literal("Matched against held item ids").withStyle(ChatFormatting.GRAY); }
		}
		if (what.equals("tag")) return Component.literal("Tag - colours every block with this tag").withStyle(ChatFormatting.GREEN);
		return ok ? Component.literal("Found: this " + what + " exists in your game").withStyle(ChatFormatting.GREEN)
			: Component.literal("Not found here (mod not installed?) - you can still add it").withStyle(ChatFormatting.YELLOW);
	}

	// ------------------------------------------------------------------ render

	/** Click on a skill / ability name: switch it ON / OFF. */
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for (Click c : clicks) {
			if (lastMouseX >= c.x1() && lastMouseX < c.x2() && lastMouseY >= c.y1() && lastMouseY < c.y2()) {
				ClientPlayNetworking.send(new AllocatePayload("__toggle:" + c.key(), 0));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		for (Rect r : rects) graphics.fill(r.x1(), r.y1(), r.x2(), r.y2(), r.color());
		for (Pic p : pics) Icons.draw(graphics, p.icon(), p.x(), p.y(), p.scale());
		for (Label l : labels) graphics.text(this.font, l.text(), l.x(), l.y(), 0xFFFFFFFF, true);

		if (tab == 4 && cfgPage == 6) graphics.text(this.font, modStatus, modStatusX, modStatusY, 0xFFFFFFFF, true);

		// clickable skill / ability names: hover = underline (the click itself is handled in mouseClicked)
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		for (Click c : clicks) {
			boolean over = mouseX >= c.x1() && mouseX < c.x2() && mouseY >= c.y1() && mouseY < c.y2();
			if (over) graphics.fill(c.x1() + 1, c.y2() - 1, c.x2() - 1, c.y2(), 0xFFFFFFFF);
		}
		if (tab == 2 && !ClientState.data.quests.isEmpty()) {
			graphics.text(this.font, Component.literal("Time left: " + timeLeft()).withStyle(ChatFormatting.RED),
				timerX, timerY, 0xFFFFFFFF, true);
		}
	}
}
