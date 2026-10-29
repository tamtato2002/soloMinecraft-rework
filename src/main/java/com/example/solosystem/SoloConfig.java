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
		public double questDifficulty = 1.0; // multiplies quest targets (0.25 = a quarter of the work, 0.5 = half as much work, 2.0 = double)
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
		public boolean toastAvoidOverlap = true; // quest toasts move below other toasts (advancements, other mods) in the same spot
		public double toastSeconds = 2.0;
		public boolean radar = true;          // Sixth Sense HUD
		public boolean senseEsp = true;       // Sixth Sense in-world overlay (ore markers / enemy HP bars)
		public int radarSize = 90;
		public int radarCorner = 0; // (old) unused, replaced by radarX / radarY

		// ---- Sixth Sense
		public boolean showOres = true;
		public boolean showEnemies = true;
		public boolean showSecrets = true;
		public boolean showAnimals = true;   // green mode while holding feed / husbandry items
		public boolean threatAlert = true;   // map turns red when a hostile is close
		public int threatRange = 20;
		/** speed (Agility) stretches the screen through the FOV effect: 0 = vanilla, 1 = reduced, 2 = off */
		public int fovMode = 2;
		public double savedFovEffect = -1;   // the player's own "FOV Effects" slider, restored when fovMode = 0
		/** held item names (contains) that switch Sixth Sense to animal mode */
		public String[] animalKeywords = {"wheat", "seeds", "carrot", "potato", "beetroot", "hay_block", "lead", "shears", "name_tag", "saddle",
			"bone", "cod", "salmon", "berries", "kelp", "bamboo", "dandelion", "golden_apple", "slime_ball", "torchflower", "pitcher_pod", "milk_bucket"};
		public int espRange = 36;       // max distance (blocks) for in-world ore markers / enemy HP bars
		public int radarOpacity = 63;   // map background opacity in %

		// ---- HUD positions: 0..1000 = fraction of the free screen space (left->right, top->bottom)
		public int radarX = 1000, radarY = 0;     // default: top-right
		public int toastX = 0, toastY = 0;        // default: top-left
		public int abilityX = 0, abilityY = 1000; // default: bottom-left

		// ---- other mods
		/** ore colours: "DIAMOND" (built-in type), "modid:block" / "modid:base_name" (modded ore) or "#modid:tag" -> "#RRGGBB" */
		public java.util.Map<String, String> oreColors = new java.util.LinkedHashMap<>();
		/** entity ids ("modid:thing") that Sixth Sense / Dominion / EXP always treat as enemies */
		public String[] extraEnemies = {};
		/** entity ids that are never treated as enemies */
		public String[] ignoredEnemies = {};
		/** held item names that switch Sixth Sense to ore mode / enemy mode (matched against the end of the item id) */
		public String[] pickKeywords = {"pickaxe", "_pick"};
		public String[] weaponKeywords = {"sword", "axe", "trident", "mace", "bow", "spear", "dagger", "scythe", "katana", "rapier", "staff", "wand"};
		public boolean menuPausesGame = false;

		// ---- extra items (e.g. weapons from other mods) added to the random pools
		public String[][] extraQuestRewards = {{}, {}, {}, {}, {}};
		public String[][] extraLoot = {{}, {}, {}, {}, {}};
	}

	/** bumped on every save so cached colours are re-read */
	public static int version = 0;

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
			version++;
			try (Writer w = Files.newBufferedWriter(path())) { GSON.toJson(data, w); }
		} catch (Exception ignored) {}
	}

	private static void clamp(Data d) {
		d.expMultiplier = Math.max(0.1, Math.min(10, d.expMultiplier));
		d.skillDropMultiplier = Math.max(0, Math.min(10, d.skillDropMultiplier));
		d.lootMultiplier = Math.max(0, Math.min(10, d.lootMultiplier));
		d.questExpMultiplier = Math.max(0.1, Math.min(10, d.questExpMultiplier));
		d.questDifficulty = Math.max(0.25, Math.min(3.0, d.questDifficulty));
		d.questsPerDay = Math.max(1, Math.min(5, d.questsPerDay));
		d.resistMaxPercent = Math.max(5, Math.min(95, d.resistMaxPercent));
		d.toastSeconds = Math.max(0.5, Math.min(10, d.toastSeconds));
		d.radarSize = Math.max(50, Math.min(200, d.radarSize));
		d.orbExpFactor = Math.max(0, Math.min(10, d.orbExpFactor));
		d.radarX = Math.max(0, Math.min(1000, d.radarX)); d.radarY = Math.max(0, Math.min(1000, d.radarY));
		d.toastX = Math.max(0, Math.min(1000, d.toastX)); d.toastY = Math.max(0, Math.min(1000, d.toastY));
		d.abilityX = Math.max(0, Math.min(1000, d.abilityX)); d.abilityY = Math.max(0, Math.min(1000, d.abilityY));
		d.radarOpacity = Math.max(10, Math.min(100, d.radarOpacity));
		d.espRange = Math.max(8, Math.min(64, d.espRange));
		d.threatRange = Math.max(6, Math.min(48, d.threatRange));
		d.fovMode = Math.max(0, Math.min(2, d.fovMode));
		if (d.animalKeywords == null) d.animalKeywords = new String[]{"wheat", "seeds", "carrot"};
		if (d.oreColors == null) d.oreColors = new java.util.LinkedHashMap<>();
		if (d.extraEnemies == null) d.extraEnemies = new String[0];
		if (d.ignoredEnemies == null) d.ignoredEnemies = new String[0];
		if (d.pickKeywords == null) d.pickKeywords = new String[]{"pickaxe", "_pick"};
		if (d.weaponKeywords == null) d.weaponKeywords = new String[]{"sword", "axe", "bow"};
		d.radarCorner = Math.max(0, Math.min(3, d.radarCorner));
		if (d.extraQuestRewards == null || d.extraQuestRewards.length < 5) d.extraQuestRewards = new String[][]{{}, {}, {}, {}, {}};
		if (d.extraLoot == null || d.extraLoot.length < 5) d.extraLoot = new String[][]{{}, {}, {}, {}, {}};
	}
}
