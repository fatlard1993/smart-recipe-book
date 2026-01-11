package com.smartrecipe.crafting;

import com.smartrecipe.SmartRecipeBookMod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.CraftRequestC2SPacket;
import net.minecraft.recipe.NetworkRecipeId;

/**
 * Helper class to send crafting packets directly.
 * Bypasses vanilla recipe book entirely.
 */
public class VanillaCraftingHelper {

	// Flag to skip our mixin intercept when we're calling select() ourselves
	private static boolean executingPlan = false;

	/**
	 * Check if we're currently executing a plan (mixin should skip)
	 */
	public static boolean isExecutingPlan() {
		return executingPlan;
	}

	/**
	 * Execute a single recipe by sending CraftRequestC2SPacket directly
	 */
	public static void sendCraftRequest(NetworkRecipeId recipeId, boolean craftAll) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.getNetworkHandler() == null) {
			SmartRecipeBookMod.LOGGER.error("Cannot send craft request - no player or network handler");
			return;
		}

		int syncId = client.player.currentScreenHandler.syncId;

		executingPlan = true;
		try {
			SmartRecipeBookMod.LOGGER.info("Sending CraftRequestC2SPacket for {} (syncId: {}, craftAll: {})",
				recipeId, syncId, craftAll);

			CraftRequestC2SPacket packet = new CraftRequestC2SPacket(syncId, recipeId, craftAll);
			client.getNetworkHandler().sendPacket(packet);

			SmartRecipeBookMod.LOGGER.info("CraftRequestC2SPacket sent");
		} finally {
			executingPlan = false;
		}
	}
}
