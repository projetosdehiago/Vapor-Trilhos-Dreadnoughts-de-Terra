package io.github.projetosdehiago.vaportrilhos.client.screen;

import io.github.projetosdehiago.vaportrilhos.boiler.BalanceConstants;
import io.github.projetosdehiago.vaportrilhos.client.hud.Gauges;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipEntity;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipMenu;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipMenu.Tab;
import io.github.projetosdehiago.vaportrilhos.landship.WhistleSound;
import io.github.projetosdehiago.vaportrilhos.module.ModuleSlot;
import io.github.projetosdehiago.vaportrilhos.module.ModuleType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/**
 * Painel do landship com abas: Caldeira, Carga, Fornalha e Módulos. Desenhado sem textura
 * própria, no estilo das telas vanilla.
 */
public class LandshipScreen extends AbstractContainerScreen<LandshipMenu> {
	private static final int BG = 0xFFC6C6C6;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;
	private static final int SLOT_BG = 0xFF8B8B8B;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int TAB_WIDTH = 50;
	private static final int TAB_HEIGHT = 18;

	private final Map<Tab, Button> tabButtons = new EnumMap<>(Tab.class);
	private final Map<ModuleSlot, Button> pageButtons = new EnumMap<>(ModuleSlot.class);
	private final List<Button> boilerButtons = new ArrayList<>();
	private Button fireButton;
	private Button damperButton;
	private Button compactorButton;
	private Button sleepButton;
	private Button disassembleButton;
	private final Map<WhistleSound, Button> whistleButtons = new EnumMap<>(WhistleSound.class);

	public LandshipScreen(LandshipMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, LandshipMenu.IMAGE_HEIGHT);
		this.inventoryLabelY = LandshipMenu.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		int x = leftPos;
		int y = topPos;
		tabButtons.clear();
		pageButtons.clear();
		boilerButtons.clear();

		for (Tab tab : Tab.values()) {
			Button button = addRenderableWidget(Button.builder(Component.translatable("tab.vapor_trilhos." + tab.name().toLowerCase(Locale.ROOT)),
					b -> press(LandshipMenu.BUTTON_TAB + tab.ordinal())).bounds(x, y - TAB_HEIGHT, TAB_WIDTH, TAB_HEIGHT).build());
			tabButtons.put(tab, button);
		}

		fireButton = addRenderableWidget(Button.builder(Component.empty(), b -> press(LandshipMenu.BUTTON_FIRE))
				.bounds(x + 66, y + 68, 52, 18).build());
		Button vent = addRenderableWidget(Button.builder(Component.translatable("button.vapor_trilhos.vent"), b -> press(LandshipMenu.BUTTON_VENT))
				.bounds(x + 120, y + 68, 50, 18).build());
		damperButton = addRenderableWidget(Button.builder(Component.empty(), b -> press(LandshipMenu.BUTTON_DAMPER))
				.bounds(x + 66, y + 88, 104, 18).build());
		boilerButtons.add(fireButton);
		boilerButtons.add(vent);
		boilerButtons.add(damperButton);

		for (ModuleSlot slot : ModuleSlot.DECK) {
			Button page = addRenderableWidget(Button.builder(Component.empty(),
					b -> press(LandshipMenu.BUTTON_CARGO_PAGE + slot.ordinal())).bounds(x, y + 4, 18, 14).build());
			pageButtons.put(slot, page);
		}

		compactorButton = addRenderableWidget(Button.builder(Component.empty(), b -> press(LandshipMenu.BUTTON_COMPACTOR))
				.bounds(x + 8, y + 64, 102, 18).build());
		sleepButton = addRenderableWidget(Button.builder(Component.translatable("button.vapor_trilhos.sleep"), b -> press(LandshipMenu.BUTTON_SLEEP))
				.bounds(x + 114, y + 64, 54, 18).build());
		disassembleButton = addRenderableWidget(Button.builder(Component.translatable("button.vapor_trilhos.disassemble"),
				b -> press(LandshipMenu.BUTTON_DISASSEMBLE)).bounds(x + 8, y + 86, 160, 18).build());

