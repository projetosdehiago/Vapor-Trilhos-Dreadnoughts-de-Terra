/**
 * Números de balanceamento da caldeira, do movimento e do desgaste.
 *
 * Fonte: design/design.md, Parte A (A3 a A5). São os mesmos da versão Java
 * (BalanceConstants.java): mudou num lugar, mude nos dois e no design.md.
 */
export const TICKS_PER_SECOND = 20;

// --- Caldeira (A3)
export const WATER_CAPACITY_MB = 8_000;
export const BUCKET_MB = 1_000;
export const BOTTLE_MB = 250;

export const AMBIENT_C = 20;
export const BOILING_C = 100;
export const SATURATION_C_PER_BAR = 10;
export const MAX_SATURATION_C = 220;
export const COOLING_C_PER_S = 2;

export const DRY_OVERHEAT_C = 120;
export const DRY_CRITICAL_C = 300;
export const THERMAL_SHOCK_C = 200;

export const WATER_MB_PER_BAR = 100;
export const LEAK_BAR_PER_S = 0.02;
export const NO_FIRE_LOSS_BAR_PER_S = 0.1;
export const LOW_INTEGRITY_WATER_FACTOR = 1.25;

export const MIN_DRIVE_BAR = 2;
export const FULL_POWER_BAR = 8;
export const RED_ZONE_BAR = 8;
export const SAFETY_VALVE_BAR = 10;
export const SAFETY_VALVE_RESET_BAR = 8;
export const MAX_BAR = 12;

export const MANUAL_VENT_BAR = 2;
export const MANUAL_VENT_COOLDOWN_S = 5;

// consumo do motor (A3.3)
export const DRIVE_BAR_PER_S = 0.15;
export const PIVOT_BAR_PER_S = 0.05;
export const STEP_UP_BAR = 0.3;

// vapor cegante (A3.4)
export const VENT_RADIUS = 5;
export const VENT_BLINDNESS_TICKS = 60;
export const MANUAL_VENT_RADIUS = 4;
export const MANUAL_VENT_BLINDNESS_TICKS = 60;
export const SHOCK_RADIUS = 6;
export const SHOCK_BLINDNESS_TICKS = 100;
export const DRY_BLIND_PULSE_TICKS = 40;

// --- Movimento (A4)
export const MAX_SPEED_M_S = 5;
export const MAX_REVERSE_M_S = 2;
export const ACCEL_M_S2 = 1;
export const BRAKE_M_S2 = 2.5;
export const COAST_M_S2 = 1.5;
export const TURN_DEG_S_STOPPED = 45;
export const TURN_DEG_S_FULL = 30;
export const MODULE_SPEED_PENALTY = 0.03;

// --- Integridade (A5)
export const MAX_INTEGRITY = 200;
export const WEAR_PER_BLOCK = 0.02;
export const WEAR_VENT = 5;
export const WEAR_DRY_PER_S = 1;
export const WEAR_DRY_CRITICAL_PER_S = 4;
export const WEAR_THERMAL_SHOCK = 15;
export const WEAR_COLLISION_MIN_SPEED = 3;
export const WEAR_COLLISION_FACTOR = 4;
export const WEAR_FALL_MIN_BLOCKS = 3;
export const WEAR_FALL_FACTOR = 5;
export const WEAR_LAVA_PER_S = 1;
export const LOW_INTEGRITY_FRACTION = 0.5;
export const CRITICAL_INTEGRITY_FRACTION = 0.25;
export const CRITICAL_SPEED_FACTOR = 0.7;
export const WOOD_REPAIR_LIMIT_FRACTION = 0.6;
export const REPAIR_COOLDOWN_TICKS = 10;

/** Recolher como item: caldeira apagada e até esta temperatura. */
export const PICKUP_MAX_C = 60;
/** Distância (blocos) até onde o painel alcança. */
export const PANEL_REACH = 5;

// --- módulos (A6), usados a partir da Fase B2
export const FURNACE_EXTRA_BURN_PER_TICK = 1;
export const FURNACE_STEAM_PENALTY_BAR_PER_S = 0.05;

/** Fração de potência do motor para uma pressão: 0 abaixo de 2 bar, 1 a partir de 8 bar. */
export function powerFactor(pressureBar: number): number {
  const f = (pressureBar - MIN_DRIVE_BAR) / (FULL_POWER_BAR - MIN_DRIVE_BAR);
  return Math.max(0, Math.min(1, f));
}
