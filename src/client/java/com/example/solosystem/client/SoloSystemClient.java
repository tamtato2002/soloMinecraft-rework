package com.example.solosystem.client;

import com.example.solosystem.PlayerData;
import com.example.solosystem.SoloConfig;
import com.example.solosystem.SoloSystemMod;
import com.example.solosystem.net.PopupPayload;
import com.example.solosystem.net.SyncPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import org.lwjgl.sdl.SDLScancode;

public class SoloSystemClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SoloConfig.get();

		ClientPlayNetworking.registerGlobalReceiver(SyncPayload.TYPE, (payload, context) -> {
			PlayerData old = ClientState.data;
			PlayerData now = payload.data();
			QuestToasts.diff(old, now);
			ClientState.data = now;
			if (context.client().gui.screen() instanceof SystemScreen s) s.refresh();
		});
		ClientPlayNetworking.registerGlobalReceiver(PopupPayload.TYPE,
			(payload, context) -> Popups.push(payload.text(), payload.kind()));

		HudElementRegistry.addLast(SoloSystemMod.id("quest_toasts"), (graphics, deltaTracker) -> QuestToasts.render(graphics));
		HudElementRegistry.addLast(SoloSystemMod.id("popups"), (graphics, deltaTracker) -> Popups.render(graphics));
		HudElementRegistry.addLast(SoloSystemMod.id("radar"), (graphics, deltaTracker) -> SixthSense.render(graphics));
		HudElementRegistry.addLast(SoloSystemMod.id("abilities"), (graphics, deltaTracker) -> AbilityHud.render(graphics));

		KeyMapping.Category category = KeyMapping.Category.register(SoloSystemMod.id("main"));
		KeyMapping open = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.solosystem.open", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_Y, category));

		KeyMapping toggleSense = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.solosystem.toggle_sense", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_V, category));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			QuestToasts.tick();
			Popups.tick();
			SixthSense.tick(mc);
			FovGuard.tick(mc);
			while (toggleSense.consumeClick()) {
				SoloConfig.Data c = SoloConfig.get();
				c.radar = !c.radar;
				SoloConfig.save();
				Popups.push(c.radar ? "SIXTH SENSE ON" : "SIXTH SENSE OFF", 2);
			}
			while (open.consumeClick()) {
				if (mc.gui.screen() == null && mc.player != null) mc.gui.setScreen(new SystemScreen());
			}
		});
	}
}
