package com.smartrecipe.mixin;

import com.smartrecipe.recipe.RecipeCache;
import com.smartrecipe.screen.RecipeMode;
import com.smartrecipe.screen.SmartRecipeBookScreen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AbstractFurnaceScreen;
import net.minecraft.client.gui.screen.ingame.BlastFurnaceScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.FurnaceScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import net.minecraft.client.gui.screen.ingame.SmokerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept when the recipe book button is clicked.
 * Opens our custom SmartRecipeBookScreen instead of the vanilla recipe book widget.
 * Supports crafting screens (inventory/crafting table) and furnace screens.
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

		// Determine the recipe mode based on screen type
		RecipeMode mode;
		if (client.currentScreen instanceof InventoryScreen ||
			client.currentScreen instanceof CraftingScreen) {
			mode = RecipeMode.CRAFTING;
		} else if (client.currentScreen instanceof BlastFurnaceScreen) {
			mode = RecipeMode.BLAST_FURNACE;
		} else if (client.currentScreen instanceof SmokerScreen) {
			mode = RecipeMode.SMOKER;
		} else if (client.currentScreen instanceof FurnaceScreen) {
			mode = RecipeMode.FURNACE;
		} else if (client.currentScreen instanceof AbstractFurnaceScreen) {
			// Fallback for any other furnace-type screens (mod compatibility)
			mode = RecipeMode.FURNACE;
		} else {
			// Unknown screen type, let vanilla handle it
			return;
		}

		// Only intercept if we have recipes in our cache
		if (!RecipeCache.hasRecipes()) {
			return; // Let vanilla handle it
		}

		// Cancel the vanilla toggle and open our custom recipe book screen
		ci.cancel();
		client.setScreen(new SmartRecipeBookScreen(client.currentScreen, mode));
	}
}
