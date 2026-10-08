package io.github.projetosdehiago.vaportrilhos.boiler;

import static io.github.projetosdehiago.vaportrilhos.boiler.BalanceConstants.*;

/**
 * Física simplificada da caldeira (design.md, A3). Um passo = 1 tick (1/20 s).
 *
 * <p>Modelo: abaixo de 100 °C o fogo só aquece. Com água e a 100 °C ou mais, o calor vira
 * vapor (pressão) e a temperatura acompanha a de saturação ({@code 100 + 10 °C/bar}). Sem água,
 * todo o calor sobe a temperatura, sem teto. O motor e os vazamentos consomem pressão.
 */
public final class BoilerSimulation {
	private BoilerSimulation() {
	}

	/** Entrega combustível novo quando o atual acaba. */
	@FunctionalInterface
	public interface FuelSource {
		/** Consome um item e devolve seus ticks de queima, ou 0 se não houver combustível. */
		int takeFuel();
	}

	/** O que o resto do veículo pede da caldeira neste tick. */
	public static final class Load {
		/** Velocidade atual em relação à máxima, de 0 a 1 (frente ou ré). */
		public float speedFraction;
		public boolean pivoting;
		/** Degraus de 1 bloco subidos neste tick. */
		public int stepUps;
		/** Consumo extra de módulos (bar/s), ex.: compactador. */
		public float extraBarPerSecond;
		/** Redução da geração de vapor por módulos (bar/s), ex.: fornalha de alta temperatura. */
		public float steamPenaltyBarPerSecond;
		/** Integridade atual / máxima. */
		public float integrityFraction = 1f;

		public Load reset() {
			speedFraction = 0f;
			pivoting = false;
			stepUps = 0;
			extraBarPerSecond = 0f;
			steamPenaltyBarPerSecond = 0f;
			integrityFraction = 1f;
			return this;
		}
	}

	/** Resultado de um tick: desgaste e eventos que viram partículas, sons e cegueira. */
	public static final class Result {
		public float wear;
		/** Válvula de segurança abriu (sobrepressão). */
		public boolean safetyVented;
		/** Pulso de vapor/fumaça da caldeira seca. */
		public boolean dryPulse;
		/** O fogo apagou neste tick por falta de combustível. */
		public boolean fireWentOut;

		public Result reset() {
			wear = 0f;
			safetyVented = false;
			dryPulse = false;
			fireWentOut = false;
			return this;
		}
	}

