import { describe, expect, it } from "vitest";
import { MAX_REVERSE_M_S, MAX_SPEED_M_S, TICKS_PER_SECOND } from "../src/boiler/constants";
import { forwardVector, inputFromVector, modelPointToWorld, NO_INPUT, step, wrapDegrees } from "../src/landship/drive";

const FULL = { power: 1, speedFactor: 1, slippery: false };
const W = { ...NO_INPUT, forward: true };
const S = { ...NO_INPUT, backward: true };

function run(seconds: number, input = W, f = FULL, speed = 0): number {
  for (let i = 0; i < seconds * TICKS_PER_SECOND; i++) {
    speed = step(speed, input, f).speed;
  }
  return speed;
}

describe("direção (A4)", () => {
  it("vai de 0 a 5 m/s em 5 s e para por aí", () => {
    expect(run(2.5) * TICKS_PER_SECOND).toBeCloseTo(2.5, 1);
    expect(run(5) * TICKS_PER_SECOND).toBeCloseTo(MAX_SPEED_M_S, 3);
    expect(run(10) * TICKS_PER_SECOND).toBeCloseTo(MAX_SPEED_M_S, 3);
  });

  it("S freia e depois dá ré até 2 m/s", () => {
    const top = run(5);
    const braked = run(1, S, FULL, top);
    expect(braked * TICKS_PER_SECOND).toBeCloseTo(MAX_SPEED_M_S - 2.5, 1);
    expect(run(10, S, FULL, top) * TICKS_PER_SECOND).toBeCloseTo(-MAX_REVERSE_M_S, 3);
  });

  it("sem pressão não anda nem gira", () => {
    const f = { ...FULL, power: 0 };
    expect(run(5, W, f)).toBe(0);
    expect(step(0, { ...NO_INPUT, left: true }, f).deltaYaw).toBe(0);
  });

  it("gira 45°/s parado e 30°/s na velocidade máxima", () => {
    const right = { ...NO_INPUT, right: true };
    expect(step(0, right, FULL).deltaYaw * TICKS_PER_SECOND).toBeCloseTo(45, 3);
    expect(step(0, { ...NO_INPUT, left: true }, FULL).deltaYaw).toBeLessThan(0);
    const top = MAX_SPEED_M_S / TICKS_PER_SECOND;
    expect(step(top, { ...W, right: true }, FULL).deltaYaw * TICKS_PER_SECOND).toBeCloseTo(30, 3);
  });

  it("o fator de terreno/peso limita a velocidade", () => {
    expect(run(10, W, { ...FULL, speedFactor: 0.5 }) * TICKS_PER_SECOND).toBeCloseTo(2.5, 3);
  });

  it("solto, desacelera até parar", () => {
    expect(run(5, NO_INPUT, FULL, run(5))).toBe(0);
  });

  it("vetor de movimento do Bedrock vira teclas (x > 0 = esquerda)", () => {
    expect(inputFromVector({ x: 0, y: 1 })).toEqual({ forward: true, backward: false, left: false, right: false });
    expect(inputFromVector({ x: 1, y: 0 }).left).toBe(true);
    expect(inputFromVector({ x: -1, y: -1 })).toEqual({ forward: false, backward: true, left: false, right: true });
    expect(inputFromVector({ x: 0.1, y: 0.2 })).toEqual(NO_INPUT);
  });

  it("geometria: frente, pontos do modelo e ângulos", () => {
    const f = forwardVector(0);
    expect(f.x).toBeCloseTo(0, 6);
    expect(f.z).toBeCloseTo(1, 6);
    const chimney = modelPointToWorld({ x: 0, y: 64, z: 0 }, 0, 0, 54, 16);
    expect(chimney.z).toBeCloseTo(-1, 6); // atrás
    expect(chimney.y).toBeCloseTo(64 + 54 / 16, 6);
    expect(wrapDegrees(350)).toBe(-10);
    expect(wrapDegrees(-190)).toBe(170);
  });
});
