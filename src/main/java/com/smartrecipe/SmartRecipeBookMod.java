package com.smartrecipe;

import com.smartrecipe.brewing.BrewingRecipesPayload;
import com.smartrecipe.crafting.AutoCraftExecutor;
import com.smartrecipe.recipe.CraftCountTracker;
import com.smartrecipe.recipe.RecipeCache;
import com.smartrecipe.recipe.RecipeCatalogPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmartRecipeBookMod implements ClientModInitializer {
	public static final String MOD_ID = "smart-recipe-book";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("Smart Recipe Book initialized");

		ClientTickEvents.END_CLIENT_TICK.register(AutoCraftExecutor::onClientTick);

		// Registering this receiver is also how the server learns to send it: Fabric answers
		// canSend() from the receivers a client declares, so a server running this mod tells
		// exactly the clients that can use the recipes and nobody else.
		ClientPlayNetworking.registerGlobalReceiver(BrewingRecipesPayload.TYPE,
			(payload, context) -> RecipeCache.setBrewingRecipes(payload.recipes()));
		ClientPlayNetworking.registerGlobalReceiver(RecipeCatalogPayload.TYPE,
			(payload, context) -> RecipeCache.receiveCatalog(payload.first(), payload.last(), payload.recipes()));

		// Clean up execution state and recipe cache when disconnecting
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			AutoCraftExecutor.reset();
			RecipeCache.clear();
			CraftCountTracker.clear();
		});
	}
}
