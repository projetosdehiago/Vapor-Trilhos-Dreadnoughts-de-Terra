package io.github.projetosdehiago.vaportrilhos.client.hud;

import io.github.projetosdehiago.vaportrilhos.landship.LandshipEntity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Medidores da caldeira ao lado da hotbar, enquanto o jogador está a bordo. */
public class LandshipHud implements HudElement {
	private static final int LABEL_WIDTH = 48;
	private static final int BAR_WIDTH = 56;
	private static final int PANEL_WIDTH = LABEL_WIDTH + BAR_WIDTH + 48;
	private static final int PANEL_HEIGHT = 58;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof LandshipEntity landship)) {
			return;
		}
		Font font = minecraft.font;
		int x = g.guiWidth() / 2 - 91 - PANEL_WIDTH - 8;
		int y = g.guiHeight() - PANEL_HEIGHT - 2;
		if (x < 2) {
			// tela estreita: acima da hotbar, à esquerda
			x = 2;
			y = g.guiHeight() - PANEL_HEIGHT - 50;
		}
		g.fill(x - 4, y - 4, x + PANEL_WIDTH, y + PANEL_HEIGHT - 2, 0x90000000);
		Gauges.boilerRows(g, font, landship, x, y, LABEL_WIDTH, BAR_WIDTH, Gauges.TEXT);
		g.text(font, Gauges.fireAndDamper(landship), x, y + 45, Gauges.TEXT, false);
	}
}
