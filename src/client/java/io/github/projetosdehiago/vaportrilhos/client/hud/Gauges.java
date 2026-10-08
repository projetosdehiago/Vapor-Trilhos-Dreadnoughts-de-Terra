package io.github.projetosdehiago.vaportrilhos.client.hud;

import io.github.projetosdehiago.vaportrilhos.boiler.BalanceConstants;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipEntity;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Desenho dos medidores da caldeira, usado no HUD e no painel. */
public final class Gauges {
	private Gauges() {
	}

	public static final int TEXT = 0xFFE8E2D4;
	public static final int TEXT_DARK = 0xFF3F3F3F;
	private static final int BAR_BG = 0xFF2A2A2A;
	private static final int WATER = 0xFF3F76E4;
	private static final int TEMPERATURE = 0xFFE07B39;
	private static final int PRESSURE = 0xFFC9C3B4;
	private static final int RED_ZONE = 0xFFB02E26;
	private static final float TEMPERATURE_SCALE = 320f;

	/** Uma linha: rótulo, barra e valor. Devolve a altura usada. */
	public static void row(GuiGraphicsExtractor g, Font font, int x, int y, int labelWidth, int barWidth,
			Component label, float fraction, int color, String value, int textColor, float markerFraction) {
		g.text(font, label, x, y + 1, textColor, false);
		int bx = x + labelWidth;
		g.fill(bx, y, bx + barWidth, y + 9, BAR_BG);
		int filled = Math.round(Mth.clamp(fraction, 0f, 1f) * (barWidth - 2));
		g.fill(bx + 1, y + 1, bx + 1 + filled, y + 8, color);
		if (markerFraction > 0f) {
			int mx = bx + 1 + Math.round(markerFraction * (barWidth - 2));
			g.fill(mx, y, mx + 1, y + 9, RED_ZONE);
		}
		g.text(font, value, bx + barWidth + 4, y + 1, textColor, false);
	}

	public static int integrityColor(float fraction) {
		if (fraction < BalanceConstants.CRITICAL_INTEGRITY_FRACTION) {
			return RED_ZONE;
		}
		if (fraction < BalanceConstants.LOW_INTEGRITY_FRACTION) {
			return 0xFFE0B739;
		}
		return 0xFF5DBB47;
	}

	/** As quatro linhas padrão (água, temperatura, pressão, integridade). */
	public static void boilerRows(GuiGraphicsExtractor g, Font font, LandshipEntity landship, int x, int y,
			int labelWidth, int barWidth, int textColor) {
		float water = landship.getWaterMb();
		row(g, font, x, y, labelWidth, barWidth, Component.translatable("gauge.vapor_trilhos.water"),
				water / BalanceConstants.WATER_CAPACITY_MB, WATER, String.format(Locale.ROOT, "%.0f mB", water), textColor, 0f);

		float temperature = landship.getTemperature();
		boolean overheating = landship.isDryOverheating();
		row(g, font, x, y + 11, labelWidth, barWidth, Component.translatable("gauge.vapor_trilhos.temperature"),
				temperature / TEMPERATURE_SCALE, overheating ? RED_ZONE : TEMPERATURE,
				String.format(Locale.ROOT, "%.0f °C", temperature), textColor, 0f);

		float pressure = landship.getPressure();
		row(g, font, x, y + 22, labelWidth, barWidth, Component.translatable("gauge.vapor_trilhos.pressure"),
				pressure / BalanceConstants.MAX_BAR, pressure >= BalanceConstants.RED_ZONE_BAR ? RED_ZONE : PRESSURE,
				String.format(Locale.ROOT, "%.1f bar", pressure), textColor,
				BalanceConstants.SAFETY_VALVE_BAR / BalanceConstants.MAX_BAR);

		float integrity = landship.getIntegrity() / BalanceConstants.MAX_INTEGRITY;
		row(g, font, x, y + 33, labelWidth, barWidth, Component.translatable("gauge.vapor_trilhos.integrity"),
				integrity, integrityColor(integrity), Math.round(integrity * 100f) + "%", textColor, 0f);
	}

	public static Component fireAndDamper(LandshipEntity landship) {
		Component fire = Component.translatable(landship.isFireLit() ? "gauge.vapor_trilhos.fire_lit" : "gauge.vapor_trilhos.fire_out");
		Component damper = Component.translatable("damper.vapor_trilhos." + landship.getDamper().name().toLowerCase(Locale.ROOT));
		return Component.translatable("gauge.vapor_trilhos.fire_and_damper", fire, damper);
	}
}
