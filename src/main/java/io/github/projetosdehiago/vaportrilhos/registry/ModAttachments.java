package io.github.projetosdehiago.vaportrilhos.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.projetosdehiago.vaportrilhos.VaporTrilhos;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Dados persistentes da cama móvel (Fabric Data Attachment API, sem mixin). */
public final class ModAttachments {
	private ModAttachments() {
	}

	/** Lar de um jogador: o landship onde ele dormiu (ou usou a cama) por último. */
	public record LandshipHome(UUID landship) {
		public static final Codec<LandshipHome> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("landship").forGetter(LandshipHome::landship)
		).apply(i, LandshipHome::new));
	}

	/** Última posição conhecida de um landship com cama. */
	public record BedLocation(ResourceKey<Level> dimension, Vec3 position, float yaw) {
		public static final Codec<BedLocation> CODEC = RecordCodecBuilder.create(i -> i.group(
				Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(BedLocation::dimension),
				Vec3.CODEC.fieldOf("position").forGetter(BedLocation::position),
				Codec.FLOAT.fieldOf("yaw").forGetter(BedLocation::yaw)
		).apply(i, BedLocation::new));
	}

	/** No jogador; sobrevive à morte (é justamente quando é usado). */
	public static final AttachmentType<LandshipHome> HOME = AttachmentRegistry.create(VaporTrilhos.id("landship_home"),
			builder -> builder.persistent(LandshipHome.CODEC).copyOnDeath());

	/**
	 * No Overworld: índice landship → posição, de todos os landships com cama. Assim dá para
	 * renascer junto do landship mesmo com ele num chunk descarregado ou em outra dimensão.
	 */
	public static final AttachmentType<Map<UUID, BedLocation>> BED_INDEX = AttachmentRegistry.create(VaporTrilhos.id("bed_index"),
			builder -> builder.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, BedLocation.CODEC)));

	public static void init() {
	}
}
