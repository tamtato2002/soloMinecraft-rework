package com.example.solosystem.net;

import com.example.solosystem.SoloSystemMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> Client: floating combat text such as MISS! / CRIT! (kind 0 = miss, 1 = crit). */
public record PopupPayload(String text, int kind) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PopupPayload> TYPE = new CustomPacketPayload.Type<>(SoloSystemMod.id("popup"));
	public static final StreamCodec<ByteBuf, PopupPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, PopupPayload::text,
		ByteBufCodecs.VAR_INT, PopupPayload::kind,
		PopupPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
