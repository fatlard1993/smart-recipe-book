package com.smartrecipe.recipe;

import com.smartrecipe.SmartRecipeBookMod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapedCraftingRecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.util.context.ContextParameterMap;

import java.util.*;

/**
 * Calculates the crafting tree needed to make an item,
 * including all sub-components that need to be crafted first.
 */
public class RecipeTreeCalculator {

	private static final Map<Item, RecipeDisplayEntry> recipeForItemCache = new HashMap<>();
	private static final Set<Item> itemsWithNoRecipe = new HashSet<>();
	private static long lastCacheClear = 0;

	// TTL keeps recipe lookups responsive to inventory changes without
	// rescanning every frame. 5s balances freshness vs. cost.
	private static final long CACHE_TTL_MS = 5000;

	// Vanilla recipes rarely chain deeper than 3 (e.g., log → plank → stick → item).
	// Capping here prevents runaway recursion from circular or modded recipe trees.
	// If this proves too shallow for modpacks, it can be raised.
	private static final int MAX_RECURSION_DEPTH = 3;

	/**
	 * Calculate a crafting plan for the given recipe
	 * @param client The Minecraft client
	 * @param recipeId The recipe to craft
	 * @return A CraftingPlan, or null if no special handling needed
	 */
	public static CraftingPlan calculatePlan(MinecraftClient client, NetworkRecipeId recipeId) {
		if (client.player == null || client.world == null) return null;

		RecipeDisplayEntry entry = RecipeCache.getRecipe(recipeId);
		if (entry == null) return null;

		RecipeDisplay display = entry.display();
		if (!(display instanceof ShapedCraftingRecipeDisplay) &&
			!(display instanceof ShapelessCraftingRecipeDisplay)) {
			return null;
		}

		Map<Item, Integer> inventory = getInventoryContents(client.player);
		ContextParameterMap contextParams = SlotDisplayContexts.createParameters(client.world);
		ItemStack resultStack = getResultItem(display, contextParams);

		CraftingPlan plan = new CraftingPlan(recipeId, resultStack);

		Set<Item> visited = new HashSet<>();
		List<CraftingPlan.CraftingStep> steps = new ArrayList<>();
		boolean success = calculateDependencies(client, entry, inventory, visited, steps, contextParams, 0);

		if (success) {
			for (CraftingPlan.CraftingStep step : steps) {
				plan.addStep(step);
			}
		} else {
			plan.setCanCraft(false);
		}

		plan.addStep(new CraftingPlan.CraftingStep(recipeId, resultStack));

		SmartRecipeBookMod.LOGGER.debug("Plan for {}: {} steps, canCraft={}",
			resultStack.getName().getString(), plan.getSteps().size(), plan.canCraft());

		return plan;
	}

	/**
	 * Recursively calculate dependencies for a recipe
	 */
	private static boolean calculateDependencies(
			MinecraftClient client,
			RecipeDisplayEntry entry,
			Map<Item, Integer> inventory,
			Set<Item> visited,
			List<CraftingPlan.CraftingStep> steps,
			ContextParameterMap contextParams,
			int depth) {

		if (depth > MAX_RECURSION_DEPTH) {
			SmartRecipeBookMod.LOGGER.debug("Dependency depth {} exceeded limit for recipe {}",
				depth, entry.id());
			return false;
		}

		RecipeDisplay display = entry.display();
		List<SlotDisplay> ingredients = getIngredients(display);
		if (ingredients == null) return false;

		for (SlotDisplay slotDisplay : ingredients) {
			List<ItemStack> possibleStacks = slotDisplay.getStacks(contextParams);
			if (possibleStacks.isEmpty()) continue;

			boolean foundIngredient = false;

			for (ItemStack possible : possibleStacks) {
				if (possible.isEmpty()) continue;

				Item neededItem = possible.getItem();
				int haveCount = inventory.getOrDefault(neededItem, 0);

				if (haveCount >= 1) {
					inventory.put(neededItem, haveCount - 1);
					foundIngredient = true;
					break;
				}

				// Circular dependency — skip this ingredient option
				if (visited.contains(neededItem)) continue;

				RecipeDisplayEntry subRecipe = findRecipeForItem(neededItem, contextParams, inventory);
				if (subRecipe != null) {
					visited.add(neededItem);
					boolean subSuccess = calculateDependencies(client, subRecipe, inventory, visited, steps, contextParams, depth + 1);

					if (subSuccess) {
						ItemStack subResult = getResultItem(subRecipe.display(), contextParams);
						steps.add(new CraftingPlan.CraftingStep(subRecipe.id(), subResult));
						inventory.merge(neededItem, subResult.getCount() - 1, Integer::sum);
						visited.remove(neededItem);
						foundIngredient = true;
						break;
					}
					visited.remove(neededItem);
				}
			}

			if (!foundIngredient) {
				return false;
			}
		}

		return true;
	}

