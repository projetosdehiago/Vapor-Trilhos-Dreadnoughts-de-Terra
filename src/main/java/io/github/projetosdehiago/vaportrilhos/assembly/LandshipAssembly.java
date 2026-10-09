package io.github.projetosdehiago.vaportrilhos.assembly;

import io.github.projetosdehiago.vaportrilhos.boiler.BalanceConstants;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipEntity;
import io.github.projetosdehiago.vaportrilhos.module.LandshipModules;
import io.github.projetosdehiago.vaportrilhos.module.ModuleSlot;
import io.github.projetosdehiago.vaportrilhos.module.ModuleType;
import io.github.projetosdehiago.vaportrilhos.registry.ModBlocks;
import io.github.projetosdehiago.vaportrilhos.registry.ModEntities;
import io.github.projetosdehiago.vaportrilhos.registry.ModSounds;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Montagem e desmontagem do landship a partir de blocos (design.md A8).
 *
 * <p>Gabarito, visto de cima com a frente para cima (a frente é a direção do Leme):
 *
 * <pre>
 * camada 0 (chão)          camada 1
 *        [Cf]              (Cf = compactador, opcional)
 *   [T] [Ch] [T]          [m] [H]  [m]
 *   [T] [Ch] [T]          [m] [ ]  [m]
 *   [T] [Ch] [T]          [m] [Cv] [m]
 * </pre>
 *
 * T = Esteira Reforçada, Ch = Chassi, H = Leme, Cv = Caldeira a Vapor, m = módulo opcional
 * (cama, baú ou fornalha). O centro da camada 1 fica vazio (assento).
 */
public final class LandshipAssembly {
	private LandshipAssembly() {
	}

	/** Um bloco do gabarito que está errado, e por quê. */
	public record Problem(BlockPos pos, Component reason) {
	}

	/** Posições do gabarito para um Leme em {@code helm} virado para {@code front}. */
	public record Layout(BlockPos helm, Direction front) {
		public Direction left() {
			return front.getCounterClockWise();
		}

		public Direction back() {
			return front.getOpposite();
		}

		/** Coluna central da camada 0 na fileira {@code row} (0 = frente, 2 = trás). */
		public BlockPos chassis(int row) {
			return helm.below().relative(back(), row);
		}

		/** Esteira da fileira {@code row}; {@code side} 1 = esquerda, −1 = direita. */
		public BlockPos track(int row, int side) {
			return chassis(row).relative(left(), side);
		}

		public BlockPos seat() {
			return helm.relative(back(), 1);
		}

		public BlockPos boiler() {
			return helm.relative(back(), 2);
		}

		public BlockPos compactor() {
			return helm.below().relative(front);
		}

		public BlockPos module(ModuleSlot slot) {
			int row = switch (slot) {
				case FRONT_LEFT, FRONT_RIGHT -> 0;
				case MID_LEFT, MID_RIGHT -> 1;
				case REAR_LEFT, REAR_RIGHT -> 2;
				case FRONT -> throw new IllegalArgumentException("o compactador fica na camada 0");
			};
			int side = switch (slot) {
				case FRONT_LEFT, MID_LEFT, REAR_LEFT -> 1;
				default -> -1;
			};
			return helm.relative(back(), row).relative(left(), side);
		}

		/** Centro da pegada (bloco do meio da camada 0): onde o landship nasce. */
		public BlockPos center() {
			return chassis(1);
		}
	}

	// =========================================================================================
	// Montar
	// =========================================================================================

	/** Resultado da checagem do gabarito: os módulos encontrados ou a lista de problemas. */
	public record Scan(Layout layout, Map<ModuleSlot, ModuleType> modules, List<Problem> problems) {
		public boolean valid() {
			return problems.isEmpty();
		}
	}

	public static Scan scan(ServerLevel level, BlockPos helmPos) {
		BlockState helmState = level.getBlockState(helmPos);
		Direction front = helmState.hasProperty(HorizontalDirectionalBlock.FACING) ? helmState.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
		Layout layout = new Layout(helmPos, front);
		List<Problem> problems = new ArrayList<>();
		Map<ModuleSlot, ModuleType> modules = new EnumMap<>(ModuleSlot.class);

		for (int row = 0; row < 3; row++) {
			expect(level, layout.chassis(row), ModBlocks.LANDSHIP_CHASSIS, problems);
			expect(level, layout.track(row, 1), ModBlocks.REINFORCED_TRACK, problems);
			expect(level, layout.track(row, -1), ModBlocks.REINFORCED_TRACK, problems);
		}
		expect(level, layout.boiler(), ModBlocks.STEAM_BOILER, problems);
		if (!level.getBlockState(layout.seat()).canBeReplaced()) {
			problems.add(new Problem(layout.seat(), Component.translatable("assembly.vapor_trilhos.seat_blocked")));
		}
		for (ModuleSlot slot : ModuleSlot.DECK) {
			BlockPos pos = layout.module(slot);
			BlockState state = level.getBlockState(pos);
			ModuleType type = moduleOf(state.getBlock());
			if (type != null && type != ModuleType.COMPACTOR) {
				modules.put(slot, type);
			} else if (!state.canBeReplaced()) {
				problems.add(new Problem(pos, Component.translatable("assembly.vapor_trilhos.module_blocked", state.getBlock().getName())));
			}
		}
		if (level.getBlockState(layout.compactor()).is(ModBlocks.COMPACTOR_MODULE)) {
			modules.put(ModuleSlot.FRONT, ModuleType.COMPACTOR);
		}
		for (ModuleType type : ModuleType.values()) {
			long count = modules.values().stream().filter(t -> t == type).count();
			if (count > type.limit) {
				problems.add(new Problem(layout.helm(), Component.translatable("assembly.vapor_trilhos.too_many",
						Component.translatable("module.vapor_trilhos." + type.id()), type.limit)));
			}
		}
		return new Scan(layout, modules, problems);
	}

