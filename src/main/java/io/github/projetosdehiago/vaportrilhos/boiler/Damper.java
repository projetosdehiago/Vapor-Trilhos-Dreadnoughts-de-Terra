package io.github.projetosdehiago.vaportrilhos.boiler;

/** Abafador da fornalha: controla o calor gerado e o ritmo de queima do combustível. */
public enum Damper {
	CLOSED(0f, 0.25f, 0f),
	NORMAL(4f, 1f, 0.20f),
	OPEN(6f, 2f, 0.30f);

	/** Aquecimento em °C/s enquanto abaixo do ponto de ebulição (ou com a caldeira seca). */
	public final float heatCPerSecond;
	/** Multiplicador do consumo de combustível. */
	public final float fuelRate;
	/** Geração de vapor em bar/s com água e temperatura de ebulição. */
	public final float steamBarPerSecond;

	Damper(float heatCPerSecond, float fuelRate, float steamBarPerSecond) {
		this.heatCPerSecond = heatCPerSecond;
		this.fuelRate = fuelRate;
		this.steamBarPerSecond = steamBarPerSecond;
	}

	public Damper next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static Damper byId(int id) {
		Damper[] all = values();
		return id >= 0 && id < all.length ? all[id] : NORMAL;
	}
}
