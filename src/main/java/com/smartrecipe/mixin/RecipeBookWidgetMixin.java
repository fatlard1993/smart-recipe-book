package com.smartrecipe.mixin;

import com.smartrecipe.crafting.AutoCraftExecutor;
import com.smartrecipe.recipe.RecipeTreeCalculator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import com.smartrecipe.recipe.CraftingPlan;
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
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookWidgetMixin {

	@Shadow
	protected Minecraft minecraft;

	@Inject(
		method = "tryPlaceRecipe",
		at = @At("HEAD"),
		cancellable = true
	)
	private void onRecipeSelect(RecipeCollection results, RecipeDisplayId recipeId, boolean craftAll, CallbackInfoReturnable<Boolean> cir) {
		if (minecraft == null || minecraft.player == null) return;

		CraftingPlan plan = RecipeTreeCalculator.calculatePlan(minecraft, recipeId);
		if (plan == null || !plan.requiresSubCrafting()) {
			return;
		}

		cir.setReturnValue(true);
		AutoCraftExecutor.execute(minecraft, plan, 1);
	}
}
