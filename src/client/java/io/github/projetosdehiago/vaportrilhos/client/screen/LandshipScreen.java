package io.github.projetosdehiago.vaportrilhos.client.screen;

import io.github.projetosdehiago.vaportrilhos.client.hud.Gauges;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipEntity;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Painel da caldeira: medidores, combustível e botões. Desenhado sem textura própria. */
public class LandshipScreen extends AbstractContainerScreen<LandshipMenu> {
	private static final int BG = 0xFFC6C6C6;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SHADOW = 0xFF555555;
	private static final int SLOT_BG = 0xFF8B8B8B;
	private static final int SLOT_DARK = 0xFF373737;

	private Button fireButton;
	private Button damperButton;

	public LandshipScreen(LandshipMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, LandshipMenu.IMAGE_HEIGHT);
		this.inventoryLabelY = LandshipMenu.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		int x = leftPos;
		int y = topPos;
		fireButton = addRenderableWidget(Button.builder(Component.empty(), b -> press(LandshipMenu.BUTTON_FIRE))
				.bounds(x + 66, y + 68, 52, 18).build());
		addRenderableWidget(Button.builder(Component.translatable("button.vapor_trilhos.vent"), b -> press(LandshipMenu.BUTTON_VENT))
				.bounds(x + 120, y + 68, 50, 18).build());
		damperButton = addRenderableWidget(Button.builder(Component.empty(), b -> press(LandshipMenu.BUTTON_DAMPER))
				.bounds(x + 66, y + 88, 104, 18).build());
		updateButtons();
	}

	private void press(int buttonId) {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		updateButtons();
	}

	private void updateButtons() {
		LandshipEntity landship = menu.getLandship();
		if (landship == null || fireButton == null) {
			return;
		}
		fireButton.setMessage(Component.translatable(landship.isFireLit() ? "button.vapor_trilhos.extinguish" : "button.vapor_trilhos.ignite"));
		damperButton.setMessage(Component.translatable("button.vapor_trilhos.damper",
				Component.translatable("damper.vapor_trilhos." + landship.getDamper().name().toLowerCase(Locale.ROOT))));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = leftPos;
		int y = topPos;
		bevel(g, x, y, imageWidth, imageHeight, BG, LIGHT, SHADOW);
		for (Slot slot : menu.slots) {
			int sx = x + slot.x - 1;
			int sy = y + slot.y - 1;
			bevel(g, sx, sy, 18, 18, SLOT_BG, SLOT_DARK, LIGHT);
		}
		LandshipEntity landship = menu.getLandship();
		if (landship != null) {
			Gauges.boilerRows(g, font, landship, x + 8, y + 18, 50, 66, Gauges.TEXT_DARK);
			// barra de queima sob os espaços de combustível
			int bx = x + LandshipMenu.FUEL_X - 1;
			int by = y + LandshipMenu.FUEL_Y + 19;
			g.fill(bx, by, bx + 54, by + 3, SLOT_DARK);
			int filled = Math.round(landship.getBurnFraction() * 54);
			g.fill(bx, by, bx + filled, by + 3, landship.isFireLit() ? 0xFFF09A30 : 0xFF7A7A7A);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
		super.extractLabels(g, xm, ym);
		g.text(font, Component.translatable("gui.vapor_trilhos.fuel"), LandshipMenu.FUEL_X, LandshipMenu.FUEL_Y - 10, Gauges.TEXT_DARK, false);
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
