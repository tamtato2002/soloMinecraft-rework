package com.example.solosystem.client;

import com.example.solosystem.PlayerData;
import com.example.solosystem.Quest;
import com.example.solosystem.QuestLogic;
import com.example.solosystem.QuestType;
import com.example.solosystem.SkillRarity;
import com.example.solosystem.SoloConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Small slide-in notifications on the left side of the screen for Daily Quest progress / clear. */
public final class QuestToasts {
	private static final int W = 160, H = 34;
	private static final int SLIDE = 4;           // quick slide, like Minecraft advancement pop-ups

	private static int lifeProgress() { return (int) (SoloConfig.get().toastSeconds * 20) + SLIDE * 2; }
	private static int lifeDone() { return lifeProgress() + 30; }

	private static final class Toast {
		String key, name;
		int cur, target, tier, age, life;
		boolean done;
	}

	private static final List<Toast> LIST = new ArrayList<>();

	private QuestToasts() {}

	/** Compare old and new quest data and pop a toast for anything that moved. */
	public static void diff(PlayerData old, PlayerData now) {
		if (old.quests.isEmpty() || now.quests.isEmpty()) return;
		for (Quest q : now.quests) {
			Quest o = null;
			for (Quest x : old.quests) if (x.type.equals(q.type) && x.tier == q.tier && x.target == q.target) o = x;
			if (o == null) continue;
			QuestType t = QuestType.byId(q.type);
			String name = t == null ? q.type : t.label;
			if (q.done && !o.done) push(q.type, name, q.target, q.target, q.tier, true);
			else if (!q.done && (int) q.progress > (int) o.progress) push(q.type, name, (int) q.progress, q.target, q.tier, false);
		}
	}

	private static void push(String key, String name, int cur, int target, int tier, boolean done) {
		if (!SoloConfig.get().notifications) return;
		for (Toast t : LIST) {
			if (t.key.equals(key)) {
				t.cur = cur; t.target = target; t.done = done; t.name = name;
				t.life = done ? lifeDone() : lifeProgress();
				t.age = Math.min(t.age, SLIDE); // keep it on screen without sliding in again
				return;
			}
		}
		Toast t = new Toast();
		t.key = key; t.name = name; t.cur = cur; t.target = target; t.tier = tier; t.done = done;
		t.life = done ? lifeDone() : lifeProgress();
		LIST.add(t);
	}

	public static void tick() {
		for (int i = LIST.size() - 1; i >= 0; i--) {
			Toast t = LIST.get(i);
			t.age++;
			if (t.age >= t.life) LIST.remove(i);
		}
	}

	public static void render(GuiGraphicsExtractor g) {
		if (LIST.isEmpty()) return;
		var font = Minecraft.getInstance().font;
		int y = 36;
		for (Toast t : LIST) {
			float in = Math.min(1f, t.age / (float) SLIDE);
			float out = Math.min(1f, (t.life - t.age) / (float) SLIDE);
			float k = Math.max(0f, Math.min(in, out));
			float ease = 1f - (1f - k) * (1f - k);
			int x = 6 - (int) ((W + 10) * (1f - ease));

			SkillRarity r = QuestLogic.tierRarity(Math.max(0, Math.min(4, t.tier)));
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

			// progress bar
			int bx = x + 8, by = y + 27, bw = W - 16;
			g.fill(bx, by, bx + bw, by + 3, 0xFF2A3350);
			int fw = (int) (bw * Math.min(1.0, t.cur / (double) Math.max(1, t.target)));
			g.fill(bx, by, bx + fw, by + 3, accent);

			if (t.done) check(g, x + W - 24, y + 5, 0xFF55FF55);
			y += H + 4;
		}
	}

	/** Pixel-art green tick. */
	private static void check(GuiGraphicsExtractor g, int x, int y, int color) {
		int[][] px = {{0, 3}, {1, 4}, {2, 5}, {3, 4}, {4, 3}, {5, 2}, {6, 1}, {7, 0}};
		for (int[] p : px) g.fill(x + p[0] * 2, y + p[1] * 2, x + p[0] * 2 + 3, y + p[1] * 2 + 3, color);
	}
}
