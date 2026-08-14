package com.smartrecipe.mixin;

import com.smartrecipe.recipe.RecipeCache;
import com.smartrecipe.screen.RecipeMode;
import com.smartrecipe.screen.SmartRecipeBookScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept when the recipe book button is clicked.
 * Opens our custom SmartRecipeBookScreen instead of the vanilla recipe book widget.
 * Supports crafting screens (inventory/crafting table) and furnace screens.
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class RecipeBookScreenMixin {

	/**
	 * Intercept the recipe book button click handler BEFORE it toggles the vanilla
	 * recipe book. Targets onRecipeBookButtonClick(), which the button press lambda
	 * in initButton() calls.
	 */
	@Inject(
		method = "onRecipeBookButtonClick",
		at = @At("HEAD"),
		cancellable = true
	)
	private void onRecipeBookButtonClick(CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();

		RecipeMode mode;
		if (client.gui.screen() instanceof InventoryScreen ||
			client.gui.screen() instanceof CraftingScreen) {
			mode = RecipeMode.CRAFTING;
		} else if (client.gui.screen() instanceof BlastFurnaceScreen) {
			mode = RecipeMode.BLAST_FURNACE;
		} else if (client.gui.screen() instanceof SmokerScreen) {
			mode = RecipeMode.SMOKER;
		} else if (client.gui.screen() instanceof FurnaceScreen) {
			mode = RecipeMode.FURNACE;
		} else if (client.gui.screen() instanceof AbstractFurnaceScreen) {
			// Fallback for any other furnace-type screens (mod compatibility)
			mode = RecipeMode.FURNACE;
		} else {
			// Unknown screen type, let vanilla handle it
			return;
		}

		if (!RecipeCache.hasRecipes()) {
			return; // Let vanilla handle it
		}

		ci.cancel();
		client.gui.setScreen(new SmartRecipeBookScreen(client.gui.screen(), mode));
	}
}
