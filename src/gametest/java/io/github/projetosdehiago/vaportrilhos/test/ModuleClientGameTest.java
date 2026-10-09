package io.github.projetosdehiago.vaportrilhos.test;

import io.github.projetosdehiago.vaportrilhos.boiler.BalanceConstants;
import io.github.projetosdehiago.vaportrilhos.boiler.BoilerState;
import io.github.projetosdehiago.vaportrilhos.client.screen.LandshipScreen;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipEntity;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipMenu;
import io.github.projetosdehiago.vaportrilhos.module.LandshipBed;
import io.github.projetosdehiago.vaportrilhos.module.LandshipModules;
import io.github.projetosdehiago.vaportrilhos.module.ModuleSlot;
import io.github.projetosdehiago.vaportrilhos.registry.ModEntities;
import io.github.projetosdehiago.vaportrilhos.registry.ModItems;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Teste de cliente dos módulos ({@code ./gradlew runClientGameTest}): instala todos, mostra as
 * abas do painel, dorme na cama até de manhã e renasce ao lado do landship.
 */
public class ModuleClientGameTest implements FabricClientGameTest {
	/** Longe do spawn do mundo: renascer ali prova que foi o lar do landship. */
	private static final double FAR = 48;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getServer().runCommand("time set noon");
			singleplayer.getServer().runCommand("weather clear");
			singleplayer.getServer().runCommand("gamerule immediate_respawn true");
			// o mundo de teste congela o relógio; para a noite passar dormindo, ele precisa andar
			singleplayer.getServer().runCommand("gamerule advance_time true");
			context.waitTicks(20);

			int landshipId = singleplayer.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				ServerLevel level = player.level();
				LandshipEntity landship = ModEntities.LANDSHIP.create(level, EntitySpawnReason.COMMAND);
				landship.snapTo(player.getX() + FAR, player.getY(), player.getZ() + FAR, 180f, 0f);
				level.addFreshEntity(landship);
				player.teleportTo(level, landship.getX() + 3.5, landship.getY(), landship.getZ() - 4.5, Set.of(), 35f, 25f, true);
				return landship.getId();
			});
			context.waitTicks(40);

			// instala tudo como um jogador faria: agachado, clique com o módulo na mão
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				LandshipEntity landship = landship(server, landshipId);
				for (Item module : new Item[] {ModItems.BED_MODULE, ModItems.CARGO_MODULE, ModItems.CARGO_MODULE, ModItems.CARGO_MODULE,
						ModItems.CARGO_MODULE, ModItems.FURNACE_MODULE, ModItems.COMPACTOR_MODULE}) {
					player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(module));
					player.setShiftKeyDown(true);
					landship.interact(player, InteractionHand.MAIN_HAND, landship.position());
				}
				player.setShiftKeyDown(false);
				if (landship.modules().total() != 7) {
					throw new AssertionError("deviam estar 7 módulos instalados, estão " + landship.modules().total());
				}
				landship.modules().cargo().setItem(LandshipModules.cargoStart(ModuleSlot.FRONT_RIGHT), new ItemStack(Items.COBBLESTONE, 64));
				landship.modules().cargo().setItem(LandshipModules.cargoStart(ModuleSlot.FRONT_RIGHT) + 4, new ItemStack(Items.DIRT, 32));
				landship.modules().furnace().setItem(LandshipModules.FURNACE_INPUT, new ItemStack(Items.RAW_IRON, 8));
				BoilerState boiler = landship.boilerState();
				boiler.waterMb = BalanceConstants.WATER_CAPACITY_MB;
				boiler.temperatureC = 150f;
				boiler.pressureBar = 5f;
				boiler.fireLit = true;
				boiler.burnRemaining = 1600;
				boiler.burnTotal = 1600;
				landship.getFuelContainer().setItem(0, new ItemStack(Items.COAL, 16));
			});
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(20);
			context.takeScreenshot("modules-1-installed");

			// painel: uma foto por aba
			singleplayer.getServer().runOnServer(server -> player(server).openMenu(landship(server, landshipId)));
			context.waitForScreen(LandshipScreen.class);
			context.takeScreenshot("modules-2-tab-boiler");
			for (LandshipMenu.Tab tab : new LandshipMenu.Tab[] {LandshipMenu.Tab.CARGO, LandshipMenu.Tab.FURNACE, LandshipMenu.Tab.MODULES}) {
				context.runOnClient(minecraft -> {
					LandshipMenu menu = ((LandshipScreen) minecraft.gui.screen()).getMenu();
					menu.select(LandshipMenu.BUTTON_TAB + tab.ordinal());
					minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LandshipMenu.BUTTON_TAB + tab.ordinal());
				});
				context.waitTicks(tab == LandshipMenu.Tab.FURNACE ? 40 : 5);
				context.takeScreenshot("modules-3-tab-" + tab.name().toLowerCase(Locale.ROOT));
			}
			context.runOnClient(minecraft -> minecraft.player.closeContainer());
			context.waitTicks(5);

			// cama: à noite, dorme até de manhã
			singleplayer.getServer().runCommand("time set 14000");
			singleplayer.getServer().runCommand("effect give @a night_vision 60 0 true"); // só para o print sair claro
			context.waitTicks(5); // o céu escurece no tick seguinte
			singleplayer.getServer().runOnServer(server -> {
				LandshipEntity landship = landship(server, landshipId);
				landship.boilerState().fireLit = false;
				LandshipBed.SleepResult result = LandshipBed.trySleep(player(server), landship);
				if (result != LandshipBed.SleepResult.SLEEPING || !player(server).isSleeping()) {
					throw new AssertionError("o jogador devia ter deitado na cama do landship: " + result
							+ ", hora " + player(server).level().getOverworldClockTime() + ", claro " + player(server).level().isBrightOutside());
				}
			});
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(30);
			context.takeScreenshot("modules-4-sleeping");
			context.waitTicks(120);
			boolean morning = singleplayer.getServer().computeOnServer(server -> player(server).level().isBrightOutside());
			boolean awake = singleplayer.getServer().computeOnServer(server -> !player(server).isSleeping());
			if (!morning || !awake) {
				throw new AssertionError("dormir na cama do landship devia pular a noite (manhã: " + morning + ", acordado: " + awake + ")");
			}
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
			context.takeScreenshot("modules-5-woke-up");

			// renascer: o lar é o landship, longe do spawn do mundo
			singleplayer.getServer().runCommand("kill @a");
			// a posição nova vale quando o cliente termina de carregar o mundo depois de renascer
			Vec3 landshipPos = singleplayer.getServer().computeOnServer(server -> landship(server, landshipId).position());
			double distance = Double.MAX_VALUE;
			for (int i = 0; i < 40 && distance > 5.0; i++) {
				context.waitTicks(5);
				distance = singleplayer.getServer().computeOnServer(server -> player(server).position()).distanceTo(landshipPos);
			}
			if (distance > 5.0) {
				throw new AssertionError("devia renascer ao lado do landship, renasceu a " + distance + " blocos");
			}
			context.waitTicks(20);
			context.takeScreenshot("modules-6-respawned");
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static LandshipEntity landship(MinecraftServer server, int id) {
		return (LandshipEntity) player(server).level().getEntity(id);
	}
}
