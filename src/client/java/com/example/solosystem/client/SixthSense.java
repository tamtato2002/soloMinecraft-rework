package com.example.solosystem.client;

import com.example.solosystem.ItemIds;
import com.example.solosystem.LootLogic;
import com.example.solosystem.Mobs;
import com.example.solosystem.SoloTags;
import com.example.solosystem.PlayerData;
import com.example.solosystem.SoloConfig;
import com.example.solosystem.Stat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "Sixth Sense" (Sense 20). A mini-map plus an in-world overlay that depends on what you hold:
 *  - pickaxe : every ore around you, colour-coded by ore type (range grows with Sense)
 *  - weapon  : enemies in red, with name and a segmented HP bar above their heads
 *  - feed / husbandry items : animals in green (name + HP)
 *  - other   : plain enemy radar
 * The map turns RED and flashes whenever a hostile is close (threat alert), whatever you hold.
 * Sense 40 ("Dungeon & Secret Perception") also reveals chests, trapped chests and spawners.
 */
public final class SixthSense {
	private enum Mode { NONE, PICK, WEAPON, ANIMAL }

	// kind: 0 ore, 1 chest, 2 trapped chest, 3 spawner
	/** group: shared by variants (deepslate / nether...) of the same ore; type: built-in type or null for modded ores */
	private record Ore(String group, String label, OreType type, String blockId, int auto) {}

	private record Dot(double x, double y, double z, int kind, Ore ore) {}

	private static final List<Dot> BLOCKS = new ArrayList<>();
	private static List<Dot> acc = new ArrayList<>();
	private static final Map<Block, Ore> ORE_MAP = new HashMap<>();
	private static Object oreLevel;

	private static int scanSlice = Integer.MAX_VALUE, scanR = 0, scanWait = 0;
	private static BlockPos scanCenter = BlockPos.ZERO;

	private static final List<Entity> ENEMIES = new ArrayList<>();
	private static final List<Entity> ANIMALS = new ArrayList<>();
	private static final List<Dot> NEAR = new ArrayList<>(); // fresh scan right around the player
	private static boolean threat;
	private static int nearTimer;
	private static final Map<Integer, double[]> POS = new HashMap<>();
	private static final double[] PLAYER = new double[6];
	private static int mobTimer;
	private static double lastTickMs;

	private SixthSense() {}

	public static double mobRadius(int sense) { return Math.min(64, 16 + (sense - 20)); }
	public static int blockRadius(int sense) { return (int) Math.min(40, 12 + 0.5 * (sense - 20)); }

	private static double nowMs() { return System.nanoTime() / 1_000_000.0; }

	/** {x, y, w, h, minX, maxX, minY, maxY} of the map - shared with the drag editor. */
	public static int[] radarRect(int gw, int gh, SoloConfig.Data cfg) {
		int size = cfg.radarSize, m = 4;
		int minX = m, maxX = Math.max(minX, gw - size - m), minY = m, maxY = Math.max(minY, gh - size - m);
		int x = minX + (int) Math.round((maxX - minX) * cfg.radarX / 1000.0);
		int y = minY + (int) Math.round((maxY - minY) * cfg.radarY / 1000.0);
		return new int[]{x, y, size, size, minX, maxX, minY, maxY};
	}

	private static boolean active(Minecraft mc) {
		return SoloConfig.get().radar && mc.player != null && mc.level != null && ClientState.data.has(Stat.SENSE, 1);
	}

	private static boolean endsWithAny(String p, String[] list) {
		if (list == null) return false;
		for (String k : list) if (k != null && !k.isBlank() && p.endsWith(k.trim())) return true;
		return false;
	}

	private static Mode mode(Minecraft mc) {
		ItemStack st = mc.player.getMainHandItem();
		if (st.isEmpty()) return Mode.NONE;
		SoloConfig.Data cfg = SoloConfig.get();
		String p = ItemIds.idOf(st.getItem());
		p = p.substring(p.indexOf(':') + 1);
		if (p.contains("pickaxe") || endsWithAny(p, cfg.pickKeywords)) return Mode.PICK;
		int cat = LootLogic.categoryOf(st.getItem());
		if (cat == 0 || cat == 2 || endsWithAny(p, cfg.weaponKeywords)) return Mode.WEAPON;
		if (cfg.animalKeywords != null) {
			for (String k : cfg.animalKeywords) if (k != null && !k.isBlank() && p.contains(k.trim())) return Mode.ANIMAL;
		}
		return Mode.NONE;
	}

