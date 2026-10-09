import { type Block, Direction, ItemStack, type Player, type Vector3 } from "@minecraft/server";
import * as C from "../boiler/constants";
import { consumeHeld, give } from "../game/items";
import { notify, tr } from "../game/text";
import { Dyn, Items, LANDSHIP } from "../ids";
import { Landship } from "./landship";

/** Usar o item Landship num bloco: coloca o veículo virado para onde o jogador olha. */
export function placeLandship(player: Player, block: Block, face: Direction, stack: ItemStack): void {
  const target = face === Direction.Up ? block.above() : adjacent(block, face);
  if (!target) {
    return;
  }
  const at: Vector3 = { x: target.location.x + 0.5, y: target.location.y, z: target.location.z + 0.5 };
  if (!hasRoom(player, at)) {
    notify(player, tr("vapor_trilhos.message.no_room"));
    return;
  }
  const entity = player.dimension.spawnEntity(LANDSHIP, at, { initialRotation: player.getRotation().y });
  // dados guardados no item quando ele foi recolhido (integridade e água)
  const integrity = stack.getDynamicProperty(Dyn.integrity);
  const water = stack.getDynamicProperty(Dyn.water);
  if (typeof integrity === "number") {
    entity.setDynamicProperty(Dyn.integrity, integrity);
  }
  if (typeof water === "number") {
    entity.setDynamicProperty(Dyn.boiler, JSON.stringify([water]));
  }
  if (stack.nameTag) {
    entity.nameTag = stack.nameTag;
  }
  entity.dimension.playSound("random.anvil_land", at, { volume: 0.5, pitch: 1.2 });
  consumeHeld(player);
}

/** Agachar + Chave de Caldeireiro (A2.1): recolhe como item, guardando integridade e água. */
export function pickUp(player: Player, ship: Landship): void {
  if (ship.riders().length > 0) {
    notify(player, tr("vapor_trilhos.message.pickup_occupied"));
    return;
  }
  if (ship.boiler.fireLit || ship.boiler.temperatureC > C.PICKUP_MAX_C) {
    notify(player, tr("vapor_trilhos.message.pickup_hot"));
    return;
  }
  const item = new ItemStack(Items.landship);
  item.setDynamicProperty(Dyn.integrity, Math.round(ship.integrity * 100) / 100);
  item.setDynamicProperty(Dyn.water, Math.round(ship.boiler.waterMb));
  item.setLore([
    tr("vapor_trilhos.lore.integrity", `${Math.round((ship.integrity / C.MAX_INTEGRITY) * 100)}%`),
    tr("vapor_trilhos.lore.water", `${Math.round(ship.boiler.waterMb / C.BUCKET_MB)}`),
  ]);
  if (ship.entity.nameTag) {
    item.nameTag = ship.entity.nameTag;
  }
  const fuel = ship.fuel();
  if (fuel) {
    for (let i = 0; i < fuel.size; i++) {
      const stack = fuel.getItem(i);
      if (stack) {
        fuel.setItem(i, undefined);
        give(player, stack);
      }
    }
  }
  give(player, item);
  ship.playSound("random.anvil_use", 1, 0.6);
  Landship.forget(ship.entity.id);
  ship.entity.remove();
}

function adjacent(block: Block, face: Direction): Block | undefined {
  switch (face) {
    case Direction.Down:
      return block.below();
    case Direction.North:
      return block.north();
    case Direction.South:
      return block.south();
    case Direction.East:
      return block.east();
    case Direction.West:
      return block.west();
    default:
      return block.above();
  }
}

/** Pegada 3 × 3 e 2 de altura livre (A2). */
function hasRoom(player: Player, at: Vector3): boolean {
  for (let dx = -1; dx <= 1; dx++) {
    for (let dz = -1; dz <= 1; dz++) {
      for (let dy = 0; dy <= 1; dy++) {
        const block = player.dimension.getBlock({ x: at.x + dx, y: at.y + dy, z: at.z + dz });
        if (block && !block.isAir && !block.isLiquid && !/(short_grass|tall_grass|fern|snow_layer|flower|dead_bush)$/.test(block.typeId)) {
          return false;
        }
      }
    }
  }
  return true;
}
