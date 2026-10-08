package io.github.projetosdehiago.vaportrilhos.boiler;

import static io.github.projetosdehiago.vaportrilhos.boiler.BalanceConstants.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BoilerSimulationTest {
	private static final int COAL_TICKS = 1600;

	private final BoilerSimulation.Load load = new BoilerSimulation.Load();
	private final BoilerSimulation.Result result = new BoilerSimulation.Result();

	/** Fonte de combustível com uma quantidade fixa de itens de carvão. */
	private static final class Coal implements BoilerSimulation.FuelSource {
		int items;

		Coal(int items) {
			this.items = items;
		}

		@Override
		public int takeFuel() {
			if (items <= 0) {
				return 0;
			}
			items--;
			return COAL_TICKS;
		}
	}

	private static BoilerState fullBoiler() {
		BoilerState s = new BoilerState();
		s.waterMb = WATER_CAPACITY_MB;
		return s;
	}

	private float runSeconds(BoilerState s, BoilerSimulation.FuelSource fuel, float seconds) {
		float wear = 0f;
		int ticks = Math.round(seconds * TICKS_PER_SECOND);
		for (int i = 0; i < ticks; i++) {
			BoilerSimulation.tick(s, load, fuel, result);
			wear += result.wear;
		}
		return wear;
	}

	@Test
	void coldStartBoilsInAbout20sAndDrivesInAbout30s() {
		BoilerState s = fullBoiler();
		Coal coal = new Coal(10);
		assertTrue(BoilerSimulation.ignite(s, coal));

		runSeconds(s, coal, 19f);
		assertTrue(s.temperatureC < BOILING_C, "ainda não ferveu aos 19 s");
		runSeconds(s, coal, 2f);
		assertTrue(s.temperatureC >= BOILING_C, "ferveu por volta dos 20 s");

		runSeconds(s, coal, 9f);
		assertTrue(s.pressureBar >= MIN_DRIVE_BAR - 0.3f && s.pressureBar < MIN_DRIVE_BAR + 0.5f,
				"perto de 2 bar aos ~30 s, foi " + s.pressureBar);
	}

	@Test
	void normalDamperUsesAbout20mbOfWaterPerSecond() {
		BoilerState s = fullBoiler();
		s.temperatureC = BOILING_C;
		Coal coal = new Coal(10);
		BoilerSimulation.ignite(s, coal);
		float before = s.waterMb;
		runSeconds(s, coal, 10f);
		float perSecond = (before - s.waterMb) / 10f;
		assertEquals(20f, perSecond, 0.5f);
	}

	@Test
	void temperatureTracksSaturationWhileBoiling() {
		BoilerState s = fullBoiler();
		s.temperatureC = BOILING_C;
		Coal coal = new Coal(10);
		BoilerSimulation.ignite(s, coal);
		runSeconds(s, coal, 15f);
		assertEquals(BOILING_C + SATURATION_C_PER_BAR * s.pressureBar, s.temperatureC, 0.01f);
	}

	@Test
	void safetyValveVentsTo8BarAndWears() {
		BoilerState s = fullBoiler();
		s.temperatureC = 200f;
		s.pressureBar = 9.999f;
		Coal coal = new Coal(10);
		BoilerSimulation.ignite(s, coal);
		BoilerSimulation.tick(s, load, coal, result);
		assertTrue(result.safetyVented);
		assertEquals(SAFETY_VALVE_RESET_BAR, s.pressureBar, 0.001f);
		assertEquals(WEAR_VENT, result.wear, 0.001f);
	}

	@Test
	void fireGoesOutWithoutFuelAndPressureDrops() {
		BoilerState s = fullBoiler();
		s.temperatureC = 150f;
		s.pressureBar = 5f;
		Coal coal = new Coal(1);
		BoilerSimulation.ignite(s, coal);
		s.burnRemaining = 1f;
		BoilerSimulation.tick(s, load, coal, result);
		assertTrue(result.fireWentOut);
		assertFalse(s.fireLit);
		runSeconds(s, coal, 10f);
		assertEquals(5f - NO_FIRE_LOSS_BAR_PER_S * 10f, s.pressureBar, 0.05f);
	}

	@Test
	void dryBoilerOverheatsWearsAndPulses() {
		BoilerState s = new BoilerState();
		s.temperatureC = 125f;
		Coal coal = new Coal(10);
		BoilerSimulation.ignite(s, coal);
		float wear = runSeconds(s, coal, 10f);
		assertTrue(s.temperatureC > 160f, "sem água o calor só sobe");
		assertEquals(WEAR_DRY_PER_S * 10f, wear, 0.2f);
		assertTrue(s.isDryOverheating());
	}

	@Test
	void criticalDryBoilerWearsFaster() {
		BoilerState s = new BoilerState();
		s.temperatureC = 310f;
		Coal coal = new Coal(10);
		BoilerSimulation.ignite(s, coal);
		float wear = runSeconds(s, coal, 5f);
		assertEquals(WEAR_DRY_CRITICAL_PER_S * 5f, wear, 0.2f);
	}

	@Test
	void refillingAHotDryBoilerCausesThermalShock() {
		BoilerState s = new BoilerState();
		s.temperatureC = 250f;
		assertTrue(BoilerSimulation.addWater(s, BUCKET_MB));
		assertEquals(BOILING_C, s.temperatureC, 0.001f);
		assertEquals(BUCKET_MB, s.waterMb, 0.001f);

		BoilerState cool = new BoilerState();
		cool.temperatureC = 150f;
		assertFalse(BoilerSimulation.addWater(cool, BUCKET_MB));
	}

	@Test
	void tankDoesNotOverflow() {
		BoilerState s = fullBoiler();
		BoilerSimulation.addWater(s, BUCKET_MB);
		assertEquals(WATER_CAPACITY_MB, s.waterMb, 0.001f);
	}

	@Test
	void drivingAtFullSpeedConsumesSteam() {
		BoilerState idle = fullBoiler();
		BoilerState driving = fullBoiler();
		for (BoilerState s : new BoilerState[] {idle, driving}) {
			s.temperatureC = 160f;
			s.pressureBar = 6f;
		}
		Coal a = new Coal(10);
		Coal b = new Coal(10);
		BoilerSimulation.ignite(idle, a);
		BoilerSimulation.ignite(driving, b);
		runSeconds(idle, a, 10f);
		load.speedFraction = 1f;
		runSeconds(driving, b, 10f);
		assertEquals(DRIVE_BAR_PER_S * 10f, idle.pressureBar - driving.pressureBar, 0.01f);
	}

	@Test
	void manualVentHasCooldown() {
		BoilerState s = fullBoiler();
		s.pressureBar = 6f;
		assertTrue(BoilerSimulation.manualVent(s));
		assertEquals(4f, s.pressureBar, 0.001f);
		assertFalse(BoilerSimulation.manualVent(s));
	}

	@Test
	void closedDamperBurnsSlowlyAndMakesNoSteam() {
		BoilerState s = fullBoiler();
		s.temperatureC = BOILING_C;
		s.damper = Damper.CLOSED;
		Coal coal = new Coal(1);
		BoilerSimulation.ignite(s, coal);
		runSeconds(s, coal, 10f);
		assertEquals(COAL_TICKS - 200 * Damper.CLOSED.fuelRate, s.burnRemaining, 0.01f);
		assertEquals(0f, s.pressureBar, 0.001f);
	}

	@Test
	void powerFactorRange() {
		assertEquals(0f, powerFactor(1.9f));
		assertEquals(0.5f, powerFactor(5f), 0.001f);
		assertEquals(1f, powerFactor(11f));
	}
}
