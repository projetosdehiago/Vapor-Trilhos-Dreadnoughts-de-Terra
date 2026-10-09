package io.github.projetosdehiago.vaportrilhos.module;

import org.jspecify.annotations.Nullable;

/**
 * Encaixes de módulo: 6 células 3×3 px do deque e o encaixe da frente (design.md A2.0). As
 * posições são as do modelo ({@code gen_landship.py}): x = esquerda do piloto, z = para trás.
 */
public enum ModuleSlot {
	FRONT_LEFT("fl", 15.5f, -15.5f),
	FRONT_RIGHT("fr", -15.5f, -15.5f),
	MID_LEFT("ml", 15.5f, 0f),
	MID_RIGHT("mr", -15.5f, 0f),
	REAR_LEFT("rl", 15.5f, 15.5f),
	REAR_RIGHT("rr", -15.5f, 15.5f),
	FRONT("front", 0f, -31f);

	/** Os 6 encaixes do deque, na ordem em que são preenchidos. */
	public static final ModuleSlot[] DECK = {FRONT_LEFT, FRONT_RIGHT, MID_LEFT, MID_RIGHT, REAR_LEFT, REAR_RIGHT};

	/** Sufixo do osso no modelo ({@code slot_<code>_<módulo>}). */
	public final String code;
	public final float leftPx;
	public final float backPx;

	ModuleSlot(String code, float leftPx, float backPx) {
		this.code = code;
		this.leftPx = leftPx;
		this.backPx = backPx;
	}

	public boolean isFront() {
		return this == FRONT;
	}

	public static @Nullable ModuleSlot byCode(String code) {
		for (ModuleSlot slot : values()) {
			if (slot.code.equals(code)) {
				return slot;
			}
		}
		return null;
	}
}
