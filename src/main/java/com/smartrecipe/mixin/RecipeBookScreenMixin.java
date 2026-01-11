package com.smartrecipe.mixin;

import com.smartrecipe.SmartRecipeBookMod;
import com.smartrecipe.recipe.RecipeCache;
import com.smartrecipe.screen.SmartRecipeBookScreen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept when the recipe book button is clicked.
 * Opens our custom SmartRecipeBookScreen instead of the vanilla recipe book widget.
 * Only applies to crafting screens (inventory and crafting table), not furnaces.
 */
@Mixin(RecipeBookScreen.class)
public abstract class RecipeBookScreenMixin {

	/**
	 * Intercept the recipe book button click handler BEFORE it toggles the vanilla recipe book.
	 * method_64513 is the button click callback that calls recipeBook.toggleOpen()
	 */
	@Inject(
		method = "method_64513",
		at = @At("HEAD"),
		cancellable = true
	)
	private void onRecipeBookButtonClick(ButtonWidget button, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();

		// Only intercept for crafting screens (inventory and crafting table)
		// Let furnaces, blast furnaces, smokers use vanilla recipe book
		if (!(client.currentScreen instanceof InventoryScreen) &&
			!(client.currentScreen instanceof CraftingScreen)) {
			return; // Not a crafting screen, let vanilla handle it
		}

		// Only intercept if we have recipes in our cache
		if (!RecipeCache.hasRecipes()) {
			SmartRecipeBookMod.LOGGER.warn("RecipeCache is empty, letting vanilla handle recipe book");
			return; // Let vanilla handle it
		}

		// Cancel the vanilla toggle
		ci.cancel();

		// Open our custom recipe book screen
		SmartRecipeBookMod.LOGGER.info("Opening SmartRecipeBookScreen with {} recipes", RecipeCache.getRecipeCount());
		client.setScreen(new SmartRecipeBookScreen(client.currentScreen));
	}
}