		// aba Apito: uma opção por linha; o personalizado mostra o nome do áudio ao lado
		whistleButtons.clear();
		for (WhistleSound whistle : WhistleSound.values()) {
			Button button = addRenderableWidget(Button.builder(Component.translatable("whistle.vapor_trilhos." + whistle.id()),
					b -> press(LandshipMenu.BUTTON_WHISTLE + whistle.ordinal())).bounds(x + 8, y + 17 + whistle.ordinal() * 17, 100, 16).build());
			whistleButtons.put(whistle, button);
		}
		updateButtons();
	}

	private void press(int buttonId) {
		// abas e páginas mudam na hora no cliente; o servidor recebe o mesmo botão
		menu.select(buttonId);
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
		}
		updateButtons();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		updateButtons();
	}

	private boolean tabAvailable(LandshipEntity landship, Tab tab) {
		return switch (tab) {
			case BOILER, MODULES, WHISTLE -> true;
			case CARGO -> landship.hasModule(ModuleType.CARGO);
			case FURNACE -> landship.hasModule(ModuleType.FURNACE);
		};
	}

	private void updateButtons() {
		LandshipEntity landship = menu.getLandship();
		if (landship == null || fireButton == null) {
			return;
		}
		Tab current = menu.getTab();
		if (!tabAvailable(landship, current)) {
			press(LandshipMenu.BUTTON_TAB + Tab.BOILER.ordinal());
			return;
		}

		// abas disponíveis lado a lado, centradas no painel; a atual fica "afundada" (desativada)
		int available = 0;
		for (Tab tab : Tab.values()) {
			if (tabAvailable(landship, tab)) {
				available++;
			}
		}
		int tx = leftPos + (imageWidth - available * TAB_WIDTH) / 2;
		for (Tab tab : Tab.values()) {
			Button button = tabButtons.get(tab);
			button.visible = tabAvailable(landship, tab);
			button.active = tab != current;
			if (button.visible) {
				button.setX(tx);
				tx += TAB_WIDTH;
			}
		}

		for (Button button : boilerButtons) {
			button.visible = current == Tab.BOILER;
		}
		fireButton.setMessage(Component.translatable(landship.isFireLit() ? "button.vapor_trilhos.extinguish" : "button.vapor_trilhos.ignite"));
		damperButton.setMessage(Component.translatable("button.vapor_trilhos.damper",
				Component.translatable("damper.vapor_trilhos." + landship.getDamper().name().toLowerCase(Locale.ROOT))));

		// páginas da carga: um botão numerado por baú, à direita do título
		ModuleType[] installed = landship.getInstalledModules();
		List<ModuleSlot> chests = new ArrayList<>();
		for (ModuleSlot slot : ModuleSlot.DECK) {
			if (installed[slot.ordinal()] == ModuleType.CARGO) {
				chests.add(slot);
			}
		}
		int px = leftPos + imageWidth - 7 - chests.size() * 19;
		for (ModuleSlot slot : ModuleSlot.DECK) {
			Button page = pageButtons.get(slot);
			int number = chests.indexOf(slot);
			page.visible = current == Tab.CARGO && number >= 0;
			page.active = menu.getCargoPage() != slot;
			if (page.visible) {
				page.setMessage(Component.literal(String.valueOf(number + 1)));
				page.setX(px + number * 19);
			}
		}

		compactorButton.visible = current == Tab.MODULES && installed[ModuleSlot.FRONT.ordinal()] == ModuleType.COMPACTOR;
		compactorButton.setMessage(Component.translatable(landship.isCompactorOn() ? "hud.vapor_trilhos.compactor_on" : "hud.vapor_trilhos.compactor_off"));
		sleepButton.visible = current == Tab.MODULES && landship.hasModule(ModuleType.BED);
		disassembleButton.visible = current == Tab.MODULES;
		WhistleSound selected = landship.getWhistle();
		for (Map.Entry<WhistleSound, Button> entry : whistleButtons.entrySet()) {
			entry.getValue().visible = current == Tab.WHISTLE;
			entry.getValue().active = entry.getKey() != selected;
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = leftPos;
		int y = topPos;
		bevel(g, x, y, imageWidth, imageHeight, BG, LIGHT, SHADOW);
		for (Slot slot : menu.slots) {
			if (slot.isActive()) {
				bevel(g, x + slot.x - 1, y + slot.y - 1, 18, 18, SLOT_BG, SLOT_DARK, LIGHT);
			}
		}
		LandshipEntity landship = menu.getLandship();
		if (landship == null) {
			return;
		}
		switch (menu.getTab()) {
			case BOILER -> {
				Gauges.boilerRows(g, font, landship, x + 8, y + 18, 50, 66, Gauges.TEXT_DARK);
				// barra de queima sob os espaços de combustível
				int bx = x + LandshipMenu.FUEL_X - 1;
				int by = y + LandshipMenu.FUEL_Y + 19;
				g.fill(bx, by, bx + 54, by + 3, SLOT_DARK);
				int filled = Math.round(landship.getBurnFraction() * 54);
				g.fill(bx, by, bx + filled, by + 3, landship.isFireLit() ? 0xFFF09A30 : 0xFF7A7A7A);
			}
			case FURNACE -> {
				// seta de progresso entre entrada e saída
				int ax = x + LandshipMenu.FURNACE_IN_X + 22;
				int ay = y + LandshipMenu.FURNACE_Y + 6;
				int width = LandshipMenu.FURNACE_OUT_X - LandshipMenu.FURNACE_IN_X - 26;
				g.fill(ax, ay, ax + width, ay + 4, SLOT_DARK);
				g.fill(ax, ay, ax + Math.round(menu.getSmeltProgress() * width), ay + 4, 0xFFF09A30);
			}
			default -> {
			}
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		super.extractLabels(g, xm, ym);
		LandshipEntity landship = menu.getLandship();
		switch (menu.getTab()) {
			case BOILER -> g.text(font, Component.translatable("gui.vapor_trilhos.fuel"), LandshipMenu.FUEL_X, LandshipMenu.FUEL_Y - 10, Gauges.TEXT_DARK, false);
			case CARGO -> g.text(font, Component.translatable("gui.vapor_trilhos.cargo_page",
					Component.translatable("slot.vapor_trilhos." + menu.getCargoPage().code)), 8, 85, Gauges.TEXT_DARK, false);
			case FURNACE -> {
				if (landship != null) {
					boolean hot = landship.isFireLit() && landship.getTemperature() >= BalanceConstants.BOILING_C;
					g.text(font, Component.translatable(hot ? "gui.vapor_trilhos.furnace_hot" : "gui.vapor_trilhos.furnace_cold",
							Math.round(landship.getTemperature())), 8, 72, hot ? 0xFF2E6B2E : 0xFF8B2E2E, false);
					g.text(font, Component.translatable("gui.vapor_trilhos.furnace_hint"), 8, 84, Gauges.TEXT_DARK, false);
				}
			}
			case WHISTLE -> {
				if (landship != null) {
					for (WhistleSound whistle : WhistleSound.values()) {
						int ty = 21 + whistle.ordinal() * 17;
						if (whistle == WhistleSound.CUSTOM) {
							g.text(font, Component.translatable("whistle.vapor_trilhos.custom_name"), 114, ty, 0xFF2E4F7A, false);
						}
						if (whistle == landship.getWhistle()) {
							g.text(font, "◀", whistle == WhistleSound.CUSTOM ? 160 : 114, ty, 0xFF2E6B2E, false);
						}
					}
				}
			}
			case MODULES -> {
				if (landship != null) {
					// o deque visto de cima: frente em cima, esquerda do piloto à esquerda
					ModuleType[] installed = landship.getInstalledModules();
					moduleCell(g, installed[ModuleSlot.FRONT.ordinal()], 48, 16);
					ModuleSlot[] deck = ModuleSlot.DECK;
					for (int i = 0; i < deck.length; i++) {
						moduleCell(g, installed[deck[i].ordinal()], i % 2 == 0 ? 8 : 90, 28 + (i / 2) * 11);
					}
				}
			}
		}
	}

	private void moduleCell(GuiGraphicsExtractor g, @Nullable ModuleType type, int x, int y) {
		g.fill(x - 1, y - 1, x + 79, y + 9, 0xFFB4B4B4);
		Component name = Component.translatable(type == null ? "gui.vapor_trilhos.empty_slot" : "module.vapor_trilhos." + type.id());
		g.text(font, name, x + 39 - font.width(name) / 2, y, type == null ? 0xFF7A7A7A : 0xFF2E4F7A, false);
	}

	/** Retângulo com borda clara em cima/esquerda e escura embaixo/direita, no estilo vanilla. */
	private static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int topLeft, int bottomRight) {
		g.fill(x, y, x + w, y + h, fill);
		g.fill(x, y, x + w - 1, y + 1, topLeft);
		g.fill(x, y, x + 1, y + h - 1, topLeft);
		g.fill(x + 1, y + h - 1, x + w, y + h, bottomRight);
		g.fill(x + w - 1, y + 1, x + w, y + h, bottomRight);
	}
}
