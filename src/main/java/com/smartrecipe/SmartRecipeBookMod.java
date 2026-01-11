package com.smartrecipe;

import com.smartrecipe.crafting.AutoCraftExecutor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmartRecipeBookMod implements ClientModInitializer {
	public static final String MOD_ID = "smart-recipe-book";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("Smart Recipe Book initialized");

		// Register tick event for auto-craft execution
		ClientTickEvents.END_CLIENT_TICK.register(AutoCraftExecutor::onClientTick);
	}
}
