import { type Player, type RawMessage, system } from "@minecraft/server";
import { CustomForm, ObservableNumber, ObservableUIRawMessage, type UIRawMessage } from "@minecraft/server-ui";
import * as C from "../boiler/constants";
import { fixed1, notify, tr } from "../game/text";
import type { Landship } from "./landship";
import { WHISTLES, whistleById } from "./whistle";

/**
 * Painel do landship (A2.1). No Bedrock o formulário de script não mostra espaços de itens:
 * o combustível fica no inventário do veículo (tecla de inventário a bordo); aqui ficam os
 * medidores, ao vivo, e os comandos que no Java são as teclas R, V e H.
 */
interface OpenPanel {
  form: CustomForm;
  player: Player;
  ship: Landship;
  refresh: () => void;
}

const open = new Map<string, OpenPanel>();

export function openPanel(player: Player, ship: Landship): void {
  if (open.has(player.id)) {
    return;
  }
  const gauges = {
    pressure: new ObservableUIRawMessage({}),
    temperature: new ObservableUIRawMessage({}),
    water: new ObservableUIRawMessage({}),
    fire: new ObservableUIRawMessage({}),
    integrity: new ObservableUIRawMessage({}),
    fireButton: new ObservableUIRawMessage({}),
    damperButton: new ObservableUIRawMessage({}),
  };
  const whistle = new ObservableNumber(ship.whistle.id, { clientWritable: true });

  const refresh = (): void => {
    const b = ship.boiler;
    gauges.pressure.setData(ui(tr("vapor_trilhos.panel.pressure", fixed1(b.pressureBar), statusOf(b.pressureBar))));
    gauges.temperature.setData(ui(tr("vapor_trilhos.panel.temperature", `${Math.round(b.temperatureC)}`)));
    gauges.water.setData(ui(tr("vapor_trilhos.panel.water", fixed1(b.waterMb / C.BUCKET_MB), `${C.WATER_CAPACITY_MB / C.BUCKET_MB}`)));
    gauges.fire.setData(ui(b.fireLit
      ? tr("vapor_trilhos.panel.fire_on", tr(`vapor_trilhos.damper.${b.damper.key}`), `${Math.round(b.burnFraction() * 100)}%`)
      : tr("vapor_trilhos.panel.fire_off")));
    gauges.integrity.setData(ui(tr("vapor_trilhos.panel.integrity", `${Math.round((ship.integrity / C.MAX_INTEGRITY) * 100)}%`)));
    gauges.fireButton.setData(ui(tr(b.fireLit ? "vapor_trilhos.button.extinguish" : "vapor_trilhos.button.ignite")));
    gauges.damperButton.setData(ui(tr("vapor_trilhos.button.damper", tr(`vapor_trilhos.damper.${b.damper.key}`))));
  };
  refresh();

  /** Cada botão confere de novo se o jogador ainda alcança o landship. */
  const guarded = (action: () => void) => () => {
    system.run(() => {
      if (ship.isUsableBy(player)) {
        action();
        refresh();
      }
    });
  };

  whistle.subscribe((id) => {
    system.run(() => {
      if (ship.isUsableBy(player) && id !== ship.whistle.id) {
        ship.selectWhistle(player, whistleById(id));
      }
    });
  });

  const form = new CustomForm(player, ui(tr("vapor_trilhos.panel.title")))
    .label(gauges.pressure)
    .label(gauges.temperature)
    .label(gauges.water)
    .label(gauges.fire)
    .label(gauges.integrity)
    .divider()
    .button(gauges.fireButton, guarded(() => ship.toggleFire(player)))
    .button(gauges.damperButton, guarded(() => ship.cycleDamper(player)))
    .button(ui(tr("vapor_trilhos.button.vent")), guarded(() => ship.manualVent(player)))
    .button(ui(tr("vapor_trilhos.button.whistle")), guarded(() => ship.blowWhistle()))
    .divider()
    .dropdown(ui(tr("vapor_trilhos.panel.whistle_sound")), whistle,
      WHISTLES.map((w) => ({ label: ui(tr(`vapor_trilhos.whistle.${w.key}`)), value: w.id })))
    .label(ui(tr("vapor_trilhos.panel.fuel_hint")))
    .closeButton();

  open.set(player.id, { form, player, ship, refresh });
  form.show().then(
    () => open.delete(player.id),
    () => open.delete(player.id),
  );
}

/** Chamado pelo laço principal: atualiza os medidores e fecha painéis fora de alcance. */
export function refreshPanels(): void {
  for (const [id, panel] of open) {
    if (!panel.player.isValid || !panel.ship.isUsableBy(panel.player)) {
      close(id);
      if (panel.player.isValid) {
        notify(panel.player, tr("vapor_trilhos.message.too_far"));
      }
      continue;
    }
    panel.refresh();
  }
}

function close(playerId: string): void {
  const panel = open.get(playerId);
  open.delete(playerId);
  try {
    if (panel?.form.isShowing()) {
      panel.form.close();
    }
  } catch {
    // já fechado pelo jogador
  }
}

function statusOf(bar: number): RawMessage {
  if (bar >= C.SAFETY_VALVE_BAR - 0.5) {
    return tr("vapor_trilhos.panel.status_danger");
  }
  if (bar >= C.RED_ZONE_BAR) {
    return tr("vapor_trilhos.panel.status_red");
  }
  if (bar >= C.MIN_DRIVE_BAR) {
    return tr("vapor_trilhos.panel.status_ok");
  }
  return tr("vapor_trilhos.panel.status_low");
}

/** O formulário usa o tipo de texto do server-ui, que é o mesmo formato do RawMessage. */
function ui(message: RawMessage): UIRawMessage {
  return message as UIRawMessage;
}
