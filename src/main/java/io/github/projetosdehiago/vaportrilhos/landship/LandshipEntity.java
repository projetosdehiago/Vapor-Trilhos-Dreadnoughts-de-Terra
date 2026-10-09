package io.github.projetosdehiago.vaportrilhos.landship;

import static io.github.projetosdehiago.vaportrilhos.boiler.BalanceConstants.*;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.util.GeckoLibUtil;
import io.github.projetosdehiago.vaportrilhos.boiler.BalanceConstants;
import io.github.projetosdehiago.vaportrilhos.boiler.BoilerSimulation;
import io.github.projetosdehiago.vaportrilhos.boiler.BoilerState;
import io.github.projetosdehiago.vaportrilhos.boiler.Damper;
import io.github.projetosdehiago.vaportrilhos.network.LandshipActionPayload;
import io.github.projetosdehiago.vaportrilhos.registry.ModDataComponents;
import io.github.projetosdehiago.vaportrilhos.registry.ModItems;
import io.github.projetosdehiago.vaportrilhos.registry.ModSounds;
import io.github.projetosdehiago.vaportrilhos.registry.ModTags;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * O landship: uma entidade única com caldeira, esteiras e (na Fase 2) módulos.
 *
 * <p>Movimento como o barco vanilla: quem simula é o lado com autoridade (o cliente do piloto, ou o
 * servidor sem piloto). A caldeira, o desgaste e todos os efeitos rodam só no servidor; os
 * medidores chegam aos clientes por {@link SynchedEntityData}.
 */
