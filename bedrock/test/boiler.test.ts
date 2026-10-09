import { describe, expect, it } from "vitest";
import * as C from "../src/boiler/constants";
import { CLOSED } from "../src/boiler/damper";
import { addWater, ignite, Load, manualVent, tick, TickResult, type FuelSource } from "../src/boiler/simulation";
import { BoilerState } from "../src/boiler/state";

// Os mesmos casos de BoilerSimulationTest.java: as duas versões precisam se comportar igual.
const COAL_TICKS = 1600;

function coal(items: number): FuelSource & { left: () => number } {
  let n = items;
  const fn = (() => (n-- > 0 ? COAL_TICKS : ((n = 0), 0))) as FuelSource & { left: () => number };
  fn.left = () => n;
  return fn;
}

function fullBoiler(): BoilerState {
  const s = new BoilerState();
  s.waterMb = C.WATER_CAPACITY_MB;
  return s;
}

function runSeconds(s: BoilerState, fuel: FuelSource, seconds: number, load = new Load()): number {
  const result = new TickResult();
  let wear = 0;
  for (let i = 0; i < Math.round(seconds * C.TICKS_PER_SECOND); i++) {
    tick(s, load, fuel, result);
    wear += result.wear;
  }
  return wear;
}

describe("caldeira (A3)", () => {
  it("partida a frio: ferve em ~20 s e chega a 2 bar em ~30 s", () => {
    const s = fullBoiler();
    const fuel = coal(10);
    expect(ignite(s, fuel)).toBe(true);
    runSeconds(s, fuel, 19);
    expect(s.temperatureC).toBeLessThan(C.BOILING_C);
    runSeconds(s, fuel, 2);
    expect(s.temperatureC).toBeGreaterThanOrEqual(C.BOILING_C);
    runSeconds(s, fuel, 9);
    expect(s.pressureBar).toBeGreaterThanOrEqual(C.MIN_DRIVE_BAR - 0.3);
    expect(s.pressureBar).toBeLessThan(C.MIN_DRIVE_BAR + 0.5);
  });

  it("abafador Normal gasta ~20 mB de água por segundo", () => {
    const s = fullBoiler();
    s.temperatureC = C.BOILING_C;
    const fuel = coal(10);
    ignite(s, fuel);
    const before = s.waterMb;
    runSeconds(s, fuel, 10);
    expect((before - s.waterMb) / 10).toBeCloseTo(20, 0);
  });

  it("fervendo, a temperatura acompanha a de saturação", () => {
    const s = fullBoiler();
    s.temperatureC = C.BOILING_C;
    const fuel = coal(10);
    ignite(s, fuel);
    runSeconds(s, fuel, 15);
    expect(s.temperatureC).toBeCloseTo(C.BOILING_C + C.SATURATION_C_PER_BAR * s.pressureBar, 2);
  });

  it("válvula de segurança ventila até 8 bar e desgasta", () => {
    const s = fullBoiler();
    s.temperatureC = 200;
    s.pressureBar = 9.999;
    const fuel = coal(10);
    ignite(s, fuel);
    const result = new TickResult();
    tick(s, new Load(), fuel, result);
    expect(result.safetyVented).toBe(true);
    expect(s.pressureBar).toBeCloseTo(C.SAFETY_VALVE_RESET_BAR, 3);
    expect(result.wear).toBeCloseTo(C.WEAR_VENT, 3);
  });

  it("sem combustível o fogo apaga e a pressão cai", () => {
    const s = fullBoiler();
    s.temperatureC = 150;
    s.pressureBar = 5;
    const fuel = coal(1);
    ignite(s, fuel);
    s.burnRemaining = 1;
    const result = new TickResult();
    tick(s, new Load(), fuel, result);
    expect(result.fireWentOut).toBe(true);
    expect(s.fireLit).toBe(false);
    runSeconds(s, fuel, 10);
    expect(Math.abs(s.pressureBar - (5 - C.NO_FIRE_LOSS_BAR_PER_S * 10))).toBeLessThan(0.05);
  });

  it("caldeira seca superaquece, desgasta e solta pulsos", () => {
    const s = new BoilerState();
    s.temperatureC = 125;
    const fuel = coal(10);
    ignite(s, fuel);
    const wear = runSeconds(s, fuel, 10);
    expect(s.temperatureC).toBeGreaterThan(160);
    expect(Math.abs(wear - C.WEAR_DRY_PER_S * 10)).toBeLessThan(0.2);
    expect(s.isDryOverheating()).toBe(true);
  });

  it("caldeira seca crítica desgasta mais rápido", () => {
    const s = new BoilerState();
    s.temperatureC = 310;
    const fuel = coal(10);
    ignite(s, fuel);
    expect(Math.abs(runSeconds(s, fuel, 5) - C.WEAR_DRY_CRITICAL_PER_S * 5)).toBeLessThan(0.2);
  });

  it("encher a caldeira seca e quente causa choque térmico", () => {
    const s = new BoilerState();
    s.temperatureC = 250;
    expect(addWater(s, C.BUCKET_MB)).toBe(true);
    expect(s.temperatureC).toBe(C.BOILING_C);
    expect(s.waterMb).toBe(C.BUCKET_MB);
    const cool = new BoilerState();
    cool.temperatureC = 150;
    expect(addWater(cool, C.BUCKET_MB)).toBe(false);
  });

  it("o tanque não transborda", () => {
    const s = fullBoiler();
    addWater(s, C.BUCKET_MB);
    expect(s.waterMb).toBe(C.WATER_CAPACITY_MB);
  });

  it("andar a toda gasta vapor", () => {
    const idle = fullBoiler();
    const driving = fullBoiler();
    for (const s of [idle, driving]) {
      s.temperatureC = 160;
      s.pressureBar = 6;
    }
    const a = coal(10);
    const b = coal(10);
    ignite(idle, a);
    ignite(driving, b);
    runSeconds(idle, a, 10);
    const load = new Load();
    load.speedFraction = 1;
    runSeconds(driving, b, 10, load);
    expect(idle.pressureBar - driving.pressureBar).toBeCloseTo(C.DRIVE_BAR_PER_S * 10, 2);
  });

  it("a válvula de alívio manual tem recarga", () => {
    const s = fullBoiler();
    s.pressureBar = 6;
    expect(manualVent(s)).toBe(true);
    expect(s.pressureBar).toBeCloseTo(4, 3);
    expect(manualVent(s)).toBe(false);
  });

  it("abafador fechado queima devagar e não faz vapor", () => {
    const s = fullBoiler();
    s.temperatureC = C.BOILING_C;
    s.damper = CLOSED;
    const fuel = coal(1);
    ignite(s, fuel);
    runSeconds(s, fuel, 10);
    expect(s.burnRemaining).toBeCloseTo(COAL_TICKS - 200 * CLOSED.fuelRate, 2);
    expect(s.pressureBar).toBe(0);
  });

  it("fator de potência vai de 0 (2 bar) a 1 (8 bar)", () => {
    expect(C.powerFactor(1.9)).toBe(0);
    expect(C.powerFactor(5)).toBeCloseTo(0.5, 3);
    expect(C.powerFactor(11)).toBe(1);
  });

  it("estado salvo e carregado continua igual", () => {
    const s = fullBoiler();
    s.temperatureC = 143.25;
    s.pressureBar = 4.5;
    s.fireLit = true;
    s.burnRemaining = 812.5;
    s.burnTotal = 1600;
    const copy = BoilerState.load(s.save());
    expect(copy).toEqual(s);
    expect(BoilerState.load("lixo").pressureBar).toBe(0);
  });
});
