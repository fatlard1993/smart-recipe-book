package com.smartrecipe;

import com.smartrecipe.crafting.AutoCraftExecutor;
import com.smartrecipe.recipe.CraftCountTracker;
import com.smartrecipe.recipe.RecipeCache;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmartRecipeBookMod implements ClientModInitializer {
	public static final String MOD_ID = "smart-recipe-book";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("Smart Recipe Book initialized");

		ClientTickEvents.END_CLIENT_TICK.register(AutoCraftExecutor::onClientTick);

		// Clean up execution state and recipe cache when disconnecting
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			AutoCraftExecutor.reset();
			RecipeCache.clear();
			CraftCountTracker.clear();
		});
	}
}
