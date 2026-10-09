package io.github.projetosdehiago.vaportrilhos.landship;

import io.github.projetosdehiago.vaportrilhos.assembly.LandshipAssembly;
import io.github.projetosdehiago.vaportrilhos.boiler.BalanceConstants;
import io.github.projetosdehiago.vaportrilhos.module.FurnaceModule;
import io.github.projetosdehiago.vaportrilhos.module.LandshipBed;
import io.github.projetosdehiago.vaportrilhos.module.LandshipModules;
import io.github.projetosdehiago.vaportrilhos.module.ModuleSlot;
import io.github.projetosdehiago.vaportrilhos.module.ModuleType;
import io.github.projetosdehiago.vaportrilhos.registry.ModMenus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Painel do landship, com abas (design.md B3.4): Caldeira (combustível e comandos), Carga (um
 * baú por vez), Fornalha e Módulos. Todos os espaços existem sempre; a aba escolhida decide
 * quais ficam ativos. Os medidores a tela lê direto da entidade (dados já sincronizados).
 */
public class LandshipMenu extends AbstractContainerMenu {
	public enum Tab {
		BOILER,
		CARGO,
		FURNACE,
		MODULES,
		WHISTLE
	}

	public static final int BUTTON_FIRE = 0;
	public static final int BUTTON_DAMPER = 1;
	public static final int BUTTON_VENT = 2;
	public static final int BUTTON_COMPACTOR = 3;
	public static final int BUTTON_SLEEP = 4;
	public static final int BUTTON_DISASSEMBLE = 5;
	/** + {@link Tab#ordinal()}. */
	public static final int BUTTON_TAB = 10;
	/** + {@link ModuleSlot#ordinal()} do baú mostrado na aba Carga. */
	public static final int BUTTON_CARGO_PAGE = 20;
	/** + {@link WhistleSound#ordinal()}. */
	public static final int BUTTON_WHISTLE = 40;

	public static final int FUEL_X = 8;
	public static final int FUEL_Y = 80;
	public static final int CARGO_X = 8;
	public static final int CARGO_Y = 26;
	public static final int FURNACE_IN_X = 44;
	public static final int FURNACE_OUT_X = 104;
	public static final int FURNACE_Y = 44;
	public static final int INVENTORY_Y = 118;
	public static final int IMAGE_HEIGHT = 200;

	private static final int FUEL_START = 0;
	private static final int CARGO_START = FUEL_START + LandshipEntity.FUEL_SLOTS;
	private static final int FURNACE_START = CARGO_START + LandshipModules.CARGO_SIZE * ModuleSlot.DECK.length;
	private static final int PLAYER_START = FURNACE_START + 2;

	private final Container fuel;
	private final Container cargo;
	private final Container furnace;
	private final ContainerData data;
	private final @Nullable LandshipEntity landship;
	private Tab tab = Tab.BOILER;
	private ModuleSlot cargoPage = ModuleSlot.FRONT_LEFT;

	/** Servidor. */
	public LandshipMenu(int containerId, Inventory inventory, LandshipEntity landship) {
		this(containerId, inventory, landship, landship.getFuelContainer(), landship.modules().cargo(),
				landship.modules().furnace(), new ContainerData() {
					@Override
					public int get(int index) {
						return landship.modules().smeltProgress;
					}

					@Override
					public void set(int index, int value) {
						landship.modules().smeltProgress = value;
					}

					@Override
					public int getCount() {
						return 1;
					}
				});
	}

	/** Cliente: a entidade vem pelo id enviado na abertura. */
	public static LandshipMenu client(int containerId, Inventory inventory, Integer entityId) {
		LandshipEntity landship = inventory.player.level().getEntity(entityId) instanceof LandshipEntity l ? l : null;
		return new LandshipMenu(containerId, inventory, landship, new SimpleContainer(LandshipEntity.FUEL_SLOTS),
				new SimpleContainer(LandshipModules.CARGO_SIZE * ModuleSlot.DECK.length), new SimpleContainer(2), new SimpleContainerData(1));
	}

