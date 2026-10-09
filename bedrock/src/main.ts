import { ButtonState, EntityDamageCause, InputButton, Player, system, world } from "@minecraft/server";
import { isCreative } from "./game/items";
import { notify, tr } from "./game/text";
import { ItemComponents, LANDSHIP } from "./ids";
import { showHud } from "./landship/hud";
import { handleInteract, lookedAtLandship } from "./landship/interact";
import { placeLandship } from "./landship/item";
import { Landship } from "./landship/landship";
import { openPanel, refreshPanels } from "./landship/panel";

/**
 * Vapor & Trilhos: Dreadnoughts de Terra — versão Bedrock (Fase B1: landship e caldeira).
 * Regras em design/design.md, Parte A; detalhes desta versão na Parte C.
 */

const DIMENSIONS = ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"];

system.beforeEvents.startup.subscribe(({ itemComponentRegistry }) => {
  itemComponentRegistry.registerCustomComponent(ItemComponents.landshipPlacer, {
    onUseOn: ({ source, block, blockFace, itemStack }) => {
      if (source instanceof Player && itemStack) {
        const stack = itemStack;
        system.run(() => placeLandship(source, block, blockFace, stack));
      }
    },
  });
  // Painel de Comando: no lugar das teclas R/V/H do Java (funciona no celular)
  itemComponentRegistry.registerCustomComponent(ItemComponents.commandPanel, {
    onUse: ({ source }) => {
      const ship = Landship.ridden(source) ?? lookedAtLandship(source);
      if (ship && ship.isUsableBy(source)) {
        system.run(() => openPanel(source, ship));
      } else {
        system.run(() => notify(source, tr("vapor_trilhos.message.look_at_landship")));
      }
    },
  });
});

system.runInterval(() => {
  const hud = system.currentTick % 5 === 0;
  for (const id of DIMENSIONS) {
    for (const entity of world.getDimension(id).getEntities({ type: LANDSHIP })) {
      const ship = Landship.of(entity);
      ship.tick();
      if (hud && entity.isValid) {
        for (const rider of ship.riders()) {
          if (rider instanceof Player) {
            showHud(ship, rider);
          }
        }
      }
    }
  }
  if (hud) {
    refreshPanels();
  }
}, 1);

world.beforeEvents.playerInteractWithEntity.subscribe((event) => {
  if (event.target.typeId !== LANDSHIP) {
    return;
  }
  if (handleInteract(event.player, Landship.of(event.target), event.itemStack)) {
    event.cancel = true;
  }
});

// pular enquanto pilota = apito (no Java é a tecla H)
world.afterEvents.playerButtonInput.subscribe(({ player }) => {
  const ship = Landship.ridden(player);
  if (ship && ship.pilot()?.id === player.id) {
    ship.blowWhistle();
  }
}, { buttons: [InputButton.Jump], state: ButtonState.Pressed });

world.afterEvents.entityHurt.subscribe(({ hurtEntity, damage, damageSource }) => {
  const attacker = damageSource.damagingEntity;
  const ship = Landship.of(hurtEntity);
  if (attacker instanceof Player && isCreative(attacker)) {
    // no criativo, agachar e bater remove o landship (como no Java); só bater não desgasta
    ship.onHurt(0, false);
    if (attacker.isSneaking) {
      Landship.forget(hurtEntity.id);
      hurtEntity.remove();
    }
    return;
  }
  const explosion = damageSource.cause === EntityDamageCause.blockExplosion || damageSource.cause === EntityDamageCause.entityExplosion;
  ship.onHurt(damage, explosion);
}, { entityTypes: [LANDSHIP] });

world.afterEvents.entityRemove.subscribe(({ removedEntityId }) => Landship.forget(removedEntityId), { entityTypes: [LANDSHIP] });
