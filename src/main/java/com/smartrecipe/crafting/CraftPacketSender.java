package com.smartrecipe.crafting;

import com.smartrecipe.SmartRecipeBookMod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.CraftRequestC2SPacket;
import net.minecraft.recipe.NetworkRecipeId;

/**
 * Sends crafting packets directly, bypassing the vanilla recipe book widget.
 */
public class CraftPacketSender {

	public static void sendCraftRequest(NetworkRecipeId recipeId, boolean craftAll) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.getNetworkHandler() == null) {
			SmartRecipeBookMod.LOGGER.error("Cannot send craft request — no player or network handler");
			return;
		}

		int syncId = client.player.currentScreenHandler.syncId;

		SmartRecipeBookMod.LOGGER.debug("CraftRequest: {} (syncId={}, craftAll={})", recipeId, syncId, craftAll);
		CraftRequestC2SPacket packet = new CraftRequestC2SPacket(syncId, recipeId, craftAll);
		client.getNetworkHandler().sendPacket(packet);
	}
}
