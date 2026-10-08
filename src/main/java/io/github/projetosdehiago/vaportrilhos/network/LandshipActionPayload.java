package io.github.projetosdehiago.vaportrilhos.network;

import io.github.projetosdehiago.vaportrilhos.VaporTrilhos;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente → servidor: o piloto apertou uma tecla de comando do landship. */
public record LandshipActionPayload(Action action) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<LandshipActionPayload> TYPE = new CustomPacketPayload.Type<>(VaporTrilhos.id("landship_action"));

	public static final StreamCodec<ByteBuf, LandshipActionPayload> CODEC = ByteBufCodecs.VAR_INT.map(
			id -> new LandshipActionPayload(Action.byId(id)), payload -> payload.action().ordinal());

	public enum Action {
		CYCLE_DAMPER,
		MANUAL_VENT,
		WHISTLE;

		public static Action byId(int id) {
			Action[] all = values();
			return id >= 0 && id < all.length ? all[id] : WHISTLE;
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
