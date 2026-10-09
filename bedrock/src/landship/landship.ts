import {
  type Block, type Container, type Dimension, type Entity, type EntityHealthComponent, type EntityInventoryComponent,
  type EntityRideableComponent, ItemStack, Player, type Vector3, world,
} from "@minecraft/server";
import * as C from "../boiler/constants";
import { nextDamper } from "../boiler/damper";
import { Load, TickResult, extinguish, ignite, manualVent, tick as tickBoiler } from "../boiler/simulation";
import { BoilerState } from "../boiler/state";
import { takeFuel } from "../game/items";
import { notify, tr } from "../game/text";
import { Dyn, Items, Particles, Props } from "../ids";
import { type DriveInput, NO_INPUT, forwardVector, inputFromVector, leftVector, modelPointToWorld, step, wrapDegrees } from "./drive";
import { type Whistle, whistleById } from "./whistle";

/** Altura do teto (A2): quem fica em pé ali anda junto. */
const ROOF_HEIGHT = 2;
/** Meia largura do casco (3 × 3 blocos). */
const HALF_WIDTH = 1.45;
/** Pontos do modelo (px): topo da chaminé e saída da válvula. Os mesmos do Java. */
const CHIMNEY_PX = [0, 54, 19] as const;
const VENT_PX = [3.5, 35, 12.5] as const;

const TERRAIN_BOG = new Set(["minecraft:soul_sand", "minecraft:soul_soil", "minecraft:mud"]);
const TERRAIN_LOOSE = new Set(["minecraft:sand", "minecraft:red_sand", "minecraft:suspicious_sand", "minecraft:snow", "minecraft:snow_layer", "minecraft:powder_snow"]);
const TERRAIN_SOFT = new Set(["minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:mycelium", "minecraft:rooted_dirt", "minecraft:moss_block", "minecraft:farmland"]);
const TERRAIN_SLIPPERY = new Set(["minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice", "minecraft:frosted_ice"]);
/** Blocos que não seguram o landship (dá para passar por cima/através ao subir degrau). */
const PASSABLE = /(short_grass|tall_grass|fern|dead_bush|seagrass|snow_layer|torch|flower|tulip|dandelion|poppy|orchid|allium|azure_bluet|daisy|cornflower|lily_of_the_valley|vine|carpet|sapling|mushroom|button|lever|rail|pressure_plate|sugar_cane|sweet_berry|wheat|carrots|potatoes|beetroot|bush)$/;

/**
 * Um landship em jogo: estado da caldeira, direção e efeitos. A entidade guarda os dados em
 * propriedades dinâmicas; aqui fica a cópia em memória usada a cada tick.
 */
export class Landship {
  private static readonly loaded = new Map<string, Landship>();

  static of(entity: Entity): Landship {
    let ship = Landship.loaded.get(entity.id);
    if (!ship) {
      ship = new Landship(entity);
      Landship.loaded.set(entity.id, ship);
    } else {
      // o jogo pode entregar outro objeto para a mesma entidade: o estado em memória continua
      ship.entity = entity;
    }
    return ship;
  }

  static forget(entityId: string): void {
    Landship.loaded.delete(entityId);
  }

  /** Landship em que o jogador está, se houver. */
  static ridden(player: Player): Landship | undefined {
    const vehicle = player.getComponent("minecraft:riding")?.entityRidingOn;
    return vehicle?.isValid && vehicle.typeId === Items.landship ? Landship.of(vehicle) : undefined;
  }

  readonly boiler: BoilerState;
  integrity: number;
  whistle: Whistle;
  /** Velocidade em blocos por tick (negativa = ré). */
  speed = 0;

  private readonly load = new Load();
  private readonly result = new TickResult();
  private last?: { x: number; y: number; z: number; yaw: number };
  private lastSpeedMs = 0;
  private blockedTicks = 0;
  private fallStartY?: number;
  private repairCooldown = 0;
  private whistleCooldown = 0;
  private chugTimer = 0;
  private ventingTicks = 0;
  private age = 0;
  private readonly props = new Map<string, number | boolean>();

  private constructor(public entity: Entity) {
    this.boiler = BoilerState.load(entity.getDynamicProperty(Dyn.boiler) as string | undefined);
    const saved = entity.getDynamicProperty(Dyn.integrity);
    this.integrity = typeof saved === "number" ? saved : C.MAX_INTEGRITY;
    this.whistle = whistleById(Number(entity.getDynamicProperty(Dyn.whistle) ?? 0));
  }