	private static void expect(ServerLevel level, BlockPos pos, Block block, List<Problem> problems) {
		if (!level.getBlockState(pos).is(block)) {
			problems.add(new Problem(pos, Component.translatable("assembly.vapor_trilhos.missing", block.getName())));
		}
	}

	/**
	 * Chave de Caldeireiro no Leme: se o gabarito estiver certo, troca os blocos pelo landship.
	 *
	 * @return o landship criado, ou {@code null} (o jogador recebe o motivo e partículas marcam os blocos errados)
	 */
	public static @Nullable LandshipEntity assemble(ServerLevel level, BlockPos helmPos, @Nullable Player player) {
		Scan scan = scan(level, helmPos);
		if (!scan.valid()) {
			if (player != null) {
				Problem first = scan.problems().getFirst();
				player.sendOverlayMessage(Component.translatable("assembly.vapor_trilhos.invalid", first.reason()));
			}
			for (Problem problem : scan.problems()) {
				Vec3 c = Vec3.atCenterOf(problem.pos());
				level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.01);
			}
			return null;
		}
		Layout layout = scan.layout();

		// tira o conteúdo dos baús antes de remover os blocos (senão ele cairia no chão)
		Map<ModuleSlot, List<ItemStack>> cargo = new EnumMap<>(ModuleSlot.class);
		for (Map.Entry<ModuleSlot, ModuleType> entry : scan.modules().entrySet()) {
			if (entry.getValue() == ModuleType.CARGO && level.getBlockEntity(layout.module(entry.getKey())) instanceof CargoModuleBlockEntity chest) {
				List<ItemStack> items = new ArrayList<>();
				for (int i = 0; i < chest.getContainerSize(); i++) {
					items.add(chest.removeItemNoUpdate(i));
				}
				cargo.put(entry.getKey(), items);
			}
		}

		for (BlockPos pos : allPositions(layout, scan.modules())) {
			level.removeBlock(pos, false);
		}

		LandshipEntity landship = ModEntities.LANDSHIP.create(level, EntitySpawnReason.TRIGGERED);
		if (landship == null) {
			return null;
		}
		Vec3 center = Vec3.atBottomCenterOf(layout.center());
		landship.snapTo(center.x, center.y, center.z, layout.front().toYRot(), 0f);
		LandshipModules modules = landship.modules();
		for (Map.Entry<ModuleSlot, ModuleType> entry : scan.modules().entrySet()) {
			modules.install(entry.getKey(), entry.getValue());
		}
		cargo.forEach((slot, items) -> {
			int start = LandshipModules.cargoStart(slot);
			for (int i = 0; i < items.size(); i++) {
				modules.cargo().setItem(start + i, items.get(i));
			}
		});
		landship.refreshModules();
		level.addFreshEntity(landship);

