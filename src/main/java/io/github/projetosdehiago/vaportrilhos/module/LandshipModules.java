package io.github.projetosdehiago.vaportrilhos.module;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Módulos instalados num landship e o estado de cada um (inventários, fornalha, compactador).
 *
 * <p>Fica no servidor; os clientes recebem só {@link #encode()} (quais módulos estão em quais
 * encaixes e se o compactador está ligado) por um dado sincronizado da entidade.
 */
public final class LandshipModules {
	public static final int CARGO_SIZE = 27;
	public static final int FURNACE_INPUT = 0;
	public static final int FURNACE_OUTPUT = 1;

	private static final int BITS_PER_SLOT = 3;
	private static final int COMPACTOR_ON_BIT = 1 << (BITS_PER_SLOT * 7);

	private final ModuleType[] slots = new ModuleType[ModuleSlot.values().length];
	/** Ordem de instalação: a chave remove sempre o último. */
	private final List<ModuleSlot> order = new ArrayList<>();
	/** Um trecho de 27 espaços por encaixe do deque; só os encaixes com baú são usados. */
	private final SimpleContainer cargo = new SimpleContainer(CARGO_SIZE * ModuleSlot.DECK.length);
	private final SimpleContainer furnace = new SimpleContainer(2);
	public int smeltProgress;
	public float storedExperience;
	public boolean compactorOn;

	public @Nullable ModuleType get(ModuleSlot slot) {
		return slots[slot.ordinal()];
	}

	public boolean has(ModuleType type) {
		return count(type) > 0;
	}

	public int count(ModuleType type) {
		int n = 0;
		for (ModuleType t : slots) {
			if (t == type) {
				n++;
			}
		}
		return n;
	}

	public int total() {
		return order.size();
	}

	public boolean isEmpty() {
		return order.isEmpty();
	}

	/** Primeiro encaixe livre que aceita o tipo, ou {@code null} (limite atingido ou sem espaço). */
	public @Nullable ModuleSlot freeSlotFor(ModuleType type) {
		if (count(type) >= type.limit) {
			return null;
		}
		if (type.frontOnly) {
			return get(ModuleSlot.FRONT) == null ? ModuleSlot.FRONT : null;
		}
		for (ModuleSlot slot : ModuleSlot.DECK) {
			if (get(slot) == null) {
				return slot;
			}
		}
		return null;
	}

	public void install(ModuleSlot slot, ModuleType type) {
		slots[slot.ordinal()] = type;
		order.remove(slot);
		order.add(slot);
	}

	public @Nullable ModuleSlot lastInstalled() {
		return order.isEmpty() ? null : order.getLast();
	}

	/**
	 * Remove o módulo do encaixe.
	 *
	 * @return o item do módulo e tudo o que estava guardado nele
	 */
	public List<ItemStack> remove(ModuleSlot slot) {
		List<ItemStack> out = new ArrayList<>();
		ModuleType type = get(slot);
		if (type == null) {
			return out;
		}
		out.add(new ItemStack(type.item()));
		switch (type) {
			case CARGO -> {
				int start = cargoStart(slot);
				for (int i = start; i < start + CARGO_SIZE; i++) {
					ItemStack stack = cargo.removeItemNoUpdate(i);
					if (!stack.isEmpty()) {
						out.add(stack);
					}
				}
			}
			case FURNACE -> {
				for (int i = 0; i < furnace.getContainerSize(); i++) {
					ItemStack stack = furnace.removeItemNoUpdate(i);
					if (!stack.isEmpty()) {
						out.add(stack);
					}
				}
				smeltProgress = 0;
				storedExperience = 0f;
			}
			case COMPACTOR -> compactorOn = false;
			case BED -> {
			}
		}
		slots[slot.ordinal()] = null;
		order.remove(slot);
		return out;
	}

	/** Remove todos os módulos (destruição do veículo). */
	public List<ItemStack> removeAll() {
		List<ItemStack> out = new ArrayList<>();
		while (!order.isEmpty()) {
			out.addAll(remove(order.getLast()));
		}
		return out;
	}

