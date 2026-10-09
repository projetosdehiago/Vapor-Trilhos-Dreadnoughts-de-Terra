import {
  BOILING_C, COOLING_C_PER_S, AMBIENT_C, DRIVE_BAR_PER_S, DRY_BLIND_PULSE_TICKS, DRY_CRITICAL_C,
  LEAK_BAR_PER_S, LOW_INTEGRITY_FRACTION, LOW_INTEGRITY_WATER_FACTOR, MANUAL_VENT_BAR,
  MANUAL_VENT_COOLDOWN_S, MAX_BAR, MAX_SATURATION_C, NO_FIRE_LOSS_BAR_PER_S, PIVOT_BAR_PER_S,
  SAFETY_VALVE_BAR, SAFETY_VALVE_RESET_BAR, SATURATION_C_PER_BAR, STEP_UP_BAR, THERMAL_SHOCK_C,
  TICKS_PER_SECOND, WATER_CAPACITY_MB, WATER_MB_PER_BAR, WEAR_DRY_CRITICAL_PER_S, WEAR_DRY_PER_S, WEAR_VENT,
} from "./constants";
import type { BoilerState } from "./state";

/**
 * Física simplificada da caldeira (design.md, A3). Um passo = 1 tick (1/20 s).
 * Tradução direta de BoilerSimulation.java: os mesmos testes valem para as duas versões.
 */

/** Consome um item e devolve seus ticks de queima, ou 0 se não houver combustível. */
export type FuelSource = () => number;

/** O que o resto do veículo pede da caldeira neste tick. */
export class Load {
  /** Velocidade atual em relação à máxima, de 0 a 1 (frente ou ré). */
  speedFraction = 0;
  pivoting = false;
  /** Degraus de 1 bloco subidos neste tick. */
  stepUps = 0;
  /** Consumo extra de módulos (bar/s). */
  extraBarPerSecond = 0;
  /** Redução da geração de vapor por módulos (bar/s). */
  steamPenaltyBarPerSecond = 0;
  /** Ticks de queima consumidos a mais neste tick. */
  extraBurnTicks = 0;
  /** Integridade atual / máxima. */
  integrityFraction = 1;

  reset(): this {
    this.speedFraction = 0;
    this.pivoting = false;
    this.stepUps = 0;
    this.extraBarPerSecond = 0;
    this.steamPenaltyBarPerSecond = 0;
    this.extraBurnTicks = 0;
    this.integrityFraction = 1;
    return this;
  }
}

/** Resultado de um tick: desgaste e eventos que viram partículas, sons e cegueira. */
export class TickResult {
  wear = 0;
  /** Válvula de segurança abriu (sobrepressão). */
  safetyVented = false;
  /** Pulso de vapor/fumaça da caldeira seca. */
  dryPulse = false;
  /** O fogo apagou neste tick por falta de combustível. */
  fireWentOut = false;

  reset(): this {
    this.wear = 0;
    this.safetyVented = false;
    this.dryPulse = false;
    this.fireWentOut = false;
    return this;
  }
}

