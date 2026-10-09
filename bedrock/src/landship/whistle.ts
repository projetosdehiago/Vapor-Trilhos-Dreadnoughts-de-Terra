/** Sons de apito (os mesmos da versão Java, design.md A2.1). Recarga em ticks. */
export interface Whistle {
  readonly id: number;
  readonly key: string;
  readonly sound: string;
  readonly cooldown: number;
}

export const WHISTLES: readonly Whistle[] = [
  { id: 0, key: "steam", sound: "vapor_trilhos.whistle.steam", cooldown: 50 },
  { id: 1, key: "foghorn", sound: "vapor_trilhos.whistle.foghorn", cooldown: 70 },
  { id: 2, key: "bell", sound: "vapor_trilhos.whistle.bell", cooldown: 80 },
  { id: 3, key: "war_horn", sound: "vapor_trilhos.whistle.war_horn", cooldown: 60 },
  { id: 4, key: "custom", sound: "vapor_trilhos.whistle.custom", cooldown: 140 },
];

export function whistleById(id: number): Whistle {
  return WHISTLES[id] ?? WHISTLES[0];
}
