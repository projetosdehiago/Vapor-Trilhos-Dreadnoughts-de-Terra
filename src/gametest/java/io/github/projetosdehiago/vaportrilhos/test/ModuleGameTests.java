package io.github.projetosdehiago.vaportrilhos.test;

import io.github.projetosdehiago.vaportrilhos.boiler.BalanceConstants;
import io.github.projetosdehiago.vaportrilhos.boiler.BoilerState;
import io.github.projetosdehiago.vaportrilhos.boiler.Damper;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipEntity;
import io.github.projetosdehiago.vaportrilhos.module.LandshipBed;
import io.github.projetosdehiago.vaportrilhos.module.LandshipModules;
import io.github.projetosdehiago.vaportrilhos.module.ModuleSlot;
import io.github.projetosdehiago.vaportrilhos.module.ModuleType;
import io.github.projetosdehiago.vaportrilhos.registry.ModAttachments;
import io.github.projetosdehiago.vaportrilhos.registry.ModEntities;
import io.github.projetosdehiago.vaportrilhos.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.util.EventResult;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Testes de servidor dos módulos (Fase 2): instalação, baú, fornalha, compactador e cama. */
public class ModuleGameTests {
	private static final int SIZE = 8;

	private static LandshipEntity setUp(GameTestHelper helper) {
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		LandshipEntity landship = helper.spawn(ModEntities.LANDSHIP, new Vec3(SIZE / 2.0, 1, SIZE / 2.0));
		landship.setYRot(0f);
		return landship;
	}