export function tick(s: BoilerState, load: Load, fuel: FuelSource, out: TickResult): void {
  const dt = 1 / TICKS_PER_SECOND;
  out.reset();
  if (s.manualVentCooldown > 0) {
    s.manualVentCooldown--;
  }

  // --- combustível
  if (s.fireLit) {
    s.burnRemaining -= s.damper.fuelRate + load.extraBurnTicks;
    if (s.burnRemaining <= 0) {
      const next = fuel();
      if (next > 0) {
        s.burnRemaining += next;
        s.burnTotal = next;
      } else {
        s.fireLit = false;
        s.burnRemaining = 0;
        s.burnTotal = 0;
        out.fireWentOut = true;
      }
    }
  }
  const heat = s.fireLit ? s.damper.heatCPerSecond : 0;

  // --- temperatura e vapor
  if (s.waterMb > 0) {
    if (s.temperatureC < BOILING_C) {
      s.temperatureC += (heat > 0 ? heat : -COOLING_C_PER_S) * dt;
      s.temperatureC = Math.max(AMBIENT_C, s.temperatureC);
    } else if (s.fireLit && s.damper.steamBarPerSecond > 0) {
      let gen = Math.max(0, s.damper.steamBarPerSecond - load.steamPenaltyBarPerSecond) * dt;
      const waterFactor = load.integrityFraction < LOW_INTEGRITY_FRACTION ? LOW_INTEGRITY_WATER_FACTOR : 1;
      let waterNeeded = gen * WATER_MB_PER_BAR * waterFactor;
      if (waterNeeded > s.waterMb) {
        gen *= s.waterMb / waterNeeded;
        waterNeeded = s.waterMb;
      }
      s.waterMb -= waterNeeded;
      s.pressureBar += gen;
    }
  } else {
    // caldeira seca: o calor só sobe a temperatura
    s.temperatureC += (heat > 0 ? heat : -COOLING_C_PER_S) * dt;
    s.temperatureC = Math.max(AMBIENT_C, s.temperatureC);
  }

  // --- consumo de pressão
  let drain = s.fireLit ? LEAK_BAR_PER_S : NO_FIRE_LOSS_BAR_PER_S;
  drain += DRIVE_BAR_PER_S * Math.min(1, Math.abs(load.speedFraction));
  if (load.pivoting) {
    drain += PIVOT_BAR_PER_S;
  }
  drain += load.extraBarPerSecond;
  s.pressureBar -= drain * dt + load.stepUps * STEP_UP_BAR;
  s.pressureBar = Math.max(0, Math.min(MAX_BAR, s.pressureBar));

  // com água fervendo, a temperatura acompanha a de saturação
  if (s.waterMb > 0 && s.temperatureC >= BOILING_C) {
    const saturation = Math.min(MAX_SATURATION_C, BOILING_C + SATURATION_C_PER_BAR * s.pressureBar);
    if (s.fireLit) {
      s.temperatureC = saturation;
    } else if (s.pressureBar > 0) {
      s.temperatureC = Math.max(saturation, s.temperatureC - COOLING_C_PER_S * dt);
    } else {
      s.temperatureC -= COOLING_C_PER_S * dt;
    }
  }

  // --- válvula de segurança
  if (s.pressureBar >= SAFETY_VALVE_BAR) {
    s.pressureBar = SAFETY_VALVE_RESET_BAR;
    out.safetyVented = true;
    out.wear += WEAR_VENT;
  }

  // --- caldeira seca
  if (s.isDryOverheating()) {
    out.wear += (s.temperatureC > DRY_CRITICAL_C ? WEAR_DRY_CRITICAL_PER_S : WEAR_DRY_PER_S) * dt;
    if (--s.dryPulseTimer <= 0) {
      s.dryPulseTimer = DRY_BLIND_PULSE_TICKS;
      out.dryPulse = true;
    }
  } else {
    s.dryPulseTimer = 0;
  }
}

/**
 * Adiciona água ao tanque.
 * @returns true se causou choque térmico (tanque vazio e caldeira a 200 °C ou mais).
 */
export function addWater(s: BoilerState, mb: number): boolean {
  const shock = s.waterMb <= 0 && s.temperatureC >= THERMAL_SHOCK_C && mb > 0;
  s.waterMb = Math.min(WATER_CAPACITY_MB, s.waterMb + mb);
  if (shock) {
    s.temperatureC = BOILING_C;
  }
  return shock;
}

/** Espaço livre no tanque, em mB. */
export function waterSpace(s: BoilerState): number {
  return WATER_CAPACITY_MB - s.waterMb;
}

/** Acende a fornalha se houver combustível. @returns true se o fogo está aceso depois. */
export function ignite(s: BoilerState, fuel: FuelSource): boolean {
  if (s.fireLit) {
    return true;
  }
  if (s.burnRemaining <= 0) {
    const next = fuel();
    if (next <= 0) {
      return false;
    }
    s.burnRemaining = next;
    s.burnTotal = next;
  }
  s.fireLit = true;
  return true;
}

/** Válvula de alívio manual. @returns true se abriu. */
export function manualVent(s: BoilerState): boolean {
  if (s.manualVentCooldown > 0 || s.pressureBar <= 0) {
    return false;
  }
  s.pressureBar = Math.max(0, s.pressureBar - MANUAL_VENT_BAR);
  s.manualVentCooldown = Math.round(MANUAL_VENT_COOLDOWN_S * TICKS_PER_SECOND);
  return true;
}

/** Apaga o fogo (ex.: caldeira submersa). */
export function extinguish(s: BoilerState): void {
  s.fireLit = false;
}
