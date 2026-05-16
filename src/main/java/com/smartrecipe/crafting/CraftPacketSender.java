package com.smartrecipe.crafting;

import com.smartrecipe.SmartRecipeBookMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

/**
 * Sends crafting packets directly, bypassing the vanilla recipe book widget.
 */
public class CraftPacketSender {

	public static void sendCraftRequest(RecipeDisplayId recipeId, boolean craftAll) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.getConnection() == null) {
			SmartRecipeBookMod.LOGGER.error("Cannot send craft request — no player or network handler");
			return;
		}

		int syncId = client.player.containerMenu.containerId;

		SmartRecipeBookMod.LOGGER.debug("CraftRequest: {} (syncId={}, craftAll={})", recipeId, syncId, craftAll);
		ServerboundPlaceRecipePacket packet = new ServerboundPlaceRecipePacket(syncId, recipeId, craftAll);
		client.getConnection().send(packet);
	}
}
