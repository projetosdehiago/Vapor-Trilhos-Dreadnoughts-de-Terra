/** Identificadores do add-on. Os mesmos nomes da versão Java, com o namespace vapor_trilhos. */
export const NS = "vapor_trilhos";

export const LANDSHIP = `${NS}:landship`;

export const Items = {
  landship: `${NS}:landship`,
  reinforcedTrack: `${NS}:reinforced_track`,
  steamBoiler: `${NS}:steam_boiler`,
  wrench: `${NS}:boilermaker_wrench`,
  repairKit: `${NS}:repair_kit`,
  commandPanel: `${NS}:command_panel`,
} as const;

/** Componentes de item feitos por script. */
export const ItemComponents = {
  landshipPlacer: `${NS}:landship_placer`,
  commandPanel: `${NS}:command_panel`,
} as const;

/** Propriedades da entidade (definidas no comportamento, lidas pelas animações). */
export const Props = {
  trackLeft: `${NS}:track_left`,
  trackRight: `${NS}:track_right`,
  working: `${NS}:working`,
  venting: `${NS}:venting`,
  aboard: `${NS}:aboard`,
} as const;

/** Propriedades dinâmicas (dados salvos com o mundo). */
export const Dyn = {
  boiler: "vt:boiler",
  whistle: "vt:whistle",
  integrity: "vt:integrity",
  water: "vt:water",
} as const;

export const Particles = {
  steamBurst: `${NS}:steam_burst`,
  steamPuff: `${NS}:steam_puff`,
  chimneySmoke: `${NS}:chimney_smoke`,
  blackSmoke: `${NS}:black_smoke`,
} as const;
