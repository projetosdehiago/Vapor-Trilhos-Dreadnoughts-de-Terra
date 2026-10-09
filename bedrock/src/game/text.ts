import { type Player, type RawMessage, system } from "@minecraft/server";

/** Mensagem traduzida pelo jogo do jogador (textos nos arquivos .lang do pacote de recursos). */
export function tr(key: string, ...args: (string | RawMessage)[]): RawMessage {
  if (args.length === 0) {
    return { translate: key };
  }
  return { translate: key, with: { rawtext: args.map((a) => (typeof a === "string" ? { text: a } : a)) } };
}

/** Número com uma casa decimal. */
export function fixed1(v: number): string {
  return (Math.round(v * 10) / 10).toFixed(1);
}

/** Até quando (tick) a barra de ação está mostrando um aviso, para a HUD não apagar. */
const holdUntil = new Map<string, number>();

/** Aviso curto acima da barra de itens (como as mensagens de "overlay" do Java). */
export function notify(player: Player, message: RawMessage): void {
  holdUntil.set(player.id, system.currentTick + 40);
  player.onScreenDisplay.setActionBar(message);
}

export function isHoldingMessage(player: Player): boolean {
  const until = holdUntil.get(player.id);
  if (until === undefined) {
    return false;
  }
  if (system.currentTick >= until) {
    holdUntil.delete(player.id);
    return false;
  }
  return true;
}
