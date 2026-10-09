package io.github.projetosdehiago.vaportrilhos.module;

import io.github.projetosdehiago.vaportrilhos.landship.LandshipEntity;
import io.github.projetosdehiago.vaportrilhos.registry.ModTags;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Compactador frontal (design.md A6.4): abre caminho numa faixa de 3 blocos à frente das esteiras.
 *
 * <p>Tudo passa pelas mesmas checagens de um jogador quebrando blocos: {@code mayInteract}
 * (spawn protegido) e {@link PlayerBlockBreakEvents#BEFORE} (mods de proteção podem vetar), em
 * nome do piloto. As listas de blocos são tags de dados ({@code vapor_trilhos:compactor/*}).
 */
public final class Compactor {
	/** Distâncias (blocos, a partir do centro) das duas fileiras processadas a cada passada. */
	private static final double[] ROWS = {2.0, 3.0};

	private Compactor() {
	}

	/**
	 * Uma passada do compactador.
	 *
	 * @return quantos blocos foram mexidos (cada um desgasta o casco)
	 */
	public static int run(ServerLevel level, LandshipEntity landship, LandshipModules modules, Player pilot) {
		Vec3 forward = landship.forwardVector();
		Vec3 left = landship.leftVector();
		int baseY = Mth.floor(landship.getY() + 0.01);
		CombinedStorage<ItemVariant, SingleSlotStorage<ItemVariant>> cargo = cargoStorage(modules);
		List<ItemStack> overflow = new ArrayList<>();
		int processed = 0;
		for (double row : ROWS) {
			for (int side = -1; side <= 1; side++) {
				Vec3 column = landship.position().add(forward.scale(row)).add(left.scale(side));
				int x = Mth.floor(column.x);
				int z = Mth.floor(column.z);
				// 1. obstáculos de até 2 blocos no nível do veículo
				for (int dy = 1; dy >= 0; dy--) {
					BlockPos pos = new BlockPos(x, baseY + dy, z);
					BlockState state = level.getBlockState(pos);
					if (state.is(ModTags.COMPACTABLE) && mayBreak(level, pilot, pos, state)) {
						for (ItemStack drop : Block.getDrops(state, level, pos, null, pilot, ItemStack.EMPTY)) {
							ItemStack rest = insert(cargo, drop);
							if (!rest.isEmpty()) {
								overflow.add(rest);
							}
						}
						level.destroyBlock(pos, false, pilot);
						processed++;
					} else if (state.is(ModTags.CRUSHABLE) && mayBreak(level, pilot, pos, state)) {
						level.destroyBlock(pos, false, pilot);
						processed++;
					}
				}
				// 2. chão: buraco de 1 bloco é tapado com terra/cascalho do baú; terra vira caminho
				BlockPos ground = new BlockPos(x, baseY - 1, z);
				BlockState groundState = level.getBlockState(ground);
				if (isHole(level, ground, groundState) && level.mayInteract(pilot, ground)) {
					BlockState fill = takeFill(cargo);
					if (fill != null) {
						level.setBlockAndUpdate(ground, fill);
						groundState = fill;
						processed++;
					}
				}
				if (groundState.is(ModTags.FLATTENS_TO_PATH) && level.getBlockState(ground.above()).isAir()
						&& level.mayInteract(pilot, ground)) {
					level.setBlockAndUpdate(ground, Blocks.DIRT_PATH.defaultBlockState());
					processed++;
				}
			}
		}
		// o que não coube no baú cai atrás do veículo
		Vec3 behind = landship.position().add(forward.scale(-2.2)).add(0, 0.5, 0);
		for (ItemStack stack : overflow) {
			level.addFreshEntity(new ItemEntity(level, behind.x, behind.y, behind.z, stack));
		}
		return processed;
	}

	private static boolean mayBreak(ServerLevel level, Player pilot, BlockPos pos, BlockState state) {
		return !state.hasBlockEntity()
				&& !state.is(ModTags.COMPACTOR_IMMUNE)
				&& level.mayInteract(pilot, pos)
				&& PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, pilot, pos, state, null);
	}

	/** Buraco de 1 bloco: vazio (ou planta) com chão firme logo abaixo. */
	private static boolean isHole(ServerLevel level, BlockPos pos, BlockState state) {
		if (!state.canBeReplaced() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
			return false;
		}
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	private static CombinedStorage<ItemVariant, SingleSlotStorage<ItemVariant>> cargoStorage(LandshipModules modules) {
		ContainerStorage all = ContainerStorage.of(modules.cargo(), null);
		List<SingleSlotStorage<ItemVariant>> slots = new ArrayList<>();
		for (ModuleSlot slot : modules.cargoSlots()) {
			int start = LandshipModules.cargoStart(slot);
			slots.addAll(all.getSlots().subList(start, start + LandshipModules.CARGO_SIZE));
		}
		return new CombinedStorage<>(slots);
	}

	/** Guarda no baú o quanto couber e devolve o resto. */
	private static ItemStack insert(CombinedStorage<ItemVariant, SingleSlotStorage<ItemVariant>> cargo, ItemStack stack) {
		if (stack.isEmpty() || cargo.parts.isEmpty()) {
			return stack;
		}
		try (Transaction transaction = Transaction.openOuter()) {
			long inserted = cargo.insert(ItemVariant.of(stack), stack.getCount(), transaction);
			transaction.commit();
			return stack.copyWithCount(stack.getCount() - (int) inserted);
		}
	}

	/** Tira 1 bloco de material de aterro do baú e devolve o bloco a colocar, ou {@code null}. */
	private static @Nullable BlockState takeFill(CombinedStorage<ItemVariant, SingleSlotStorage<ItemVariant>> cargo) {
		for (StorageView<ItemVariant> view : cargo) {
			ItemVariant variant = view.getResource();
			if (view.isResourceBlank() || !variant.toStack().is(ModTags.FILL_MATERIAL)
					|| !(variant.getItem() instanceof BlockItem blockItem)) {
				continue;
			}
			try (Transaction transaction = Transaction.openOuter()) {
				if (view.extract(variant, 1, transaction) == 1) {
					transaction.commit();
					return blockItem.getBlock().defaultBlockState();
				}
			}
		}
		return null;
	}
}