  get dimension(): Dimension {
    return this.entity.dimension;
  }

  get yaw(): number {
    return this.entity.getRotation().y;
  }

  riders(): Entity[] {
    return (this.entity.getComponent("minecraft:rideable") as EntityRideableComponent | undefined)?.getRiders() ?? [];
  }

  /** O piloto é quem está no primeiro assento. */
  pilot(): Player | undefined {
    const first = this.riders()[0];
    return first instanceof Player ? first : undefined;
  }

  isAboard(player: Player): boolean {
    return this.riders().some((r) => r.id === player.id);
  }

  fuel(): Container | undefined {
    return (this.entity.getComponent("minecraft:inventory") as EntityInventoryComponent | undefined)?.container;
  }

  /** Painel e comandos: a bordo ou a até 5 blocos do casco (como no Java). */
  isUsableBy(player: Player): boolean {
    if (!this.entity.isValid) {
      return false;
    }
    if (this.isAboard(player)) {
      return true;
    }
    const e = this.entity.location;
    const p = player.location;
    const dx = Math.max(0, Math.abs(p.x - e.x) - HALF_WIDTH);
    const dz = Math.max(0, Math.abs(p.z - e.z) - HALF_WIDTH);
    const dy = Math.max(0, e.y - p.y, p.y - (e.y + ROOF_HEIGHT));
    return player.dimension.id === this.dimension.id && dx * dx + dy * dy + dz * dz <= C.PANEL_REACH * C.PANEL_REACH;
  }

  isStationary(): boolean {
    return Math.abs(this.speed) < 0.01;
  }

  /** Ponto do modelo (px Bedrock) no mundo. */
  modelPoint(px: readonly [number, number, number]): Vector3 {
    return modelPointToWorld(this.entity.location, this.yaw, px[0], px[1], px[2]);
  }

  // =====================================================================================
  // Tick
  // =====================================================================================

  tick(): void {
    const e = this.entity;
    if (!e.isValid) {
      return;
    }
    this.age++;
    if (this.repairCooldown > 0) {
      this.repairCooldown--;
    }
    if (this.whistleCooldown > 0) {
      this.whistleCooldown--;
    }
    if (this.ventingTicks > 0) {
      this.ventingTicks--;
    }

    // movimento medido desde o tick anterior
    const now = e.location;
    const yaw = this.yaw;
    let horizontal = 0;
    let dy = 0;
    let dYaw = 0;
    let dx = 0;
    let dz = 0;
    if (this.last) {
      dx = now.x - this.last.x;
      dz = now.z - this.last.z;
      dy = now.y - this.last.y;
      dYaw = wrapDegrees(yaw - this.last.yaw);
      horizontal = Math.sqrt(dx * dx + dz * dz);
      if (horizontal > 2) {
        horizontal = 0; // teleporte, não movimento
        dx = dz = 0;
      }
    }
    const speedMs = horizontal * C.TICKS_PER_SECOND;
    const fwd = forwardVector(yaw);
    const forwardSpeed = dx * fwd.x + dz * fwd.z;

    this.carryEntitiesOnTop(dx, dy, dz, dYaw);
    this.updateFall(now.y);
    const stepped = this.drive(horizontal);

    // carga pedida à caldeira (A3.3)
    const load = this.load.reset();
    load.speedFraction = Math.min(1, speedMs / C.MAX_SPEED_M_S);
    load.pivoting = Math.abs(dYaw) > 0.05 && speedMs < 0.3;
    load.stepUps = stepped ? 1 : 0;
    load.integrityFraction = this.integrity / C.MAX_INTEGRITY;

    let wear = horizontal * C.WEAR_PER_BLOCK;
    if (this.lastSpeedMs >= C.WEAR_COLLISION_MIN_SPEED && this.lastSpeedMs - speedMs > 1.5) {
      wear += (this.lastSpeedMs - 2) * C.WEAR_COLLISION_FACTOR;
      this.playSound("random.anvil_land", 0.6, 0.6);
    }
    if (this.blockAt(now)?.typeId.includes("lava")) {
      wear += C.WEAR_LAVA_PER_S / C.TICKS_PER_SECOND;
    }

    // caldeira submersa apaga o fogo (A3.6)
    if (this.boiler.fireLit && this.blockAt({ x: now.x, y: now.y + 1.5, z: now.z })?.typeId.includes("water")) {
      extinguish(this.boiler);
      this.playSound("random.fizz", 1, 1);
    }

    tickBoiler(this.boiler, load, () => this.takeFuel(), this.result);
    wear += this.result.wear;
    if (this.result.safetyVented) {
      this.steamBurst(C.VENT_RADIUS, C.VENT_BLINDNESS_TICKS, true);
    }
    if (this.result.dryPulse) {
      this.steamBurst(C.VENT_RADIUS, C.VENT_BLINDNESS_TICKS, true);
    }
    if (this.boiler.fireLit && speedMs > 0.2 && --this.chugTimer <= 0) {
      this.playSound("tile.piston.out", 0.5, 0.8 + speedMs * 0.08);
      this.chugTimer = Math.max(4, Math.round(16 - speedMs * 2.4));
    }

    this.lastSpeedMs = speedMs;
    this.last = { x: now.x, y: now.y, z: now.z, yaw: this.yaw };

    this.ambientParticles();
    this.updateVisuals(forwardSpeed, dYaw);
    if (this.age % 20 === 0) {
      this.save();
    }
    if (wear > 0) {
      this.applyWear(wear);
    }
  }

