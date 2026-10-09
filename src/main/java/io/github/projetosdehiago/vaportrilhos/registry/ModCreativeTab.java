package io.github.projetosdehiago.vaportrilhos.registry;

import io.github.projetosdehiago.vaportrilhos.VaporTrilhos;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTab {
	private ModCreativeTab() {
	}

	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, VaporTrilhos.id("main"),
			FabricCreativeModeTab.builder()
					.title(Component.translatable("itemGroup.vapor_trilhos"))
					.icon(() -> new ItemStack(ModItems.LANDSHIP))
					.displayItems((parameters, output) -> {
						output.accept(ModItems.LANDSHIP);
						output.accept(ModItems.STEAM_BOILER);
						output.accept(ModItems.REINFORCED_TRACK);
						output.accept(ModItems.BOILERMAKER_WRENCH);
						output.accept(ModItems.REPAIR_KIT);
						output.accept(ModItems.BED_MODULE);
						output.accept(ModItems.CARGO_MODULE);
						output.accept(ModItems.FURNACE_MODULE);
						output.accept(ModItems.COMPACTOR_MODULE);
					})
					.build());

	public static void init() {
	}
}
