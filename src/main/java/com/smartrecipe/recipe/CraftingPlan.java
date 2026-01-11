package com.smartrecipe.recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.NetworkRecipeId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Represents a plan to craft an item, potentially with multiple steps
 * for crafting sub-components first.
 */
public class CraftingPlan {
	private final List<CraftingStep> steps;
	private final NetworkRecipeId targetRecipe;
	private final ItemStack targetItem;
	private final Map<ItemStack, List<NetworkRecipeId>> recipeChoices;
	private boolean canCraft = true; // Track if all dependencies were resolved

	public CraftingPlan(NetworkRecipeId targetRecipe, ItemStack targetItem) {
		this.steps = new ArrayList<>();
		this.targetRecipe = targetRecipe;
		this.targetItem = targetItem;
		this.recipeChoices = new HashMap<>();
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

	public void addStepAtBeginning(CraftingStep step) {
		steps.add(0, step);
	}

	public List<CraftingStep> getSteps() {
		return steps;
	}

	public NetworkRecipeId getTargetRecipe() {
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

	public void addRecipeChoice(ItemStack item, List<NetworkRecipeId> recipes) {
		recipeChoices.put(item, recipes);
	}

	public boolean hasRecipeChoices() {
		return !recipeChoices.isEmpty();
	}

	public Map<ItemStack, List<NetworkRecipeId>> getRecipeChoices() {
		return recipeChoices;
	}

	/**
	 * Represents a single crafting step
	 */
	public static class CraftingStep {
		private final NetworkRecipeId recipeId;
		private final ItemStack result;
		private final int quantity;

		public CraftingStep(NetworkRecipeId recipeId, ItemStack result, int quantity) {
			this.recipeId = recipeId;
			this.result = result;
			this.quantity = quantity;
		}

		public NetworkRecipeId getRecipeId() {
			return recipeId;
		}

		public ItemStack getResult() {
			return result;
		}

		public int getQuantity() {
			return quantity;
		}
	}
}
