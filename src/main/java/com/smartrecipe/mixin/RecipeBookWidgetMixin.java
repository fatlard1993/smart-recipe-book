package com.smartrecipe.mixin;

import com.smartrecipe.SmartRecipeBookMod;
import com.smartrecipe.crafting.AutoCraftExecutor;
import com.smartrecipe.crafting.VanillaCraftingHelper;
import com.smartrecipe.recipe.RecipeTreeCalculator;
import com.smartrecipe.recipe.CraftingPlan;
import com.smartrecipe.screen.RecipeChoiceScreen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.recipe.NetworkRecipeId;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookWidget.class)
public abstract class RecipeBookWidgetMixin {

	@Shadow
	protected MinecraftClient client;

	/**
	 * Intercept recipe selection to check if we need to auto-craft dependencies
	 */
	@Inject(
		method = "select",
		at = @At("HEAD"),
		cancellable = true
	)
	private void onRecipeSelect(RecipeResultCollection results, NetworkRecipeId recipeId, boolean craftAll, CallbackInfoReturnable<Boolean> cir) {
		// Skip intercept if we're executing our own plan
		if (VanillaCraftingHelper.isExecutingPlan()) {
			SmartRecipeBookMod.LOGGER.info("Executing plan step via vanilla: {}", recipeId);
			return; // Let vanilla handle it
		}

		if (client == null || client.player == null) return;

		// Get the crafting plan for this recipe
		CraftingPlan plan = RecipeTreeCalculator.calculatePlan(client, recipeId);

		if (plan == null) {
			// No plan needed, let vanilla handle it
			return;
		}

		// Only intercept if we need to craft sub-components
		if (!plan.requiresSubCrafting()) {
			// Single step, let vanilla handle it normally
			SmartRecipeBookMod.LOGGER.info("Single step recipe, letting vanilla handle");
			return;
		}

		SmartRecipeBookMod.LOGGER.info("Recipe requires sub-crafting: {} steps", plan.getSteps().size());

		// Check if there are multiple recipe choices
		if (plan.hasRecipeChoices()) {
			// Show popup for user to choose recipes
			cir.setReturnValue(true);
			client.setScreen(new RecipeChoiceScreen(
				client.currentScreen,
				plan,
				(finalPlan) -> {
					// Execute the plan after user confirms choices
					AutoCraftExecutor.execute(client, finalPlan, craftAll);
				}
			));
			return;
		}

		// Execute multi-step plan
		cir.setReturnValue(true);
		AutoCraftExecutor.execute(client, plan, craftAll);
	}
}
