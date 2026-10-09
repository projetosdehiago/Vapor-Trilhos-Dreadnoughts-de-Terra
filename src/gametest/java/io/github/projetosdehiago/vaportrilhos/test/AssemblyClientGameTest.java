package io.github.projetosdehiago.vaportrilhos.test;

import io.github.projetosdehiago.vaportrilhos.assembly.LandshipAssembly;
import io.github.projetosdehiago.vaportrilhos.assembly.LandshipAssembly.Layout;
import io.github.projetosdehiago.vaportrilhos.client.screen.LandshipScreen;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipEntity;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipMenu;
import io.github.projetosdehiago.vaportrilhos.module.ModuleSlot;
import io.github.projetosdehiago.vaportrilhos.module.ModuleType;
import io.github.projetosdehiago.vaportrilhos.registry.ModBlocks;
import io.github.projetosdehiago.vaportrilhos.registry.ModItems;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Teste de cliente da montagem ({@code ./gradlew runClientGameTest}): o jogador monta o gabarito
 * clicando com a Chave de Caldeireiro no Leme e depois desmonta pelo painel.
 */
public class AssemblyClientGameTest implements FabricClientGameTest {
	private static final Direction FRONT = Direction.EAST;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getServer().runCommand("time set noon");
			singleplayer.getServer().runCommand("weather clear");
			context.waitTicks(20);

			// gabarito com cama, baú e compactador, 6 blocos à frente do jogador
			BlockPos helm = singleplayer.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				ServerLevel level = player.level();
				BlockPos center = player.blockPosition().relative(FRONT, 6);
				Layout layout = new Layout(center.relative(FRONT).above(), FRONT);
				for (int row = 0; row < 3; row++) {
					level.setBlockAndUpdate(layout.chassis(row), ModBlocks.LANDSHIP_CHASSIS.defaultBlockState());
					level.setBlockAndUpdate(layout.track(row, 1), ModBlocks.REINFORCED_TRACK.defaultBlockState());
					level.setBlockAndUpdate(layout.track(row, -1), ModBlocks.REINFORCED_TRACK.defaultBlockState());
				}
				level.setBlockAndUpdate(layout.helm(), ModBlocks.LANDSHIP_HELM.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, FRONT));
				level.setBlockAndUpdate(layout.boiler(), ModBlocks.STEAM_BOILER.defaultBlockState());
				Map.of(ModuleSlot.FRONT_LEFT, ModuleType.BED, ModuleSlot.REAR_RIGHT, ModuleType.CARGO, ModuleSlot.MID_LEFT, ModuleType.CARGO)
						.forEach((slot, type) -> level.setBlockAndUpdate(layout.module(slot), LandshipAssembly.blockOf(type).defaultBlockState()));
				level.setBlockAndUpdate(layout.compactor(), ModBlocks.COMPACTOR_MODULE.defaultBlockState());

				// o jogador fica na frente do leme, olhando para ele, com a chave na mão
				Vec3 stand = Vec3.atBottomCenterOf(layout.helm().below().relative(FRONT, 4)).add(0.0, 0.0, 1.6);
				player.teleportTo(level, stand.x, stand.y, stand.z, Set.of(), FRONT.getOpposite().toYRot() + 22f, 18f, true);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BOILERMAKER_WRENCH));
				return layout.helm();
			});
			context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(30);
			context.takeScreenshot("assembly-1-template");

			// olha exatamente para o leme e clica com a chave
			context.runOnClient(minecraft -> {
				Vec3 eye = minecraft.player.getEyePosition();
				Vec3 target = Vec3.atCenterOf(helm);
				Vec3 d = target.subtract(eye);
				minecraft.player.setYRot((float) (Math.atan2(-d.x, d.z) * 180.0 / Math.PI));
				minecraft.player.setXRot((float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180.0 / Math.PI));
			});
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(20);
			LandshipEntity landship = singleplayer.getServer().computeOnServer(server -> {
				List<LandshipEntity> found = player(server).level().getEntitiesOfClass(LandshipEntity.class, new AABB(helm).inflate(4), e -> true);
				return found.isEmpty() ? null : found.getFirst();
			});
			if (landship == null) {
				throw new AssertionError("a chave no leme devia montar o landship");
			}
			int landshipId = landship.getId();
			boolean modulesOk = singleplayer.getServer().computeOnServer(server -> {
				LandshipEntity l = (LandshipEntity) player(server).level().getEntity(landshipId);
				return l.getModuleAt(ModuleSlot.FRONT_LEFT) == ModuleType.BED && l.getModuleAt(ModuleSlot.FRONT) == ModuleType.COMPACTOR
						&& l.modules().count(ModuleType.CARGO) == 2;
			});
			if (!modulesOk) {
				throw new AssertionError("os módulos do gabarito deviam vir montados");
			}
			context.takeScreenshot("assembly-2-assembled");

			// desmonta pelo painel
			singleplayer.getServer().runOnServer(server -> player(server).openMenu((LandshipEntity) player(server).level().getEntity(landshipId)));
			context.waitForScreen(LandshipScreen.class);
			context.runOnClient(minecraft -> {
				LandshipMenu menu = ((LandshipScreen) minecraft.gui.screen()).getMenu();
				menu.select(LandshipMenu.BUTTON_TAB + LandshipMenu.Tab.MODULES.ordinal());
				minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LandshipMenu.BUTTON_TAB + LandshipMenu.Tab.MODULES.ordinal());
			});
			context.waitTicks(5);
			context.takeScreenshot("assembly-3-modules-tab");
			context.runOnClient(minecraft -> {
				LandshipMenu menu = ((LandshipScreen) minecraft.gui.screen()).getMenu();
				minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LandshipMenu.BUTTON_DISASSEMBLE);
			});
			context.waitTicks(20);
			boolean back = singleplayer.getServer().computeOnServer(server -> player(server).level().getBlockState(helm).is(ModBlocks.LANDSHIP_HELM)
					&& player(server).level().getEntity(landshipId) == null);
			if (!back) {
				throw new AssertionError("o botão Desmontar devia trocar o landship pelos blocos");
			}
			context.takeScreenshot("assembly-4-disassembled");
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}
}
