package com.example.solosystem.client;

import com.example.solosystem.PlayerData;
import com.example.solosystem.Quest;
import com.example.solosystem.QuestLogic;
import com.example.solosystem.QuestType;
import com.example.solosystem.SkillRarity;
import com.example.solosystem.SoloConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Quest progress / clear notifications on the left side of the screen.
 * Animation is driven by real time (not game ticks), so it stays smooth at any frame rate:
 * eased slide-in/out, toasts glide to their slot when others disappear, and the bar fills smoothly.
 */
public final class QuestToasts {
	private static final int W = 160, H = 34, GAP = 4, TOP = 36;
	private static final double SLIDE_IN = 240, SLIDE_OUT = 280; // milliseconds

	private static final class Toast {
		String key, name;
		int cur, target, tier;
		boolean done, placed;
		double born, expire;
		float y, shown;
	}

	private static final List<Toast> LIST = new ArrayList<>();
	private static double lastFrame = 0;

	private QuestToasts() {}

	private static double nowMs() { return System.nanoTime() / 1_000_000.0; }

	private static double lifeMs(boolean done) { return SoloConfig.get().toastSeconds * 1000.0 + (done ? 1500 : 0); }

	/** Compare old and new quest data and pop a toast for anything that moved. */
	public static void diff(PlayerData old, PlayerData now) {
		if (old.quests.isEmpty() || now.quests.isEmpty()) return;
		for (Quest q : now.quests) {
			Quest o = null;
			for (Quest x : old.quests) if (x.type.equals(q.type) && x.tier == q.tier && x.target == q.target) o = x;
			if (o == null) continue;
			QuestType t = QuestType.byId(q.type);
			String name = t == null ? q.type : t.label;
			if (q.done && !o.done) push(q.type, name, q.target, q.target, q.tier, true, (int) o.progress);
			else if (!q.done && (int) q.progress > (int) o.progress) push(q.type, name, (int) q.progress, q.target, q.tier, false, (int) o.progress);
		}
	}

	private static void push(String key, String name, int cur, int target, int tier, boolean done, int from) {
		if (!SoloConfig.get().notifications) return;
		double now = nowMs();
		for (Toast t : LIST) {
			if (t.key.equals(key)) {
				t.cur = cur; t.target = target; t.done = done; t.name = name;
				t.expire = now + lifeMs(done);
				if (now - t.born > SLIDE_IN) t.born = now - SLIDE_IN; // already on screen: just refresh, no re-slide
				return;
			}
		}
		Toast t = new Toast();
		t.key = key; t.name = name; t.cur = cur; t.target = target; t.tier = tier; t.done = done;
		t.born = now;
		t.expire = now + SLIDE_IN + lifeMs(done);
		t.shown = Math.min(1f, from / (float) Math.max(1, target)); // bar animates from the previous value
		LIST.add(t);
	}

	public static void tick() {
		double now = nowMs();
		LIST.removeIf(t -> now > t.expire);
	}

	public static void render(GuiGraphicsExtractor g) {
		if (LIST.isEmpty()) { lastFrame = 0; return; }
		var font = Minecraft.getInstance().font;
		double now = nowMs();
		float dt = (float) (lastFrame == 0 ? 0.016 : Math.min(100, now - lastFrame) / 1000.0);
		lastFrame = now;

		for (Iterator<Toast> it = LIST.iterator(); it.hasNext(); ) if (now > it.next().expire) it.remove();

		int slot = 0;
		for (Toast t : LIST) {
			float targetY = TOP + slot * (H + GAP);
			if (!t.placed) { t.y = targetY; t.placed = true; }
			t.y += (targetY - t.y) * (1f - (float) Math.exp(-dt * 14f));
			float goal = Math.min(1f, t.cur / (float) Math.max(1, t.target));
			t.shown += (goal - t.shown) * (1f - (float) Math.exp(-dt * 9f));

			// eased slide: ease-out cubic when entering, ease-in cubic when leaving
			double inP = Math.min(1.0, (now - t.born) / SLIDE_IN);
			double inE = 1.0 - Math.pow(1.0 - inP, 3);
			double leave = 1.0 - Math.min(1.0, (t.expire - now) / SLIDE_OUT);
			double outE = 1.0 - leave * leave * leave;
			double k = Math.max(0.0, Math.min(inE, outE));
			int x = 6 - (int) Math.round((W + 10) * (1.0 - k));
			int y = Math.round(t.y);

			SkillRarity r = QuestLogic.tierRarity(t.tier);
			int[] rgbByTier = {0xFFFFFF, 0x55FF55, 0x55FFFF, 0xFF55FF, 0xFFAA00};
			int accent = t.done ? 0xFF4CD964 : (0xFF000000 | rgbByTier[Math.max(0, Math.min(4, t.tier))]);

			// panel + border + left accent bar
			g.fill(x, y, x + W, y + H, 0xE0101828);
			g.fill(x, y, x + W, y + 1, accent);
			g.fill(x, y + H - 1, x + W, y + H, accent);
			g.fill(x + W - 1, y, x + W, y + H, accent);
			g.fill(x, y, x + 3, y + H, accent);

			String title = t.done ? "QUEST CLEAR!" : "DAILY QUEST  [" + r.name() + "]";
			g.text(font, Component.literal(title).withStyle(t.done ? ChatFormatting.GREEN : ChatFormatting.GRAY), x + 8, y + 4, 0xFFFFFFFF, true);
			g.text(font, Component.literal(t.name + "   " + t.cur + "/" + t.target), x + 8, y + 15, 0xFFFFFFFF, true);

			// progress bar (smoothly animated)
			int bx = x + 8, by = y + 27, bw = W - 16;
			g.fill(bx, by, bx + bw, by + 3, 0xFF2A3350);
			g.fill(bx, by, bx + (int) (bw * Math.min(1f, t.shown)), by + 3, accent);

			if (t.done) check(g, x + W - 24, y + 5, 0xFF55FF55);
			slot++;
		}
	}

	/** Pixel-art green tick. */
	private static void check(GuiGraphicsExtractor g, int x, int y, int color) {
		int[][] px = {{0, 3}, {1, 4}, {2, 5}, {3, 4}, {4, 3}, {5, 2}, {6, 1}, {7, 0}};
		for (int[] p : px) g.fill(x + p[0] * 2, y + p[1] * 2, x + p[0] * 2 + 3, y + p[1] * 2 + 3, color);
	}
}
