package io.github.projetosdehiago.vaportrilhos.landship;

import io.github.projetosdehiago.vaportrilhos.registry.ModSounds;
import java.util.Locale;
import net.minecraft.sounds.SoundEvent;

/**
 * Opções de apito do landship (tecla H). Cada landship guarda a sua; todos por perto ouvem.
 * A recarga acompanha a duração do som, para um não tocar por cima do outro. Os quatro primeiros
 * são sintetizados por {@code design/audio/gen_whistles.py}; o personalizado é um áudio enviado.
 */
public enum WhistleSound {
	STEAM(50),
	FOGHORN(70),
	BELL(80),
	WAR_HORN(60),
	/** Áudio enviado pelo usuário ({@code sounds/whistle/gemidao.ogg}). */
	CUSTOM(140);

	public final int cooldownTicks;

	WhistleSound(int cooldownTicks) {
		this.cooldownTicks = cooldownTicks;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public SoundEvent sound() {
		return switch (this) {
			case STEAM -> ModSounds.WHISTLE;
			case FOGHORN -> ModSounds.WHISTLE_FOGHORN;
			case BELL -> ModSounds.WHISTLE_BELL;
			case WAR_HORN -> ModSounds.WHISTLE_WAR_HORN;
			case CUSTOM -> ModSounds.WHISTLE_CUSTOM;
		};
	}

	public static WhistleSound byId(int id) {
		WhistleSound[] all = values();
		return id >= 0 && id < all.length ? all[id] : STEAM;
	}
}
