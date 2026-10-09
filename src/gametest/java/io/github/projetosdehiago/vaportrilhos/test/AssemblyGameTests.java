package io.github.projetosdehiago.vaportrilhos.test;

import io.github.projetosdehiago.vaportrilhos.assembly.CargoModuleBlockEntity;
import io.github.projetosdehiago.vaportrilhos.assembly.LandshipAssembly;
import io.github.projetosdehiago.vaportrilhos.assembly.LandshipAssembly.Layout;
import io.github.projetosdehiago.vaportrilhos.boiler.BalanceConstants;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipEntity;
import io.github.projetosdehiago.vaportrilhos.module.LandshipModules;
import io.github.projetosdehiago.vaportrilhos.module.ModuleSlot;
import io.github.projetosdehiago.vaportrilhos.module.ModuleType;
import io.github.projetosdehiago.vaportrilhos.registry.ModBlocks;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Testes de servidor da montagem a partir de blocos (Fase 3, design.md A8). */
public class AssemblyGameTests {
	private static final int SIZE = 8;
	private static final BlockPos CENTER = new BlockPos(4, 1, 4);

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** Gabarito com o centro em {@link #CENTER}; devolve as posições (absolutas). */
	private static Layout build(GameTestHelper helper, Direction front, Map<ModuleSlot, ModuleType> modules) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		BlockPos center = helper.absolutePos(CENTER);
		Layout layout = new Layout(center.relative(front).above(), front);
		for (int row = 0; row < 3; row++) {
			level.setBlockAndUpdate(layout.chassis(row), ModBlocks.LANDSHIP_CHASSIS.defaultBlockState());
			level.setBlockAndUpdate(layout.track(row, 1), ModBlocks.REINFORCED_TRACK.defaultBlockState());
			level.setBlockAndUpdate(layout.track(row, -1), ModBlocks.REINFORCED_TRACK.defaultBlockState());
		}
		level.setBlockAndUpdate(layout.helm(), ModBlocks.LANDSHIP_HELM.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, front));
		level.setBlockAndUpdate(layout.boiler(), ModBlocks.STEAM_BOILER.defaultBlockState());
		modules.forEach((slot, type) -> level.setBlockAndUpdate(slot.isFront() ? layout.compactor() : layout.module(slot),
				LandshipAssembly.blockOf(type).defaultBlockState()));
		return layout;
	}

	private static List<LandshipEntity> landships(GameTestHelper helper) {
		AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(SIZE + 2);
		return helper.getLevel().getEntitiesOfClass(LandshipEntity.class, area, e -> true);
	}

	private static void assemblesFacing(GameTestHelper helper, Direction front) {
		Layout layout = build(helper, front, Map.of());
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		LandshipEntity landship = LandshipAssembly.assemble(helper.getLevel(), layout.helm(), player);
		helper.assertTrue(landship != null, "o gabarito certo devia montar (frente " + front + ")");
		helper.assertTrue(landship.position().distanceTo(Vec3.atBottomCenterOf(layout.center())) < 0.01, "nasce no centro do gabarito");
		helper.assertTrue(Math.abs(Mth.wrapDegrees(landship.getYRot() - front.toYRot())) < 0.01f,
				"a frente do landship é a do leme: esperado " + front.toYRot() + ", veio " + landship.getYRot());
		helper.assertTrue(landship.forwardVector().distanceTo(Vec3.atLowerCornerOf(front.getUnitVec3i())) < 0.01, "o vetor de frente bate com o leme");
		for (BlockPos pos : List.of(layout.helm(), layout.boiler(), layout.chassis(0), layout.track(2, 1), layout.track(1, -1))) {
			helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "os blocos do gabarito somem: " + pos);
		}
		helper.succeed();
	}

	@GameTest
	public void assemblesFacingNorth(GameTestHelper helper) {
		assemblesFacing(helper, Direction.NORTH);
	}

	@GameTest
	public void assemblesFacingEast(GameTestHelper helper) {
		assemblesFacing(helper, Direction.EAST);
	}

	@GameTest
	public void assemblesFacingSouth(GameTestHelper helper) {
		assemblesFacing(helper, Direction.SOUTH);
	}

	@GameTest
	public void assemblesFacingWest(GameTestHelper helper) {
		assemblesFacing(helper, Direction.WEST);
	}

	@GameTest
	public void modulesAndCargoComeAlong(GameTestHelper helper) {
		Layout layout = build(helper, Direction.EAST, Map.of(
				ModuleSlot.FRONT_LEFT, ModuleType.BED,
				ModuleSlot.FRONT_RIGHT, ModuleType.CARGO,
				ModuleSlot.REAR_LEFT, ModuleType.FURNACE,
				ModuleSlot.FRONT, ModuleType.COMPACTOR));
		CargoModuleBlockEntity chest = (CargoModuleBlockEntity) helper.getLevel().getBlockEntity(layout.module(ModuleSlot.FRONT_RIGHT));
		chest.setItem(5, new ItemStack(Items.DIAMOND, 7));

		LandshipEntity landship = LandshipAssembly.assemble(helper.getLevel(), layout.helm(), null);
		helper.assertTrue(landship != null, "devia montar com módulos");
		helper.assertValueEqual(landship.getModuleAt(ModuleSlot.FRONT_LEFT), ModuleType.BED, "cama na frente esquerda");
		helper.assertValueEqual(landship.getModuleAt(ModuleSlot.FRONT_RIGHT), ModuleType.CARGO, "baú na frente direita");
		helper.assertValueEqual(landship.getModuleAt(ModuleSlot.REAR_LEFT), ModuleType.FURNACE, "fornalha atrás à esquerda");
		helper.assertValueEqual(landship.getModuleAt(ModuleSlot.FRONT), ModuleType.COMPACTOR, "compactador na frente");
		ItemStack moved = landship.modules().cargo().getItem(LandshipModules.cargoStart(ModuleSlot.FRONT_RIGHT) + 5);
		helper.assertTrue(moved.is(Items.DIAMOND) && moved.getCount() == 7, "o conteúdo do baú vai junto, veio " + moved);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,
				new AABB(layout.helm()).inflate(6), e -> true).isEmpty(), "nada pode cair no chão na montagem");
		helper.succeed();
	}

	@GameTest
	public void incompleteTemplateDoesNotAssemble(GameTestHelper helper) {
		Layout layout = build(helper, Direction.NORTH, Map.of());
		helper.getLevel().removeBlock(layout.track(2, -1), false);
		helper.getLevel().setBlockAndUpdate(layout.seat(), Blocks.STONE.defaultBlockState());
		LandshipAssembly.Scan scan = LandshipAssembly.scan(helper.getLevel(), layout.helm());
		helper.assertValueEqual(scan.problems().size(), 2, "esteira faltando e assento ocupado");
		helper.assertTrue(LandshipAssembly.assemble(helper.getLevel(), layout.helm(), null) == null, "não monta");
		helper.assertTrue(landships(helper).isEmpty(), "nenhum landship aparece");
		helper.assertTrue(helper.getLevel().getBlockState(layout.helm()).is(ModBlocks.LANDSHIP_HELM), "os blocos ficam onde estavam");
		helper.succeed();
	}

	@GameTest
	public void tooManyChestsDoNotAssemble(GameTestHelper helper) {
		Map<ModuleSlot, ModuleType> five = new EnumMap<>(ModuleSlot.class);
		for (ModuleSlot slot : List.of(ModuleSlot.FRONT_LEFT, ModuleSlot.FRONT_RIGHT, ModuleSlot.MID_LEFT, ModuleSlot.MID_RIGHT, ModuleSlot.REAR_LEFT)) {
			five.put(slot, ModuleType.CARGO);
		}
		Layout layout = build(helper, Direction.SOUTH, five);
		helper.assertTrue(LandshipAssembly.assemble(helper.getLevel(), layout.helm(), null) == null, "5 baús passam do limite de 4");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void disassemblyRoundTrip(GameTestHelper helper) {
		Layout layout = build(helper, Direction.WEST, Map.of(ModuleSlot.MID_LEFT, ModuleType.CARGO, ModuleSlot.REAR_RIGHT, ModuleType.BED));
		((CargoModuleBlockEntity) helper.getLevel().getBlockEntity(layout.module(ModuleSlot.MID_LEFT))).setItem(0, new ItemStack(Items.EMERALD, 3));
		LandshipEntity landship = LandshipAssembly.assemble(helper.getLevel(), layout.helm(), null);
		helper.assertTrue(landship != null, "devia montar");
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> {
					helper.assertTrue(LandshipAssembly.disassemble(helper.getLevel(), landship, player), "devia desmontar (parado, frio, inteiro)");
					helper.assertTrue(landship.isRemoved(), "o landship some");
					expectBlock(helper, layout.helm(), ModBlocks.LANDSHIP_HELM);
					helper.assertValueEqual(helper.getLevel().getBlockState(layout.helm()).getValue(HorizontalDirectionalBlock.FACING), Direction.WEST,
							"o leme volta virado para a frente do landship");
					expectBlock(helper, layout.boiler(), ModBlocks.STEAM_BOILER);
					for (int row = 0; row < 3; row++) {
						expectBlock(helper, layout.chassis(row), ModBlocks.LANDSHIP_CHASSIS);
						expectBlock(helper, layout.track(row, 1), ModBlocks.REINFORCED_TRACK);
						expectBlock(helper, layout.track(row, -1), ModBlocks.REINFORCED_TRACK);
					}
					expectBlock(helper, layout.module(ModuleSlot.REAR_RIGHT), ModBlocks.BED_MODULE);
					CargoModuleBlockEntity chest = (CargoModuleBlockEntity) helper.getLevel().getBlockEntity(layout.module(ModuleSlot.MID_LEFT));
					helper.assertTrue(chest != null && chest.getItem(0).is(Items.EMERALD) && chest.getItem(0).getCount() == 3,
							"o baú volta como bloco com o conteúdo");
					// e monta de novo
					helper.assertTrue(LandshipAssembly.assemble(helper.getLevel(), layout.helm(), null) != null, "o gabarito desmontado monta de novo");
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 40)
	public void disassemblyNeedsAFullHullAndRoom(GameTestHelper helper) {
		Layout layout = build(helper, Direction.NORTH, Map.of());
		LandshipEntity landship = LandshipAssembly.assemble(helper.getLevel(), layout.helm(), null);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> {
					landship.setServerIntegrity(BalanceConstants.MAX_INTEGRITY - 20);
					helper.assertTrue(!LandshipAssembly.disassemble(helper.getLevel(), landship, player), "casco avariado não desmonta");
					landship.setServerIntegrity(BalanceConstants.MAX_INTEGRITY);
					helper.getLevel().setBlockAndUpdate(layout.boiler(), Blocks.STONE.defaultBlockState());
					helper.assertTrue(!LandshipAssembly.disassemble(helper.getLevel(), landship, player), "sem espaço não desmonta");
					helper.assertTrue(!landship.isRemoved(), "o landship continua inteiro");
					helper.assertTrue(helper.getLevel().getBlockState(layout.chassis(0)).isAir(), "nenhum bloco foi colocado pela metade");
				})
				.thenSucceed();
	}

	private static void expectBlock(GameTestHelper helper, BlockPos pos, Block block) {
		helper.assertTrue(helper.getLevel().getBlockState(pos).is(block), "esperava " + block + " em " + pos + ", veio " + helper.getLevel().getBlockState(pos));
	}
}
