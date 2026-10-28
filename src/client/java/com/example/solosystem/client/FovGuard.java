package com.example.solosystem.client;

import com.example.solosystem.SoloConfig;
import net.minecraft.client.Minecraft;

/**
 * Speed (Agility) makes the game widen the field of view: the screen "stretches". The game already has a
 * "FOV Effects" slider for that; this keeps it at 0 (off) or low, and gives the player's own value back
 * when the option is set to "Vanilla".
 */
public final class FovGuard {
	private static int timer;

	private FovGuard() {}

	public static void tick(Minecraft mc) {
		if (++timer < 10) return;
		timer = 0;
		SoloConfig.Data c = SoloConfig.get();
		var opt = mc.options.fovEffectScale();
		double cur = opt.get();

		if (c.fovMode == 0) { // vanilla: put back what the player had before
			if (c.savedFovEffect >= 0) {
				if (Math.abs(cur - c.savedFovEffect) > 1e-6) opt.set(c.savedFovEffect);
				c.savedFovEffect = -1;
				SoloConfig.save();
			}
			return;
		}
		if (c.savedFovEffect < 0) { // remember the player's own setting once
			c.savedFovEffect = cur;
			SoloConfig.save();
		}
		double target = c.fovMode == 1 ? 0.3 : 0.0;
		if (Math.abs(cur - target) > 1e-6) opt.set(target);
	}
}
