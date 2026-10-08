package io.github.projetosdehiago.vaportrilhos;

import io.github.projetosdehiago.vaportrilhos.network.ModNetworking;
import io.github.projetosdehiago.vaportrilhos.registry.ModCreativeTab;
import io.github.projetosdehiago.vaportrilhos.registry.ModDataComponents;
import io.github.projetosdehiago.vaportrilhos.registry.ModEntities;
import io.github.projetosdehiago.vaportrilhos.registry.ModItems;
import io.github.projetosdehiago.vaportrilhos.registry.ModMenus;
import io.github.projetosdehiago.vaportrilhos.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VaporTrilhos implements ModInitializer {
	public static final String MOD_ID = "vapor_trilhos";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModDataComponents.init();
		ModSounds.init();
		ModEntities.init();
		ModItems.init();
		ModMenus.init();
		ModCreativeTab.init();
		ModNetworking.init();
		LOGGER.info("Vapor & Trilhos: caldeiras acendendo");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
