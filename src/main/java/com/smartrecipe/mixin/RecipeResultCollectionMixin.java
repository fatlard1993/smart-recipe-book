package com.smartrecipe.mixin;

import com.smartrecipe.recipe.RecipeTreeCalculator;
import com.smartrecipe.recipe.CraftingPlan;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.recipe.NetworkRecipeId;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes recipes show as craftable if we can craft the intermediate ingredients
 */
@Mixin(RecipeResultCollection.class)
public class RecipeResultCollectionMixin {

	@Inject(
		method = "isCraftable",
		at = @At("RETURN"),
		cancellable = true
	)
	private void onIsCraftable(NetworkRecipeId recipeId, CallbackInfoReturnable<Boolean> cir) {
		// If vanilla already says it's craftable, no need to check
		if (cir.getReturnValue()) {
			return;
		}

		// Check if we can craft it with recursive sub-crafting
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null) {
			return;
		}

		try {
			CraftingPlan plan = RecipeTreeCalculator.calculatePlan(client, recipeId);
			if (plan != null && plan.isValid()) {
				cir.setReturnValue(true);
			}
		} catch (Exception e) {
			// Silently ignore errors during craftability check
		}
	}
}
