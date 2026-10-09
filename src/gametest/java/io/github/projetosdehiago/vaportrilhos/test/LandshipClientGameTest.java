package io.github.projetosdehiago.vaportrilhos.test;

import io.github.projetosdehiago.vaportrilhos.boiler.BalanceConstants;
import io.github.projetosdehiago.vaportrilhos.boiler.BoilerState;
import io.github.projetosdehiago.vaportrilhos.client.screen.LandshipScreen;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipEntity;
import io.github.projetosdehiago.vaportrilhos.registry.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Teste de cliente ({@code ./gradlew runClientGameTest}): abre o jogo, cria um mundo plano, coloca
 * um landship, embarca, dirige, força a válvula de segurança e abre o painel, tirando prints.
 */
public class LandshipClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getServer().runCommand("time set noon");
			singleplayer.getServer().runCommand("weather clear");
			context.waitTicks(20);

			int landshipId = singleplayer.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				ServerLevel level = player.level();
				LandshipEntity landship = ModEntities.LANDSHIP.create(level, EntitySpawnReason.COMMAND);
				landship.snapTo(player.getX() + 4, player.getY(), player.getZ() + 5, 200f, 0f);
				level.addFreshEntity(landship);
				player.snapTo(player.getX(), player.getY(), player.getZ(), -35f, 20f);
				return landship.getId();
			});
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(40);
			context.takeScreenshot("landship-1-parked");

			// caldeira pronta e piloto a bordo
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				LandshipEntity landship = landship(server, landshipId);
				BoilerState boiler = landship.boilerState();
				boiler.waterMb = BalanceConstants.WATER_CAPACITY_MB;
				boiler.temperatureC = 180f;
				boiler.pressureBar = 8f;
				boiler.fireLit = true;
				landship.getFuelContainer().setItem(0, new ItemStack(Items.COAL, 16));
				boiler.burnRemaining = 1600;
				boiler.burnTotal = 1600;
				player.startRiding(landship);
			});
			context.waitTicks(5);
			// olhar na direção do veículo: em terceira pessoa a câmera fica atrás dele
			context.runOnClient(minecraft -> {
				LandshipEntity landship = (LandshipEntity) minecraft.level.getEntity(landshipId);
				minecraft.player.setYRot(landship.getYRot());
				minecraft.player.setXRot(15f);
				minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			context.waitTicks(30);
			context.takeScreenshot("landship-2-aboard-hud");
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("landship-2b-front-view");
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));

			// dirigir: segura W por 3 s, depois curva
			Vec3 start = singleplayer.getServer().computeOnServer(server -> landship(server, landshipId).position());
			context.getInput().holdKeyFor(options -> options.keyUp, 60);
			context.takeScreenshot("landship-3-driving");
			context.getInput().holdKeyFor(options -> options.keyRight, 30);
			context.waitTicks(10);
			double moved = singleplayer.getServer().computeOnServer(server -> landship(server, landshipId).position().distanceTo(start));
			if (moved < 2.0) {
				throw new AssertionError("o landship devia ter andado com W, andou " + moved);
			}
			context.takeScreenshot("landship-4-turning");

			// sobrepressão: a válvula de segurança solta vapor que cega até quem está a bordo
			singleplayer.getServer().runOnServer(server -> landship(server, landshipId).boilerState().pressureBar = 9.99f);
			context.waitTicks(4);
			boolean blinded = singleplayer.getServer().computeOnServer(server -> player(server).hasEffect(MobEffects.BLINDNESS));
			if (!blinded) {
				throw new AssertionError("a válvula de segurança devia cegar o piloto");
			}
			context.takeScreenshot("landship-5-safety-valve");
			context.waitTicks(70);

			// painel da caldeira pela tecla de inventário (E) enquanto embarcado
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(LandshipScreen.class);
			context.takeScreenshot("landship-6-boiler-panel");
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitTicks(5);

			float integrity = singleplayer.getServer().computeOnServer(server -> landship(server, landshipId).serverIntegrity());
			if (integrity >= BalanceConstants.MAX_INTEGRITY) {
				throw new AssertionError("a ventilação devia desgastar o casco");
			}

			// plataforma: o jogador desce, sobe no teto e o landship anda sem piloto (movido pelo
			// servidor). Quem leva o jogador junto é o próprio cliente dele.
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				LandshipEntity landship = landship(server, landshipId);
				player.stopRiding();
				Vec3 roof = landship.position().add(landship.forwardVector().scale(-0.6)).add(landship.leftVector().scale(0.6));
				player.teleportTo(roof.x, landship.getBoundingBox().maxY + 0.3, roof.z);
			});
			context.waitTicks(30);
			context.runOnClient(minecraft -> {
				LandshipEntity landship = (LandshipEntity) minecraft.level.getEntity(landshipId);
				minecraft.player.setYRot(landship.getYRot() + 180f);
				minecraft.player.setXRot(25f);
				minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			Vec3 playerStart = context.computeOnClient(minecraft -> minecraft.player.position());
			for (int i = 0; i < 40; i++) {
				singleplayer.getServer().runOnServer(server -> {
					LandshipEntity landship = landship(server, landshipId);
					landship.setPos(landship.position().add(landship.forwardVector().scale(0.1)));
				});
				context.waitTick();
			}
			context.waitTicks(10);
			context.takeScreenshot("landship-7-roof-platform");
			Vec3 playerEnd = context.computeOnClient(minecraft -> minecraft.player.position());
			double roofTop = context.computeOnClient(minecraft -> minecraft.level.getEntity(landshipId).getBoundingBox().maxY);
			double carried = playerEnd.subtract(playerStart).horizontalDistance();
			if (carried < 3.0 || Math.abs(playerEnd.y - roofTop) > 0.2) {
				throw new AssertionError("o jogador no teto devia andar junto: andou " + carried + ", y " + playerEnd.y + " (teto " + roofTop + ")");
			}
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static LandshipEntity landship(MinecraftServer server, int id) {
		return (LandshipEntity) player(server).level().getEntity(id);
	}
}