  /** Aplica W/A/S/D do piloto. @returns true se subiu um degrau neste tick. */
  private drive(movedLastTick: number): boolean {
    const e = this.entity;
    const pilot = this.pilot();
    let input: DriveInput = NO_INPUT;
    if (pilot) {
      try {
        input = inputFromVector(pilot.inputInfo.getMovementVector());
      } catch {
        input = NO_INPUT;
      }
    }

    // medido: se mal andou com velocidade pedida, está batendo em algo
    let stepped = false;
    if (Math.abs(this.speed) > 0.02 && movedLastTick < Math.abs(this.speed) * 0.3) {
      this.blockedTicks++;
    } else {
      this.blockedTicks = 0;
    }
    if (this.blockedTicks >= 2) {
      if (this.tryStepUp()) {
        stepped = true;
      } else {
        this.speed *= 0.2;
      }
      this.blockedTicks = 0;
    }

    const ground = this.blockAt({ x: e.location.x, y: e.location.y - 0.2, z: e.location.z })?.typeId ?? "";
    const terrain = TERRAIN_BOG.has(ground) ? 0.5 : TERRAIN_LOOSE.has(ground) ? 0.7 : TERRAIN_SOFT.has(ground) ? 0.9 : 1;
    const condition = this.integrity < C.MAX_INTEGRITY * C.CRITICAL_INTEGRITY_FRACTION ? C.CRITICAL_SPEED_FACTOR : 1;
    const water = e.isInWater ? 0.5 : 1;
    const result = step(this.speed, input, {
      power: C.powerFactor(this.boiler.pressureBar),
      speedFactor: terrain * condition * water,
      slippery: TERRAIN_SLIPPERY.has(ground),
    });
    this.speed = result.speed;
    if (result.deltaYaw !== 0) {
      e.setRotation({ x: 0, y: this.yaw + result.deltaYaw });
    }

    // a física do jogo move com colisão; a gravidade continua valendo (mantém a velocidade vertical)
    const f = forwardVector(this.yaw);
    const vy = e.getVelocity().y;
    e.clearVelocity();
    e.applyImpulse({ x: f.x * this.speed, y: e.isInWater ? Math.max(vy, -0.06) : vy, z: f.z * this.speed });
    return stepped;
  }

  /** As esteiras sobem degraus de 1 bloco (A2): se a frente bloqueou e há espaço em cima, sobe. */
  private tryStepUp(): boolean {
    const e = this.entity;
    const base = e.location;
    const floorY = Math.floor(base.y + 0.01);
    const dir = this.speed >= 0 ? 1 : -1;
    const f = forwardVector(this.yaw);
    const l = leftVector(this.yaw);
    let bump = false;
    for (const side of [-1, 0, 1]) {
      const x = base.x + f.x * dir * 1.9 + l.x * side;
      const z = base.z + f.z * dir * 1.9 + l.z * side;
      if (this.isSolid({ x, y: floorY, z })) {
        bump = true;
      }
      if (this.isSolid({ x, y: floorY + 1, z }) || this.isSolid({ x, y: floorY + 2, z })) {
        return false;
      }
    }
    if (!bump) {
      return false;
    }
    // espaço acima do casco inteiro para subir 1 bloco
    for (const a of [-1, 0, 1]) {
      for (const b of [-1, 0, 1]) {
        if (this.isSolid({ x: base.x + a, y: floorY + 2, z: base.z + b })) {
          return false;
        }
      }
    }
    e.teleport({ x: base.x + f.x * dir * 0.25, y: floorY + 1.01, z: base.z + f.z * dir * 0.25 }, { keepVelocity: true });
    return true;
  }

