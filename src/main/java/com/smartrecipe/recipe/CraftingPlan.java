package com.smartrecipe.recipe;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

/**
 * Represents a plan to craft an item, potentially with multiple steps
 * for crafting sub-components first.
 */
public class CraftingPlan {
	private final List<CraftingStep> steps;
	private final RecipeDisplayId targetRecipe;
	private final ItemStack targetItem;
	private boolean canCraft = true;

	public CraftingPlan(RecipeDisplayId targetRecipe, ItemStack targetItem) {
		this.steps = new ArrayList<>();
		this.targetRecipe = targetRecipe;
		this.targetItem = targetItem;
	}

	public void setCanCraft(boolean canCraft) {
		this.canCraft = canCraft;
	}

	public boolean canCraft() {
		return canCraft;
	}

	public void addStep(CraftingStep step) {
		steps.add(step);
	}

	public List<CraftingStep> getSteps() {
		return steps;
	}

	public RecipeDisplayId getTargetRecipe() {
		return targetRecipe;
	}

	public ItemStack getTargetItem() {
		return targetItem;
	}

	public boolean requiresSubCrafting() {
		return steps.size() > 1;
	}

	public boolean isValid() {
		return !steps.isEmpty() && canCraft;
	}

	public static class CraftingStep {
		private final RecipeDisplayId recipeId;
		private final ItemStack result;

		public CraftingStep(RecipeDisplayId recipeId, ItemStack result) {
			this.recipeId = recipeId;
			this.result = result;
		}

		public RecipeDisplayId getRecipeId() {
			return recipeId;
		}

		public ItemStack getResult() {
			return result;
		}
	}
}
