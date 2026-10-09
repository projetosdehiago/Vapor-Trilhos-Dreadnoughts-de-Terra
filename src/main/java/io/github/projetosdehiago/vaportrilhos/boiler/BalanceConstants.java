package io.github.projetosdehiago.vaportrilhos.boiler;

/**
 * Números de balanceamento da caldeira, do movimento e do desgaste.
 *
 * <p>Fonte: {@code design/design.md}, Parte A (seções A3 a A5). Valores "por segundo" são
 * convertidos para ticks (20 por segundo) por quem os usa. Mudou aqui, mude lá também.
 */
public final class BalanceConstants {
	private BalanceConstants() {
	}

	public static final float TICKS_PER_SECOND = 20f;

	// --- Caldeira (A3) -----------------------------------------------------------------------
	public static final int WATER_CAPACITY_MB = 8_000;
	public static final int BUCKET_MB = 1_000;
	public static final int BOTTLE_MB = 250;

	public static final float AMBIENT_C = 20f;
	public static final float BOILING_C = 100f;
	public static final float SATURATION_C_PER_BAR = 10f;
	public static final float MAX_SATURATION_C = 220f;
	public static final float COOLING_C_PER_S = 2f;

	public static final float DRY_OVERHEAT_C = 120f;
	public static final float DRY_CRITICAL_C = 300f;
	public static final float THERMAL_SHOCK_C = 200f;

	public static final float WATER_MB_PER_BAR = 100f;
	public static final float LEAK_BAR_PER_S = 0.02f;
	public static final float NO_FIRE_LOSS_BAR_PER_S = 0.10f;
	public static final float LOW_INTEGRITY_WATER_FACTOR = 1.25f;

	public static final float MIN_DRIVE_BAR = 2f;
	public static final float FULL_POWER_BAR = 8f;
	public static final float RED_ZONE_BAR = 8f;
	public static final float SAFETY_VALVE_BAR = 10f;
	public static final float SAFETY_VALVE_RESET_BAR = 8f;
	public static final float MAX_BAR = 12f;

	public static final float MANUAL_VENT_BAR = 2f;
	public static final float MANUAL_VENT_COOLDOWN_S = 5f;

	// consumo do motor (A3.3)
	public static final float DRIVE_BAR_PER_S = 0.15f;
	public static final float PIVOT_BAR_PER_S = 0.05f;
	public static final float STEP_UP_BAR = 0.3f;

	// vapor cegante (A3.4)
	public static final float VENT_RADIUS = 5f;
	public static final int VENT_BLINDNESS_TICKS = 60;
	public static final float MANUAL_VENT_RADIUS = 4f;
	public static final int MANUAL_VENT_BLINDNESS_TICKS = 60;
	public static final float SHOCK_RADIUS = 6f;
	public static final int SHOCK_BLINDNESS_TICKS = 100;
	public static final int DRY_BLIND_PULSE_TICKS = 40;

	// --- Movimento (A4) ----------------------------------------------------------------------
	public static final float MAX_SPEED_M_S = 5f;
	public static final float MAX_REVERSE_M_S = 2f;
	public static final float ACCEL_M_S2 = 1f;
	public static final float BRAKE_M_S2 = 2.5f;
	public static final float TURN_DEG_S_STOPPED = 45f;
	public static final float TURN_DEG_S_FULL = 30f;
	public static final float MODULE_SPEED_PENALTY = 0.03f;

	// --- Integridade (A5) --------------------------------------------------------------------
	public static final float MAX_INTEGRITY = 200f;
	public static final float WEAR_PER_BLOCK = 0.02f;
	public static final float WEAR_VENT = 5f;
	public static final float WEAR_DRY_PER_S = 1f;
	public static final float WEAR_DRY_CRITICAL_PER_S = 4f;
	public static final float WEAR_THERMAL_SHOCK = 15f;
	public static final float WEAR_COLLISION_MIN_SPEED = 3f;
	public static final float WEAR_COLLISION_FACTOR = 4f;
	public static final float WEAR_FALL_MIN_BLOCKS = 3f;
	public static final float WEAR_FALL_FACTOR = 5f;
	public static final float WEAR_LAVA_PER_S = 1f;
	public static final float LOW_INTEGRITY_FRACTION = 0.5f;
	public static final float CRITICAL_INTEGRITY_FRACTION = 0.25f;
	public static final float CRITICAL_SPEED_FACTOR = 0.7f;
	public static final float WOOD_REPAIR_LIMIT_FRACTION = 0.6f;
	public static final int REPAIR_COOLDOWN_TICKS = 10;

	// --- módulos (A6)
	/** Fornalha de alta temperatura: 5 s por item (a comum leva 10 s). */
	public static final int FURNACE_TICKS_PER_ITEM = 100;
	/** Cada item consome 5 s de queima da caldeira: 1 tick de queima extra por tick trabalhando. */
	public static final float FURNACE_EXTRA_BURN_PER_TICK = 1f;
	/** A fornalha rouba calor: −0,05 bar/s da geração de vapor enquanto trabalha. */
	public static final float FURNACE_STEAM_PENALTY_BAR_PER_S = 0.05f;
	/** Compactador ligado e trabalhando: +0,03 bar/s. */
	public static final float COMPACTOR_BAR_PER_S = 0.03f;
	public static final float COMPACTOR_MIN_SPEED_M_S = 0.5f;
	public static final int COMPACTOR_INTERVAL_TICKS = 5;
	public static final float WEAR_PER_COMPACTED_BLOCK = 0.05f;
	/** Distância (blocos) até onde o painel e os baús de carga alcançam. */
	public static final double MODULE_REACH = 5.0;

	/** Fração de potência do motor para uma pressão: 0 abaixo de 2 bar, 1 a partir de 8 bar. */
	public static float powerFactor(float pressureBar) {
		float f = (pressureBar - MIN_DRIVE_BAR) / (FULL_POWER_BAR - MIN_DRIVE_BAR);
		return Math.max(0f, Math.min(1f, f));
	}
}
