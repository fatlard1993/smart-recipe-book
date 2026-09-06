package com.smartrecipe.crafting;

import com.smartrecipe.SmartRecipeBookMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

/**
 * Sends crafting packets directly, bypassing the vanilla recipe book widget.
 */
public class CraftPacketSender {

	/**
	 * Tested here, in a class that always loads, so that a client without Pandorical never
	 * reaches {@link PandoricalStations} - which names Pandorical's own types.
	 */
	private static final boolean PANDORICAL_LOADED = FabricLoader.getInstance().isModLoaded("pandorical");

	public static void sendCraftRequest(RecipeDisplayId recipeId, boolean craftAll) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.getConnection() == null) {
			SmartRecipeBookMod.LOGGER.error("Cannot send craft request: no player or network handler");
			return;
		}

		// A Pandorical station fills its own grid; the vanilla packet would be dropped there.
		if (PANDORICAL_LOADED && PandoricalStations.sendPlaceRecipe(client, recipeId, craftAll)) {
			return;
		}

		int syncId = client.player.containerMenu.containerId;

		SmartRecipeBookMod.LOGGER.debug("CraftRequest: {} (syncId={}, craftAll={})", recipeId, syncId, craftAll);
		ServerboundPlaceRecipePacket packet = new ServerboundPlaceRecipePacket(syncId, recipeId, craftAll);
		client.getConnection().send(packet);
	}

	/**
	 * Whether the player is at a Pandorical crafting station.
	 *
	 * <p>A station changes what crafting means: there is no grid of the player's own to run a
	 * multi-step plan through, so it gets the one thing a book does at a crafting table - the
	 * recipe laid out in the bench's grid, for the player to take.
	 */
	public static boolean atStation(Minecraft client) {
		return PANDORICAL_LOADED && PandoricalStations.openStation(client) != null;
	}
}