		level.playSound(null, center.x, center.y + 1, center.z, ModSounds.WHISTLE, SoundSource.BLOCKS, 1.5f, 1.2f);
		level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 1, center.z, 30, 1.2, 0.6, 1.2, 0.02);
		if (player != null) {
			player.sendOverlayMessage(Component.translatable("assembly.vapor_trilhos.assembled"));
		}
		return landship;
	}

	/** Todos os blocos do gabarito presentes (os módulos e o compactador só se houver). */
	private static List<BlockPos> allPositions(Layout layout, Map<ModuleSlot, ModuleType> modules) {
		List<BlockPos> positions = new ArrayList<>();
		// camada 1 primeiro: nada fica "flutuando" sobre um buraco por um instante
		positions.add(layout.helm());
		positions.add(layout.boiler());
		for (ModuleSlot slot : ModuleSlot.DECK) {
			if (modules.containsKey(slot)) {
				positions.add(layout.module(slot));
			}
		}
		for (int row = 0; row < 3; row++) {
			positions.add(layout.chassis(row));
			positions.add(layout.track(row, 1));
			positions.add(layout.track(row, -1));
		}
		if (modules.containsKey(ModuleSlot.FRONT)) {
			positions.add(layout.compactor());
		}
		return positions;
	}

	// =========================================================================================
	// Desmontar
	// =========================================================================================

	/**
	 * O inverso da montagem: o landship vira os blocos do gabarito, no lugar onde está.
	 *
	 * <p>Exige o veículo parado, vazio, com a caldeira fria e o casco 100 % reparado (os blocos
	 * novos não podem servir de conserto de graça). Verifica o espaço de todos os blocos antes de
	 * mexer em qualquer um.
	 *
	 * @return {@code true} se desmontou
	 */
	public static boolean disassemble(ServerLevel level, LandshipEntity landship, Player player) {
		Component refusal = disassemblyRefusal(landship);
		if (refusal != null) {
			player.sendOverlayMessage(refusal);
			return false;
		}
		Direction front = Direction.fromYRot(landship.getYRot());
		BlockPos center = BlockPos.containing(landship.getX(), landship.getY() + 0.01, landship.getZ());
		Layout layout = new Layout(center.relative(front).above(), front);
		ModuleType[] installed = landship.getInstalledModules();
		Map<ModuleSlot, ModuleType> modules = new EnumMap<>(ModuleSlot.class);
		for (ModuleSlot slot : ModuleSlot.values()) {
			if (installed[slot.ordinal()] != null) {
				modules.put(slot, installed[slot.ordinal()]);
			}
		}

		List<BlockPos> needed = new ArrayList<>(allPositions(layout, modules));
		needed.add(layout.seat());
		List<BlockPos> blocked = new ArrayList<>();
		for (BlockPos pos : needed) {
			if (!level.getBlockState(pos).canBeReplaced()) {
				blocked.add(pos);
			}
		}
		if (!blocked.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("assembly.vapor_trilhos.no_room"));
			for (BlockPos pos : blocked) {
				Vec3 c = Vec3.atCenterOf(pos);
				level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.01);
			}
			return false;
		}

		// o que não vira bloco volta para o jogador: combustível e a fornalha
		giveBack(player, landship.getFuelContainer());
		giveBack(player, landship.modules().furnace());

		for (int row = 0; row < 3; row++) {
			level.setBlockAndUpdate(layout.chassis(row), ModBlocks.LANDSHIP_CHASSIS.defaultBlockState());
			level.setBlockAndUpdate(layout.track(row, 1), ModBlocks.REINFORCED_TRACK.defaultBlockState());
			level.setBlockAndUpdate(layout.track(row, -1), ModBlocks.REINFORCED_TRACK.defaultBlockState());
		}
		level.setBlockAndUpdate(layout.helm(), ModBlocks.LANDSHIP_HELM.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, front));
		level.setBlockAndUpdate(layout.boiler(), ModBlocks.STEAM_BOILER.defaultBlockState());
		level.removeBlock(layout.seat(), false);
		for (Map.Entry<ModuleSlot, ModuleType> entry : modules.entrySet()) {
			ModuleSlot slot = entry.getKey();
			BlockPos pos = slot.isFront() ? layout.compactor() : layout.module(slot);
			level.setBlockAndUpdate(pos, blockOf(entry.getValue()).defaultBlockState());
			if (entry.getValue() == ModuleType.CARGO && level.getBlockEntity(pos) instanceof CargoModuleBlockEntity chest) {
				int start = LandshipModules.cargoStart(slot);
				for (int i = 0; i < LandshipModules.CARGO_SIZE; i++) {
					chest.setItem(i, landship.modules().cargo().removeItemNoUpdate(start + i));
				}
			}
		}
		Vec3 c = landship.position();
		level.playSound(null, c.x, c.y + 1, c.z, ModSounds.REPAIR, SoundSource.BLOCKS, 1f, 0.6f);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 1, c.z, 20, 1.2, 0.6, 1.2, 0.02);
		landship.discard();
		player.sendOverlayMessage(Component.translatable("assembly.vapor_trilhos.disassembled"));
		return true;
	}

	/** Por que o landship não pode ser desmontado agora, ou {@code null} se pode. */
	public static @Nullable Component disassemblyRefusal(LandshipEntity landship) {
		if (landship.isVehicle()) {
			return Component.translatable("message.vapor_trilhos.pickup_occupied");
		}
		if (!landship.isStationary()) {
			return Component.translatable("message.vapor_trilhos.module_moving");
		}
		if (landship.isFireLit() || landship.getTemperature() > 60f) {
			return Component.translatable("message.vapor_trilhos.pickup_hot");
		}
		if (landship.getIntegrity() < BalanceConstants.MAX_INTEGRITY) {
			return Component.translatable("assembly.vapor_trilhos.needs_repair");
		}
		return null;
	}

	private static void giveBack(Player player, SimpleContainer container) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.removeItemNoUpdate(i);
			if (!stack.isEmpty()) {
				player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
			}
		}
	}

	private static @Nullable ModuleType moduleOf(Block block) {
		for (ModuleType type : ModuleType.values()) {
			if (blockOf(type) == block) {
				return type;
			}
		}
		return null;
	}

	public static Block blockOf(ModuleType type) {
		return switch (type) {
			case BED -> ModBlocks.BED_MODULE;
			case CARGO -> ModBlocks.CARGO_MODULE;
			case FURNACE -> ModBlocks.FURNACE_MODULE;
			case COMPACTOR -> ModBlocks.COMPACTOR_MODULE;
		};
	}
}
