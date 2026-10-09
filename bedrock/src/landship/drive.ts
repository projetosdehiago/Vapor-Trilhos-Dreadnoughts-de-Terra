import {
  ACCEL_M_S2, BRAKE_M_S2, COAST_M_S2, MAX_REVERSE_M_S, MAX_SPEED_M_S, TICKS_PER_SECOND, TURN_DEG_S_FULL,
  TURN_DEG_S_STOPPED,
} from "../boiler/constants";

/**
 * Direção (design.md A4), sem depender do jogo: dá para testar fora dele. É a mesma conta de
 * LandshipEntity.drive() no Java. Velocidades em blocos por tick, giro em graus por tick.
 */

export interface DriveInput {
  forward: boolean;
  backward: boolean;
  left: boolean;
  right: boolean;
}

export const NO_INPUT: DriveInput = { forward: false, backward: false, left: false, right: false };

export interface DriveFactors {
  /** powerFactor(pressão), 0 a 1. */
  power: number;
  /** Terreno × estado do casco × água × peso dos módulos. */
  speedFactor: number;
  /** Gelo: pouca aderência. */
  slippery: boolean;
}

export interface DriveStep {
  speed: number;
  deltaYaw: number;
}

/** Zona morta do analógico (celular e controle). */
const STICK_DEADZONE = 0.3;

/**
 * Converte o vetor de movimento do Bedrock em teclas. No Bedrock y > 0 é para a frente e
 * x > 0 é para a esquerda (como o "leftImpulse" do Java).
 */
export function inputFromVector(v: { x: number; y: number }): DriveInput {
  return {
    forward: v.y > STICK_DEADZONE,
    backward: v.y < -STICK_DEADZONE,
    left: v.x > STICK_DEADZONE,
    right: v.x < -STICK_DEADZONE,
  };
}

export function step(speed: number, input: DriveInput, f: DriveFactors): DriveStep {
  const maxForward = (MAX_SPEED_M_S / TICKS_PER_SECOND) * f.power * f.speedFactor;
  const maxReverse = (MAX_REVERSE_M_S / TICKS_PER_SECOND) * f.power * f.speedFactor;
  const grip = f.slippery ? 0.3 : 1;
  const tick2 = TICKS_PER_SECOND * TICKS_PER_SECOND;
  const accel = (ACCEL_M_S2 / tick2) * grip;
  const brake = (BRAKE_M_S2 / tick2) * grip;
  const coast = COAST_M_S2 / tick2;
  const powered = f.power > 0;

  if (input.forward && !input.backward && powered) {
    speed = speed < maxForward ? Math.min(maxForward, speed + (speed < 0 ? brake : accel)) : Math.max(maxForward, speed - coast);
  } else if (input.backward && !input.forward && powered) {
    speed = speed > 0 ? Math.max(0, speed - brake) : Math.max(-maxReverse, speed - accel);
  } else {
    speed = speed > 0 ? Math.max(0, speed - coast) : Math.min(0, speed + coast);
  }

  let deltaYaw = 0;
  if (input.left !== input.right && powered) {
    const fraction = Math.min(1, Math.abs(speed) / (MAX_SPEED_M_S / TICKS_PER_SECOND));
    const degPerTick = ((TURN_DEG_S_STOPPED + (TURN_DEG_S_FULL - TURN_DEG_S_STOPPED) * fraction) / TICKS_PER_SECOND) * Math.max(0.35, f.power);
    deltaYaw = input.right ? degPerTick : -degPerTick;
  }
  return { speed, deltaYaw };
}

/** Vetor para a frente para um yaw (graus), na convenção do Minecraft (0 = sul, +z). */
export function forwardVector(yawDeg: number): { x: number; z: number } {
  const yaw = (yawDeg * Math.PI) / 180;
  return { x: -Math.sin(yaw), z: Math.cos(yaw) };
}

/** Vetor para a esquerda do veículo. */
export function leftVector(yawDeg: number): { x: number; z: number } {
  const yaw = (yawDeg * Math.PI) / 180;
  return { x: Math.cos(yaw), z: Math.sin(yaw) };
}

/**
 * Converte um ponto do modelo (px Bedrock: x = esquerda, y = cima, z = trás) para o mundo.
 * Igual a LandshipEntity.modelPointToWorld no Java.
 */
export function modelPointToWorld(
  origin: { x: number; y: number; z: number }, yawDeg: number, leftPx: number, upPx: number, backPx: number,
): { x: number; y: number; z: number } {
  const f = forwardVector(yawDeg);
  const l = leftVector(yawDeg);
  return {
    x: origin.x + (l.x * leftPx - f.x * backPx) / 16,
    y: origin.y + upPx / 16,
    z: origin.z + (l.z * leftPx - f.z * backPx) / 16,
  };
}

export function wrapDegrees(deg: number): number {
  let d = deg % 360;
  if (d >= 180) {
    d -= 360;
  }
  if (d < -180) {
    d += 360;
  }
  return d;
}
