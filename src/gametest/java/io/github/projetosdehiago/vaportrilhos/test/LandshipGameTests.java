package io.github.projetosdehiago.vaportrilhos.test;

import io.github.projetosdehiago.vaportrilhos.boiler.BalanceConstants;
import io.github.projetosdehiago.vaportrilhos.boiler.BoilerState;
import io.github.projetosdehiago.vaportrilhos.landship.FuelHelper;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipEntity;
import io.github.projetosdehiago.vaportrilhos.registry.ModDataComponents;
import io.github.projetosdehiago.vaportrilhos.registry.ModEntities;
import io.github.projetosdehiago.vaportrilhos.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Testes de servidor do landship (rodam com {@code ./gradlew runGameTest} e no CI). */
public class LandshipGameTests {
	private static final int SIZE = 8;

	/** Chão de pedra e o landship no meio da área de teste. */
	private static LandshipEntity setUp(GameTestHelper helper) {
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		return helper.spawn(ModEntities.LANDSHIP, new Vec3(SIZE / 2.0, 1, SIZE / 2.0));
	}

	private static Player player(GameTestHelper helper, LandshipEntity landship, ItemStack stack) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setPos(landship.getX() + 2, landship.getY(), landship.getZ());
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		return player;
	}

	private static void use(LandshipEntity landship, Player player) {
		landship.interact(player, InteractionHand.MAIN_HAND, landship.position());
	}

	/** Caldeira quente, cheia e com pressão: pronta para andar. */
	private static void hotBoiler(LandshipEntity landship, float pressure) {
		BoilerState boiler = landship.boilerState();
		boiler.waterMb = BalanceConstants.WATER_CAPACITY_MB;
		boiler.temperatureC = BalanceConstants.BOILING_C + 10 * pressure;
		boiler.pressureBar = pressure;
		boiler.fireLit = true;
		boiler.burnRemaining = 10_000;
		boiler.burnTotal = 10_000;
	}

	@GameTest(maxTicks = 60)
	public void landshipSettlesOnTheGround(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		helper.succeedWhen(() -> helper.assertTrue(landship.onGround(), "o landship devia estar no chão"));
	}

	@GameTest
	public void coalBurnsLikeInAFurnace(GameTestHelper helper) {
		helper.assertValueEqual(FuelHelper.burnTicks(helper.getLevel(), new ItemStack(Items.COAL), Vec3.ZERO), 1600, "queima do carvão");
		helper.assertValueEqual(FuelHelper.burnTicks(helper.getLevel(), new ItemStack(Items.LAVA_BUCKET), Vec3.ZERO), 20000, "queima do balde de lava");
		helper.assertValueEqual(FuelHelper.burnTicks(helper.getLevel(), new ItemStack(Items.STONE), Vec3.ZERO), 0, "pedra não queima");
		helper.succeed();
	}

	@GameTest
	public void waterBucketFillsTheTank(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		Player player = player(helper, landship, new ItemStack(Items.WATER_BUCKET));
		use(landship, player);
		helper.assertValueEqual(Math.round(landship.boilerState().waterMb), BalanceConstants.BUCKET_MB, "água no tanque");
		helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "o balde devia voltar vazio");
		helper.succeed();
	}

	@GameTest
	public void coalAndFlintLightTheFirebox(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		Player player = player(helper, landship, new ItemStack(Items.COAL, 5));
		use(landship, player);
		helper.assertValueEqual(landship.getFuelContainer().getItem(0).getCount(), 5, "carvão no compartimento");

		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
		use(landship, player);
		helper.assertTrue(landship.boilerState().fireLit, "o fogo devia acender");
		helper.assertValueEqual(landship.getFuelContainer().getItem(0).getCount(), 4, "um carvão queimando");
		helper.succeed();
	}

	@GameTest
	public void flintWithoutFuelDoesNothing(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		Player player = player(helper, landship, new ItemStack(Items.FLINT_AND_STEEL));
		use(landship, player);
		helper.assertFalse(landship.boilerState().fireLit, "sem combustível não acende");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void safetyValveVentsAndWears(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		hotBoiler(landship, 9.995f);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(landship.boilerState().pressureBar <= BalanceConstants.SAFETY_VALVE_RESET_BAR + 0.1f,
					"a válvula devia aliviar para 8 bar, está em " + landship.boilerState().pressureBar);
			helper.assertTrue(landship.serverIntegrity() <= BalanceConstants.MAX_INTEGRITY - BalanceConstants.WEAR_VENT + 0.01f,
					"a ventilação devia desgastar o casco");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void dryBoilerWearsTheHull(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		hotBoiler(landship, 0f);
		landship.boilerState().waterMb = 0;
		landship.boilerState().temperatureC = 150;
		helper.runAfterDelay(40, () -> {
			float lost = BalanceConstants.MAX_INTEGRITY - landship.serverIntegrity();
			helper.assertTrue(lost > 1.5f && lost < 2.5f, "2 s de caldeira seca deviam tirar ~2 de integridade, tiraram " + lost);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void woodPatchesStopAt60PercentButIronGoesOn(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		landship.setServerIntegrity(118f);
		Player player = player(helper, landship, new ItemStack(Items.OAK_PLANKS, 4));
		helper.startSequence()
				.thenExecute(() -> use(landship, player))
				.thenExecuteAfter(12, () -> use(landship, player))
				.thenExecute(() -> helper.assertTrue(Math.abs(landship.serverIntegrity() - 120f) < 0.01f,
						"madeira para em 60 %, está em " + landship.serverIntegrity()))
				.thenExecuteAfter(12, () -> {
					player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT));
					use(landship, player);
				})
				.thenExecute(() -> helper.assertTrue(Math.abs(landship.serverIntegrity() - 130f) < 0.01f,
						"ferro passa de 60 %, está em " + landship.serverIntegrity()))
				.thenSucceed();
	}

	@GameTest
	public void wrenchPicksUpAColdLandship(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		landship.setServerIntegrity(150f);
		landship.boilerState().waterMb = 2000;
		Player player = player(helper, landship, new ItemStack(ModItems.BOILERMAKER_WRENCH));
		player.setShiftKeyDown(true);
		use(landship, player);
		helper.assertTrue(landship.isRemoved(), "o landship devia ser recolhido");
		ItemStack item = ItemStack.EMPTY;
		for (ItemStack stack : player.getInventory()) {
			if (stack.is(ModItems.LANDSHIP)) {
				item = stack;
			}
		}
		helper.assertFalse(item.isEmpty(), "o jogador devia receber o item do landship");
		ModDataComponents.LandshipData data = item.get(ModDataComponents.LANDSHIP_DATA);
		helper.assertTrue(data != null && data.integrity() == 150f && data.waterMb() == 2000, "o item devia guardar integridade e água");
		helper.succeed();
	}

	@GameTest
	public void hotLandshipCannotBePickedUp(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		hotBoiler(landship, 3f);
		Player player = player(helper, landship, new ItemStack(ModItems.BOILERMAKER_WRENCH));
		player.setShiftKeyDown(true);
		use(landship, player);
		helper.assertFalse(landship.isRemoved(), "com a caldeira quente não dá para recolher");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void destroyedLandshipDropsScrap(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		landship.getFuelContainer().setItem(0, new ItemStack(Items.COAL, 3));
		landship.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 500f);
		helper.assertTrue(landship.isRemoved(), "integridade 0 destrói o landship");
		helper.runAfterDelay(2, () -> {
			AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(SIZE * 2);
			boolean tracks = !helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(ModItems.REINFORCED_TRACK)).isEmpty();
			boolean coal = !helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(Items.COAL)).isEmpty();
			helper.assertTrue(tracks, "devia dropar esteiras");
			helper.assertTrue(coal, "devia dropar o combustível");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void pilotDrivesForwardWithSteam(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		landship.setYRot(0f);
		hotBoiler(landship, 8f);
		Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
		pilot.snapTo(landship.getX(), landship.getY(), landship.getZ());
		pilot.startRiding(landship);
		double[] startZ = new double[1];
		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> startZ[0] = landship.getZ())
				.thenExecuteFor(40, () -> landship.setInput(true, false, false, false))
				.thenExecute(() -> {
					// 1 m/s² a partir do repouso (+Z = frente com yaw 0): 2 s → 2 blocos e 2 m/s
					double moved = landship.getZ() - startZ[0];
					double speedMs = landship.getDeltaMovement().z * BalanceConstants.TICKS_PER_SECOND;
					helper.assertTrue(moved > 1.7 && moved < 2.3, "devia andar ~2 blocos para a frente, andou " + moved);
					helper.assertTrue(speedMs > 1.8 && speedMs < 2.2, "devia estar a ~2 m/s, está a " + speedMs);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 80)
	public void noPressureNoMovement(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
		pilot.snapTo(landship.getX(), landship.getY(), landship.getZ());
		pilot.startRiding(landship);
		Vec3 start = landship.position();
		helper.startSequence()
				.thenIdle(5)
				.thenExecuteFor(40, () -> landship.setInput(true, false, false, false))
				.thenExecute(() -> helper.assertTrue(landship.position().horizontalDistanceSqr() - start.horizontalDistanceSqr() < 0.01
						&& landship.position().distanceTo(start) < 0.2, "sem pressão o landship não anda"))
				.thenSucceed();
	}

	/** Landship com caldeira pronta, piloto a bordo e um suporte de armadura em pé no teto. */
	private static ArmorStand standOnTop(GameTestHelper helper, LandshipEntity landship, double leftOffset) {
		landship.setYRot(0f);
		hotBoiler(landship, 8f);
		Player pilot = helper.makeMockPlayer(GameType.SURVIVAL);
		pilot.snapTo(landship.getX(), landship.getY(), landship.getZ());
		pilot.startRiding(landship);
		ArmorStand stand = new ArmorStand(helper.getLevel(), landship.getX() + leftOffset, landship.getY() + 2.6, landship.getZ());
		helper.getLevel().addFreshEntity(stand);
		return stand;
	}

	@GameTest(maxTicks = 100)
	public void entitiesStandOnTheRoofAndRideAlong(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		ArmorStand stand = standOnTop(helper, landship, 0.5);
		Vec3[] start = new Vec3[2];
		helper.startSequence()
				.thenIdle(20)
				.thenExecute(() -> {
					double top = landship.getBoundingBox().maxY;
					helper.assertTrue(Math.abs(stand.getY() - top) < 0.05, "o teto devia ser sólido: suporte em " + stand.getY() + ", teto em " + top);
					start[0] = landship.position();
					start[1] = stand.position();
				})
				.thenExecuteFor(40, () -> landship.setInput(true, false, false, false))
				.thenExecute(() -> {
					Vec3 shipMoved = landship.position().subtract(start[0]);
					Vec3 standMoved = stand.position().subtract(start[1]);
					helper.assertTrue(shipMoved.z > 1.5, "o landship devia ter andado, andou " + shipMoved.z);
					helper.assertTrue(standMoved.distanceTo(shipMoved) < 0.2,
							"quem está em cima devia andar junto: landship " + shipMoved + ", suporte " + standMoved);
					helper.assertTrue(Math.abs(stand.getY() - landship.getBoundingBox().maxY) < 0.05, "o suporte devia continuar em pé no teto");
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void pivotTurnsWhoIsOnTheRoof(GameTestHelper helper) {
		LandshipEntity landship = setUp(helper);
		ArmorStand stand = standOnTop(helper, landship, 1.0);
		Vec3[] localStart = new Vec3[1];
		float[] yawStart = new float[1];
		helper.startSequence()
				.thenIdle(20)
				.thenExecute(() -> {
					localStart[0] = toLocal(landship, stand);
					yawStart[0] = landship.getYRot();
				})
				.thenExecuteFor(40, () -> landship.setInput(false, false, false, true))
				.thenExecute(() -> {
					float turned = Math.abs(landship.getYRot() - yawStart[0]);
					helper.assertTrue(turned > 60f, "o landship devia ter girado no lugar, girou " + turned + "°");
					Vec3 local = toLocal(landship, stand);
					helper.assertTrue(local.distanceTo(localStart[0]) < 0.2,
							"o suporte devia girar junto (mesma posição em relação ao landship): antes " + localStart[0] + ", depois " + local);
				})
				.thenSucceed();
	}

	/** Posição da entidade no referencial do landship (desfaz a rotação dele). */
	private static Vec3 toLocal(LandshipEntity landship, Entity entity) {
		return entity.position().subtract(landship.position()).yRot(landship.getYRot() * Mth.DEG_TO_RAD);
	}

	@GameTest
	public void entityTypeHasTheDesignedFootprint(GameTestHelper helper) {
		EntityType<LandshipEntity> type = ModEntities.LANDSHIP;
		helper.assertValueEqual(type.getWidth(), 2.9f, "largura");
		helper.assertValueEqual(type.getHeight(), 2.0f, "altura");
		helper.succeed();
	}
}
