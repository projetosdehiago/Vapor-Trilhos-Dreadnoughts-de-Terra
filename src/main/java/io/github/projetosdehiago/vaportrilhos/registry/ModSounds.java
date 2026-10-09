package io.github.projetosdehiago.vaportrilhos.registry;

import io.github.projetosdehiago.vaportrilhos.VaporTrilhos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Eventos de som próprios; quase todos os áudios são do jogo base (ver assets/vapor_trilhos/sounds.json). */
public final class ModSounds {
	private ModSounds() {
	}

	public static final SoundEvent BOILER_IGNITE = register("boiler.ignite");
	public static final SoundEvent BOILER_HISS = register("boiler.hiss");
	public static final SoundEvent BOILER_VENT = register("boiler.vent");
	public static final SoundEvent BOILER_SHOCK = register("boiler.shock");
	public static final SoundEvent BOILER_FILL = register("boiler.fill");
	public static final SoundEvent ENGINE_CHUG = register("engine.chug");
	public static final SoundEvent WHISTLE = register("whistle");
	public static final SoundEvent WHISTLE_FOGHORN = register("whistle.foghorn");
	public static final SoundEvent WHISTLE_BELL = register("whistle.bell");
	public static final SoundEvent WHISTLE_WAR_HORN = register("whistle.war_horn");
	/** O único áudio que vem no mod (os outros são do jogo base). */
	public static final SoundEvent WHISTLE_CUSTOM = register("whistle.custom");
	public static final SoundEvent REPAIR = register("repair");

	private static SoundEvent register(String name) {
		Identifier id = VaporTrilhos.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}
}
