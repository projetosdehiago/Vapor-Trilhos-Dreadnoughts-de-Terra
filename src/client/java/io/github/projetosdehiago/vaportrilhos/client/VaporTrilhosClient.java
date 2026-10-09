package io.github.projetosdehiago.vaportrilhos.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.projetosdehiago.vaportrilhos.VaporTrilhos;
import io.github.projetosdehiago.vaportrilhos.client.hud.LandshipHud;
import io.github.projetosdehiago.vaportrilhos.client.render.LandshipRenderer;
import io.github.projetosdehiago.vaportrilhos.client.screen.LandshipScreen;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipEntity;
import io.github.projetosdehiago.vaportrilhos.network.LandshipActionPayload;
import io.github.projetosdehiago.vaportrilhos.registry.ModEntities;
import io.github.projetosdehiago.vaportrilhos.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.player.Input;

public final class VaporTrilhosClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(VaporTrilhos.id("main"));

	public static final KeyMapping DAMPER = key("damper", InputConstants.KEY_R);
	public static final KeyMapping VENT = key("vent", InputConstants.KEY_V);
	public static final KeyMapping WHISTLE = key("whistle", InputConstants.KEY_H);

	private static KeyMapping key(String name, int defaultKey) {
		return new KeyMapping("key.vapor_trilhos." + name, defaultKey, CATEGORY);
	}

	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.LANDSHIP, LandshipRenderer::new);
		LandshipEntity.setLocalPlayerAboardCheck(landship -> {
			LocalPlayer player = Minecraft.getInstance().player;
			return player != null && player.getVehicle() == landship;
		});
		MenuScreens.register(ModMenus.LANDSHIP, LandshipScreen::new);
		KeyMappingHelper.registerKeyMapping(DAMPER);
		KeyMappingHelper.registerKeyMapping(VENT);
		KeyMappingHelper.registerKeyMapping(WHISTLE);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, VaporTrilhos.id("landship_gauges"), new LandshipHud());

		ClientTickEvents.START_CLIENT_TICK.register(VaporTrilhosClient::beforeTick);
	}

	/** Passa o WASD do piloto para o landship e envia as teclas de comando ao servidor. */
	private static void beforeTick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || !(player.getVehicle() instanceof LandshipEntity landship) || landship.getControllingPassenger() != player) {
			consumeAll();
			return;
		}
		Input input = player.input.keyPresses;
		landship.setInput(input.forward(), input.backward(), input.left(), input.right());
		while (DAMPER.consumeClick()) {
			ClientPlayNetworking.send(new LandshipActionPayload(LandshipActionPayload.Action.CYCLE_DAMPER));
		}
		while (VENT.consumeClick()) {
			ClientPlayNetworking.send(new LandshipActionPayload(LandshipActionPayload.Action.MANUAL_VENT));
		}
		while (WHISTLE.consumeClick()) {
			ClientPlayNetworking.send(new LandshipActionPayload(LandshipActionPayload.Action.WHISTLE));
		}
	}

	private static void consumeAll() {
		while (DAMPER.consumeClick()) {
			// tecla sem efeito fora do landship
		}
		while (VENT.consumeClick()) {
			// idem
		}
		while (WHISTLE.consumeClick()) {
			// idem
		}
	}
}
