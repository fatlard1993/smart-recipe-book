package com.smartrecipe.mixin;

import com.smartrecipe.SmartRecipeBookMod;
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

	private static int checkCount = 0;

	@Inject(
		method = "isCraftable",
		at = @At("RETURN"),
		cancellable = true
	)
	private void onIsCraftable(NetworkRecipeId recipeId, CallbackInfoReturnable<Boolean> cir) {
		// Log occasionally to confirm mixin is working
		if (checkCount++ % 100 == 0) {
			SmartRecipeBookMod.LOGGER.info("isCraftable check #{} for {} = {}",
				checkCount, recipeId, cir.getReturnValue());
		}

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
			if (plan != null) {
				SmartRecipeBookMod.LOGGER.info("Plan for {}: valid={}, steps={}",
					recipeId, plan.isValid(), plan.getSteps().size());
				if (plan.isValid()) {
					cir.setReturnValue(true);
				}
			}
		} catch (Exception e) {
			SmartRecipeBookMod.LOGGER.warn("Error checking craftability for {}: {}", recipeId, e.getMessage());
		}
	}
}