  private updateFall(y: number): void {
    const e = this.entity;
    if (!e.isOnGround && e.getVelocity().y < -0.1 && !e.isInWater) {
      this.fallStartY ??= y;
      this.fallStartY = Math.max(this.fallStartY, y);
    } else if (this.fallStartY !== undefined) {
      const height = this.fallStartY - y;
      this.fallStartY = undefined;
      if (height > C.WEAR_FALL_MIN_BLOCKS && e.isOnGround) {
        this.applyWear((height - C.WEAR_FALL_MIN_BLOCKS) * C.WEAR_FALL_FACTOR);
        this.playSound("random.anvil_land", 0.8, 0.5);
      }
    }
  }

  /**
   * Plataforma (A2): quem está em pé no teto anda e gira junto. O Bedrock não carrega jogadores
   * em entidades que se movem, então o script empurra cada um pelo mesmo deslocamento do casco.
   */
  private carryEntitiesOnTop(dx: number, dy: number, dz: number, dYaw: number): void {
    if (!this.last || (Math.abs(dx) + Math.abs(dz) < 1e-4 && Math.abs(dYaw) < 1e-3)) {
      return;
    }
    const prev = this.last;
    const top = prev.y + ROOF_HEIGHT;
    const riders = new Set(this.riders().map((r) => r.id));
    for (const player of this.dimension.getPlayers({ location: { x: prev.x, y: top, z: prev.z }, maxDistance: 3 })) {
      const p = player.location;
      if (riders.has(player.id) || Math.abs(p.y - top) > 0.35 || Math.abs(p.x - prev.x) > HALF_WIDTH + 0.3 || Math.abs(p.z - prev.z) > HALF_WIDTH + 0.3) {
        continue;
      }
      const ox = p.x - prev.x;
      const oz = p.z - prev.z;
      const r = (-dYaw * Math.PI) / 180;
      const tx = ox * Math.cos(r) + oz * Math.sin(r);
      const tz = -ox * Math.sin(r) + oz * Math.cos(r);
      try {
        player.applyKnockback({ x: tx - ox + dx, z: tz - oz + dz }, Math.max(0, dy));
      } catch {
        // jogador saindo do mundo
      }
    }
  }

  // =====================================================================================
  // Comandos
  // =====================================================================================

  igniteFire(player?: Player): boolean {
    if (this.boiler.fireLit) {
      if (player) {
        notify(player, tr("vapor_trilhos.message.already_lit"));
      }
      return false;
    }
    if (!ignite(this.boiler, () => this.takeFuel())) {
      if (player) {
        notify(player, tr("vapor_trilhos.message.no_fuel"));
      }
      return false;
    }
    this.playSound("fire.ignite", 1, 1);
    this.save();
    return true;
  }

  toggleFire(player: Player): void {
    if (this.boiler.fireLit) {
      extinguish(this.boiler);
      this.playSound("random.fizz", 0.6, 1.2);
      this.save();
    } else {
      this.igniteFire(player);
    }
  }

  cycleDamper(player: Player): void {
    this.boiler.damper = nextDamper(this.boiler.damper);
    this.playSound("random.click", 0.6, 0.8 + this.boiler.damper.id * 0.2);
    notify(player, tr("vapor_trilhos.message.damper", tr(`vapor_trilhos.damper.${this.boiler.damper.key}`)));
  }

  manualVent(player: Player): void {
    if (manualVent(this.boiler)) {
      this.steamBurst(C.MANUAL_VENT_RADIUS, C.MANUAL_VENT_BLINDNESS_TICKS, false);
    } else {
      notify(player, tr("vapor_trilhos.message.vent_not_ready"));
    }
  }