	public static void tick(BoilerState s, Load load, FuelSource fuel, Result out) {
		final float dt = 1f / TICKS_PER_SECOND;
		out.reset();
		if (s.manualVentCooldown > 0) {
			s.manualVentCooldown--;
		}

		// --- combustível
		if (s.fireLit) {
			s.burnRemaining -= s.damper.fuelRate;
			if (s.burnRemaining <= 0f) {
				int next = fuel.takeFuel();
				if (next > 0) {
					s.burnRemaining += next;
					s.burnTotal = next;
				} else {
					s.fireLit = false;
					s.burnRemaining = 0f;
					s.burnTotal = 0f;
					out.fireWentOut = true;
				}
			}
		}
		float heat = s.fireLit ? s.damper.heatCPerSecond : 0f;

		// --- temperatura e vapor
		boolean hasWater = s.waterMb > 0f;
		if (hasWater) {
			if (s.temperatureC < BOILING_C) {
				s.temperatureC += (heat > 0f ? heat : -COOLING_C_PER_S) * dt;
				s.temperatureC = Math.max(AMBIENT_C, s.temperatureC);
			} else if (s.fireLit && s.damper.steamBarPerSecond > 0f) {
				float gen = Math.max(0f, s.damper.steamBarPerSecond - load.steamPenaltyBarPerSecond) * dt;
				float waterFactor = load.integrityFraction < LOW_INTEGRITY_FRACTION ? LOW_INTEGRITY_WATER_FACTOR : 1f;
				float waterNeeded = gen * WATER_MB_PER_BAR * waterFactor;
				if (waterNeeded > s.waterMb) {
					gen *= s.waterMb / waterNeeded;
					waterNeeded = s.waterMb;
				}
				s.waterMb -= waterNeeded;
				s.pressureBar += gen;
			}
		} else {
			// caldeira seca: o calor só sobe a temperatura
			s.temperatureC += (heat > 0f ? heat : -COOLING_C_PER_S) * dt;
			s.temperatureC = Math.max(AMBIENT_C, s.temperatureC);
		}

		// --- consumo de pressão
		float drain = s.fireLit ? LEAK_BAR_PER_S : NO_FIRE_LOSS_BAR_PER_S;
		drain += DRIVE_BAR_PER_S * Math.min(1f, Math.abs(load.speedFraction));
		if (load.pivoting) {
			drain += PIVOT_BAR_PER_S;
		}
		drain += load.extraBarPerSecond;
		s.pressureBar -= drain * dt + load.stepUps * STEP_UP_BAR;
		s.pressureBar = Math.max(0f, Math.min(MAX_BAR, s.pressureBar));

		// com água fervendo, a temperatura acompanha a de saturação
		if (s.waterMb > 0f && s.temperatureC >= BOILING_C) {
			float saturation = Math.min(MAX_SATURATION_C, BOILING_C + SATURATION_C_PER_BAR * s.pressureBar);
			if (s.fireLit) {
				s.temperatureC = saturation;
			} else if (s.pressureBar > 0f) {
				s.temperatureC = Math.max(saturation, s.temperatureC - COOLING_C_PER_S * dt);
			} else {
				s.temperatureC -= COOLING_C_PER_S * dt;
			}
		}

		// --- válvula de segurança
		if (s.pressureBar >= SAFETY_VALVE_BAR) {
			s.pressureBar = SAFETY_VALVE_RESET_BAR;
			out.safetyVented = true;
			out.wear += WEAR_VENT;
		}

		// --- caldeira seca
		if (s.isDryOverheating()) {
			out.wear += (s.temperatureC > DRY_CRITICAL_C ? WEAR_DRY_CRITICAL_PER_S : WEAR_DRY_PER_S) * dt;
			if (--s.dryPulseTimer <= 0) {
				s.dryPulseTimer = DRY_BLIND_PULSE_TICKS;
				out.dryPulse = true;
			}
		} else {
			s.dryPulseTimer = 0;
		}
	}

	/**
	 * Adiciona água ao tanque.
	 *
	 * @return {@code true} se causou choque térmico (tanque vazio e caldeira a 200 °C ou mais);
	 *         nesse caso quem chama aplica o desgaste e a explosão de vapor.
	 */
	public static boolean addWater(BoilerState s, float mb) {
		boolean shock = s.waterMb <= 0f && s.temperatureC >= THERMAL_SHOCK_C && mb > 0f;
		s.waterMb = Math.min(WATER_CAPACITY_MB, s.waterMb + mb);
		if (shock) {
			s.temperatureC = BOILING_C;
		}
		return shock;
	}

	/** Espaço livre no tanque, em mB. */
	public static float waterSpace(BoilerState s) {
		return WATER_CAPACITY_MB - s.waterMb;
	}

	/**
	 * Acende a fornalha se houver combustível (o atual ou um novo da fonte).
	 *
	 * @return {@code true} se o fogo está aceso depois da chamada.
	 */
	public static boolean ignite(BoilerState s, FuelSource fuel) {
		if (s.fireLit) {
			return true;
		}
		if (s.burnRemaining <= 0f) {
			int next = fuel.takeFuel();
			if (next <= 0) {
				return false;
			}
			s.burnRemaining = next;
			s.burnTotal = next;
		}
		s.fireLit = true;
		return true;
	}

	/**
	 * Válvula de alívio manual.
	 *
	 * @return {@code true} se abriu (há pressão e a recarga terminou).
	 */
	public static boolean manualVent(BoilerState s) {
		if (s.manualVentCooldown > 0 || s.pressureBar <= 0f) {
			return false;
		}
		s.pressureBar = Math.max(0f, s.pressureBar - MANUAL_VENT_BAR);
		s.manualVentCooldown = Math.round(MANUAL_VENT_COOLDOWN_S * TICKS_PER_SECOND);
		return true;
	}

	/** Apaga o fogo (ex.: caldeira submersa). */
	public static void extinguish(BoilerState s) {
		s.fireLit = false;
	}
}