	/** Jogador agachado (instalar/remover módulos) com um item na mão. */
	private static Player sneaking(GameTestHelper helper, LandshipEntity landship, ItemStack stack) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setPos(landship.getX() + 2, landship.getY(), landship.getZ());
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		return player;
	}

	private static void install(GameTestHelper helper, LandshipEntity landship, Item module) {
		Player player = sneaking(helper, landship, new ItemStack(module));
		landship.interact(player, InteractionHand.MAIN_HAND, landship.position());
		helper.assertTrue(player.getMainHandItem().isEmpty(), "o módulo " + module + " devia ter sido instalado");
	}

	private static void hotBoiler(LandshipEntity landship) {
		BoilerState boiler = landship.boilerState();
		boiler.waterMb = BalanceConstants.WATER_CAPACITY_MB;
		boiler.temperatureC = 180f;
		boiler.pressureBar = 8f;
		boiler.fireLit = true;
		boiler.burnRemaining = 10_000;
		boiler.burnTotal = 10_000;
	}

	@GameTest(maxTicks = 40)
	public void modulesFillSlotsInOrderAndRespectLimits(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> {
					install(helper, landship, ModItems.BED_MODULE);
					// segunda cama: limite de 1, o item fica na mão
					Player extraBed = sneaking(helper, landship, new ItemStack(ModItems.BED_MODULE));
					landship.interact(extraBed, InteractionHand.MAIN_HAND, landship.position());
					helper.assertTrue(!extraBed.getMainHandItem().isEmpty(), "só cabe 1 cama");
					for (int i = 0; i < 4; i++) {
						install(helper, landship, ModItems.CARGO_MODULE);
					}
					Player fifthChest = sneaking(helper, landship, new ItemStack(ModItems.CARGO_MODULE));
					landship.interact(fifthChest, InteractionHand.MAIN_HAND, landship.position());
					helper.assertTrue(!fifthChest.getMainHandItem().isEmpty(), "só cabem 4 baús");
					install(helper, landship, ModItems.FURNACE_MODULE);
					install(helper, landship, ModItems.COMPACTOR_MODULE);

					ModuleType[] installed = landship.getInstalledModules();
					helper.assertValueEqual(installed[ModuleSlot.FRONT_LEFT.ordinal()], ModuleType.BED, "frente esquerda");
					helper.assertValueEqual(installed[ModuleSlot.FRONT_RIGHT.ordinal()], ModuleType.CARGO, "frente direita");
					helper.assertValueEqual(installed[ModuleSlot.REAR_LEFT.ordinal()], ModuleType.CARGO, "trás esquerda");
					helper.assertValueEqual(installed[ModuleSlot.REAR_RIGHT.ordinal()], ModuleType.FURNACE, "trás direita");
					helper.assertValueEqual(installed[ModuleSlot.FRONT.ordinal()], ModuleType.COMPACTOR, "frente");
					helper.assertValueEqual(landship.modules().total(), 7, "módulos instalados");
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 40)
	public void wrenchRemovesTheLastModuleAndReturnsItsCargo(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> {
					install(helper, landship, ModItems.BED_MODULE);
					install(helper, landship, ModItems.CARGO_MODULE);
					landship.modules().cargo().setItem(LandshipModules.cargoStart(ModuleSlot.FRONT_RIGHT) + 3, new ItemStack(Items.COBBLESTONE, 10));

					Player player = sneaking(helper, landship, new ItemStack(ModItems.BOILERMAKER_WRENCH));
					landship.interact(player, InteractionHand.MAIN_HAND, landship.position());
					helper.assertTrue(landship.getModuleAt(ModuleSlot.FRONT_RIGHT) == null, "o baú (último instalado) devia sair");
					helper.assertTrue(landship.getModuleAt(ModuleSlot.FRONT_LEFT) == ModuleType.BED, "a cama devia ficar");
					helper.assertTrue(player.getInventory().countItem(ModItems.CARGO_MODULE) == 1, "o módulo volta para o jogador");
					helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 10, "o conteúdo do baú volta para o jogador");

					// enquanto houver módulo, a chave tira módulos em vez de recolher o veículo
					landship.interact(player, InteractionHand.MAIN_HAND, landship.position());
					helper.assertTrue(landship.modules().isEmpty() && !landship.isRemoved(), "a segunda vez tira a cama, sem recolher o landship");
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 40)
	public void destroyedLandshipDropsModulesAndCargo(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> {
					install(helper, landship, ModItems.CARGO_MODULE);
					landship.modules().cargo().setItem(LandshipModules.cargoStart(ModuleSlot.FRONT_LEFT), new ItemStack(Items.DIAMOND, 3));
					landship.setServerIntegrity(1f);
					landship.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 5f);
				})
				.thenIdle(2)
				.thenExecute(() -> {
					AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(SIZE);
					long chests = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(ModItems.CARGO_MODULE)).size();
					int diamonds = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(Items.DIAMOND))
							.stream().mapToInt(e -> e.getItem().getCount()).sum();
					helper.assertTrue(chests == 1, "o módulo de baú devia cair");
					helper.assertTrue(diamonds == 3, "o conteúdo do baú devia cair, caíram " + diamonds);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 260)
	public void furnaceSmeltsTwiceAsFastWithBoilerHeat(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		float[] burnStart = new float[1];
		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> {
					install(helper, landship, ModItems.FURNACE_MODULE);
					hotBoiler(landship);
					landship.boilerState().damper = Damper.NORMAL;
					landship.modules().furnace().setItem(LandshipModules.FURNACE_INPUT, new ItemStack(Items.RAW_IRON, 2));
					burnStart[0] = landship.boilerState().burnRemaining;
				})
				// 5 s por item (a fornalha comum leva 10 s)
				.thenIdle(BalanceConstants.FURNACE_TICKS_PER_ITEM + 2)
				.thenExecute(() -> {
					ItemStack out = landship.modules().furnace().getItem(LandshipModules.FURNACE_OUTPUT);
					helper.assertTrue(out.is(Items.IRON_INGOT) && out.getCount() == 1, "devia ter 1 lingote depois de 5 s, tem " + out);
					float burned = burnStart[0] - landship.boilerState().burnRemaining;
					// Normal gasta 1 tick de queima por tick; a fornalha gasta outro tanto
					helper.assertTrue(burned > 1.8f * BalanceConstants.FURNACE_TICKS_PER_ITEM, "a fornalha devia gastar combustível da caldeira, gastou " + burned);
					helper.assertTrue(landship.modules().storedExperience > 0f, "a experiência devia acumular");
				})
				.thenIdle(BalanceConstants.FURNACE_TICKS_PER_ITEM)
				.thenExecute(() -> helper.assertTrue(landship.modules().furnace().getItem(LandshipModules.FURNACE_OUTPUT).getCount() == 2,
						"devia ter 2 lingotes depois de 10 s"))
				.thenSucceed();
	}

	@GameTest(maxTicks = 160)
	public void furnaceNeedsAHotBoiler(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> {
					install(helper, landship, ModItems.FURNACE_MODULE);
					landship.modules().furnace().setItem(LandshipModules.FURNACE_INPUT, new ItemStack(Items.RAW_IRON, 2));
				})
				.thenIdle(BalanceConstants.FURNACE_TICKS_PER_ITEM + 20)
				.thenExecute(() -> helper.assertTrue(landship.modules().furnace().getItem(LandshipModules.FURNACE_OUTPUT).isEmpty(),
						"com a caldeira fria a fornalha não funde"))
				.thenSucceed();
	}

	@GameTest(maxTicks = 120)
	public void compactorClearsDirtFlattensTheGroundAndFillsHoles(GameTestHelper helper) {
		// base de pedra, chão de grama por cima; o landship começa no fundo da área virado para +Z
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}
		BlockPos obstacle = new BlockPos(4, 2, 5);
		BlockPos plant = new BlockPos(3, 2, 6);
		BlockPos hole = new BlockPos(5, 1, 6);
		helper.setBlock(obstacle, Blocks.DIRT);
		helper.setBlock(plant, Blocks.SHORT_GRASS);
		helper.setBlock(hole, Blocks.AIR);

		LandshipEntity landship = helper.spawn(ModEntities.LANDSHIP, new Vec3(SIZE / 2.0, 2, 1.5));
		landship.setYRot(0f);
		hotBoiler(landship);
		Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> {
					install(helper, landship, ModItems.CARGO_MODULE);
					install(helper, landship, ModItems.COMPACTOR_MODULE);
					landship.modules().compactorOn = true;
					pilot.snapTo(landship.getX(), landship.getY(), landship.getZ());
					pilot.startRiding(landship);
				})
				.thenExecuteFor(40, () -> landship.setInput(true, false, false, false))
				.thenExecute(() -> {
					helper.assertBlockPresent(Blocks.AIR, obstacle);
					helper.assertBlockPresent(Blocks.AIR, plant);
					helper.assertBlockPresent(Blocks.DIRT_PATH, obstacle.below());
					// o buraco foi tapado com a terra que o próprio compactador guardou, e virou caminho
					helper.assertBlockPresent(Blocks.DIRT_PATH, hole);
					helper.assertTrue(landship.serverIntegrity() < BalanceConstants.MAX_INTEGRITY, "compactar desgasta o casco");
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 40)
	public void bedIsAValidBedOnlyWhereItIs(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> {
					install(helper, landship, ModItems.BED_MODULE);
					Player player = helper.makeMockPlayer(GameType.SURVIVAL);
					BlockPos bed = landship.bedBlockPos();
					EventResult here = EntitySleepEvents.ALLOW_BED.invoker().allowBed(player, bed, Blocks.AIR.defaultBlockState(), false);
					EventResult elsewhere = EntitySleepEvents.ALLOW_BED.invoker().allowBed(player, bed.east(3), Blocks.AIR.defaultBlockState(), false);
					helper.assertValueEqual(here, EventResult.ALLOW, "a cama do landship parado vale como cama");
					helper.assertValueEqual(elsewhere, EventResult.PASS, "fora da cama não vale");
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 40)
	public void respawnPointLastsOnlyWhileTheLandshipExists(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> {
					install(helper, landship, ModItems.BED_MODULE);
					LandshipBed.recordHome(player, landship);
					LandshipBed.RespawnTarget target = LandshipBed.findRespawn(player);
					helper.assertTrue(target != null, "com o landship inteiro, o lar vale");
					helper.assertTrue(target.position().distanceTo(landship.position()) < 4.0,
							"renasce ao lado do landship, não em " + target.position());
					landship.discard();
				})
				.thenIdle(1)
				.thenExecute(() -> {
					helper.assertTrue(LandshipBed.findRespawn(player) == null, "sem o landship o lar não vale mais");
					helper.assertTrue(!player.hasAttached(ModAttachments.HOME), "e é apagado");
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 40)
	public void deathUsesTheLandshipAsRespawnPointThenRestoresTheOldOne(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ServerPlayer.RespawnConfig bedAtHome = new ServerPlayer.RespawnConfig(
				LevelData.RespawnData.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 1, 1)), 0f, 0f), false);
		player.setRespawnPosition(bedAtHome, false);
		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> {
					install(helper, landship, ModItems.BED_MODULE);
					LandshipBed.recordHome(player, landship);
					ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(player, helper.getLevel().damageSources().generic());
					ServerPlayer.RespawnConfig onDeath = player.getRespawnConfig();
					helper.assertTrue(onDeath != null && onDeath.forced(), "na morte, o lar vira o ponto de renascimento forçado");
					helper.assertTrue(Vec3.atBottomCenterOf(onDeath.respawnData().pos()).distanceTo(landship.position()) < 4.0,
							"o ponto fica ao lado do landship, não em " + onDeath.respawnData().pos());

					ServerPlayer respawned = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
					respawned.setRespawnPosition(onDeath, false);
					ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(player, respawned, false);
					helper.assertValueEqual(respawned.getRespawnConfig(), bedAtHome, "depois de renascer, o ponto normal volta");
				})
				.thenSucceed();
	}
}
