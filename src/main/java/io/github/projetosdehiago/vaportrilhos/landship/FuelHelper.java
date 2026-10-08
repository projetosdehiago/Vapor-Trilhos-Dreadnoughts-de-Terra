package io.github.projetosdehiago.vaportrilhos.landship;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;

/**
 * Tempo de queima de combustível igual ao de uma fornalha comum.
 *
 * <p>No 26.3 o tempo de queima é um componente de dados ({@link DataComponents#COOKING_FUEL}) que
 * pode depender do bloco (fornalha comum ou alto-forno); aqui o contexto é o de uma fornalha comum.
 */
public final class FuelHelper {
	private FuelHelper() {
	}

	private static final ContextKeySet PARAMS = new ContextKeySet.Builder()
			.required(LootContextParams.BLOCK_STATE)
			.required(LootContextParams.ORIGIN)
			.build();

	public static boolean isFuel(ItemStack stack) {
		return stack.has(DataComponents.COOKING_FUEL);
	}

	public static int burnTicks(ServerLevel level, ItemStack stack, Vec3 origin) {
		if (!isFuel(stack)) {
			return 0;
		}
		LootContext context = new LootContext.Builder(new LootParams.Builder(level)
				.withParameter(LootContextParams.BLOCK_STATE, Blocks.FURNACE.defaultBlockState())
				.withParameter(LootContextParams.ORIGIN, origin)
				.create(PARAMS))
				.create(Optional.empty());
		return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
	}
}
