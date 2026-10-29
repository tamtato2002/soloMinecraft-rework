package com.example.solosystem;

import com.example.solosystem.net.AllocatePayload;
import com.example.solosystem.net.PopupPayload;
import com.example.solosystem.net.SyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class SoloSystemMod implements ModInitializer {
	public static final String MOD_ID = "solosystem";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

	public static final AttachmentType<PlayerData> DATA = AttachmentRegistry.create(
		id("player_data"),
		b -> b.persistent(PlayerData.CODEC).copyOnDeath().initializer(PlayerData::new));

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(SyncPayload.TYPE, SyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PopupPayload.TYPE, PopupPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AllocatePayload.TYPE, AllocatePayload.CODEC);
		SoloConfig.load();

		ServerPlayNetworking.registerGlobalReceiver(AllocatePayload.TYPE,
			(payload, context) -> SystemLogic.allocate(context.player(), payload.stat(), payload.amount()));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			SystemLogic.onJoin(handler.getPlayer());
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SystemLogic.onLeave(handler.getPlayer()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
			SystemLogic.applyAll(newP, SystemLogic.data(newP));
			SystemLogic.sync(newP);
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> QuestLogic.warmUp());
		ServerTickEvents.END_SERVER_TICK.register(SystemLogic::tick);

		ExpEvents.register();
		ResistLogic.register();
	}
}
