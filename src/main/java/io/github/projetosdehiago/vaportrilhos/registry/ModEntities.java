package io.github.projetosdehiago.vaportrilhos.registry;

import io.github.projetosdehiago.vaportrilhos.VaporTrilhos;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	private ModEntities() {
	}

	/** Pegada quadrada 2,9 × 2,9 (gira sem mudar a área), casco com 2 blocos de altura. */
	public static final EntityType<LandshipEntity> LANDSHIP = register("landship",
			EntityType.Builder.of(LandshipEntity::new, MobCategory.MISC)
					.noLootTable()
					.sized(2.9F, 2.0F)
					.eyeHeight(1.6F)
					.fireImmune()
					.clientTrackingRange(10));

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, VaporTrilhos.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
	}
}
