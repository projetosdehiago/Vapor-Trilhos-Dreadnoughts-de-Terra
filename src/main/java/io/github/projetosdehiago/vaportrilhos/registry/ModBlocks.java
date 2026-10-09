package io.github.projetosdehiago.vaportrilhos.registry;

import io.github.projetosdehiago.vaportrilhos.VaporTrilhos;
import io.github.projetosdehiago.vaportrilhos.assembly.CargoModuleBlock;
import io.github.projetosdehiago.vaportrilhos.assembly.LandshipHelmBlock;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Blocos da montagem (Fase 3, design.md A8): as peças do landship colocadas no mundo. */
public final class ModBlocks {
	private ModBlocks() {
	}

	public static final Block REINFORCED_TRACK = register("reinforced_track", Block::new, metal(MapColor.COLOR_GRAY));
	public static final Block STEAM_BOILER = register("steam_boiler", Block::new, metal(MapColor.COLOR_GREEN));
	public static final Block LANDSHIP_CHASSIS = register("landship_chassis", Block::new, metal(MapColor.METAL));
	public static final Block LANDSHIP_HELM = register("landship_helm", LandshipHelmBlock::new, wood(MapColor.WOOD));
	public static final Block BED_MODULE = register("bed_module", Block::new, wood(MapColor.COLOR_RED));
	public static final Block CARGO_MODULE = register("cargo_module", CargoModuleBlock::new, wood(MapColor.WOOD));
	public static final Block FURNACE_MODULE = register("furnace_module", Block::new, metal(MapColor.COLOR_RED).sound(SoundType.STONE));
	public static final Block COMPACTOR_MODULE = register("compactor_module", Block::new, metal(MapColor.METAL));

	private static BlockBehaviour.Properties metal(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(3.0f, 6.0f).sound(SoundType.METAL);
	}

	private static BlockBehaviour.Properties wood(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).strength(2.0f, 3.0f).sound(SoundType.WOOD);
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, VaporTrilhos.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
	}
}
