package io.github.projetosdehiago.vaportrilhos.landship;

import io.github.projetosdehiago.vaportrilhos.registry.ModDataComponents;
import io.github.projetosdehiago.vaportrilhos.registry.ModEntities;
import net.minecraft.core.component.DataComponents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Coloca o landship no mundo, como o item do barco, preservando integridade e água guardadas. */
public class LandshipItem extends Item {
	public LandshipItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return InteractionResult.PASS;
		}
		LandshipEntity landship = ModEntities.LANDSHIP.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (landship == null) {
			return InteractionResult.FAIL;
		}
		var location = hit.getLocation();
		landship.snapTo(location.x, location.y, location.z, player.getYRot(), 0f);
		if (!level.noCollision(landship, landship.getBoundingBox())) {
			return InteractionResult.FAIL;
		}
		if (!level.isClientSide()) {
			ModDataComponents.LandshipData data = stack.get(ModDataComponents.LANDSHIP_DATA);
			if (data != null) {
				landship.loadFromItem(data);
			}
			if (stack.has(DataComponents.CUSTOM_NAME)) {
				landship.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
			}
			level.addFreshEntity(landship);
			level.gameEvent(player, GameEvent.ENTITY_PLACE, location);
			stack.consume(1, player);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		return InteractionResult.SUCCESS;
	}
}
