package io.github.projetosdehiago.vaportrilhos.module;

import io.github.projetosdehiago.vaportrilhos.registry.ModItems;
import java.util.Locale;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Tipos de módulo e seus limites (design.md A6). */
public enum ModuleType {
	BED(1, false),
	CARGO(4, false),
	FURNACE(1, false),
	COMPACTOR(1, true);

	/** Quantos deste tipo cabem num landship. */
	public final int limit;
	/** Só no encaixe da frente (o compactador); os outros só nos 6 encaixes do deque. */
	public final boolean frontOnly;

	ModuleType(int limit, boolean frontOnly) {
		this.limit = limit;
		this.frontOnly = frontOnly;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Item item() {
		return switch (this) {
			case BED -> ModItems.BED_MODULE;
			case CARGO -> ModItems.CARGO_MODULE;
			case FURNACE -> ModItems.FURNACE_MODULE;
			case COMPACTOR -> ModItems.COMPACTOR_MODULE;
		};
	}

	public static @Nullable ModuleType of(ItemStack stack) {
		for (ModuleType type : values()) {
			if (stack.is(type.item())) {
				return type;
			}
		}
		return null;
	}
}