	private static void clearCacheIfExpired() {
		long now = System.currentTimeMillis();
		if (now - lastCacheClear > CACHE_TTL_MS) {
			recipeForItemCache.clear();
			itemsWithNoRecipe.clear();
			lastCacheClear = now;
		}
	}

	private static RecipeDisplayEntry findRecipeForItem(Item item, ContextParameterMap contextParams, Map<Item, Integer> inventory) {
		clearCacheIfExpired();

		if (recipeForItemCache.containsKey(item)) {
			return recipeForItemCache.get(item);
		}
		if (itemsWithNoRecipe.contains(item)) {
			return null;
		}

		RecipeDisplayEntry fallbackRecipe = null;

		for (var entry : RecipeCache.getAllRecipes()) {
			RecipeDisplay display = entry.display();
			if (!(display instanceof ShapedCraftingRecipeDisplay) &&
				!(display instanceof ShapelessCraftingRecipeDisplay)) {
				continue;
			}

			ItemStack result = getResultItem(display, contextParams);
			if (result.getItem() == item) {
				// Prefer recipes we can craft with current inventory
				if (canCraftDirect(display, contextParams, inventory)) {
					recipeForItemCache.put(item, entry);
					return entry;
				}
				if (fallbackRecipe == null) {
					fallbackRecipe = entry;
				}
			}
		}

		if (fallbackRecipe != null) {
			recipeForItemCache.put(item, fallbackRecipe);
		} else {
			itemsWithNoRecipe.add(item);
		}
		return fallbackRecipe;
	}

	/**
	 * Check if we have all ingredients to craft a recipe directly (no sub-crafting).
	 * Simulates ingredient consumption against a copy of the given inventory.
	 */
	public static boolean canCraftDirect(RecipeDisplay display, ContextParameterMap contextParams, Map<Item, Integer> inventory) {
		List<SlotDisplay> ingredients = getIngredients(display);
		if (ingredients == null) return false;

		// Create a copy of inventory to simulate consumption
		Map<Item, Integer> simInventory = new HashMap<>(inventory);

		for (SlotDisplay slot : ingredients) {
			List<ItemStack> possibleIngredients = slot.getStacks(contextParams);
			if (possibleIngredients.isEmpty()) continue;

			boolean foundIngredient = false;
			for (ItemStack possible : possibleIngredients) {
				if (possible.isEmpty()) continue;
				int have = simInventory.getOrDefault(possible.getItem(), 0);
				if (have > 0) {
					simInventory.put(possible.getItem(), have - 1);
					foundIngredient = true;
					break;
				}
			}

			if (!foundIngredient) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Get ingredients from a recipe display
	 */
	private static List<SlotDisplay> getIngredients(RecipeDisplay display) {
		if (display instanceof ShapedCraftingRecipeDisplay shaped) {
			return shaped.ingredients();
		} else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
			return shapeless.ingredients();
		}
		return null;
	}

	/**
	 * Get the result item from a recipe display
	 */
	private static ItemStack getResultItem(RecipeDisplay display, ContextParameterMap contextParams) {
		SlotDisplay resultSlot = null;
		if (display instanceof ShapedCraftingRecipeDisplay shaped) {
			resultSlot = shaped.result();
		} else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
			resultSlot = shapeless.result();
		}

		if (resultSlot != null) {
			List<ItemStack> stacks = resultSlot.getStacks(contextParams);
			if (!stacks.isEmpty()) {
				return stacks.get(0);
			}
		}
		return ItemStack.EMPTY;
	}

