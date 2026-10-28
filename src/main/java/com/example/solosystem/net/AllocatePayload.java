package com.example.solosystem.net;

import com.example.solosystem.SoloSystemMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> Server: spend stat points. */
public record AllocatePayload(String stat, int amount) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<AllocatePayload> TYPE = new CustomPacketPayload.Type<>(SoloSystemMod.id("allocate"));
	public static final StreamCodec<ByteBuf, AllocatePayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, AllocatePayload::stat,
		ByteBufCodecs.VAR_INT, AllocatePayload::amount,
		AllocatePayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