	// ------------------------------------------------------------------ tick (scanning)

	public static void tick(Minecraft mc) {
		lastTickMs = nowMs();
		if (!active(mc)) {
			BLOCKS.clear(); NEAR.clear(); ENEMIES.clear(); ANIMALS.clear(); POS.clear();
			scanSlice = Integer.MAX_VALUE;
			threat = false;
			return;
		}
		PlayerData d = ClientState.data;
		int sense = d.stat(Stat.SENSE);
		Mode mode = mode(mc);
		SoloConfig.Data cfg = SoloConfig.get();

		System.arraycopy(PLAYER, 3, PLAYER, 0, 3);
		PLAYER[3] = mc.player.getX(); PLAYER[4] = mc.player.getY(); PLAYER[5] = mc.player.getZ();

		boolean ores = mode == Mode.PICK && cfg.showOres;
		boolean secrets = d.has(Stat.SENSE, 2) && cfg.showSecrets;
		if (ores || secrets) {
			stepScan(mc, sense, ores, secrets);             // the whole area, nearest slices first
			if (++nearTimer >= 4) { nearTimer = 0; scanNear(mc, ores, secrets); } // fresh, exact scan right around you
		} else {
			BLOCKS.clear(); NEAR.clear();
			scanSlice = Integer.MAX_VALUE;
		}

		if (++mobTimer >= 4) {
			mobTimer = 0;
			scanMobs(mc, mobRadius(sense), mode == Mode.ANIMAL && cfg.showAnimals);
		}

		// threat alert: any hostile closer than threatRange
		threat = false;
		if (cfg.threatAlert) {
			double tr = cfg.threatRange;
			for (Entity e : ENEMIES) if (e.isAlive() && e.distanceToSqr(mc.player) <= tr * tr) { threat = true; break; }
		}

		track(ENEMIES);
		track(ANIMALS);
	}

	/** remember the last two positions of every tracked mob, for smooth drawing between ticks */
	private static void track(List<Entity> list) {
		for (Entity e : list) {
			double[] a = POS.computeIfAbsent(e.getId(), k -> new double[]{e.getX(), e.getY(), e.getZ(), e.getX(), e.getY(), e.getZ()});
			a[0] = a[3]; a[1] = a[4]; a[2] = a[5];
			a[3] = e.getX(); a[4] = e.getY(); a[5] = e.getZ();
		}
	}

