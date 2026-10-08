package io.github.projetosdehiago.vaportrilhos.landship;

import io.github.projetosdehiago.vaportrilhos.boiler.BalanceConstants;
import io.github.projetosdehiago.vaportrilhos.registry.ModItems;
import io.github.projetosdehiago.vaportrilhos.registry.ModTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** Itens de reparo e quanto cada um devolve de integridade (design.md A5). */
public final class RepairMaterials {
	private RepairMaterials() {
	}

	/** @param amount pontos de integridade por item; @param limitFraction teto que esse material alcança. */
	public record Repair(float amount, float limitFraction) {
	}

	private static final float FULL = 1f;
	private static final float WOOD = BalanceConstants.WOOD_REPAIR_LIMIT_FRACTION;

	public static @Nullable Repair of(ItemStack stack) {
		if (stack.is(Items.IRON_INGOT)) {
			return new Repair(10f, FULL);
		}
		if (stack.is(Items.IRON_NUGGET)) {
			return new Repair(1f, FULL);
		}
		if (stack.is(Items.IRON_BLOCK)) {
			return new Repair(90f, FULL);
		}
		if (stack.is(Items.COPPER_INGOT)) {
			return new Repair(6f, FULL);
		}
		if (stack.is(ModItems.REPAIR_KIT)) {
			return new Repair(60f, FULL);
		}
		if (stack.is(ModTags.REPAIR_WOOD_LARGE)) {
			return new Repair(8f, WOOD);
		}
		if (stack.is(ModTags.REPAIR_WOOD_SMALL)) {
			return new Repair(3f, WOOD);
		}
		return null;
	}
}
