package com.smartrecipe.crafting;

import com.smartrecipe.SmartRecipeBookMod;
import justfatlard.pandorical.api.ScreenApi;
import justfatlard.pandorical.client.screen.ScreenHelper;
import justfatlard.pandorical.protocol.OpenScreenS2C;
import justfatlard.pandorical.screen.PandoricalMenu;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

/**
 * Everything that names a Pandorical class, kept in one place nothing loads without it.
 *
 * <p>Pandorical is a suggestion here, not a requirement, and a type named in a signature is
 * resolved when its class loads - so these calls cannot live in {@link CraftPacketSender}, which
 * every craft goes through. Reached only behind that mod's loaded flag; a client without it never
 * touches this class.
 */
final class PandoricalStations {
	private PandoricalStations() {}

	/** The Pandorical crafting station the player has open, or null. */
	static OpenScreenS2C openStation(Minecraft client) {
		if (client.player == null) return null;
		if (!(client.player.containerMenu instanceof PandoricalMenu menu)) return null;
		OpenScreenS2C def = menu.getScreenDef();
		return def != null && def.recipeStation().isPresent() ? def : null;
	}

	/**
	 * Ask the station to lay the recipe out in its own grid; true when it was asked.
	 *
	 * <p>{@code ServerboundPlaceRecipePacket} is answered only for a {@code RecipeBookMenu}, and a
	 * Pandorical menu is not one: sent there it is dropped without a word, which is why a recipe
	 * picked at a fletching table used to show, promise, and do nothing at all.
	 */
	static boolean sendPlaceRecipe(Minecraft client, RecipeDisplayId recipeId, boolean craftAll) {
		OpenScreenS2C def = openStation(client);
		if (def == null) return false;

		SmartRecipeBookMod.LOGGER.debug("CraftRequest to station {}: {} (craftAll={})",
			def.recipeStation().get(), recipeId, craftAll);
		ScreenHelper.sendAction(def.screenId(), ScreenApi.PLACE_RECIPE_COMPONENT, Map.of(
			ScreenApi.PLACE_RECIPE_DATA_RECIPE, String.valueOf(recipeId.index()),
			ScreenApi.PLACE_RECIPE_DATA_ALL, String.valueOf(craftAll)));
		return true;
	}
}