	private static void scanMobs(Minecraft mc, double r, boolean wantAnimals) {
		ENEMIES.clear();
		ANIMALS.clear();
		double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
		List<Entity> enemies = new ArrayList<>(), animals = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!e.isAlive()) continue;
			double dx = e.getX() - px, dz = e.getZ() - pz;
			if (dx * dx + dz * dz > r * r || Math.abs(e.getY() - py) > r) continue;
			if (Mobs.isHostile(e)) enemies.add(e);
			else if (wantAnimals && Mobs.isAnimal(e)) animals.add(e);
		}
		enemies.sort((a, b) -> Double.compare(a.distanceToSqr(mc.player), b.distanceToSqr(mc.player)));
		animals.sort((a, b) -> Double.compare(a.distanceToSqr(mc.player), b.distanceToSqr(mc.player)));
		for (int i = 0; i < Math.min(enemies.size(), 40); i++) ENEMIES.add(enemies.get(i));
		for (int i = 0; i < Math.min(animals.size(), 40); i++) ANIMALS.add(animals.get(i));
		Set<Integer> ids = new HashSet<>();
		for (Entity e : ENEMIES) ids.add(e.getId());
		for (Entity e : ANIMALS) ids.add(e.getId());
		POS.keySet().retainAll(ids);
	}

	private static final String[] PREFIXES = {"deepslate_", "nether_", "end_", "stone_", "netherrack_", "blackstone_", "basalt_",
		"sand_", "gravel_", "dirt_", "dense_", "poor_", "rich_", "normal_"};

	private static String baseName(String path) {
		String b = path;
		boolean again = true;
		while (again) {
			again = false;
			for (String pre : PREFIXES) if (b.startsWith(pre) && b.length() > pre.length()) { b = b.substring(pre.length()); again = true; }
		}
		if (b.endsWith("_ore")) b = b.substring(0, b.length() - 4);
		if (b.endsWith("_ores")) b = b.substring(0, b.length() - 5);
		return b;
	}

	private static String pretty(String base) {
		StringBuilder sb = new StringBuilder();
		for (String w : base.split("_")) {
			if (w.isEmpty()) continue;
			if (sb.length() > 0) sb.append(' ');
			sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return sb.toString();
	}

	private static boolean looksLikeOre(String path) {
		return path.endsWith("_ore") || path.contains("_ore_") || path.equals("ore") || path.endsWith("_ores");
	}

	/** Every ore: built-in types, modded ores found by tag (c:ores/...) or name (*_ore), and anything listed in config colours. */
	private static void buildOreMap(Minecraft mc) {
		ORE_MAP.clear();
		SoloConfig.Data cfg = SoloConfig.get();

		// tags the user added in config: "#modid:tag"
		List<String> tagKeys = new ArrayList<>();
		List<TagKey<Block>> tags = new ArrayList<>();
		for (String k : cfg.oreColors.keySet()) {
			if (!k.startsWith("#") || k.indexOf(':') < 0) continue;
			String t = k.substring(1);
			tagKeys.add(k);
			tags.add(SoloTags.tag(t.substring(0, t.indexOf(':')), t.substring(t.indexOf(':') + 1)));
		}

		for (Block b : BuiltInRegistries.BLOCK) {
			BlockState st = b.defaultBlockState();
			String id = BuiltInRegistries.BLOCK.getKey(b).toString();
			int colon = id.indexOf(':');
			String ns = id.substring(0, colon), path = id.substring(colon + 1);
			String base = baseName(path);
			String group = ns + ":" + base;

			Ore found = null;
			for (int i = 0; i < tags.size() && found == null; i++)
				if (st.is(tags.get(i))) found = new Ore(tagKeys.get(i), pretty(tagKeys.get(i).substring(tagKeys.get(i).indexOf(':') + 1)), null, id, OreColors.auto(tagKeys.get(i)));
			if (found == null && (cfg.oreColors.containsKey(id) || cfg.oreColors.containsKey(group)))
				found = new Ore(group, pretty(base) + " Ore", null, id, OreColors.auto(group));
			if (found == null) {
				OreType t = OreType.classify(st);
				if (t != null && t != OreType.OTHER) found = new Ore(t.name(), t.label, t, id, 0);
				else if (!ns.equals("minecraft") && looksLikeOre(path)) found = new Ore(group, pretty(base) + " Ore", null, id, OreColors.auto(group));
				else if (t == OreType.OTHER) found = new Ore(OreType.OTHER.name(), OreType.OTHER.label, OreType.OTHER, id, 0);
			}
			if (found != null) ORE_MAP.put(b, found);
		}
		oreLevel = mc.level;
	}

	/** Colour of an ore: config override > built-in type colour > automatic colour. */
	private static int colorOf(Ore o) {
		Integer c = OreColors.override(o.blockId());
		if (c == null) c = OreColors.override(o.group());
		if (c == null && o.type() != null) c = OreColors.override(o.type().name());
		if (c == null) c = o.type() != null ? (o.type().color & 0xFFFFFF) : o.auto();
		return 0xFF000000 | c;
	}

	/** Modded ore groups seen so far, as {key, label} (used by the colour page in CONFIG). */
	public static List<String[]> moddedOres() {
		Map<String, String[]> out = new LinkedHashMap<>();
		for (Ore o : ORE_MAP.values()) if (o.type() == null && !out.containsKey(o.group())) out.put(o.group(), new String[]{o.group(), o.label()});
		return new ArrayList<>(out.values());
	}

	public static int colorForKey(String key, int fallbackRgb) {
		Integer c = OreColors.override(key);
		return c != null ? c : fallbackRgb;
	}

	/** Scan one slab (fixed x) of the cube around {@code c}. Shared by the wide scan and the near scan. */
	private static void scanSlab(Minecraft mc, BlockPos c, int x, int rz, int ry, List<Dot> out, boolean ores, boolean secrets, int cap) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int z = -rz; z <= rz && out.size() < cap; z++) {
			if (x * x + z * z > rz * rz) continue;
			for (int y = -ry; y <= ry; y++) {
				m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
				BlockState s = mc.level.getBlockState(m);
				if (s.isAir()) continue;
				Block b = s.getBlock();
				if (ores) {
					Ore t = ORE_MAP.get(b);
					if (t != null) { out.add(new Dot(m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5, 0, t)); continue; }
				}
				if (secrets) {
					int kind = b == Blocks.CHEST ? 1 : b == Blocks.TRAPPED_CHEST ? 2 : b == Blocks.SPAWNER ? 3 : -1;
					if (kind > 0) out.add(new Dot(m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5, kind, null));
				}
			}
		}
	}

	/**
	 * Wide scan, one slab per tick. Slabs are visited from the centre outwards (0, -1, +1, -2, +2 ...), so the ores
	 * nearest to you are always found first and can never be crowded out by a lot of far-away ores.
	 */
	private static void stepScan(Minecraft mc, int sense, boolean ores, boolean secrets) {
		if (scanWait > 0) { scanWait--; return; }
		if (scanSlice > 2 * scanR) {
			if (mc.level != oreLevel || ORE_MAP.isEmpty()) buildOreMap(mc);
			scanR = blockRadius(sense);
			scanSlice = 0;
			scanCenter = mc.player.blockPosition();
			acc = new ArrayList<>();
		}
		int i = scanSlice;
		int x = (i % 2 == 1) ? -((i + 1) / 2) : (i / 2);
		scanSlab(mc, scanCenter, x, scanR, Math.min(scanR, 32), acc, ores, secrets, 1500);
		scanSlice++;
		if (scanSlice > 2 * scanR) { // finished a full scan: publish it, rest a moment
			BLOCKS.clear();
			BLOCKS.addAll(acc);
			scanWait = 10;
		}
	}

	/** Exact, fresh scan of the 23x23x23 cube around you every few ticks (shows ores you can almost touch, and forgets mined ones). */
	private static void scanNear(Minecraft mc, boolean ores, boolean secrets) {
		if (mc.level != oreLevel || ORE_MAP.isEmpty()) buildOreMap(mc);
		BlockPos c = mc.player.blockPosition();
		List<Dot> tmp = new ArrayList<>();
		for (int x = -11; x <= 11; x++) scanSlab(mc, c, x, 11, 11, tmp, ores, secrets, 400);
		NEAR.clear();
		NEAR.addAll(tmp);
	}

	/** Near results (fresh) + wide results that are outside the near cube. */
	private static List<Dot> visible() {
		List<Dot> out = new ArrayList<>(NEAR);
		double px = PLAYER[3], py = PLAYER[4], pz = PLAYER[5];
		for (Dot d : BLOCKS)
			if (Math.abs(d.x() - px) > 10 || Math.abs(d.y() - py) > 10 || Math.abs(d.z() - pz) > 10) out.add(d);
		return out;
	}

	// ------------------------------------------------------------------ camera projection

	private static final class Cam {
		double ex, ey, ez, fx, fy, fz, rx, rz, ux, uy, uz, tanHalf, halfH;
		int cx, cy;
	}

	private static Cam camera(Minecraft mc, GuiGraphicsExtractor g, double pt) {
		Cam c = new Cam();
		c.ex = PLAYER[0] + (PLAYER[3] - PLAYER[0]) * pt;
		c.ey = PLAYER[1] + (PLAYER[4] - PLAYER[1]) * pt + mc.player.getEyeHeight();
		c.ez = PLAYER[2] + (PLAYER[5] - PLAYER[2]) * pt;
		double yaw = Math.toRadians(mc.player.getYRot()), pitch = Math.toRadians(mc.player.getXRot());
		c.fx = -Math.sin(yaw) * Math.cos(pitch);
		c.fy = -Math.sin(pitch);
		c.fz = Math.cos(yaw) * Math.cos(pitch);
		c.rx = -Math.cos(yaw);
		c.rz = -Math.sin(yaw);
		// up = right x forward
		c.ux = 0 * c.fz - c.rz * c.fy;
		c.uy = c.rz * c.fx - c.rx * c.fz;
		c.uz = c.rx * c.fy - 0 * c.fx;
		c.tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
		c.halfH = g.guiHeight() / 2.0;
		c.cx = g.guiWidth() / 2;
		c.cy = g.guiHeight() / 2;
		return c;
	}

	/** World -> GUI coordinates, or null if behind the camera. */
	private static int[] project(Cam c, double wx, double wy, double wz) {
		double vx = wx - c.ex, vy = wy - c.ey, vz = wz - c.ez;
		double z = vx * c.fx + vy * c.fy + vz * c.fz;
		if (z < 0.3) return null;
		double x = vx * c.rx + vz * c.rz;
		double y = vx * c.ux + vy * c.uy + vz * c.uz;
		int sx = c.cx + (int) Math.round((x / z) / c.tanHalf * c.halfH);
		int sy = c.cy - (int) Math.round((y / z) / c.tanHalf * c.halfH);
		return new int[]{sx, sy};
	}

	// ------------------------------------------------------------------ render

	public static void render(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (!active(mc)) return;
		SoloConfig.Data cfg = SoloConfig.get();
		int sense = ClientState.data.stat(Stat.SENSE);
		Mode mode = mode(mc);
		double pt = Math.max(0, Math.min(1, (nowMs() - lastTickMs) / 50.0));

		if (cfg.senseEsp) {
			Cam cam = camera(mc, g, pt);
			if (mode == Mode.PICK) drawOreEsp(g, mc, cam, sense);
			else if (mode == Mode.WEAPON && cfg.showEnemies) drawEntityEsp(g, mc, cam, pt, ENEMIES, 0xFFFF3030, ChatFormatting.RED);
			else if (mode == Mode.ANIMAL && cfg.showAnimals) drawEntityEsp(g, mc, cam, pt, ANIMALS, 0xFF4CD964, ChatFormatting.GREEN);
		}
		drawRadar(g, mc, mode, sense, cfg);
	}

	private static void drawOreEsp(GuiGraphicsExtractor g, Minecraft mc, Cam c, int sense) {
		var font = mc.font;
		double max = Math.min(blockRadius(sense), SoloConfig.get().espRange);
		record Item(Dot d, double dist) {}
		List<Item> list = new ArrayList<>();
		for (Dot d : visible()) {
			if (d.kind() != 0) continue;
			double dx = d.x() - c.ex, dy = d.y() - c.ey, dz = d.z() - c.ez;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist <= max) list.add(new Item(d, dist));
		}
		list.sort((a, b) -> Double.compare(b.dist(), a.dist())); // far first, near drawn on top
		int skip = Math.max(0, list.size() - 120);                // keep the nearest 120
		for (int i = skip; i < list.size(); i++) {
			Item it = list.get(i);
			int[] p = project(c, it.d().x(), it.d().y(), it.d().z());
			if (p == null) continue;
			int col = colorOf(it.d().ore());
			g.fill(p[0] - 3, p[1] - 3, p[0] + 4, p[1] + 4, 0xFF000000);
			g.fill(p[0] - 2, p[1] - 2, p[0] + 3, p[1] + 3, col);
			if (Math.abs(p[0] - c.cx) < 50 && Math.abs(p[1] - c.cy) < 50) {
				String t = it.d().ore().label() + "  " + Math.round(it.dist()) + "m";
				g.text(font, Component.literal(t), p[0] - font.width(t) / 2, p[1] - 13, 0xFF000000 | (col & 0xFFFFFF), true);
			}
		}
	}

	/** Box + name + segmented HP bar above every entity in the list (red for enemies, green for animals). */
	private static void drawEntityEsp(GuiGraphicsExtractor g, Minecraft mc, Cam c, double pt, List<Entity> list, int rgb, ChatFormatting nameColor) {
		var font = mc.font;
		int outline = 0xFF000000 | (rgb & 0xFFFFFF), fillCol = 0x30000000 | (rgb & 0xFFFFFF);
		double range = SoloConfig.get().espRange;
		for (Entity e : list) {
			if (!(e instanceof LivingEntity le) || !e.isAlive()) continue;
			if (e.distanceToSqr(mc.player) > range * range) continue;
			double[] a = POS.get(e.getId());
			double x = a == null ? e.getX() : a[0] + (a[3] - a[0]) * pt;
			double y = a == null ? e.getY() : a[1] + (a[4] - a[1]) * pt;
			double z = a == null ? e.getZ() : a[2] + (a[5] - a[2]) * pt;
			int[] feet = project(c, x, y, z);
			int[] head = project(c, x, y + e.getBbHeight(), z);
			if (feet == null || head == null) continue;

			int h = Math.max(8, feet[1] - head[1]);
			int w = Math.max(6, (int) (h * 0.55));
			int cx = (feet[0] + head[0]) / 2;
			int top = head[1];

			g.fill(cx - w / 2, top, cx + w / 2, top + h, fillCol);
			g.fill(cx - w / 2, top, cx + w / 2, top + 1, outline);
			g.fill(cx - w / 2, top + h - 1, cx + w / 2, top + h, outline);
			g.fill(cx - w / 2, top, cx - w / 2 + 1, top + h, outline);
			g.fill(cx + w / 2 - 1, top, cx + w / 2, top + h, outline);

			// segmented HP bar
			float hp = Math.max(0f, le.getHealth()), max = Math.max(1f, le.getMaxHealth());
			int segs = max <= 20 ? (int) Math.ceil(max / 2f) : 10;
			segs = Math.max(1, segs);
			int segW = 5, gap = 1;
			int barW = segs * (segW + gap) - gap;
			int bx = cx - barW / 2, by = top - 8;
			float fill = Math.min(1f, hp / max) * segs;
			int color = hp / max >= 0.5f ? 0xFF4CD964 : hp / max >= 0.25f ? 0xFFFFD93D : 0xFFFF3030;
			for (int i = 0; i < segs; i++) {
				int sx = bx + i * (segW + gap);
				g.fill(sx - 1, by - 1, sx + segW + 1, by + 5, 0xFF000000);
				g.fill(sx, by, sx + segW, by + 4, 0xFF303040);
				float part = Math.max(0f, Math.min(1f, fill - i));
				if (part > 0) g.fill(sx, by, sx + Math.max(1, Math.round(segW * part)), by + 4, color);
			}

			String name = e.getName().getString();
			g.text(font, Component.literal(name).withStyle(nameColor), cx - font.width(name) / 2, by - 11, 0xFFFFFFFF, true);
			String hpText = Math.round(hp) + "/" + Math.round(max);
			g.text(font, Component.literal(hpText).withStyle(ChatFormatting.GRAY), bx + barW + 4, by - 1, 0xFFFFFFFF, true);
		}
	}

	private static void drawRadar(GuiGraphicsExtractor g, Minecraft mc, Mode mode, int sense, SoloConfig.Data cfg) {
		var font = mc.font;
		int size = cfg.radarSize, margin = 4;
		int gw = g.guiWidth(), gh = g.guiHeight();
		// free position: 0..1000 = share of the free screen space
		int[] rr = radarRect(gw, gh, cfg);
		int x0 = rr[0], y0 = rr[1];
		int cx = x0 + size / 2, cy = y0 + size / 2;
		boolean pulse = (mc.level.getGameTime() / 5) % 2 == 0;

		// colour logic: RED = threat nearby (flashing), GREEN = animal items, YELLOW = pickaxe, ORANGE = weapon, BLUE = nothing special
		int edge, bgRgb;
		String title = "SIXTH SENSE";
		ChatFormatting titleColor = ChatFormatting.AQUA;
		if (threat) {
			edge = pulse ? 0xFFFF3030 : 0xFF9A1818;
			bgRgb = 0x301010;
			title = "! DANGER !";
			titleColor = ChatFormatting.RED;
		} else if (mode == Mode.ANIMAL) {
			edge = 0xFF4CD964; bgRgb = 0x102A18; titleColor = ChatFormatting.GREEN;
		} else if (mode == Mode.PICK) {
			edge = 0xFFFFD93D; bgRgb = 0x282410; titleColor = ChatFormatting.YELLOW;
		} else if (mode == Mode.WEAPON) {
			edge = 0xFFFF9A3C; bgRgb = 0x2A1C10; titleColor = ChatFormatting.GOLD;
		} else {
			edge = 0xFF3A9BDC; bgRgb = 0x101828;
		}

		int alpha = cfg.radarOpacity * 255 / 100;
		g.fill(x0, y0, x0 + size, y0 + size, (alpha << 24) | bgRgb);
		g.fill(x0, y0, x0 + size, y0 + 1, edge);
		g.fill(x0, y0 + size - 1, x0 + size, y0 + size, edge);
		g.fill(x0, y0, x0 + 1, y0 + size, edge);
		g.fill(x0 + size - 1, y0, x0 + size, y0 + size, edge);
		g.fill(x0 + 1, cy, x0 + size - 1, cy + 1, 0x30FFFFFF);
		g.fill(cx, y0 + 1, cx + 1, y0 + size - 1, 0x30FFFFFF);

		g.text(font, Component.literal(title).withStyle(titleColor), x0 + 3, y0 + 3, 0xFFFFFFFF, true);
		String modeText = mode == Mode.PICK ? "ORE SCAN" : mode == Mode.WEAPON ? "ENEMY SCAN" : mode == Mode.ANIMAL ? "ANIMAL SCAN" : "PASSIVE";
		g.text(font, Component.literal(modeText).withStyle(titleColor), x0 + 3, y0 + size - 11, 0xFFFFFFFF, true);

		double scale = (size / 2.0 - 3) / (mode == Mode.PICK ? Math.max(8, blockRadius(sense)) : mobRadius(sense));
		double yaw = Math.toRadians(mc.player.getYRot());
		double cos = Math.cos(yaw), sin = Math.sin(yaw);
		double px = mc.player.getX(), pz = mc.player.getZ();

		Map<String, int[]> legend = new LinkedHashMap<>(); // label -> {rgb, count}
		for (Dot d : visible()) {
			int color;
			if (d.kind() == 0) {
				color = colorOf(d.ore());
				final int legendColor = color;
				legend.computeIfAbsent(d.ore().label(), k -> new int[]{legendColor, 0})[1]++;
			} else {
				color = d.kind() == 1 ? 0xFFD2A050 : d.kind() == 2 ? (pulse ? 0xFFFF2020 : 0xFFFFFF40) : 0xFFB45CFF;
			}
			plot(g, d.x(), d.z(), px, pz, cos, sin, scale, cx, cy, x0, y0, size, color, d.kind() == 0 ? 0 : 1);
		}
		if (cfg.showEnemies)
			for (Entity e : ENEMIES) plot(g, e.getX(), e.getZ(), px, pz, cos, sin, scale, cx, cy, x0, y0, size, 0xFFFF4040, (mode == Mode.WEAPON || threat) ? 1 : 0);
		if (mode == Mode.ANIMAL && cfg.showAnimals)
			for (Entity e : ANIMALS) plot(g, e.getX(), e.getZ(), px, pz, cos, sin, scale, cx, cy, x0, y0, size, 0xFF55FF55, 1);

		g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
		g.fill(cx, cy - 3, cx + 1, cy - 1, 0xFFFFFFFF);

		// legend of the ore colours currently detected (below the map, or above it near the bottom of the screen)
		if (mode == Mode.PICK && !legend.isEmpty()) {
			int rows = Math.min(legend.size(), 12);
			int ly = (y0 + size + 4 + rows * 10 <= gh) ? y0 + size + 4 : y0 - rows * 10 - 4;
			int n = 0;
			for (Map.Entry<String, int[]> en : legend.entrySet()) {
				if (n++ >= rows) break;
				g.fill(x0, ly + 1, x0 + 6, ly + 7, 0xFF000000);
				g.fill(x0 + 1, ly + 2, x0 + 5, ly + 6, en.getValue()[0]);
				g.text(font, Component.literal(en.getKey() + " x" + en.getValue()[1]), x0 + 10, ly, 0xFFFFFFFF, true);
				ly += 10;
			}
		}
	}

	private static void plot(GuiGraphicsExtractor g, double wx, double wz, double px, double pz, double cos, double sin, double scale,
							 int cx, int cy, int x0, int y0, int size, int color, int r) {
		double dx = wx - px, dz = wz - pz;
		// forward = (-sin, cos), right = (-cos, -sin): the way you face is "up" on the map
		double right = -dx * cos - dz * sin;
		double fwd = -dx * sin + dz * cos;
		int sx = cx + (int) Math.round(right * scale);
		int sy = cy - (int) Math.round(fwd * scale);
		if (sx < x0 + 2 || sx > x0 + size - 3 || sy < y0 + 2 || sy > y0 + size - 3) return;
		g.fill(sx - r, sy - r, sx + r + 1, sy + r + 1, color);
	}
}
