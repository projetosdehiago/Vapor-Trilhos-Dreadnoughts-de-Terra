package io.github.projetosdehiago.vaportrilhos.boiler;

/**
 * Estado mutável da caldeira. Não depende do Minecraft: quem persiste e sincroniza é a entidade.
 */
public final class BoilerState {
	public float waterMb;
	public float temperatureC = BalanceConstants.AMBIENT_C;
	public float pressureBar;
	public boolean fireLit;
	public Damper damper = Damper.NORMAL;
	/** Ticks de queima restantes do item de combustível atual (fracionário por causa do abafador). */
	public float burnRemaining;
	/** Ticks de queima totais do item atual (para a barra de combustível). */
	public float burnTotal;
	/** Ticks até o próximo pulso de vapor cegante com a caldeira seca. */
	public int dryPulseTimer;
	/** Ticks até a válvula de alívio manual poder ser usada de novo. */
	public int manualVentCooldown;

	public boolean isDryOverheating() {
		return waterMb <= 0f && temperatureC > BalanceConstants.DRY_OVERHEAT_C;
	}

	public float waterFraction() {
		return waterMb / BalanceConstants.WATER_CAPACITY_MB;
	}

	public float burnFraction() {
		return burnTotal <= 0f ? 0f : Math.max(0f, burnRemaining / burnTotal);
	}

	public void copyFrom(BoilerState other) {
		waterMb = other.waterMb;
		temperatureC = other.temperatureC;
		pressureBar = other.pressureBar;
		fireLit = other.fireLit;
		damper = other.damper;
		burnRemaining = other.burnRemaining;
		burnTotal = other.burnTotal;
		dryPulseTimer = other.dryPulseTimer;
		manualVentCooldown = other.manualVentCooldown;
	}
}