	/**
	 * Get the contents of a player's inventory as item counts.
	 * Includes main inventory (0-35) and offhand (40).
	 */
	public static Map<Item, Integer> getInventoryContents(ClientPlayerEntity player) {
		Map<Item, Integer> contents = new HashMap<>();

		for (int i = 0; i < 36; i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (!stack.isEmpty()) {
				contents.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}

		// Offhand slot
		ItemStack offhand = player.getInventory().getStack(40);
		if (!offhand.isEmpty()) {
			contents.merge(offhand.getItem(), offhand.getCount(), Integer::sum);
		}

		return contents;
	}

	/**
	 * Calculate maximum craftable quantity for a recipe, considering sub-crafting.
	 * Uses binary search to find the highest quantity that can be crafted.
	 */
	public static int calculateMaxCraftable(MinecraftClient client, NetworkRecipeId recipeId) {
		if (client.player == null || client.world == null) return 1;

		// Guard: if we can't even craft 1, return 0
		if (!canCraftQuantity(client, recipeId, 1)) return 0;

		// Binary search for max craftable
		int lo = 1, hi = 64;
		while (lo < hi) {
			int mid = (lo + hi + 1) / 2;
			if (canCraftQuantity(client, recipeId, mid)) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		return lo;
	}

	/**
	 * Check if we can craft a specific quantity of a recipe (including sub-crafting)
	 */
	private static boolean canCraftQuantity(MinecraftClient client, NetworkRecipeId recipeId, int quantity) {
		RecipeDisplayEntry entry = RecipeCache.getRecipe(recipeId);
		if (entry == null) return false;

		RecipeDisplay display = entry.display();
		if (!(display instanceof ShapedCraftingRecipeDisplay) &&
			!(display instanceof ShapelessCraftingRecipeDisplay)) {
			return false;
		}

		// Get current inventory and simulate crafting 'quantity' times
		Map<Item, Integer> inventory = getInventoryContents(client.player);
		ContextParameterMap contextParams = SlotDisplayContexts.createParameters(client.world);

		// Try to "craft" quantity times
		for (int i = 0; i < quantity; i++) {
			Set<Item> visited = new HashSet<>();
			if (!canCraftOnce(client, entry, inventory, visited, contextParams, 0)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check if we can craft one instance of a recipe, consuming from simulated inventory.
	 * This recursively handles sub-crafting.
	 */
	private static boolean canCraftOnce(
			MinecraftClient client,
			RecipeDisplayEntry entry,
			Map<Item, Integer> inventory,
			Set<Item> visited,
			ContextParameterMap contextParams,
			int depth) {

		if (depth > MAX_RECURSION_DEPTH) return false;

		RecipeDisplay display = entry.display();
		List<SlotDisplay> ingredients = getIngredients(display);
		if (ingredients == null) return false;

		for (SlotDisplay slotDisplay : ingredients) {
			List<ItemStack> possibleStacks = slotDisplay.getStacks(contextParams);
			if (possibleStacks.isEmpty()) continue;

			boolean foundIngredient = false;
			for (ItemStack possible : possibleStacks) {
				if (possible.isEmpty()) continue;

				Item neededItem = possible.getItem();
				int haveCount = inventory.getOrDefault(neededItem, 0);

				if (haveCount >= 1) {
					// Consume from inventory
					inventory.put(neededItem, haveCount - 1);
					foundIngredient = true;
					break;
				}

				// Need to sub-craft
				if (visited.contains(neededItem)) continue;

				RecipeDisplayEntry subRecipe = findRecipeForItem(neededItem, contextParams, inventory);
				if (subRecipe != null) {
					visited.add(neededItem);

					// Recursively craft the sub-item
					if (canCraftOnce(client, subRecipe, inventory, visited, contextParams, depth + 1)) {
						// Add sub-crafted result to inventory
						ItemStack subResult = getResultItem(subRecipe.display(), contextParams);
						int produced = subResult.getCount();
						inventory.merge(neededItem, produced, Integer::sum);

						// Now consume what we need
						inventory.put(neededItem, inventory.get(neededItem) - 1);
						visited.remove(neededItem);
						foundIngredient = true;
						break;
					}
					visited.remove(neededItem);
				}
			}

			if (!foundIngredient) return false;
		}

		return true;
	}
}