	private LandshipMenu(int containerId, Inventory inventory, @Nullable LandshipEntity landship, Container fuel, Container cargo,
			Container furnace, ContainerData data) {
		super(ModMenus.LANDSHIP, containerId);
		this.landship = landship;
		this.fuel = fuel;
		this.cargo = cargo;
		this.furnace = furnace;
		this.data = data;
		fuel.startOpen(inventory.player);
		ModuleSlot first = firstCargoPage();
		if (first != null) {
			cargoPage = first;
		}

		for (int i = 0; i < LandshipEntity.FUEL_SLOTS; i++) {
			addSlot(new Slot(fuel, i, FUEL_X + i * 18, FUEL_Y) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return FuelHelper.isFuel(stack);
				}

				@Override
				public boolean isActive() {
					return tab == Tab.BOILER;
				}
			});
		}
		for (ModuleSlot page : ModuleSlot.DECK) {
			int start = LandshipModules.cargoStart(page);
			for (int i = 0; i < LandshipModules.CARGO_SIZE; i++) {
				addSlot(new Slot(cargo, start + i, CARGO_X + (i % 9) * 18, CARGO_Y + (i / 9) * 18) {
					@Override
					public boolean isActive() {
						return tab == Tab.CARGO && cargoPage == page && hasModuleAt(page, ModuleType.CARGO);
					}

					@Override
					public boolean mayPlace(ItemStack stack) {
						return isActive();
					}
				});
			}
		}
		addSlot(new Slot(furnace, LandshipModules.FURNACE_INPUT, FURNACE_IN_X, FURNACE_Y) {
			@Override
			public boolean isActive() {
				return tab == Tab.FURNACE && hasModule(ModuleType.FURNACE);
			}

			@Override
			public boolean mayPlace(ItemStack stack) {
				return isActive();
			}
		});
		addSlot(new Slot(furnace, LandshipModules.FURNACE_OUTPUT, FURNACE_OUT_X, FURNACE_Y) {
			@Override
			public boolean isActive() {
				return tab == Tab.FURNACE && hasModule(ModuleType.FURNACE);
			}

			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack stack) {
				super.onTake(player, stack);
				if (LandshipMenu.this.landship != null && player.level() instanceof ServerLevel level) {
					FurnaceModule.awardExperience(level, player.position(), LandshipMenu.this.landship.modules());
				}
			}
		});
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
		addDataSlots(data);
	}

	public @Nullable LandshipEntity getLandship() {
		return landship;
	}

	public Tab getTab() {
		return tab;
	}

	public ModuleSlot getCargoPage() {
		return cargoPage;
	}

	/** Progresso da fusão atual, de 0 a 1. */
	public float getSmeltProgress() {
		return data.get(0) / (float) BalanceConstants.FURNACE_TICKS_PER_ITEM;
	}

	private boolean hasModule(ModuleType type) {
		return landship != null && landship.hasModule(type);
	}

	private boolean hasModuleAt(ModuleSlot slot, ModuleType type) {
		return landship != null && landship.getModuleAt(slot) == type;
	}

	private @Nullable ModuleSlot firstCargoPage() {
		for (ModuleSlot slot : ModuleSlot.DECK) {
			if (hasModuleAt(slot, ModuleType.CARGO)) {
				return slot;
			}
		}
		return null;
	}

	/** Troca de aba/página. O cliente aplica na hora e manda o mesmo botão para o servidor. */
	public boolean select(int buttonId) {
		if (buttonId >= BUTTON_TAB && buttonId < BUTTON_TAB + Tab.values().length) {
			tab = Tab.values()[buttonId - BUTTON_TAB];
			if (tab == Tab.CARGO && !hasModuleAt(cargoPage, ModuleType.CARGO)) {
				ModuleSlot first = firstCargoPage();
				if (first != null) {
					cargoPage = first;
				}
			}
			return true;
		}
		if (buttonId >= BUTTON_CARGO_PAGE && buttonId < BUTTON_CARGO_PAGE + ModuleSlot.DECK.length) {
			cargoPage = ModuleSlot.values()[buttonId - BUTTON_CARGO_PAGE];
			return true;
		}
		return false;
	}

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (select(buttonId)) {
			return true;
		}
		if (landship == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (buttonId >= BUTTON_WHISTLE && buttonId < BUTTON_WHISTLE + WhistleSound.values().length) {
			landship.selectWhistle(player, WhistleSound.byId(buttonId - BUTTON_WHISTLE));
			return true;
		}
		switch (buttonId) {
			case BUTTON_FIRE -> landship.toggleFire(level, player);
			case BUTTON_DAMPER -> landship.cycleDamper(player);
			case BUTTON_VENT -> landship.manualVent(level, player);
			case BUTTON_COMPACTOR -> landship.toggleCompactor(player);
			// o painel fecha sozinho quando o landship some (stillValid)
			case BUTTON_DISASSEMBLE -> LandshipAssembly.disassemble(level, landship, player);
			case BUTTON_SLEEP -> {
				if (player instanceof ServerPlayer serverPlayer) {
					serverPlayer.closeContainer();
					LandshipBed.trySleep(serverPlayer, landship);
				}
			}
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
		int playerEnd = slots.size();
		if (index < PLAYER_START) {
			// do landship para o inventário
			if (!moveItemStackTo(stack, PLAYER_START, playerEnd, true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickCraft(stack, original);
		} else {
			// do inventário para o que a aba atual mostra
			boolean moved = switch (tab) {
				case BOILER -> FuelHelper.isFuel(stack) && moveItemStackTo(stack, FUEL_START, CARGO_START, false);
				case CARGO -> hasModuleAt(cargoPage, ModuleType.CARGO) && moveItemStackTo(stack,
						CARGO_START + LandshipModules.cargoStart(cargoPage), CARGO_START + LandshipModules.cargoStart(cargoPage) + LandshipModules.CARGO_SIZE, false);
				case FURNACE -> hasModule(ModuleType.FURNACE) && moveItemStackTo(stack, FURNACE_START, FURNACE_START + 1, false);
				case MODULES, WHISTLE -> false;
			};
			if (!moved) {
				return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		slot.onTake(player, stack);
		return original;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
		// o duplo clique não pode juntar itens de baús escondidos
		return target.isActive() && super.canTakeItemForPickAll(carried, target);
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
