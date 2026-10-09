package io.github.projetosdehiago.vaportrilhos.registry;

import io.github.projetosdehiago.vaportrilhos.VaporTrilhos;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModItems {
	private ModItems() {
	}

	// peças que também são blocos (montagem, Fase 3)
	public static final Item REINFORCED_TRACK = block("reinforced_track", ModBlocks.REINFORCED_TRACK, new Item.Properties());
	public static final Item STEAM_BOILER = block("steam_boiler", ModBlocks.STEAM_BOILER, new Item.Properties().stacksTo(16));
	public static final Item LANDSHIP_CHASSIS = block("landship_chassis", ModBlocks.LANDSHIP_CHASSIS, new Item.Properties());
	public static final Item LANDSHIP_HELM = block("landship_helm", ModBlocks.LANDSHIP_HELM, new Item.Properties().stacksTo(16));
	public static final Item LANDSHIP = register("landship", LandshipItem::new,
			new Item.Properties().stacksTo(1).component(ModDataComponents.LANDSHIP_DATA, ModDataComponents.LandshipData.NEW));
	public static final Item BOILERMAKER_WRENCH = register("boilermaker_wrench", Item::new, new Item.Properties().stacksTo(1));
	public static final Item REPAIR_KIT = register("repair_kit", Item::new, new Item.Properties().stacksTo(16));
	public static final Item BED_MODULE = block("bed_module", ModBlocks.BED_MODULE, new Item.Properties().stacksTo(1));
	public static final Item CARGO_MODULE = block("cargo_module", ModBlocks.CARGO_MODULE, new Item.Properties().stacksTo(4));
	public static final Item FURNACE_MODULE = block("furnace_module", ModBlocks.FURNACE_MODULE, new Item.Properties().stacksTo(1));
	public static final Item COMPACTOR_MODULE = block("compactor_module", ModBlocks.COMPACTOR_MODULE, new Item.Properties().stacksTo(1));

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, VaporTrilhos.id(name));
		Item item = factory.apply(properties.setId(key));
		if (item instanceof BlockItem blockItem) {
			// o que o registro vanilla faz: liga o bloco ao item (pegar bloco, drops, Block.asItem)
			blockItem.registerBlocks(Item.BY_BLOCK, item);
		}
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	/** Item que coloca o bloco; mantém o nome de item ({@code item.vapor_trilhos.*}). */
	private static Item block(String name, Block block, Item.Properties properties) {
		return register(name, p -> new BlockItem(block, p), properties.useItemDescriptionPrefix());
	}

	public static void init() {
	}
}
