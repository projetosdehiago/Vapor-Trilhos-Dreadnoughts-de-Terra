package io.github.projetosdehiago.vaportrilhos.registry;

import io.github.projetosdehiago.vaportrilhos.VaporTrilhos;
import io.github.projetosdehiago.vaportrilhos.assembly.CargoModuleBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	private ModBlockEntities() {
	}

	public static final BlockEntityType<CargoModuleBlockEntity> CARGO_MODULE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			VaporTrilhos.id("cargo_module"), new BlockEntityType<>(CargoModuleBlockEntity::new, Set.of(ModBlocks.CARGO_MODULE)));

	public static void init() {
	}
}