  blowWhistle(): void {
    if (this.whistleCooldown > 0) {
      return;
    }
    this.whistleCooldown = this.whistle.cooldown;
    const loc = this.modelPoint(VENT_PX);
    this.dimension.playSound(this.whistle.sound, loc, { volume: 4, pitch: 1 });
    this.dimension.spawnParticle(Particles.steamPuff, loc);
  }

  selectWhistle(player: Player, whistle: Whistle): void {
    this.whistle = whistle;
    this.entity.setDynamicProperty(Dyn.whistle, whistle.id);
    notify(player, tr("vapor_trilhos.message.whistle_selected", tr(`vapor_trilhos.whistle.${whistle.key}`)));
  }

  /** Reparo com o item na mão (A5). @returns true se gastou o item. */
  repair(player: Player, amount: number, limitFraction: number): boolean {
    if (this.repairCooldown > 0) {
      return false;
    }
    if (this.boiler.isDryOverheating()) {
      notify(player, tr("vapor_trilhos.message.too_hot_to_repair"));
      return false;
    }
    const limit = C.MAX_INTEGRITY * limitFraction;
    if (this.integrity >= limit) {
      notify(player, limitFraction < 1
        ? tr("vapor_trilhos.message.needs_metal", `${Math.round(limitFraction * 100)}%`)
        : tr("vapor_trilhos.message.fully_repaired"));
      return false;
    }
    this.integrity = Math.min(limit, this.integrity + amount);
    this.repairCooldown = C.REPAIR_COOLDOWN_TICKS;
    this.playSound("random.anvil_use", 0.8, 0.9 + Math.random() * 0.2);
    notify(player, tr("vapor_trilhos.message.integrity", `${Math.round((this.integrity / C.MAX_INTEGRITY) * 100)}%`));
    this.save();
    return true;
  }

  /** Choque térmico ao pôr água numa caldeira seca e quente (A3.4). */
  thermalShock(): void {
    this.steamBurst(C.SHOCK_RADIUS, C.SHOCK_BLINDNESS_TICKS, true);
    this.playSound("random.explode", 0.8, 1.4);
    this.applyWear(C.WEAR_THERMAL_SHOCK);
  }

  // =====================================================================================
  // Efeitos
  // =====================================================================================

  /** Nuvem de vapor que cega quem está perto (A3.4). */
  steamBurst(radius: number, blindTicks: number, includeOccupants: boolean): void {
    const vent = this.modelPoint(VENT_PX);
    const center = this.entity.location;
    this.dimension.spawnParticle(Particles.steamBurst, vent);
    this.dimension.spawnParticle(Particles.steamBurst, { x: center.x, y: center.y + 1.2, z: center.z });
    this.playSound("random.fizz", 1.2, 0.9 + Math.random() * 0.2);
    this.ventingTicks = 40;
    const riders = new Set(this.riders().map((r) => r.id));
    for (const player of this.dimension.getPlayers({ location: center, maxDistance: radius + 1.5 })) {
      if (!includeOccupants && riders.has(player.id)) {
        continue;
      }
      player.addEffect("blindness", blindTicks, { showParticles: false });
    }
  }

  /** Fumaça da chaminé e vapor contínuo: quem está a bordo não vê (tapava a câmera, A2.0). */
  private ambientParticles(): void {
    if (this.age % 4 !== 0) {
      return;
    }
    const b = this.boiler;
    const redZone = b.pressureBar >= C.RED_ZONE_BAR;
    const dry = b.isDryOverheating();
    const critical = this.integrity < C.MAX_INTEGRITY * C.CRITICAL_INTEGRITY_FRACTION;
    if (!b.fireLit && !redZone && !dry && !critical) {
      return;
    }
    const riders = new Set(this.riders().map((r) => r.id));
    const chimney = this.modelPoint(CHIMNEY_PX);
    const vent = this.modelPoint(VENT_PX);
    const center = this.entity.location;
    for (const player of this.dimension.getPlayers({ location: center, maxDistance: 64 })) {
      if (riders.has(player.id)) {
        continue;
      }
      try {
        if (b.fireLit) {
          player.spawnParticle(dry ? Particles.blackSmoke : Particles.chimneySmoke, chimney);
        }
        if (redZone) {
          player.spawnParticle(Particles.steamPuff, vent);
        }
        if (critical) {
          player.spawnParticle(Particles.blackSmoke, { x: center.x, y: center.y + 1.2, z: center.z });
        }
      } catch {
        // jogador em outra dimensão no meio do tick
      }
    }
  }

