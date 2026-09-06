package com.smartrecipe.screen;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

/**
 * Builds the recipe book, in the one flavour this client can actually load.
 *
 * <p>The navigable variant names Pandorical in its class signature, and Pandorical is a
 * suggestion rather than a requirement. The flag is tested here, in a class that always loads, so
 * a client without Pandorical never reaches the class that names it - the same isolation
 * couch-controls uses for its own Pandorical integration.
 */
public final class RecipeBookScreens {
	private RecipeBookScreens() {}

	private static final boolean PANDORICAL_LOADED = FabricLoader.getInstance().isModLoaded("pandorical");

	public static SmartRecipeBookScreen open(Screen parent, RecipeMode mode) {
		return PANDORICAL_LOADED
			? new NavigableRecipeBookScreen(parent, mode)
			: new SmartRecipeBookScreen(parent, mode);
	}

	public static SmartRecipeBookScreen open(Screen parent, RecipeMode mode, String stationCategory) {
		return PANDORICAL_LOADED
			? new NavigableRecipeBookScreen(parent, mode, stationCategory)
			: new SmartRecipeBookScreen(parent, mode, stationCategory);
	}
}