public class LandshipEntity extends VehicleEntity implements HasCustomInventoryScreen, ExtendedMenuProvider<Integer>, GeoEntity {
	private static final EntityDataAccessor<Float> DATA_WATER = SynchedEntityData.defineId(LandshipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_TEMPERATURE = SynchedEntityData.defineId(LandshipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_PRESSURE = SynchedEntityData.defineId(LandshipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_INTEGRITY = SynchedEntityData.defineId(LandshipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_BURN = SynchedEntityData.defineId(LandshipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(LandshipEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_DAMPER = SynchedEntityData.defineId(LandshipEntity.class, EntityDataSerializers.INT);

	private static final int FLAG_FIRE = 1;
	private static final int FLAG_DRY = 2;

	public static final int MAX_PASSENGERS = 3;
	public static final int FUEL_SLOTS = 3;
	/** Meia distância entre as esteiras, em blocos (centro da esteira a 19,5 px do eixo). */
	public static final float TRACK_HALF_SPAN = 19.5f / 16f;
	private static final float SEAT_HEIGHT = 1.15f;
	/** Folga vertical para considerar uma entidade "em pé em cima" do landship. */
	private static final double PLATFORM_TOLERANCE = 0.1;

	// animações (nomes iguais aos do landship.animation.json)
	private static final RawAnimation LEFT_FORWARD = RawAnimation.begin().thenLoop("animation.landship.track_left.forward");
	private static final RawAnimation LEFT_REVERSE = RawAnimation.begin().thenLoop("animation.landship.track_left.reverse");
	private static final RawAnimation RIGHT_FORWARD = RawAnimation.begin().thenLoop("animation.landship.track_right.forward");
	private static final RawAnimation RIGHT_REVERSE = RawAnimation.begin().thenLoop("animation.landship.track_right.reverse");
	private static final RawAnimation ENGINE_IDLE = RawAnimation.begin().thenLoop("animation.landship.engine.idle");
	private static final RawAnimation ENGINE_WORKING = RawAnimation.begin().thenLoop("animation.landship.engine.working");
	private static final RawAnimation VENT = RawAnimation.begin().thenPlay("animation.landship.boiler.vent");
	/** Velocidade da esteira (blocos/tick) que corresponde à animação em 1×. */
	private static final float TRACK_ANIM_UNIT = 1f / TICKS_PER_SECOND;

	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

	// --- só no servidor
	private final BoilerState boiler = new BoilerState();
	private final BoilerSimulation.Load load = new BoilerSimulation.Load();
	private final BoilerSimulation.Result result = new BoilerSimulation.Result();
	private final SimpleContainer fuel = new SimpleContainer(FUEL_SLOTS);
	private float integrity = MAX_INTEGRITY;
	private double lastServerX;
	private double lastServerY;
	private double lastServerZ;
	private float lastServerYaw;
	private float lastSpeedMs;
	private boolean hasLastServerPos;
	private int repairCooldown;
	private int whistleCooldown;
	private int chugTimer;

	// --- lado com autoridade de movimento
	private float speed;
	private float deltaRotation;
	private boolean inputForward;
	private boolean inputBackward;
	private boolean inputLeft;
	private boolean inputRight;

	// --- plataforma: posição no fim do tick anterior (em cada lado)
	private double lastTickX;
	private double lastTickY;
	private double lastTickZ;
	private float lastTickYaw;
	private boolean hasLastTick;

	// --- cliente (visual)
	/** Preenchido pelo cliente: o jogador local está a bordo deste landship? */
	private static Predicate<LandshipEntity> localPlayerAboard = landship -> false;
	private float trackLeftSpeed;
	private float trackRightSpeed;
	private float visualTurnRate;
	private float bodyPitch;
	private float bodyRoll;

	public LandshipEntity(EntityType<? extends LandshipEntity> type, Level level) {
		super(type, level);
		this.blocksBuilding = true;
	}

	// =====================================================================================
	// Dados sincronizados
	// =====================================================================================

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_WATER, 0f);
		builder.define(DATA_TEMPERATURE, AMBIENT_C);
		builder.define(DATA_PRESSURE, 0f);
		builder.define(DATA_INTEGRITY, MAX_INTEGRITY);
		builder.define(DATA_BURN, 0f);
		builder.define(DATA_FLAGS, (byte) 0);
		builder.define(DATA_DAMPER, Damper.NORMAL.ordinal());
	}

	public float getWaterMb() {
		return entityData.get(DATA_WATER);
	}

	public float getTemperature() {
		return entityData.get(DATA_TEMPERATURE);
	}

	public float getPressure() {
		return entityData.get(DATA_PRESSURE);
	}

	public float getIntegrity() {
		return entityData.get(DATA_INTEGRITY);
	}

	public float getBurnFraction() {
		return entityData.get(DATA_BURN);
	}

	public boolean isFireLit() {
		return (entityData.get(DATA_FLAGS) & FLAG_FIRE) != 0;
	}

	public boolean isDryOverheating() {
		return (entityData.get(DATA_FLAGS) & FLAG_DRY) != 0;
	}

	public Damper getDamper() {
		return Damper.byId(entityData.get(DATA_DAMPER));
	}

	public SimpleContainer getFuelContainer() {
		return fuel;
	}

	private void syncBoiler() {
		// valores arredondados: evitam reenviar o estado a cada tick por mudanças invisíveis
		entityData.set(DATA_WATER, (float) Math.round(boiler.waterMb));
		entityData.set(DATA_TEMPERATURE, Math.round(boiler.temperatureC * 10f) / 10f);
		entityData.set(DATA_PRESSURE, Math.round(boiler.pressureBar * 100f) / 100f);
		entityData.set(DATA_BURN, Math.round(boiler.burnFraction() * 100f) / 100f);
		entityData.set(DATA_INTEGRITY, Math.round(integrity * 10f) / 10f);
		entityData.set(DATA_DAMPER, boiler.damper.ordinal());
		byte flags = 0;
		if (boiler.fireLit) {
			flags |= FLAG_FIRE;
		}
		if (boiler.isDryOverheating()) {
			flags |= FLAG_DRY;
		}
		entityData.set(DATA_FLAGS, flags);
	}

	// =====================================================================================
	// Física básica, colisão e passageiros
	// =====================================================================================

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return true;
	}

	@Override
	public boolean canCollideWith(Entity entity) {
		// quem está em cima é carregado junto (carryEntitiesOnTop) e não impede a subida de degraus
		if (entity.getY() >= getBoundingBox().maxY - PLATFORM_TOLERANCE) {
			return false;
		}
		return (entity.canBeCollidedWith(this) || entity.isPushable()) && !isPassengerOfSameVehicle(entity);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public float maxUpStep() {
		return 1.0f;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.08;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < MAX_PASSENGERS;
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof Player player ? player : null;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		// x = esquerda, z = frente (antes de girar pela direção do veículo)
		int index = getPassengers().indexOf(passenger);
		Vec3 seat = switch (index) {
			case 0 -> new Vec3(0, SEAT_HEIGHT, 11f / 16f);
			case 1 -> new Vec3(4f / 16f, SEAT_HEIGHT, 1f / 16f);
			default -> new Vec3(-4f / 16f, SEAT_HEIGHT, 1f / 16f);
		};
		return seat.yRot(-getYRot() * Mth.DEG_TO_RAD);
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		if (passenger.isLocalInstanceAuthoritative()) {
			passenger.setYRot(passenger.getYRot() + deltaRotation);
			passenger.setYHeadRot(passenger.getYHeadRot() + deltaRotation);
		}
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		// desce pela lateral do lado em que o passageiro está sentado
		Vec3 side = getCollisionHorizontalEscapeVector(getBbWidth() * Mth.SQRT_OF_TWO, passenger.getBbWidth(), passenger.getYRot());
		Vec3 target = new Vec3(getX() + side.x, getY(), getZ() + side.z);
		BlockPos pos = BlockPos.containing(target.x, getBoundingBox().maxY, target.z);
		for (int dy = 0; dy >= -3; dy--) {
			BlockPos p = pos.above(dy);
			double floor = level().getBlockFloorHeight(p);
			if (net.minecraft.world.entity.vehicle.DismountHelper.isBlockFloorValid(floor)) {
				Vec3 spot = new Vec3(target.x, p.getY() + floor, target.z);
				if (net.minecraft.world.entity.vehicle.DismountHelper.canDismountTo(level(), spot, passenger, passenger.getPose())) {
					return spot;
				}
			}
		}
		return super.getDismountLocationForPassenger(passenger);
	}

	// =====================================================================================
	// Tick
	// =====================================================================================

	@Override
	public void tick() {
		if (getHurtTime() > 0) {
			setHurtTime(getHurtTime() - 1);
		}
		// xo/zo/yRotO são gravados antes da interpolação: assim o delta vale também para quem só
		// vê o landship de longe (antes, as esteiras ficavam paradas para os outros jogadores)
		double prevX = xo;
		double prevZ = zo;
		float prevYaw = yRotO;

		super.tick();

		if (isLocalInstanceAuthoritative()) {
			if (!(getControllingPassenger() instanceof Player)) {
				setInput(false, false, false, false);
			}
			drive();
		} else {
			setDeltaMovement(Vec3.ZERO);
			deltaRotation = 0f;
		}

		applyEffectsFromBlocks();

		if (level() instanceof ServerLevel serverLevel) {
			serverTick(serverLevel);
		} else {
			clientVisualTick(prevX, prevZ, prevYaw);
		}
		if (!isRemoved()) {
			carryEntitiesOnTop();
		}
	}

	/**
	 * Plataforma: quem está em pé em cima do landship anda e gira junto com ele.
	 *
	 * <p>Cada lado move só o que controla ({@code isLocalInstanceAuthoritative}): o cliente move o
	 * próprio jogador, o servidor move mobs e itens. O delta é medido do fim do tick anterior até
	 * agora, então inclui o movimento do piloto, a interpolação e os pacotes do servidor.
	 */
	private void carryEntitiesOnTop() {
		double dx = getX() - lastTickX;
		double dy = getY() - lastTickY;
		double dz = getZ() - lastTickZ;
		float dYaw = Mth.wrapDegrees(getYRot() - lastTickYaw);
		boolean moved = hasLastTick && (dx * dx + dy * dy + dz * dz > 1.0E-8 || Math.abs(dYaw) > 1.0E-3f)
				&& dx * dx + dy * dy + dz * dz < 4.0;
		if (moved) {
			AABB before = getBoundingBox().move(-dx, -dy, -dz);
			double top = before.maxY;
			AABB deck = new AABB(before.minX, top - PLATFORM_TOLERANCE, before.minZ, before.maxX, top + PLATFORM_TOLERANCE, before.maxZ);
			for (Entity entity : level().getEntities(this, deck, this::canCarry)) {
				if (entity.getY() < top - PLATFORM_TOLERANCE || entity.getY() > top + PLATFORM_TOLERANCE) {
					continue;
				}
				Vec3 offset = new Vec3(entity.getX() - lastTickX, 0, entity.getZ() - lastTickZ);
				Vec3 turned = offset.yRot(-dYaw * Mth.DEG_TO_RAD);
				entity.move(MoverType.SELF, new Vec3(turned.x - offset.x + dx, dy, turned.z - offset.z + dz));
				if (dYaw != 0f) {
					entity.setYRot(entity.getYRot() + dYaw);
					entity.setYHeadRot(entity.getYHeadRot() + dYaw);
				}
			}
		}
		lastTickX = getX();
		lastTickY = getY();
		lastTickZ = getZ();
		lastTickYaw = getYRot();
		hasLastTick = true;
	}

	private boolean canCarry(Entity entity) {
		return entity.isLocalInstanceAuthoritative()
				&& !entity.isPassenger()
				&& !entity.noPhysics
				&& !entity.isSpectator()
				&& !(entity instanceof LandshipEntity);
	}

	/** Chamado pelo cliente do piloto antes de cada tick. */
	public void setInput(boolean forward, boolean backward, boolean left, boolean right) {
		this.inputForward = forward;
		this.inputBackward = backward;
		this.inputLeft = left;
		this.inputRight = right;
	}

	private void drive() {
		float power = powerFactor(getPressure());
		float terrain = terrainFactor();
		float condition = getIntegrity() < MAX_INTEGRITY * CRITICAL_INTEGRITY_FRACTION ? CRITICAL_SPEED_FACTOR : 1f;
		float water = isInWater() ? 0.5f : 1f;
		float factor = power * terrain * condition * water;
		float maxForward = MAX_SPEED_M_S / TICKS_PER_SECOND * factor;
		float maxReverse = MAX_REVERSE_M_S / TICKS_PER_SECOND * factor;
		float grip = isOnSlipperyGround() ? 0.3f : 1f;
		float accel = ACCEL_M_S2 / (TICKS_PER_SECOND * TICKS_PER_SECOND) * grip;
		float brake = BRAKE_M_S2 / (TICKS_PER_SECOND * TICKS_PER_SECOND) * grip;
		float coast = 1.5f / (TICKS_PER_SECOND * TICKS_PER_SECOND);

		if (inputForward && !inputBackward && power > 0f) {
			speed = speed < maxForward ? Math.min(maxForward, speed + (speed < 0 ? brake : accel)) : Math.max(maxForward, speed - coast);
		} else if (inputBackward && !inputForward && power > 0f) {
			speed = speed > 0f ? Math.max(0f, speed - brake) : Math.max(-maxReverse, speed - accel);
		} else {
			speed = speed > 0f ? Math.max(0f, speed - coast) : Math.min(0f, speed + coast);
		}

		deltaRotation = 0f;
		if (inputLeft != inputRight && power > 0f) {
			float fraction = Math.min(1f, Math.abs(speed) / (MAX_SPEED_M_S / TICKS_PER_SECOND));
			float degPerTick = Mth.lerp(fraction, TURN_DEG_S_STOPPED, TURN_DEG_S_FULL) / TICKS_PER_SECOND * Math.max(0.35f, power);
			deltaRotation = inputRight ? degPerTick : -degPerTick;
			setYRot(getYRot() + deltaRotation);
		}

		Vec3 forward = forwardVector();
		Vec3 motion = getDeltaMovement();
		double vy = (motion.y - getGravity()) * 0.98;
		if (isInWater()) {
			vy = Math.max(vy, -0.06);
		}
		setDeltaMovement(forward.x * speed, vy, forward.z * speed);
		move(MoverType.SELF, getDeltaMovement());
		if (horizontalCollision) {
			speed *= 0.2f;
		}
	}

	public Vec3 forwardVector() {
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
	}

	/** Vetor para a esquerda do veículo. */
	public Vec3 leftVector() {
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
	}

	/** Converte um ponto do modelo (px Bedrock: x = esquerda, y = cima, z = trás) para o mundo. */
	public Vec3 modelPointToWorld(float leftPx, float upPx, float backPx) {
		Vec3 f = forwardVector();
		Vec3 l = leftVector();
		return new Vec3(
				getX() + (l.x * leftPx - f.x * backPx) / 16.0,
				getY() + upPx / 16.0,
				getZ() + (l.z * leftPx - f.z * backPx) / 16.0);
	}

	private BlockState groundState() {
		return level().getBlockState(BlockPos.containing(getX(), getY() - 0.2, getZ()));
	}

	private float terrainFactor() {
		BlockState ground = groundState();
		if (ground.is(ModTags.TERRAIN_BOG)) {
			return 0.5f;
		}
		if (ground.is(ModTags.TERRAIN_LOOSE)) {
			return 0.7f;
		}
		if (ground.is(ModTags.TERRAIN_SOFT)) {
			return 0.9f;
		}
		return 1f;
	}

	private boolean isOnSlipperyGround() {
		return groundState().is(ModTags.TERRAIN_SLIPPERY);
	}

	// =====================================================================================
	// Servidor: caldeira, desgaste e efeitos
	// =====================================================================================

	private void serverTick(ServerLevel level) {
		if (repairCooldown > 0) {
			repairCooldown--;
		}
		if (whistleCooldown > 0) {
			whistleCooldown--;
		}

		// movimento medido (vale para piloto no cliente e para o servidor)
		double dx = getX() - lastServerX;
		double dy = getY() - lastServerY;
		double dz = getZ() - lastServerZ;
		float yawChange = Math.abs(Mth.wrapDegrees(getYRot() - lastServerYaw));
		double horizontal = hasLastServerPos ? Math.sqrt(dx * dx + dz * dz) : 0;
		if (horizontal > 2.0) {
			horizontal = 0; // teleporte, não movimento
		}
		float speedMs = (float) horizontal * TICKS_PER_SECOND;
		load.reset();
		load.speedFraction = Math.min(1f, speedMs / MAX_SPEED_M_S);
		load.pivoting = yawChange > 0.05f && speedMs < 0.3f;
		load.stepUps = hasLastServerPos && dy > 0.45 && dy < 1.2 && horizontal > 0.01 ? 1 : 0;
		load.integrityFraction = integrity / MAX_INTEGRITY;

		float wear = (float) horizontal * WEAR_PER_BLOCK;
		if (hasLastServerPos && lastSpeedMs >= WEAR_COLLISION_MIN_SPEED && lastSpeedMs - speedMs > 1.5f) {
			wear += (lastSpeedMs - 2f) * WEAR_COLLISION_FACTOR;
			playSound(level, net.minecraft.sounds.SoundEvents.ANVIL_LAND, 0.6f, 0.6f);
		}
		if (isInLava()) {
			wear += WEAR_LAVA_PER_S / TICKS_PER_SECOND;
		}

		// caldeira submersa apaga o fogo
		if (boiler.fireLit && level.getFluidState(BlockPos.containing(getX(), getY() + 1.5, getZ())).is(FluidTags.WATER)) {
			BoilerSimulation.extinguish(boiler);
			playSound(level, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, 1f, 1f);
		}

		BoilerSimulation.tick(boiler, load, () -> takeFuel(level), result);
		wear += result.wear;
		if (result.safetyVented) {
			steamBurst(level, VENT_RADIUS, VENT_BLINDNESS_TICKS, true, ModSounds.BOILER_VENT);
			triggerAnim("boiler", "vent");
		}
		if (result.dryPulse) {
			steamBurst(level, VENT_RADIUS, VENT_BLINDNESS_TICKS, true, ModSounds.BOILER_HISS);
		}

		if (boiler.fireLit && speedMs > 0.2f && --chugTimer <= 0) {
			playSound(level, ModSounds.ENGINE_CHUG, 0.5f, 0.8f + speedMs * 0.08f);
			chugTimer = Math.max(4, Math.round(16 - speedMs * 2.4f));
		}

		lastSpeedMs = speedMs;
		lastServerX = getX();
		lastServerY = getY();
		lastServerZ = getZ();
		lastServerYaw = getYRot();
		hasLastServerPos = true;

		if (wear > 0f) {
			applyWear(level, wear, null);
		}
		if (!isRemoved()) {
			syncBoiler();
		}
	}

	private int takeFuel(ServerLevel level) {
		for (int i = 0; i < fuel.getContainerSize(); i++) {
			ItemStack stack = fuel.getItem(i);
			int ticks = FuelHelper.burnTicks(level, stack, position());
			if (ticks > 0) {
				ItemStackTemplate remainder = stack.getItem().getCraftingRemainder();
				stack.shrink(1);
				if (remainder != null) {
					if (stack.isEmpty()) {
						fuel.setItem(i, remainder.create());
					} else {
						spawnAtLocation(level, remainder.create());
					}
				}
				fuel.setChanged();
				return ticks;
			}
		}
		return 0;
	}

	/**
	 * Nuvem de vapor que cega jogadores próximos (design.md A3.4).
	 *
	 * @param includeOccupants se falso, quem está no veículo não é afetado (válvula manual)
	 */
	private void steamBurst(ServerLevel level, float radius, int blindTicks, boolean includeOccupants, SoundEvent sound) {
		Vec3 vent = modelPointToWorld(3.5f, 35f, 12.5f);
		level.sendParticles(ParticleTypes.CLOUD, vent.x, vent.y, vent.z, 40, 0.6, 0.4, 0.6, 0.08);
		level.sendParticles(ParticleTypes.POOF, vent.x, vent.y, vent.z, 20, 1.2, 0.6, 1.2, 0.05);
		level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.2, getZ(), 30, radius * 0.4, 0.5, radius * 0.4, 0.02);
		playSound(level, sound, 1.2f, 0.9f + random.nextFloat() * 0.2f);
		AABB area = getBoundingBox().inflate(radius);
		for (Player player : level.getEntitiesOfClass(Player.class, area, p -> p.distanceToSqr(this) <= radius * radius + 4)) {
			if (!includeOccupants && player.getVehicle() == this) {
				continue;
			}
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blindTicks), this);
		}
	}

	private void playSound(ServerLevel level, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, getX(), getY() + 1, getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
	}

	private void applyWear(ServerLevel level, float amount, @Nullable DamageSource source) {
		integrity = Math.max(0f, integrity - amount);
		if (integrity <= 0f) {
			breakApart(level);
		}
	}

	/** Destruição (design.md A5): sucata, combustível e (Fase 2) módulos caem no chão. */
	private void breakApart(ServerLevel level) {
		if (isRemoved()) {
			return;
		}
		level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 3, 1, 0.5, 1, 0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 40, 1.2, 0.8, 1.2, 0.05);
		playSound(level, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.8f);
		if (level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ENTITY_DROPS)) {
			spawnAtLocation(level, new ItemStack(ModItems.REINFORCED_TRACK, 2));
			spawnAtLocation(level, new ItemStack(Items.IRON_NUGGET, 6));
			spawnAtLocation(level, new ItemStack(Items.OAK_PLANKS, 4));
			Containers.dropContents(level, this, fuel);
		}
		ejectPassengers();
		kill(level);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isRemoved() || isInvulnerableToBase(source)) {
			return false;
		}
		if (source.getEntity() instanceof Player player && player.getAbilities().instabuild) {
			if (player.isShiftKeyDown()) {
				discard();
			}
			return true;
		}
		setHurtDir(-getHurtDir());
		setHurtTime(10);
		markHurt();
		float amount = source.is(DamageTypeTags.IS_EXPLOSION) ? damage * 2f : damage;
		applyWear(level, amount, source);
		return true;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		if (fallDistance > WEAR_FALL_MIN_BLOCKS && level() instanceof ServerLevel serverLevel) {
			applyWear(serverLevel, (float) (fallDistance - WEAR_FALL_MIN_BLOCKS) * WEAR_FALL_FACTOR, damageSource);
		}
		return false;
	}

	@Override
	protected Item getDropItem() {
		return ModItems.LANDSHIP;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(ModItems.LANDSHIP);
	}

	// =====================================================================================
	// Interação
	// =====================================================================================

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		ItemStack stack = player.getItemInHand(hand);

		if (player.isSecondaryUseActive()) {
			if (stack.is(ModItems.BOILERMAKER_WRENCH)) {
				return level().isClientSide() ? InteractionResult.SUCCESS : tryPickUp(player);
			}
			if (!level().isClientSide()) {
				player.openMenu(this);
			}
			return InteractionResult.SUCCESS;
		}

		if (!stack.isEmpty()) {
			InteractionResult itemResult = useItem(player, hand, stack);
			if (itemResult != InteractionResult.PASS) {
				return itemResult;
			}
		}

		if (!level().isClientSide() && !player.startRiding(this)) {
			return InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	private InteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
		// água: qualquer recipiente que a Transfer API conheça (balde, garrafa, recipientes de mods)
		Storage<FluidVariant> fluidStorage = FluidStorage.ITEM.find(stack, ContainerItemContext.forPlayerInteraction(player, hand));
		if (fluidStorage != null && fluidStorage.supportsExtraction()) {
			if (level().isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			return fillWater(player, fluidStorage) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
		}

		if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) {
			if (level().isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			ServerLevel level = (ServerLevel) level();
			if (boiler.fireLit) {
				player.sendOverlayMessage(Component.translatable("message.vapor_trilhos.already_lit"));
				return InteractionResult.FAIL;
			}
			if (!BoilerSimulation.ignite(boiler, () -> takeFuel(level))) {
				player.sendOverlayMessage(Component.translatable("message.vapor_trilhos.no_fuel"));
				return InteractionResult.FAIL;
			}
			if (stack.is(Items.FLINT_AND_STEEL)) {
				stack.hurtAndBreak(1, player, hand);
			} else {
				stack.consume(1, player);
			}
			playSound(level, ModSounds.BOILER_IGNITE, 1f, 1f);
			syncBoiler();
			return InteractionResult.SUCCESS_SERVER;
		}

		RepairMaterials.Repair repair = RepairMaterials.of(stack);
		if (repair != null) {
			if (level().isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			return tryRepair((ServerLevel) level(), player, stack, repair);
		}

		if (FuelHelper.isFuel(stack)) {
			if (level().isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			ItemStack rest = fuel.addItem(stack.copy());
			if (rest.getCount() == stack.getCount()) {
				player.sendOverlayMessage(Component.translatable("message.vapor_trilhos.fuel_full"));
				return InteractionResult.FAIL;
			}
			if (!player.getAbilities().instabuild) {
				stack.setCount(rest.getCount());
			}
			return InteractionResult.SUCCESS_SERVER;
		}

		return InteractionResult.PASS;
	}

	private boolean fillWater(Player player, Storage<FluidVariant> source) {
		float space = BoilerSimulation.waterSpace(boiler);
		long maxDroplets = (long) Math.floor(space / BUCKET_MB * FluidConstants.BUCKET);
		if (maxDroplets <= 0) {
			player.sendOverlayMessage(Component.translatable("message.vapor_trilhos.water_full"));
			return false;
		}
		try (Transaction transaction = Transaction.openOuter()) {
			long extracted = source.extract(FluidVariant.of(Fluids.WATER), maxDroplets, transaction);
			if (extracted <= 0) {
				return false;
			}
			float mb = (float) extracted / FluidConstants.BUCKET * BUCKET_MB;
			ServerLevel level = (ServerLevel) level();
			if (BoilerSimulation.addWater(boiler, mb)) {
				steamBurst(level, SHOCK_RADIUS, SHOCK_BLINDNESS_TICKS, true, ModSounds.BOILER_SHOCK);
				triggerAnim("boiler", "vent");
				applyWear(level, WEAR_THERMAL_SHOCK, null);
			} else {
				playSound(level, ModSounds.BOILER_FILL, 1f, 1f);
			}
			transaction.commit();
			syncBoiler();
			return true;
		}
	}

	private InteractionResult tryRepair(ServerLevel level, Player player, ItemStack stack, RepairMaterials.Repair repair) {
		if (repairCooldown > 0) {
			return InteractionResult.CONSUME;
		}
		if (boiler.isDryOverheating()) {
			player.sendOverlayMessage(Component.translatable("message.vapor_trilhos.too_hot_to_repair"));
			return InteractionResult.FAIL;
		}
		float limit = MAX_INTEGRITY * repair.limitFraction();
		if (integrity >= limit) {
			player.sendOverlayMessage(Component.translatable(repair.limitFraction() < 1f
					? "message.vapor_trilhos.needs_metal" : "message.vapor_trilhos.fully_repaired"));
			return InteractionResult.FAIL;
		}
		integrity = Math.min(limit, integrity + repair.amount());
		stack.consume(1, player);
		repairCooldown = REPAIR_COOLDOWN_TICKS;
		playSound(level, ModSounds.REPAIR, 1f, 0.9f + random.nextFloat() * 0.2f);
		syncBoiler();
		player.sendOverlayMessage(Component.translatable("message.vapor_trilhos.integrity", Math.round(integrity / MAX_INTEGRITY * 100f)));
		return InteractionResult.SUCCESS_SERVER;
	}

	private InteractionResult tryPickUp(Player player) {
		if (isVehicle()) {
			player.sendOverlayMessage(Component.translatable("message.vapor_trilhos.pickup_occupied"));
			return InteractionResult.FAIL;
		}
		if (boiler.fireLit || boiler.temperatureC > 60f) {
			player.sendOverlayMessage(Component.translatable("message.vapor_trilhos.pickup_hot"));
			return InteractionResult.FAIL;
		}
		ServerLevel level = (ServerLevel) level();
		ItemStack item = new ItemStack(ModItems.LANDSHIP);
		item.set(ModDataComponents.LANDSHIP_DATA, new ModDataComponents.LandshipData(integrity, Math.round(boiler.waterMb)));
		if (hasCustomName()) {
			item.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, getCustomName());
		}
		for (int i = 0; i < fuel.getContainerSize(); i++) {
			ItemStack stack = fuel.removeItemNoUpdate(i);
			if (!stack.isEmpty() && !player.getInventory().add(stack)) {
				spawnAtLocation(level, stack);
			}
		}
		if (!player.getInventory().add(item)) {
			spawnAtLocation(level, item);
		}
		playSound(level, ModSounds.REPAIR, 1f, 0.6f);
		discard();
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Restaura o estado guardado no item (ao colocar o landship no mundo). */
	public void loadFromItem(ModDataComponents.LandshipData data) {
		integrity = Mth.clamp(data.integrity(), 1f, MAX_INTEGRITY);
		boiler.waterMb = Mth.clamp(data.waterMb(), 0, WATER_CAPACITY_MB);
		syncBoiler();
	}

	// =====================================================================================
	// Comandos do piloto e do painel
	// =====================================================================================

	public void handleAction(ServerPlayer sender, LandshipActionPayload.Action action) {
		if (getControllingPassenger() != sender) {
			return;
		}
		ServerLevel level = (ServerLevel) level();
		switch (action) {
			case CYCLE_DAMPER -> cycleDamper(sender);
			case MANUAL_VENT -> manualVent(level, sender);
			case WHISTLE -> {
				if (whistleCooldown <= 0) {
					playSound(level, ModSounds.WHISTLE, 3f, 1f);
					whistleCooldown = 20;
				}
			}
		}
	}

	public void cycleDamper(Player player) {
		boiler.damper = boiler.damper.next();
		syncBoiler();
		player.sendOverlayMessage(Component.translatable("message.vapor_trilhos.damper",
				Component.translatable("damper.vapor_trilhos." + boiler.damper.name().toLowerCase(java.util.Locale.ROOT))));
	}

	public void manualVent(ServerLevel level, Player player) {
		if (BoilerSimulation.manualVent(boiler)) {
			steamBurst(level, MANUAL_VENT_RADIUS, MANUAL_VENT_BLINDNESS_TICKS, false, ModSounds.BOILER_VENT);
			triggerAnim("boiler", "vent");
			syncBoiler();
		} else {
			player.sendOverlayMessage(Component.translatable("message.vapor_trilhos.vent_unavailable"));
		}
	}

	/** Botão do painel: acende (se houver combustível) ou apaga a fornalha. */
	public void toggleFire(ServerLevel level, Player player) {
		if (boiler.fireLit) {
			BoilerSimulation.extinguish(boiler);
		} else if (BoilerSimulation.ignite(boiler, () -> takeFuel(level))) {
			playSound(level, ModSounds.BOILER_IGNITE, 1f, 1f);
		} else {
			player.sendOverlayMessage(Component.translatable("message.vapor_trilhos.no_fuel"));
		}
		syncBoiler();
	}

	@Override
	public void openCustomInventoryScreen(Player player) {
		if (!level().isClientSide()) {
			player.openMenu(this);
		}
	}

	@Override
	public Integer getScreenOpeningData(ServerPlayer player) {
		return getId();
	}

	@Override
	public Component getDisplayName() {
		return hasCustomName() ? getCustomName() : getType().getDescription();
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new LandshipMenu(containerId, inventory, this);
	}

	public boolean isUsableBy(Player player) {
		return !isRemoved() && player.distanceToSqr(this) <= 8 * 8;
	}

	// =====================================================================================
	// Persistência
	// =====================================================================================

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putFloat("Integrity", integrity);
		output.putFloat("Water", boiler.waterMb);
		output.putFloat("Temperature", boiler.temperatureC);
		output.putFloat("Pressure", boiler.pressureBar);
		output.putBoolean("FireLit", boiler.fireLit);
		output.putInt("Damper", boiler.damper.ordinal());
		output.putFloat("BurnRemaining", boiler.burnRemaining);
		output.putFloat("BurnTotal", boiler.burnTotal);
		ContainerHelper.saveAllItems(output.child("Fuel"), fuel.getItems());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		integrity = input.getFloatOr("Integrity", MAX_INTEGRITY);
		boiler.waterMb = input.getFloatOr("Water", 0f);
		boiler.temperatureC = input.getFloatOr("Temperature", AMBIENT_C);
		boiler.pressureBar = input.getFloatOr("Pressure", 0f);
		boiler.fireLit = input.getBooleanOr("FireLit", false);
		boiler.damper = Damper.byId(input.getIntOr("Damper", Damper.NORMAL.ordinal()));
		boiler.burnRemaining = input.getFloatOr("BurnRemaining", 0f);
		boiler.burnTotal = input.getFloatOr("BurnTotal", 0f);
		ContainerHelper.loadAllItems(input.childOrEmpty("Fuel"), fuel.getItems());
		syncBoiler();
	}

	// =====================================================================================
	// Cliente: visual
	// =====================================================================================

	private void clientVisualTick(double prevX, double prevZ, float prevYaw) {
		Vec3 forward = forwardVector();
		double v = (getX() - prevX) * forward.x + (getZ() - prevZ) * forward.z;
		float turnRad = Mth.wrapDegrees(getYRot() - prevYaw) * Mth.DEG_TO_RAD;
		// curva para a direita (yaw crescendo): a esteira esquerda corre mais
		trackLeftSpeed = (float) v + turnRad * TRACK_HALF_SPAN;
		trackRightSpeed = (float) v - turnRad * TRACK_HALF_SPAN;
		visualTurnRate = Mth.lerp(0.3f, visualTurnRate, turnRad);
		updateBodyTilt();
		spawnAmbientParticles();
	}

	/** Inclinação visual: compara a altura do chão sob os quatro cantos das esteiras. */
	private void updateBodyTilt() {
		float half = 1.2f;
		Vec3 f = forwardVector().scale(half);
		Vec3 l = leftVector().scale(half);
		double fl = groundHeight(getX() + f.x + l.x, getZ() + f.z + l.z);
		double fr = groundHeight(getX() + f.x - l.x, getZ() + f.z - l.z);
		double rl = groundHeight(getX() - f.x + l.x, getZ() - f.z + l.z);
		double rr = groundHeight(getX() - f.x - l.x, getZ() - f.z - l.z);
		float targetPitch = (float) Math.atan2(((fl + fr) - (rl + rr)) / 2, half * 2);
		float targetRoll = (float) Math.atan2(((fl + rl) - (fr + rr)) / 2, half * 2);
		float max = 15f * Mth.DEG_TO_RAD;
		bodyPitch = Mth.lerp(0.2f, bodyPitch, Mth.clamp(targetPitch, -max, max));
		bodyRoll = Mth.lerp(0.2f, bodyRoll, Mth.clamp(targetRoll, -max, max));
	}

	private double groundHeight(double x, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dy = 1; dy >= -2; dy--) {
			pos.set(x, getY() + dy, z);
			BlockState state = level().getBlockState(pos);
			var shape = state.getCollisionShape(level(), pos);
			if (!shape.isEmpty()) {
				return pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) - getY();
			}
		}
		return -1;
	}

	private void spawnAmbientParticles() {
		// fumaça e vapor contínuos somem para quem está a bordo (tapavam a câmera em terceira pessoa);
		// quem vê de fora continua vendo. O vapor da zona vermelha já aparece no HUD do piloto.
		if (isFireLit() && !localPlayerAboard.test(this)) {
			float power = powerFactor(getPressure());
			if (random.nextFloat() < 0.25f + power * 0.5f) {
				Vec3 top = modelPointToWorld(0, 54, 19);
				ParticleOptions smoke = isDryOverheating() ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE;
				level().addParticle(smoke, top.x, top.y, top.z, 0, 0.05 + power * 0.04, 0);
			}
			if (getPressure() >= RED_ZONE_BAR && random.nextFloat() < 0.3f) {
				Vec3 vent = modelPointToWorld(3.5f, 35, 12.5f);
				level().addParticle(ParticleTypes.CLOUD, vent.x, vent.y, vent.z, 0, 0.08, 0);
			}
		}
		float integrityFraction = getIntegrity() / MAX_INTEGRITY;
		if (integrityFraction < CRITICAL_INTEGRITY_FRACTION && random.nextFloat() < 0.3f) {
			level().addParticle(ParticleTypes.LARGE_SMOKE, getX() + random.nextGaussian() * 0.6, getY() + 1.2, getZ() + random.nextGaussian() * 0.6, 0, 0.04, 0);
		} else if (integrityFraction < LOW_INTEGRITY_FRACTION && random.nextFloat() < 0.05f) {
			level().addParticle(ParticleTypes.SMALL_FLAME, getX() + random.nextGaussian() * 0.6, getY() + 0.8, getZ() + random.nextGaussian() * 0.6, 0, 0.02, 0);
		}
	}

	public static void setLocalPlayerAboardCheck(Predicate<LandshipEntity> check) {
		localPlayerAboard = check;
	}

	public float getTrackLeftSpeed() {
		return trackLeftSpeed;
	}

	public float getTrackRightSpeed() {
		return trackRightSpeed;
	}

	public float getVisualTurnRate() {
		return visualTurnRate;
	}

	public float getBodyPitch() {
		return bodyPitch;
	}

	public float getBodyRoll() {
		return bodyRoll;
	}

	// =====================================================================================
	// GeckoLib
	// =====================================================================================

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<LandshipEntity>("track_left", 0,
				test -> trackState(test, test.animatable().trackLeftSpeed, LEFT_FORWARD, LEFT_REVERSE)));
		controllers.add(new AnimationController<LandshipEntity>("track_right", 0,
				test -> trackState(test, test.animatable().trackRightSpeed, RIGHT_FORWARD, RIGHT_REVERSE)));
		controllers.add(new AnimationController<LandshipEntity>("engine", 5, test -> {
			LandshipEntity self = test.animatable();
			if (!self.isFireLit()) {
				return PlayState.STOP;
			}
			boolean moving = Math.abs(self.trackLeftSpeed) + Math.abs(self.trackRightSpeed) > 0.01f;
			return test.setAndContinue(moving ? ENGINE_WORKING : ENGINE_IDLE);
		}));
		controllers.add(new AnimationController<LandshipEntity>("boiler", 0, test -> PlayState.STOP)
				.triggerableAnim("vent", VENT));
	}

	private static PlayState trackState(AnimationTest<LandshipEntity> test, float trackSpeed, RawAnimation forward, RawAnimation reverse) {
		// parado: a animação fica congelada (velocidade 0) em vez de voltar à pose inicial
		float abs = Math.abs(trackSpeed);
		test.setControllerSpeed(abs < 0.002f ? 0f : abs / TRACK_ANIM_UNIT);
		if (abs < 0.002f && test.controller().getCurrentRawAnimation() != null) {
			return PlayState.CONTINUE;
		}
		return test.setAndContinue(trackSpeed >= 0 ? forward : reverse);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return geoCache;
	}

	/** Estado interno da caldeira (servidor). Usado pelos GameTests. */
	public BoilerState boilerState() {
		return boiler;
	}

	/** Integridade no servidor (o valor sincronizado é arredondado). Usado pelos GameTests. */
	public float serverIntegrity() {
		return integrity;
	}

	public void setServerIntegrity(float value) {
		integrity = Mth.clamp(value, 0f, MAX_INTEGRITY);
		syncBoiler();
	}
}
