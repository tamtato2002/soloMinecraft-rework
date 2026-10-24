package com.example.solosystem.client;

import com.example.solosystem.PlayerData;
import com.example.solosystem.SoloConfig;
import com.example.solosystem.SoloTags;
import com.example.solosystem.Stat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sense ability "HUD Radar" (needs Sense 20): mini-map showing hostile mobs and rare ores.
 * Sense 40 ("Dungeon & Secret Perception") also shows chests, trapped chests (traps!) and spawners.
 * Works for modded mobs (anything that is an Enemy) and modded ores (c:ores/* tags).
 */
public final class RadarHud {
	private record Dot(double x, double z, int kind) {}

	// kinds: 0 mob, 1 diamond, 2 emerald, 3 ancient debris, 4 chest, 5 trapped chest, 6 spawner
	private static final int[] KIND_COLOR = {0xFFFF4040, 0xFF4FE0E8, 0xFF3CE05A, 0xFFFF9A3C, 0xFFD2A050, 0xFFFF2020, 0xFFB45CFF};

	private static final List<Dot> MOBS = new ArrayList<>();
	private static final List<Dot> BLOCKS = new ArrayList<>();
	private static int mobTimer, blockTimer;

	private RadarHud() {}

	public static double mobRadius(int sense) { return Math.min(48, 16 + 0.8 * (sense - 20)); }
	public static int blockRadius(int sense) { return (int) Math.min(24, 10 + 0.3 * (sense - 20)); }

	private static boolean active(Minecraft mc) {
		return SoloConfig.get().radar && mc.player != null && mc.level != null && ClientState.data.has(Stat.SENSE, 1);
	}

	public static void tick(Minecraft mc) {
		if (!active(mc)) {
			MOBS.clear();
			BLOCKS.clear();
			return;
		}
		PlayerData d = ClientState.data;
		int sense = d.stat(Stat.SENSE);
		if (++mobTimer >= 4) { mobTimer = 0; scanMobs(mc, mobRadius(sense)); }
		if (++blockTimer >= 40) { blockTimer = 0; scanBlocks(mc, d, blockRadius(sense)); }
	}

	private static void scanMobs(Minecraft mc, double r) {
		MOBS.clear();
		double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof Enemy) || !e.isAlive()) continue;
			double dx = e.getX() - px, dz = e.getZ() - pz;
			if (dx * dx + dz * dz > r * r || Math.abs(e.getY() - py) > r) continue;
			MOBS.add(new Dot(e.getX(), e.getZ(), 0));
			if (MOBS.size() >= 150) break;
		}
	}

	private static void scanBlocks(Minecraft mc, PlayerData d, int r) {
		BLOCKS.clear();
		boolean secrets = d.has(Stat.SENSE, 2);
		BlockPos c = mc.player.blockPosition();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (x * x + z * z > r * r) continue;
				for (int y = -r; y <= r; y++) {
					m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					BlockState s = mc.level.getBlockState(m);
					if (s.isAir()) continue;
					int kind = -1;
					if (s.is(SoloTags.DIAMOND) || s.is(SoloTags.C_DIAMOND)) kind = 1;
					else if (s.is(SoloTags.EMERALD) || s.is(SoloTags.C_EMERALD)) kind = 2;
					else if (s.is(Blocks.ANCIENT_DEBRIS) || s.is(SoloTags.C_NETHERITE)) kind = 3;
					else if (secrets) {
						if (s.is(Blocks.CHEST)) kind = 4;
						else if (s.is(Blocks.TRAPPED_CHEST)) kind = 5;
						else if (s.is(Blocks.SPAWNER)) kind = 6;
					}
					if (kind >= 0) {
						BLOCKS.add(new Dot(m.getX() + 0.5, m.getZ() + 0.5, kind));
						if (BLOCKS.size() >= 250) return;
					}
				}
			}
		}
	}

	public static void render(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (!active(mc) || (MOBS.isEmpty() && BLOCKS.isEmpty() && mc.player == null)) return;

		SoloConfig.Data cfg = SoloConfig.get();
		int size = cfg.radarSize, margin = 6;
		int gw = g.guiWidth(), gh = g.guiHeight();
		int corner = cfg.radarCorner;
		int x0 = (corner == 0 || corner == 2) ? gw - size - margin : margin;
		int y0 = corner < 2 ? margin : gh - size - margin;
		int cx = x0 + size / 2, cy = y0 + size / 2;

		// panel
		g.fill(x0, y0, x0 + size, y0 + size, 0xA0101828);
		int edge = 0xFF3A9BDC;
		g.fill(x0, y0, x0 + size, y0 + 1, edge);
		g.fill(x0, y0 + size - 1, x0 + size, y0 + size, edge);
		g.fill(x0, y0, x0 + 1, y0 + size, edge);
		g.fill(x0 + size - 1, y0, x0 + size, y0 + size, edge);
		g.fill(x0 + 1, cy, x0 + size - 1, cy + 1, 0x30FFFFFF);
		g.fill(cx, y0 + 1, cx + 1, y0 + size - 1, 0x30FFFFFF);

		int sense = ClientState.data.stat(Stat.SENSE);
		double scale = (size / 2.0 - 3) / mobRadius(sense);
		double yaw = Math.toRadians(mc.player.getYRot());
		double cos = Math.cos(yaw), sin = Math.sin(yaw);
		double px = mc.player.getX(), pz = mc.player.getZ();
		boolean pulse = (mc.level.getGameTime() / 5) % 2 == 0;

		for (Dot dt : BLOCKS) draw(g, dt, px, pz, cos, sin, scale, cx, cy, x0, y0, size, pulse);
		for (Dot dt : MOBS) draw(g, dt, px, pz, cos, sin, scale, cx, cy, x0, y0, size, pulse);

		// player marker (white)
		g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
		g.fill(cx, cy - 3, cx + 1, cy - 1, 0xFFFFFFFF);
	}

	private static void draw(GuiGraphicsExtractor g, Dot dt, double px, double pz, double cos, double sin, double scale,
							 int cx, int cy, int x0, int y0, int size, boolean pulse) {
		double dx = dt.x() - px, dz = dt.z() - pz;
		// forward = (-sin, cos), right = (-cos, -sin): player's facing direction is "up" on the radar
		double right = -dx * cos - dz * sin;
		double fwd = -dx * sin + dz * cos;
		int sx = cx + (int) Math.round(right * scale);
		int sy = cy - (int) Math.round(fwd * scale);
		if (sx < x0 + 2 || sx > x0 + size - 3 || sy < y0 + 2 || sy > y0 + size - 3) return;
		int color = KIND_COLOR[dt.kind()];
		if (dt.kind() == 5 && !pulse) color = 0xFFFFFF40; // trapped chest blinks
		int r = dt.kind() == 0 || dt.kind() >= 4 ? 1 : 0;
		g.fill(sx - r, sy - r, sx + r + 1, sy + r + 1, color);
	}
}
