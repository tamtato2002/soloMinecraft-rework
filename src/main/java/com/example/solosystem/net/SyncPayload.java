package com.example.solosystem.net;

import com.example.solosystem.PlayerData;
import com.example.solosystem.SoloSystemMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> Client: full player data. */
public record SyncPayload(PlayerData data) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SyncPayload> TYPE = new CustomPacketPayload.Type<>(SoloSystemMod.id("sync"));
	public static final StreamCodec<ByteBuf, SyncPayload> CODEC =
		ByteBufCodecs.fromCodec(PlayerData.CODEC).map(SyncPayload::new, SyncPayload::data);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
