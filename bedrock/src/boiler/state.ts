import { AMBIENT_C, DRY_OVERHEAT_C, WATER_CAPACITY_MB } from "./constants";
import { type Damper, NORMAL, damperById } from "./damper";

/** Estado mutável da caldeira. Não depende do jogo: quem persiste é o landship. */
export class BoilerState {
  waterMb = 0;
  temperatureC = AMBIENT_C;
  pressureBar = 0;
  fireLit = false;
  damper: Damper = NORMAL;
  /** Ticks de queima restantes do item de combustível atual (fracionário por causa do abafador). */
  burnRemaining = 0;
  /** Ticks de queima totais do item atual (para o medidor de combustível). */
  burnTotal = 0;
  /** Ticks até o próximo pulso de vapor cegante com a caldeira seca. */
  dryPulseTimer = 0;
  /** Ticks até a válvula de alívio manual poder ser usada de novo. */
  manualVentCooldown = 0;

  isDryOverheating(): boolean {
    return this.waterMb <= 0 && this.temperatureC > DRY_OVERHEAT_C;
  }

  waterFraction(): number {
    return this.waterMb / WATER_CAPACITY_MB;
  }

  burnFraction(): number {
    return this.burnTotal <= 0 ? 0 : Math.max(0, this.burnRemaining / this.burnTotal);
  }

  /** Forma compacta para gravar numa propriedade dinâmica (texto JSON). */
  save(): string {
    return JSON.stringify([
      round(this.waterMb), round(this.temperatureC), round(this.pressureBar), this.fireLit ? 1 : 0,
      this.damper.id, round(this.burnRemaining), round(this.burnTotal), this.dryPulseTimer, this.manualVentCooldown,
    ]);
  }

  static load(text: string | undefined): BoilerState {
    const s = new BoilerState();
    if (!text) {
      return s;
    }
    try {
      const a = JSON.parse(text) as number[];
      s.waterMb = num(a[0], 0);
      s.temperatureC = num(a[1], AMBIENT_C);
      s.pressureBar = num(a[2], 0);
      s.fireLit = a[3] === 1;
      s.damper = damperById(num(a[4], NORMAL.id));
      s.burnRemaining = num(a[5], 0);
      s.burnTotal = num(a[6], 0);
      s.dryPulseTimer = num(a[7], 0);
      s.manualVentCooldown = num(a[8], 0);
    } catch {
      // dado corrompido: começa do zero em vez de quebrar o tick
    }
    return s;
  }
}

function round(v: number): number {
  return Math.round(v * 1000) / 1000;
}

function num(v: unknown, fallback: number): number {
  return typeof v === "number" && Number.isFinite(v) ? v : fallback;
}
