package com.smartrecipe.mixin;

import com.smartrecipe.SmartRecipeBookMod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

/**
 * Logs recipe book state for debugging.
 */
@Mixin(ClientRecipeBook.class)
public class ClientRecipeBookMixin {

	private static boolean hasLoggedOnce = false;
	private static int lastWorldHash = 0;

	/**
	 * Log recipe book state for debugging
	 */
	@Inject(method = "getOrderedResults", at = @At("RETURN"))
	private void onGetOrderedResultsReturn(CallbackInfoReturnable<List<RecipeResultCollection>> cir) {
		MinecraftClient client = MinecraftClient.getInstance();

		// Reset logging if world changed
		int worldHash = client.world != null ? client.world.hashCode() : 0;
		if (worldHash != lastWorldHash) {
			hasLoggedOnce = false;
			lastWorldHash = worldHash;
		}

		if (hasLoggedOnce) return;
		hasLoggedOnce = true;

		List<RecipeResultCollection> results = cir.getReturnValue();
		int totalRecipes = 0;
		for (var collection : results) {
			totalRecipes += collection.getAllRecipes().size();
		}

		SmartRecipeBookMod.LOGGER.info("Recipe book returning {} collections, {} total recipes",
			results.size(), totalRecipes);

		if (totalRecipes == 0) {
			SmartRecipeBookMod.LOGGER.warn("Recipe book is empty! Check if recipes were synced from server.");
		}
	}
}
