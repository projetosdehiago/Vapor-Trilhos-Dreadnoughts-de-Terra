/** Abafador da fornalha: controla o calor gerado e o ritmo de queima do combustível (A3.1). */
export interface Damper {
  readonly id: number;
  readonly key: "closed" | "normal" | "open";
  /** Aquecimento em °C/s abaixo do ponto de ebulição (ou com a caldeira seca). */
  readonly heatCPerSecond: number;
  /** Multiplicador do consumo de combustível. */
  readonly fuelRate: number;
  /** Geração de vapor em bar/s com água e temperatura de ebulição. */
  readonly steamBarPerSecond: number;
}

export const DAMPERS: readonly Damper[] = [
  { id: 0, key: "closed", heatCPerSecond: 0, fuelRate: 0.25, steamBarPerSecond: 0 },
  { id: 1, key: "normal", heatCPerSecond: 4, fuelRate: 1, steamBarPerSecond: 0.2 },
  { id: 2, key: "open", heatCPerSecond: 6, fuelRate: 2, steamBarPerSecond: 0.3 },
];

export const CLOSED = DAMPERS[0];
export const NORMAL = DAMPERS[1];
export const OPEN = DAMPERS[2];

export function damperById(id: number): Damper {
  return DAMPERS[id] ?? NORMAL;
}

export function nextDamper(d: Damper): Damper {
  return DAMPERS[(d.id + 1) % DAMPERS.length];
}
