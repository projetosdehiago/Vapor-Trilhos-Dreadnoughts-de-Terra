package io.github.projetosdehiago.vaportrilhos.module;

import io.github.projetosdehiago.vaportrilhos.boiler.BalanceConstants;
import io.github.projetosdehiago.vaportrilhos.boiler.BoilerState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.phys.Vec3;

/**
 * Fornalha de alta temperatura (design.md A6.3): sem combustível próprio, usa o calor da caldeira
 * e as receitas da fornalha comum, 2× mais rápida.
 */
public final class FurnaceModule {
	private static final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> SMELTING = RecipeManager.createCheck(RecipeType.SMELTING);

	private FurnaceModule() {
	}

	/**
	 * Um tick da fornalha.
	 *
	 * @return {@code true} se trabalhou neste tick (aí a caldeira paga o custo em combustível e vapor)
	 */
	public static boolean tick(ServerLevel level, LandshipModules modules, BoilerState boiler) {
		if (!modules.has(ModuleType.FURNACE)) {
			return false;
		}
		SimpleContainer furnace = modules.furnace();
		ItemStack input = furnace.getItem(LandshipModules.FURNACE_INPUT);
		if (input.isEmpty()) {
			modules.smeltProgress = 0;
			return false;
		}
		if (!isHotEnough(boiler)) {
			return false; // o progresso fica parado até a caldeira esquentar de novo
		}
		SingleRecipeInput recipeInput = new SingleRecipeInput(input);
		RecipeHolder<SmeltingRecipe> recipe = SMELTING.getRecipeFor(recipeInput, level).orElse(null);
		if (recipe == null) {
			modules.smeltProgress = 0;
			return false;
		}
		ItemStack result = recipe.value().assemble(recipeInput);
		ItemStack output = furnace.getItem(LandshipModules.FURNACE_OUTPUT);
		if (result.isEmpty() || !fits(output, result)) {
			return false;
		}
		if (++modules.smeltProgress >= BalanceConstants.FURNACE_TICKS_PER_ITEM) {
			modules.smeltProgress = 0;
			if (output.isEmpty()) {
				furnace.setItem(LandshipModules.FURNACE_OUTPUT, result.copy());
			} else {
				output.grow(result.getCount());
			}
			input.shrink(1);
			modules.storedExperience += recipe.value().experience();
			furnace.setChanged();
		}
		return true;
	}

	public static boolean isHotEnough(BoilerState boiler) {
		return boiler.fireLit && boiler.temperatureC >= BalanceConstants.BOILING_C;
	}

	private static boolean fits(ItemStack output, ItemStack result) {
		if (output.isEmpty()) {
			return true;
		}
		return ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
	}

	/** Entrega a experiência acumulada a quem retirou a saída (como a fornalha comum). */
	public static void awardExperience(ServerLevel level, Vec3 position, LandshipModules modules) {
		float amount = modules.storedExperience;
		modules.storedExperience = 0f;
		int whole = Mth.floor(amount);
		float fraction = Mth.frac(amount);
		if (fraction > 0f && level.getRandom().nextFloat() < fraction) {
			whole++;
		}
		if (whole > 0) {
			ExperienceOrb.award(level, position, whole);
		}
	}
}
