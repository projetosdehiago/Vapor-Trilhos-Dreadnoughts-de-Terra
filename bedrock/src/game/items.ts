import { type Container, type EntityInventoryComponent, GameMode, ItemStack, type Player } from "@minecraft/server";
import { BOTTLE_MB, BUCKET_MB, WOOD_REPAIR_LIMIT_FRACTION } from "../boiler/constants";
import { Items } from "../ids";

/**
 * Tempo de queima (ticks) igual ao da fornalha comum (A3.1). O Bedrock não expõe esse valor
 * para scripts, então a tabela vem da wiki; itens fora dela não queimam na caldeira.
 */
const FUEL_TICKS: Record<string, number> = {
  "minecraft:coal": 1600,
  "minecraft:charcoal": 1600,
  "minecraft:coal_block": 16000,
  "minecraft:lava_bucket": 20000,
  "minecraft:blaze_rod": 2400,
  "minecraft:dried_kelp_block": 4000,
  "minecraft:stick": 100,
  "minecraft:bamboo": 50,
  "minecraft:bowl": 100,
  "minecraft:scaffolding": 50,
  "minecraft:crafting_table": 300,
  "minecraft:chest": 300,
  "minecraft:trapped_chest": 300,
  "minecraft:barrel": 300,
  "minecraft:bookshelf": 300,
  "minecraft:ladder": 300,
  "minecraft:jukebox": 300,
  "minecraft:noteblock": 300,
  "minecraft:wooden_sword": 200,
  "minecraft:wooden_pickaxe": 200,
  "minecraft:wooden_axe": 200,
  "minecraft:wooden_shovel": 200,
  "minecraft:wooden_hoe": 200,
};

/** Grupos de itens por etiqueta (tag) do Bedrock. */
const FUEL_TAGS: [string, number][] = [
  ["minecraft:logs", 300],
  ["minecraft:planks", 300],
  ["minecraft:wooden_slabs", 150],
  ["minecraft:wool", 100],
];

export function burnTicks(stack: ItemStack | undefined): number {
  if (!stack) {
    return 0;
  }
  const direct = FUEL_TICKS[stack.typeId];
  if (direct !== undefined) {
    return direct;
  }
  for (const [tag, ticks] of FUEL_TAGS) {
    if (stack.hasTag(tag)) {
      return ticks;
    }
  }
  if (stack.typeId.endsWith("_sapling")) {
    return 100;
  }
  return 0;
}

/** O que sobra depois de queimar (o balde de lava devolve o balde). */
function burnRemainder(typeId: string): ItemStack | undefined {
  return typeId === "minecraft:lava_bucket" ? new ItemStack("minecraft:bucket") : undefined;
}

/** Consome um combustível do compartimento e devolve os ticks de queima (0 se não houver). */
export function takeFuel(container: Container, dropRemainder: (stack: ItemStack) => void): number {
  for (let i = 0; i < container.size; i++) {
    const stack = container.getItem(i);
    const ticks = burnTicks(stack);
    if (stack && ticks > 0) {
      const remainder = burnRemainder(stack.typeId);
      if (stack.amount > 1) {
        stack.amount--;
        container.setItem(i, stack);
        if (remainder) {
          dropRemainder(remainder);
        }
      } else {
        container.setItem(i, remainder);
      }
      return ticks;
    }
  }
  return 0;
}

export interface Repair {
  amount: number;
  limitFraction: number;
}

/** Itens de reparo (A5). */
export function repairOf(stack: ItemStack): Repair | undefined {
  switch (stack.typeId) {
    case "minecraft:iron_ingot":
      return { amount: 10, limitFraction: 1 };
    case "minecraft:iron_nugget":
      return { amount: 1, limitFraction: 1 };
    case "minecraft:iron_block":
      return { amount: 90, limitFraction: 1 };
    case "minecraft:copper_ingot":
      return { amount: 6, limitFraction: 1 };
    case Items.repairKit:
      return { amount: 60, limitFraction: 1 };
  }
  if (stack.hasTag("minecraft:logs")) {
    return { amount: 8, limitFraction: WOOD_REPAIR_LIMIT_FRACTION };
  }
  if (stack.hasTag("minecraft:planks")) {
    return { amount: 3, limitFraction: WOOD_REPAIR_LIMIT_FRACTION };
  }
  return undefined;
}

export interface WaterSource {
  mb: number;
  /** O que o recipiente vira depois de esvaziar. */
  empty: string;
}

/** Balde d'água (1.000 mB) ou garrafa d'água (250 mB). */
export function waterOf(stack: ItemStack): WaterSource | undefined {
  if (stack.typeId === "minecraft:water_bucket") {
    return { mb: BUCKET_MB, empty: "minecraft:bucket" };
  }
  if (stack.typeId === "minecraft:potion") {
    try {
      const potion = stack.getComponent("minecraft:potion");
      if (potion && /^(minecraft:)?water$/i.test(potion.potionEffectType.id)) {
        return { mb: BOTTLE_MB, empty: "minecraft:glass_bottle" };
      }
    } catch {
      // sem o componente de poção: não é água
    }
  }
  return undefined;
}

export function isCreative(player: Player): boolean {
  return player.getGameMode() === GameMode.Creative;
}

function inventory(player: Player): Container | undefined {
  return (player.getComponent("minecraft:inventory") as EntityInventoryComponent | undefined)?.container;
}

/** Gasta um do item na mão (fora do criativo), opcionalmente trocando por outro (balde vazio). */
export function consumeHeld(player: Player, replaceWith?: string): void {
  if (isCreative(player)) {
    return;
  }
  const container = inventory(player);
  if (!container) {
    return;
  }
  const slot = player.selectedSlotIndex;
  const stack = container.getItem(slot);
  if (!stack) {
    return;
  }
  if (stack.amount > 1) {
    stack.amount--;
    container.setItem(slot, stack);
    if (replaceWith) {
      give(player, new ItemStack(replaceWith));
    }
  } else {
    container.setItem(slot, replaceWith ? new ItemStack(replaceWith) : undefined);
  }
}

/** Gasta durabilidade do item na mão (isqueiro). */
export function damageHeld(player: Player): void {
  if (isCreative(player)) {
    return;
  }
  const container = inventory(player);
  const slot = player.selectedSlotIndex;
  const stack = container?.getItem(slot);
  const durability = stack?.getComponent("minecraft:durability");
  if (!container || !stack || !durability) {
    return;
  }
  if (durability.damage + 1 >= durability.maxDurability) {
    container.setItem(slot, undefined);
    player.dimension.playSound("random.break", player.location);
  } else {
    durability.damage++;
    container.setItem(slot, stack);
  }
}

/** Põe no inventário; o que não couber cai aos pés do jogador. */
export function give(player: Player, stack: ItemStack): void {
  const rest = inventory(player)?.addItem(stack) ?? stack;
  if (rest) {
    player.dimension.spawnItem(rest, player.location);
  }
}
