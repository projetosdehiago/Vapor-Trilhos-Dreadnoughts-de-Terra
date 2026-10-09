package io.github.projetosdehiago.vaportrilhos.test;

import io.github.projetosdehiago.vaportrilhos.VaporTrilhos;
import io.github.projetosdehiago.vaportrilhos.registry.ModCreativeTab;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

/**
 * Teste de cliente da aba do criativo ({@code ./gradlew runClientGameTest}): abre o inventário
 * do criativo na aba do mod, confere os itens e tira um print das texturas. Confere também
 * que as traduções copiadas no build (pt_pt e variantes do inglês) chegaram ao jogo.
 */
public class CreativeTabClientGameTest implements FabricClientGameTest {
	private static final int ITEMS_IN_TAB = 11;
	private static final String[] COPIED_LANGUAGES = {"pt_pt", "en_gb", "en_au", "en_ca", "en_nz"};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getServer().runCommand("gamemode creative @a");
			context.waitTicks(20);

			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(CreativeModeInventoryScreen.class);
			context.runOnClient(minecraft -> ((FabricCreativeModeInventoryScreen) minecraft.gui.screen()).setSelectedTab(ModCreativeTab.TAB));
			context.waitTicks(5);

			int items = context.computeOnClient(minecraft -> ModCreativeTab.TAB.getDisplayItems().size());
			if (items != ITEMS_IN_TAB) {
				throw new AssertionError("a aba do mod devia ter " + ITEMS_IN_TAB + " itens, tem " + items);
			}
			context.takeScreenshot("creative-tab");

			for (String language : COPIED_LANGUAGES) {
				boolean present = context.computeOnClient(minecraft -> minecraft.getResourceManager()
						.getResource(VaporTrilhos.id("lang/" + language + ".json")).isPresent());
				if (!present) {
					throw new AssertionError("falta a tradução " + language + " no jogo");
				}
			}
		}
	}
}