	/** Encaixes do deque com baú, na ordem do deque (as "páginas" da aba Carga). */
	public List<ModuleSlot> cargoSlots() {
		return cargoSlots(slots);
	}

	public static List<ModuleSlot> cargoSlots(ModuleType[] slots) {
		List<ModuleSlot> out = new ArrayList<>();
		for (ModuleSlot slot : ModuleSlot.DECK) {
			if (slots[slot.ordinal()] == ModuleType.CARGO) {
				out.add(slot);
			}
		}
		return out;
	}

	public static int cargoStart(ModuleSlot slot) {
		return slot.ordinal() * CARGO_SIZE;
	}

	public SimpleContainer cargo() {
		return cargo;
	}

	public SimpleContainer furnace() {
		return furnace;
	}

	// ---------------------------------------------------------------------------------------
	// Sincronização (servidor → clientes)
	// ---------------------------------------------------------------------------------------

	/** 3 bits por encaixe (0 = vazio, senão tipo + 1) e 1 bit para o compactador ligado. */
	public int encode() {
		int bits = 0;
		for (int i = 0; i < slots.length; i++) {
			if (slots[i] != null) {
				bits |= (slots[i].ordinal() + 1) << (i * BITS_PER_SLOT);
			}
		}
		if (compactorOn) {
			bits |= COMPACTOR_ON_BIT;
		}
		return bits;
	}

	public static ModuleType[] decode(int bits) {
		ModuleType[] out = new ModuleType[ModuleSlot.values().length];
		ModuleType[] types = ModuleType.values();
		for (int i = 0; i < out.length; i++) {
			int value = (bits >> (i * BITS_PER_SLOT)) & 0b111;
			out[i] = value > 0 && value <= types.length ? types[value - 1] : null;
		}
		return out;
	}

	public static boolean decodeCompactorOn(int bits) {
		return (bits & COMPACTOR_ON_BIT) != 0;
	}

	public static int countIn(int bits) {
		int n = 0;
		for (ModuleType type : decode(bits)) {
			if (type != null) {
				n++;
			}
		}
		return n;
	}

	// ---------------------------------------------------------------------------------------
	// Persistência
	// ---------------------------------------------------------------------------------------

	public void save(ValueOutput output) {
		List<String> installed = new ArrayList<>();
		for (ModuleSlot slot : order) {
			installed.add(slot.code + ":" + get(slot).id());
		}
		output.store("Installed", Codec.STRING.listOf(), installed);
		ContainerHelper.saveAllItems(output.child("Cargo"), cargo.getItems());
		ContainerHelper.saveAllItems(output.child("Furnace"), furnace.getItems());
		output.putInt("SmeltProgress", smeltProgress);
		output.putFloat("StoredExperience", storedExperience);
		output.putBoolean("CompactorOn", compactorOn);
	}

	public void load(ValueInput input) {
		Arrays.fill(slots, null);
		order.clear();
		for (String entry : input.read("Installed", Codec.STRING.listOf()).orElse(List.of())) {
			String[] parts = entry.split(":", 2);
			ModuleSlot slot = parts.length == 2 ? ModuleSlot.byCode(parts[0]) : null;
			ModuleType type = null;
			if (parts.length == 2) {
				try {
					type = ModuleType.valueOf(parts[1].toUpperCase(Locale.ROOT));
				} catch (IllegalArgumentException ignored) {
					// tipo desconhecido (versão futura): ignora
				}
			}
			if (slot != null && type != null && slot.isFront() == type.frontOnly) {
				install(slot, type);
			}
		}
		ContainerHelper.loadAllItems(input.childOrEmpty("Cargo"), cargo.getItems());
		ContainerHelper.loadAllItems(input.childOrEmpty("Furnace"), furnace.getItems());
		smeltProgress = input.getIntOr("SmeltProgress", 0);
		storedExperience = input.getFloatOr("StoredExperience", 0f);
		compactorOn = input.getBooleanOr("CompactorOn", false) && has(ModuleType.COMPACTOR);
	}
}
