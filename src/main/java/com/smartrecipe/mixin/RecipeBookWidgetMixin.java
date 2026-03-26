package com.smartrecipe.mixin;

import com.smartrecipe.crafting.AutoCraftExecutor;
import com.smartrecipe.recipe.RecipeTreeCalculator;
import com.smartrecipe.recipe.CraftingPlan;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.recipe.NetworkRecipeId;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts recipe selection in the vanilla recipe book widget.
 * When a recipe requires sub-crafting (e.g., sticks for a pickaxe),
 * cancels vanilla handling and delegates to AutoCraftExecutor.
 */
@Mixin(RecipeBookWidget.class)
public abstract class RecipeBookWidgetMixin {

	@Shadow
	protected MinecraftClient client;

	@Inject(
		method = "select",
		at = @At("HEAD"),
		cancellable = true
	)
	private void onRecipeSelect(RecipeResultCollection results, NetworkRecipeId recipeId, boolean craftAll, CallbackInfoReturnable<Boolean> cir) {
		if (client == null || client.player == null) return;

		CraftingPlan plan = RecipeTreeCalculator.calculatePlan(client, recipeId);
		if (plan == null || !plan.requiresSubCrafting()) {
			return;
		}

		cir.setReturnValue(true);
		AutoCraftExecutor.execute(client, plan, 1);
	}
}
