import { type ItemStack, type Player, system } from "@minecraft/server";
import * as C from "../boiler/constants";
import { addWater, waterSpace } from "../boiler/simulation";
import { burnTicks, consumeHeld, damageHeld, isCreative, repairOf, waterOf } from "../game/items";
import { notify, tr } from "../game/text";
import { Items } from "../ids";
import { pickUp } from "./item";
import { Landship } from "./landship";
import { openPanel } from "./panel";

/**
 * Interação com o landship (A2.1). Decide, ainda no evento "antes", se o clique é para usar um
 * item (aí cancela o embarque) e faz a mudança no tick seguinte, como o Bedrock exige.
 * Devolve true se a interação foi tratada aqui.
 */
export function handleInteract(player: Player, ship: Landship, stack: ItemStack | undefined): boolean {
  const sneaking = player.isSneaking;
  const typeId = stack?.typeId;

  if (sneaking) {
    if (typeId === Items.wrench) {
      system.run(() => pickUp(player, ship));
    } else {
      system.run(() => openPanel(player, ship));
    }
    return true;
  }
  if (!stack) {
    return false; // mão vazia: embarca
  }
  if (typeId === Items.commandPanel) {
    system.run(() => openPanel(player, ship));
    return true;
  }
  const water = waterOf(stack);
  if (water) {
    system.run(() => fillWater(player, ship, water.mb, water.empty));
    return true;
  }
  if (typeId === "minecraft:flint_and_steel" || typeId === "minecraft:fire_charge") {
    system.run(() => {
      if (ship.igniteFire(player)) {
        if (typeId === "minecraft:flint_and_steel") {
          damageHeld(player);
        } else {
          consumeHeld(player);
        }
      }
    });
    return true;
  }
  const repair = repairOf(stack);
  if (repair) {
    system.run(() => {
      if (ship.repair(player, repair.amount, repair.limitFraction)) {
        consumeHeld(player);
      }
    });
    return true;
  }
  if (burnTicks(stack) > 0) {
    system.run(() => addFuel(player, ship));
    return true;
  }
  return false;
}

function fillWater(player: Player, ship: Landship, mb: number, empty: string): void {
  if (waterSpace(ship.boiler) < mb) {
    notify(player, tr("vapor_trilhos.message.water_full"));
    return;
  }
  if (addWater(ship.boiler, mb)) {
    ship.thermalShock();
  } else {
    ship.playSound("bucket.empty_water", 1, 1);
  }
  consumeHeld(player, empty);
  ship.save();
}

/** Põe o combustível da mão no compartimento (o pacote todo, se couber). */
function addFuel(player: Player, ship: Landship): void {
  const fuel = ship.fuel();
  const inventory = player.getComponent("minecraft:inventory")?.container;
  const slot = player.selectedSlotIndex;
  const stack = inventory?.getItem(slot);
  if (!fuel || !inventory || !stack) {
    return;
  }
  const rest = fuel.addItem(stack.clone());
  if (rest && rest.amount === stack.amount) {
    notify(player, tr("vapor_trilhos.message.fuel_full"));
    return;
  }
  if (!isCreative(player)) {
    inventory.setItem(slot, rest);
  }
  ship.playSound("random.pop", 0.4, 0.8);
}

/** Usado pelo Painel de Comando fora do veículo: o landship na mira, até 6 blocos. */
export function lookedAtLandship(player: Player): Landship | undefined {
  const hit = player.getEntitiesFromViewDirection({ maxDistance: C.PANEL_REACH + 1.5, type: Items.landship })[0];
  return hit ? Landship.of(hit.entity) : undefined;
}
