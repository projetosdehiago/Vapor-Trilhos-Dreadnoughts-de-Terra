package io.github.projetosdehiago.vaportrilhos.registry;

import io.github.projetosdehiago.vaportrilhos.VaporTrilhos;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
	private ModItems() {
	}

	public static final Item REINFORCED_TRACK = register("reinforced_track", Item::new, new Item.Properties());
	public static final Item STEAM_BOILER = register("steam_boiler", Item::new, new Item.Properties().stacksTo(16));
	public static final Item LANDSHIP = register("landship", LandshipItem::new,
			new Item.Properties().stacksTo(1).component(ModDataComponents.LANDSHIP_DATA, ModDataComponents.LandshipData.NEW));
	public static final Item BOILERMAKER_WRENCH = register("boilermaker_wrench", Item::new, new Item.Properties().stacksTo(1));
	public static final Item REPAIR_KIT = register("repair_kit", Item::new, new Item.Properties().stacksTo(16));

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, VaporTrilhos.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
	}
}