  /** Propriedades lidas pelas animações do pacote de recursos (esteiras, motor, válvula). */
  private updateVisuals(forwardSpeed: number, dYaw: number): void {
    const left = forwardSpeed + dYaw * 0.05;
    const right = forwardSpeed - dYaw * 0.05;
    this.setProp(Props.trackLeft, left > 0.005 ? 1 : left < -0.005 ? -1 : 0);
    this.setProp(Props.trackRight, right > 0.005 ? 1 : right < -0.005 ? -1 : 0);
    this.setProp(Props.working, this.boiler.fireLit && this.boiler.pressureBar >= C.MIN_DRIVE_BAR);
    this.setProp(Props.venting, this.ventingTicks > 0);
  }

  private setProp(id: string, value: number | boolean): void {
    if (this.props.get(id) !== value) {
      this.props.set(id, value);
      this.entity.setProperty(id, value);
    }
  }

  playSound(sound: string, volume: number, pitch: number): void {
    const l = this.entity.location;
    this.dimension.playSound(sound, { x: l.x, y: l.y + 1, z: l.z }, { volume, pitch });
  }

  // =====================================================================================
  // Desgaste, destruição e dados
  // =====================================================================================

  applyWear(amount: number): void {
    this.integrity = Math.max(0, this.integrity - amount);
    if (this.integrity <= 0) {
      this.breakApart();
    }
  }

  /** Destruição (A5): sucata e combustível caem no chão; cabine e caldeira se perdem. */
  breakApart(): void {
    const e = this.entity;
    if (!e.isValid) {
      return;
    }
    const at = { x: e.location.x, y: e.location.y + 1, z: e.location.z };
    const dim = this.dimension;
    dim.spawnParticle("minecraft:huge_explosion_emitter", at);
    dim.playSound("random.explode", at, { volume: 1, pitch: 0.8 });
    if (world.gameRules.doEntityDrops) {
      dim.spawnItem(new ItemStack(Items.reinforcedTrack, 2), at);
      dim.spawnItem(new ItemStack("minecraft:iron_nugget", 6), at);
      dim.spawnItem(new ItemStack("minecraft:oak_planks", 4), at);
      this.dropFuel(at);
    }
    (e.getComponent("minecraft:rideable") as EntityRideableComponent | undefined)?.ejectRiders();
    Landship.forget(e.id);
    e.remove();
  }

  dropFuel(at: Vector3): void {
    const fuel = this.fuel();
    if (!fuel) {
      return;
    }
    for (let i = 0; i < fuel.size; i++) {
      const stack = fuel.getItem(i);
      if (stack) {
        fuel.setItem(i, undefined);
        this.dimension.spawnItem(stack, at);
      }
    }
  }

  /** Dano vindo do jogo (ataques, explosões ×2): vira desgaste; a "vida" da entidade é só um amortecedor. */
  onHurt(damage: number, explosion: boolean): void {
    const health = this.entity.getComponent("minecraft:health") as EntityHealthComponent | undefined;
    health?.resetToMaxValue();
    this.applyWear(explosion ? damage * 2 : damage);
  }

  save(): void {
    const e = this.entity;
    if (!e.isValid) {
      return;
    }
    e.setDynamicProperty(Dyn.boiler, this.boiler.save());
    e.setDynamicProperty(Dyn.integrity, Math.round(this.integrity * 100) / 100);
  }

  private takeFuel(): number {
    const fuel = this.fuel();
    if (!fuel) {
      return 0;
    }
    const at = this.entity.location;
    return takeFuel(fuel, (rest) => this.dimension.spawnItem(rest, at));
  }

  private blockAt(location: Vector3): Block | undefined {
    try {
      return this.dimension.getBlock(location);
    } catch {
      return undefined; // fora do mundo carregado
    }
  }

  private isSolid(location: Vector3): boolean {
    const block = this.blockAt(location);
    if (!block || block.isAir || block.isLiquid) {
      return false;
    }
    return !PASSABLE.test(block.typeId);
  }

  /** Abafador: usado pelo painel. */
  get damper() {
    return this.boiler.damper;
  }

  /** Para a HUD e o painel. */
  get canDrive(): boolean {
    return this.boiler.pressureBar >= C.MIN_DRIVE_BAR;
  }
}
