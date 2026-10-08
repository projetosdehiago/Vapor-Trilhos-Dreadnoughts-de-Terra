package io.github.projetosdehiago.vaportrilhos.registry;

import io.github.projetosdehiago.vaportrilhos.VaporTrilhos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Tags de dados: dá para ajustar terreno e reparo por datapack sem mexer no código. */
public final class ModTags {
	private ModTags() {
	}

	// terreno (design.md A4): o que não estiver em nenhuma tag anda a 100 %
	public static final TagKey<Block> TERRAIN_SOFT = block("terrain/soft");          // ×0,9
	public static final TagKey<Block> TERRAIN_LOOSE = block("terrain/loose");        // ×0,7
	public static final TagKey<Block> TERRAIN_BOG = block("terrain/bog");            // ×0,5
	public static final TagKey<Block> TERRAIN_SLIPPERY = block("terrain/slippery");  // pouca aderência

	// reparo (design.md A5)
	public static final TagKey<Item> REPAIR_WOOD_SMALL = item("repair/wood_small");  // +3, até 60 %
	public static final TagKey<Item> REPAIR_WOOD_LARGE = item("repair/wood_large");  // +8, até 60 %

	private static TagKey<Block> block(String path) {
		return TagKey.create(Registries.BLOCK, VaporTrilhos.id(path));
	}

	private static TagKey<Item> item(String path) {
		return TagKey.create(Registries.ITEM, VaporTrilhos.id(path));
	}
}
