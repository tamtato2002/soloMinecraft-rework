package com.example.solosystem.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Minecraft-style 16x16 pixel-art icons, drawn with plain fills (no texture files needed). */
public final class Icons {
	public static final int STATUS = 0, SKILLS = 1, QUESTS = 2, RESISTANCE = 3, CONFIG = 4,
		STRENGTH = 5, AGILITY = 6, ENDURANCE = 7, INTELLIGENCE = 8, SENSE = 9;

	private static final String PALETTE = "kwmgGrRoyYbBcCpPnNleEsSaA";
	private static final int[] COLORS = {
		0x14121E, 0xF4F4F4, 0xC6CAD2, 0x8B909A, 0x5A5E68, 0xE83B3B, 0x9E1E2A, 0xFF9A3C, 0xFFD93D, 0xC9931C,
		0x3A8FE8, 0x1F4EA8, 0x4FE0E8, 0x1E8FA0, 0xA85CE8, 0x5E2E9E, 0x9A6234, 0x5E381C, 0x7BE85A, 0x3CA83C,
		0x1E6228, 0xF0C294, 0xC89268, 0xF0E0B0, 0xC8B078};

	private static final String[][] ART = {
		// STATUS
		{
			"................",
			"....kkkkkkkk....",
			"...kNNNNNNNNk...",
			"..kNNNNNNNNNNk..",
			"..kNNNNNNNNNNk..",
			"..kNNssssssNNk..",
			"...kNssssssNk...",
			"...kswBssBwsk...",
			"...kssssssssk...",
			"...ksssSSsssk...",
			"...kSSSSSSSSk...",
			"..kCCCCssCCCCk..",
			"..kcccccccccck..",
			"..kcccccccccck..",
			"..kcccccccccck..",
			"...kkkkkkkkkk..."
		},
		// SKILLS
		{
			".......kk.......",
			"......kyyk......",
			"......kyyk......",
			".....kywyyk.....",
			".kkkkkyyyykkkkk.",
			"kyyyywyyyyyyyyyk",
			".kyyyywyyyyyyyk.",
			"..kyyyyyyyyyyk..",
			"...kyyyyyYYYk...",
			"...kyyyyYYYYk...",
			"...kyyyyYYYYk...",
			"..kyyyykkyYYYk..",
			"..kyyyk..kyYYk..",
			".kyyyk....kyYYk.",
			"..kkk......kkk..",
			"................"
		},
		// QUESTS
		{
			"................",
			"..kkkkkkkkkkkk..",
			".kAAAAAAAAAAAAk.",
			".kAAAAAAAAAAAAk.",
			"..kaaaaaaaaaAk..",
			"..kaNNaoraaaAk..",
			"..kaaaarraNNAk..",
			"..kaNNarraaaAk..",
			"..kaaaarraNNAk..",
			"..kaaaarraaaAk..",
			"..kaaaaaaaaaAk..",
			"..kaaaarraaaAk..",
			"..kaaaarraaaAk..",
			".kAAAAAAAAAAAAk.",
			".kAAAAAAAAAAAAk.",
			"..kkkkkkkkkkkk.."
		},
		// RESISTANCE
		{
			"................",
			"...kkkkkkkkkk...",
			"..kmmmmmmmmmmk..",
			"..kmbbbbBBBBgk..",
			"..kmbbbwwBBBgk..",
			"..kmbbbwwBBBgk..",
			"..kmwwwwwwwwgk..",
			"..kmwwwwwwwwgk..",
			"..kmbbbwwBBBgk..",
			"...kbbbwwBBBk...",
			"...kbbbwwBBBk...",
			"....kbbwwBBk....",
			".....kbbBBk.....",
			"......kbBk......",
			".......kk.......",
			"................"
		},
		// CONFIG
		{
			"......kmmk......",
			"......kmmk......",
			"...k.kkmkkk.k...",
			"..kmkmmmmmmkgk..",
			"...kmmmmmmggk...",
			"..kmmmmmmggggk..",
			"kkkmmmkkkkggGkk.",
			"mmkmmmk..kgGGGGk",
			"mmmmmmk..kGGGGGk",
			"kkkmmgkkkkGGGkk.",
			"..kmggggGGGGGk..",
			"...kgggGGGGGk...",
			"..kgkgGGGGGkGk..",
			"...k.kkGGkk.k...",
			"......kGGk......",
			".......kk......."
		},
		// STRENGTH
		{
			".............k..",
			"............kwk.",
			"............kwwk",
			"...........kwmmk",
			"..........kwmmk.",
			".........kwmmk..",
			"........kwmmk...",
			".......kwmmk....",
			"...kk.kwmmk.....",
			"..kyYkwmmk......",
			"...kyYmmk.......",
			"....knYk........",
			"...knkNYk.......",
			".kknkNkyYk......",
			"kynkNk.kyk......",
			".kYNk...k......."
		},
		// AGILITY
		{
			"................",
			".k.....kkkk.....",
			"kwkkkkkwwwwk....",
			".kwwwwkwwwwk....",
			"kkwwwwkcbbBk....",
			"mmmkkkwcbbBk....",
			"kwwwwwmcbbBk....",
			"kwwwwwwcbbBk....",
			"kwwwkmkcbbBkkkk.",
			".kkmmmkcccccccck",
			"..kmkkkbbbbbbBBk",
			".kmk..kbbbbbbBBk",
			"..k...kBBBBBBBBk",
			"......kNNNNNNNNk",
			".......kkkkkkkk.",
			"................"
		},
		// ENDURANCE
		{
			"................",
			"................",
			"..kkkk...kkkk...",
			".krrrrk.krrrrk..",
			"krroorrkrrrrrrk.",
			"kroowrrrrrrrrrrk",
			"krrwrrrrrrrrrrRk",
			"krrrrrrrrrrrrRRk",
			".krrrrrrrrrRRRk.",
			"..krrrrrrrRRRk..",
			"...krrrrrRRRk...",
			"....krrrRRRk....",
			".....krrRRk.....",
			"......krRk......",
			".......kk.......",
			"................"
		},
		// INTELLIGENCE
		{
			"................",
			"...kkkkkkkkkk...",
			".kkPPpppppppPk..",
			"kckPPwwwwwwwPk..",
			"ckcPPpppppppPk..",
			"kckPPpppppppPk..",
			".kkPPppyypppPk..",
			"..kPPpywyyppPk..",
			"..kPPpyyyyppPkk.",
			"..kPPppyypppPkck",
			"..kPPpppppppPckc",
			"..kPPpppppppPkck",
			"..kPPwwwwwwwPkk.",
			"..kPPPPPPPPPPk..",
			"...kkkkkkkkkk...",
			"................"
		},
		// SENSE
		{
			"................",
			"................",
			"................",
			".....kkkkkk.....",
			"...kkmmmmmmkk...",
			".kkwwwccccwwwkk.",
			"kwwwwcwCCCcwwwwk",
			"wwwwwcCkkCcwwwww",
			"wwwwwcCkkCcwwwww",
			"kwwwwcCCCCcwwwwk",
			".kkmmwccccwmmkk.",
			"...kkmmmmmmkk...",
			".....kkkkkk.....",
			"................",
			"................",
			"................"
		},
	};

	/** per icon: runs of {x, y, width, argb} */
	private static final List<List<int[]>> RUNS = new ArrayList<>();

	static {
		for (String[] art : ART) {
			List<int[]> runs = new ArrayList<>();
			for (int y = 0; y < art.length; y++) {
				String row = art[y];
				int x = 0;
				while (x < row.length()) {
					char ch = row.charAt(x);
					if (ch == '.') { x++; continue; }
					int start = x;
					while (x < row.length() && row.charAt(x) == ch) x++;
					int idx = PALETTE.indexOf(ch);
					if (idx >= 0) runs.add(new int[]{start, y, x - start, 0xFF000000 | COLORS[idx]});
				}
			}
			RUNS.add(runs);
		}
	}

	private Icons() {}

	public static void draw(GuiGraphicsExtractor g, int icon, int x, int y, int scale) {
		if (icon < 0 || icon >= RUNS.size()) return;
		for (int[] r : RUNS.get(icon))
			g.fill(x + r[0] * scale, y + r[1] * scale, x + (r[0] + r[2]) * scale, y + (r[1] + 1) * scale, r[3]);
	}
}
