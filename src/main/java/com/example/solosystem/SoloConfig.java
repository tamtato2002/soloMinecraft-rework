package com.example.solosystem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Simple JSON config: config/solosystem.json (also editable from the in-game CONFIG tab). */
public final class SoloConfig {
	public static final class Data {
		public String _help = "Item lists use 'modid:item,min,max' (works with items from other mods). Index 0..4 = Common..Legendary.";

		// ---- gameplay
		public double expMultiplier = 1.0;
		public double skillDropMultiplier = 1.0;
		public double lootMultiplier = 1.0;
		public double questExpMultiplier = 1.0;
		public int questsPerDay = 3;
		public boolean enableDodge = true;
		public boolean enableCrit = true;
		public boolean enableResistance = true;
		public boolean enableMasterwork = true;
		public boolean enableDominion = true;
		public boolean enableReflect = true;
		public boolean testMode = false;     // unlimited stat points (+ -1/-5/reset buttons) for testing builds
		public double orbExpFactor = 0.5;    // System EXP per vanilla/modded XP point picked up
		public boolean autoModItems = true; // quest rewards may include items from other mods automatically
		public int resistMaxPercent = 75;

		// ---- client / HUD
		public boolean notifications = true;
		public double toastSeconds = 2.0;
		public boolean radar = true;          // Sixth Sense HUD
		public boolean senseEsp = true;       // Sixth Sense in-world overlay (ore markers / enemy HP bars)
		public int radarSize = 90;
		public int radarCorner = 0; // 0 top-right, 1 top-left, 2 bottom-right, 3 bottom-left
		public boolean menuPausesGame = false;

		// ---- extra items (e.g. weapons from other mods) added to the random pools
		public String[][] extraQuestRewards = {{}, {}, {}, {}, {}};
		public String[][] extraLoot = {{}, {}, {}, {}, {}};
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Data data;

	private SoloConfig() {}

	private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("solosystem.json"); }

	public static Data get() {
		if (data == null) load();
		return data;
	}

	public static synchronized void load() {
		Data d = null;
		try {
			Path p = path();
			if (Files.exists(p)) {
				try (Reader r = Files.newBufferedReader(p)) { d = GSON.fromJson(r, Data.class); }
			}
		} catch (Exception e) {
			d = null;
		}
		data = d != null ? d : new Data();
		clamp(data);
		save();
	}

	public static synchronized void save() {
		try {
			clamp(data);
			try (Writer w = Files.newBufferedWriter(path())) { GSON.toJson(data, w); }
		} catch (Exception ignored) {}
	}

	private static void clamp(Data d) {
		d.expMultiplier = Math.max(0.1, Math.min(10, d.expMultiplier));
		d.skillDropMultiplier = Math.max(0, Math.min(10, d.skillDropMultiplier));
		d.lootMultiplier = Math.max(0, Math.min(10, d.lootMultiplier));
		d.questExpMultiplier = Math.max(0.1, Math.min(10, d.questExpMultiplier));
		d.questsPerDay = Math.max(1, Math.min(5, d.questsPerDay));
		d.resistMaxPercent = Math.max(5, Math.min(95, d.resistMaxPercent));
		d.toastSeconds = Math.max(0.5, Math.min(10, d.toastSeconds));
		d.radarSize = Math.max(50, Math.min(200, d.radarSize));
		d.orbExpFactor = Math.max(0, Math.min(10, d.orbExpFactor));
		d.radarCorner = Math.max(0, Math.min(3, d.radarCorner));
		if (d.extraQuestRewards == null || d.extraQuestRewards.length < 5) d.extraQuestRewards = new String[][]{{}, {}, {}, {}, {}};
		if (d.extraLoot == null || d.extraLoot.length < 5) d.extraLoot = new String[][]{{}, {}, {}, {}, {}};
	}
}
