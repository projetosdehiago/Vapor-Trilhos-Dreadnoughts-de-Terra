package io.github.projetosdehiago.vaportrilhos.landship;

import io.github.projetosdehiago.vaportrilhos.registry.ModMenus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Painel da caldeira: 3 espaços de combustível e botões (acender/apagar, abafador, válvula).
 * Os medidores a tela lê direto da entidade (dados já sincronizados).
 */
public class LandshipMenu extends AbstractContainerMenu {
	public static final int BUTTON_FIRE = 0;
	public static final int BUTTON_DAMPER = 1;
	public static final int BUTTON_VENT = 2;

	public static final int FUEL_X = 8;
	public static final int FUEL_Y = 80;
	public static final int INVENTORY_Y = 118;
	public static final int IMAGE_HEIGHT = 200;

	private final Container fuel;
	private final @Nullable LandshipEntity landship;

	/** Servidor. */
	public LandshipMenu(int containerId, Inventory inventory, LandshipEntity landship) {
		this(containerId, inventory, landship, landship.getFuelContainer());
	}

	/** Cliente: a entidade vem pelo id enviado na abertura. */
	public static LandshipMenu client(int containerId, Inventory inventory, Integer entityId) {
		LandshipEntity landship = inventory.player.level().getEntity(entityId) instanceof LandshipEntity l ? l : null;
		return new LandshipMenu(containerId, inventory, landship, new SimpleContainer(LandshipEntity.FUEL_SLOTS));
	}

	private LandshipMenu(int containerId, Inventory inventory, @Nullable LandshipEntity landship, Container fuel) {
		super(ModMenus.LANDSHIP, containerId);
		this.landship = landship;
		this.fuel = fuel;
		fuel.startOpen(inventory.player);

		for (int i = 0; i < LandshipEntity.FUEL_SLOTS; i++) {
			addSlot(new Slot(fuel, i, FUEL_X + i * 18, FUEL_Y) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return FuelHelper.isFuel(stack);
				}
			});
		}
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
	}

	public @Nullable LandshipEntity getLandship() {
		return landship;
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (landship == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		switch (buttonId) {
			case BUTTON_FIRE -> landship.toggleFire(level, player);
			case BUTTON_DAMPER -> landship.cycleDamper(player);
			case BUTTON_VENT -> landship.manualVent(level, player);
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int fuelEnd = LandshipEntity.FUEL_SLOTS;
		if (index < fuelEnd) {
			if (!moveItemStackTo(stack, fuelEnd, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (FuelHelper.isFuel(stack)) {
			if (!moveItemStackTo(stack, 0, fuelEnd, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		return landship != null ? landship.isUsableBy(player) : fuel.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		fuel.stopOpen(player);
	}
}
