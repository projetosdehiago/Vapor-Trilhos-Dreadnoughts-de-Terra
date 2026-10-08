package io.github.projetosdehiago.vaportrilhos.registry;

import io.github.projetosdehiago.vaportrilhos.VaporTrilhos;
import io.github.projetosdehiago.vaportrilhos.landship.LandshipMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

public final class ModMenus {
	private ModMenus() {
	}

	/** Os dados de abertura são o id da entidade do landship. */
	public static final ExtendedMenuType<LandshipMenu, Integer> LANDSHIP = Registry.register(BuiltInRegistries.MENU,
			VaporTrilhos.id("landship"), new ExtendedMenuType<>(LandshipMenu::client, ByteBufCodecs.VAR_INT));

	public static void init() {
	}
}
