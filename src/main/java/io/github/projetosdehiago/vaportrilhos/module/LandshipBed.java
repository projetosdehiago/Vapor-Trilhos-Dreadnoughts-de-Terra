package io.github.projetosdehiago.vaportrilhos.module;

import io.github.projetosdehiago.vaportrilhos.landship.LandshipEntity;
import io.github.projetosdehiago.vaportrilhos.registry.ModAttachments;
import io.github.projetosdehiago.vaportrilhos.registry.ModAttachments.BedLocation;
import io.github.projetosdehiago.vaportrilhos.registry.ModAttachments.LandshipHome;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.util.EventResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Cama móvel (design.md A6.1): dormir e ponto de renascimento.
 *
 * <p>No 26.3 o sono vanilla exige um bloco de cama. Aqui o jogador deita direto na pose de sono
 * (as mesmas chamadas que a cama vanilla faz) e o evento {@link EntitySleepEvents#ALLOW_BED} diz
 * ao jogo, a cada tick, que aquele ponto é uma cama válida enquanto o landship estiver ali parado.
 * O resto (pular a noite, tela "Sair da cama", acordar de manhã) é o fluxo normal do jogo.
 *
 * <p>Renascimento: o lar fica num anexo de dados do jogador ({@link ModAttachments#HOME}) e o
 * Overworld guarda um índice landship → posição ({@link ModAttachments#BED_INDEX}). Depois de
 * renascer, o jogador é levado para o lado do landship. Se o landship foi destruído, recolhido
 * ou perdeu a cama, o índice não tem mais a entrada: o lar é apagado e vale o spawn normal.
 */
public final class LandshipBed {
	private LandshipBed() {
	}

	public static void register() {
		EntitySleepEvents.ALLOW_BED.register((entity, pos, state, vanillaResult) -> {
			if (vanillaResult) {
				return EventResult.PASS;
			}
			LandshipEntity landship = findLandshipBed(entity.level(), pos);
			return landship != null && landship.isStationary() ? EventResult.ALLOW : EventResult.PASS;
		});
		EntitySleepEvents.MODIFY_SLEEPING_DIRECTION.register((entity, pos, direction) -> {
			if (direction != null) {
				return direction;
			}
			LandshipEntity landship = findLandshipBed(entity.level(), pos);
			return landship != null ? Direction.fromYRot(landship.getYRot()) : null;
		});
		EntitySleepEvents.STOP_SLEEPING.register(LandshipBed::wakeUp);
		// dormir numa cama comum (ou marcar o spawn nela) troca o lar: vale a cama mais recente
		EntitySleepEvents.ALLOW_SETTING_SPAWN.register((player, pos) -> {
			player.removeAttached(ModAttachments.HOME);
			return true;
		});
		ServerPlayerEvents.AFTER_RESPAWN.register(LandshipBed::afterRespawn);
	}

	// =========================================================================================
	// Dormir
	// =========================================================================================

	/** Resultado de uma tentativa de dormir. */
	public enum SleepResult {
		SLEEPING,
		NO_BED,
		MOVING,
		NOT_NOW,
		NOT_SAFE,
		OCCUPIED
	}

	/** Botão "Dormir" do painel. */
	public static SleepResult trySleep(ServerPlayer player, LandshipEntity landship) {
		ServerLevel level = player.level();
		if (!landship.hasModule(ModuleType.BED) || player.isSleeping() || !player.isAlive()) {
			return SleepResult.NO_BED;
		}
		if (!landship.isStationary()) {
			player.sendOverlayMessage(Component.translatable("message.vapor_trilhos.bed_moving"));
			return SleepResult.MOVING;
		}
		// o corpo deitado só gira de 90 em 90 graus: alinha o landship (a pegada é quadrada)
		landship.alignToGrid();
		BlockPos bedPos = landship.bedBlockPos();
		BedRule rule = level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, bedPos);
		if (rule.canSetSpawn(level)) {
			setHome(player, landship);
		}
		if (!rule.canSleep(level)) {
			rule.errorMessage().ifPresent(player::sendOverlayMessage);
			return SleepResult.NOT_NOW;
		}
		if (!player.isCreative() && monstersNearby(level, player, bedPos)) {
			player.sendOverlayMessage(Component.translatable("block.minecraft.bed.not_safe"));
			return SleepResult.NOT_SAFE;
		}
		if (isOccupied(level, bedPos)) {
			player.sendOverlayMessage(Component.translatable("block.minecraft.bed.occupied"));
			return SleepResult.OCCUPIED;
		}
		player.stopRiding();
		Vec3 head = landship.bedSleepPosition();
		player.setPos(head.x, head.y, head.z);
		player.setPose(Pose.SLEEPING);
		player.setSleepingPos(bedPos);
		player.setDeltaMovement(Vec3.ZERO);
		player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
		player.connection.teleport(head.x, head.y, head.z, player.getYRot(), player.getXRot());
		if (!level.canSleepThroughNights()) {
			player.sendOverlayMessage(Component.translatable("sleep.not_possible"));
		}
		level.updateSleepingPlayerList();
		return SleepResult.SLEEPING;
	}

	private static boolean monstersNearby(ServerLevel level, ServerPlayer player, BlockPos bedPos) {
		Vec3 c = Vec3.atBottomCenterOf(bedPos);
		AABB area = new AABB(c.x - 8, c.y - 5, c.z - 8, c.x + 8, c.y + 5, c.z + 8);
		return !level.getEntitiesOfClass(Monster.class, area, monster -> monster.isPreventingPlayerRest(level, player)).isEmpty();
	}

	private static boolean isOccupied(ServerLevel level, BlockPos bedPos) {
		for (ServerPlayer other : level.players()) {
			if (other.isSleeping() && other.getSleepingPos().filter(bedPos::equals).isPresent()) {
				return true;
			}
		}
		return false;
	}

	/** O landship cuja cama está em {@code pos}, se houver. Vale nos dois lados (usa dados sincronizados). */
	public static @Nullable LandshipEntity findLandshipBed(Level level, BlockPos pos) {
		List<LandshipEntity> found = level.getEntitiesOfClass(LandshipEntity.class, new AABB(pos).inflate(3),
				landship -> landship.hasModule(ModuleType.BED) && landship.bedBlockPos().equals(pos));
		return found.isEmpty() ? null : found.getFirst();
	}

	/** Acordar: levanta ao lado do landship (deitado, o jogador está dentro da caixa dele). */
	private static void wakeUp(LivingEntity entity, BlockPos pos) {
		if (!(entity instanceof ServerPlayer player)) {
			return;
		}
		LandshipEntity landship = findLandshipBed(player.level(), pos);
		if (landship == null) {
			return;
		}
		Vec3 spot = standingSpotNear(player.level(), landship.position(), landship.getYRot());
		player.setPos(spot.x, spot.y, spot.z);
		player.connection.teleport(spot.x, spot.y, spot.z, landship.getYRot(), 0f);
	}

	/**
	 * Um lugar seguro para ficar em pé ao lado do landship (esquerda, direita, trás, frente). Se
	 * nada servir, em cima do teto, que é sólido.
	 */
	public static Vec3 standingSpotNear(ServerLevel level, Vec3 center, float yaw) {
		Vec3 forward = Vec3.directionFromRotation(0, yaw);
		Vec3 left = forward.yRot((float) Math.PI / 2);
		Vec3[] sides = {left, left.reverse(), forward.reverse(), forward};
		for (Vec3 side : sides) {
			Vec3 target = center.add(side.scale(2.4));
			for (int dy : new int[] {0, 1, -1, 2, -2}) {
				BlockPos pos = BlockPos.containing(target.x, center.y + dy, target.z);
				Vec3 spot = DismountHelper.findSafeDismountLocation(EntityTypes.PLAYER, level, pos, true);
				if (spot != null) {
					return spot;
				}
			}
		}
		return center.add(0, LandshipEntity.ROOF_HEIGHT + 0.05, 0);
	}

	// =========================================================================================
	// Renascimento
	// =========================================================================================

	private static void setHome(ServerPlayer player, LandshipEntity landship) {
		if (recordHome(player, landship)) {
			player.sendSystemMessage(Component.translatable("message.vapor_trilhos.home_set"));
		}
	}

	/**
	 * Marca o landship como lar do jogador.
	 *
	 * @return {@code true} se o lar mudou
	 */
	public static boolean recordHome(ServerPlayer player, LandshipEntity landship) {
		LandshipHome current = player.getAttached(ModAttachments.HOME);
		player.setAttached(ModAttachments.HOME, new LandshipHome(landship.getUUID()));
		updateIndex(player.level().getServer(), landship);
		return current == null || !current.landship().equals(landship.getUUID());
	}

	/** Para onde o jogador vai ao renascer. */
	public record RespawnTarget(ServerLevel level, Vec3 position, float yaw) {
	}

	/**
	 * Onde o jogador deve renascer, se o lar dele ainda existe. Se o landship sumiu (destruído,
	 * recolhido ou sem cama), apaga o lar e devolve {@code null}: vale o spawn normal.
	 */
	public static @Nullable RespawnTarget findRespawn(ServerPlayer player) {
		LandshipHome home = player.getAttached(ModAttachments.HOME);
		if (home == null) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		BedLocation location = index(server).get(home.landship());
		ServerLevel level = location != null ? server.getLevel(location.dimension()) : null;
		if (level == null) {
			player.removeAttached(ModAttachments.HOME);
			return null;
		}
		return new RespawnTarget(level, standingSpotNear(level, location.position(), location.yaw()), location.yaw());
	}

	private static void afterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (alive || !newPlayer.hasAttached(ModAttachments.HOME)) {
			return; // voltando do End (não morreu) ou sem lar
		}
		RespawnTarget target = findRespawn(newPlayer);
		if (target == null) {
			newPlayer.sendSystemMessage(Component.translatable("message.vapor_trilhos.home_lost"));
			return;
		}
		Vec3 spot = target.position();
		newPlayer.teleportTo(target.level(), spot.x, spot.y, spot.z, Set.of(), target.yaw(), 0f, true);
	}

	private static Map<UUID, BedLocation> index(MinecraftServer server) {
		Map<UUID, BedLocation> index = server.overworld().getAttached(ModAttachments.BED_INDEX);
		return index != null ? index : Map.of();
	}

	/** Atualiza a posição de um landship com cama no índice. */
	public static void updateIndex(MinecraftServer server, LandshipEntity landship) {
		BedLocation now = new BedLocation(landship.level().dimension(), landship.position(), landship.getYRot());
		BedLocation known = index(server).get(landship.getUUID());
		if (known != null && known.dimension().equals(now.dimension()) && known.position().distanceToSqr(now.position()) < 0.25) {
			return;
		}
		Map<UUID, BedLocation> copy = new HashMap<>(index(server));
		copy.put(landship.getUUID(), now);
		server.overworld().setAttached(ModAttachments.BED_INDEX, copy);
	}

	/** O landship deixou de existir (ou perdeu a cama): quem tinha lar nele volta ao spawn normal. */
	public static void removeFromIndex(MinecraftServer server, UUID landship) {
		Map<UUID, BedLocation> index = index(server);
		if (index.containsKey(landship)) {
			Map<UUID, BedLocation> copy = new HashMap<>(index);
			copy.remove(landship);
			server.overworld().setAttached(ModAttachments.BED_INDEX, copy);
		}
	}

	public static boolean isIndexed(MinecraftServer server, UUID landship) {
		return index(server).containsKey(landship);
	}
}
