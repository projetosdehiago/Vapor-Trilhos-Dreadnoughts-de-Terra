import type { Player } from "@minecraft/server";
import * as C from "../boiler/constants";
import { fixed1, isHoldingMessage, tr } from "../game/text";
import type { Landship } from "./landship";

/**
 * Medidores (A2.1): o Bedrock não tem HUD personalizada para add-ons, então a linha vai na
 * barra de ação, acima dos itens, para quem está a bordo.
 */
export function showHud(ship: Landship, player: Player): void {
  if (isHoldingMessage(player)) {
    return;
  }
  const b = ship.boiler;
  const fire = b.fireLit
    ? tr("vapor_trilhos.hud.fire_on", tr(`vapor_trilhos.damper.${b.damper.key}`), `${Math.round(b.burnFraction() * 100)}%`)
    : tr("vapor_trilhos.hud.fire_off");
  const temp = b.isDryOverheating() ? `§c${Math.round(b.temperatureC)}` : `${Math.round(b.temperatureC)}`;
  const integrity = ship.integrity / C.MAX_INTEGRITY;
  const integrityColor = integrity < C.CRITICAL_INTEGRITY_FRACTION ? "§c" : integrity < C.LOW_INTEGRITY_FRACTION ? "§6" : "§a";
  player.onScreenDisplay.setActionBar(tr(
    "vapor_trilhos.hud",
    pressureColor(b.pressureBar) + fixed1(b.pressureBar),
    pressureBar(b.pressureBar),
    temp,
    `${fixed1(b.waterMb / C.BUCKET_MB)}/${C.WATER_CAPACITY_MB / C.BUCKET_MB}`,
    fire,
    `${integrityColor}${Math.round(integrity * 100)}%`,
  ));
}

function pressureColor(bar: number): string {
  return bar >= C.RED_ZONE_BAR ? "§c" : bar >= C.MIN_DRIVE_BAR ? "§a" : "§7";
}

/** Barra de 12 traços, um por bar: cinza abaixo de 2, verde até 8, vermelho na zona vermelha. */
export function pressureBar(bar: number): string {
  let out = "";
  for (let i = 0; i < C.MAX_BAR; i++) {
    const color = i < C.MIN_DRIVE_BAR ? "§7" : i < C.RED_ZONE_BAR ? "§a" : "§c";
    out += i < Math.round(bar) ? `${color}|` : "§8|";
  }
  return `${out}§r`;
}
