package io.github.projetosdehiago.vaportrilhos.network;

import io.github.projetosdehiago.vaportrilhos.VaporTrilhos;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente → servidor: o jogador confirmou a tecla de desmontar olhando para um landship. Leva o
 * id da entidade porque quem desmonta está do lado de fora (o veículo precisa estar vazio).
 */
public record LandshipDisassemblePayload(int entityId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<LandshipDisassemblePayload> TYPE = new CustomPacketPayload.Type<>(VaporTrilhos.id("landship_disassemble"));

	public static final StreamCodec<ByteBuf, LandshipDisassemblePayload> CODEC = ByteBufCodecs.VAR_INT.map(
			LandshipDisassemblePayload::new, LandshipDisassemblePayload::entityId);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
