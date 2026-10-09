package io.github.projetosdehiago.vaportrilhos.network;

import io.github.projetosdehiago.vaportrilhos.assembly.LandshipAssembly;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(LandshipActionPayload.TYPE, LandshipActionPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LandshipDisassemblePayload.TYPE, LandshipDisassemblePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(LandshipActionPayload.TYPE, (payload, context) -> {
			// o próprio landship confere se quem mandou é o piloto
			if (context.player().getVehicle() instanceof LandshipEntity landship) {
				landship.handleAction(context.player(), payload.action());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(LandshipDisassemblePayload.TYPE, (payload, context) -> {
			// mesmo alcance do painel; as demais regras (vazio, parado, frio, reparado) ficam no disassemble
			if (context.player().level().getEntity(payload.entityId()) instanceof LandshipEntity landship
					&& !context.player().isSpectator() && landship.isUsableBy(context.player())) {
				LandshipAssembly.disassemble(context.player().level(), landship, context.player());
			}
		});
	}
}
