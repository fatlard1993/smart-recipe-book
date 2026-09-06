package com.smartrecipe.recipe;

import com.smartrecipe.SmartRecipeBookMod;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * Calculates the crafting tree needed to make an item,
 * including all sub-components that need to be crafted first.
 */
public class RecipeTreeCalculator {

	/** Main inventory plus hotbar; armour and offhand are no use to a craft. */
	private static final int INVENTORY_SLOTS = 36;

	/** Keyed by item, holding every recipe that makes it; see {@link #findRecipesForItem}. */
	private static final Map<Item, List<RecipeDisplayEntry>> recipesForItemCache = new HashMap<>();
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
	public static CraftingPlan calculatePlan(Minecraft client, RecipeDisplayId recipeId) {
		return calculatePlan(client, recipeId, 1);
	}

	/**
	 * The steps for making this many, in order, against one running inventory.
	 *
	 * <p>Not the one-craft plan repeated: four torches take one stick craft, not four, because
	 * the first craft's spare sticks carry into the next. Repeating the plan spent planks the
	 * estimate of how many could be made never spent, and the run outran the planks. This walks
	 * the same simulation the estimate walks, and keeps the steps it takes.
	 */
	public static CraftingPlan calculatePlan(Minecraft client, RecipeDisplayId recipeId, int quantity) {
		if (client.player == null || client.level == null) return null;

		RecipeDisplayEntry entry = RecipeCache.getRecipe(recipeId);
		if (entry == null) return null;

		RecipeDisplay display = entry.display();
		if (!(display instanceof ShapedCraftingRecipeDisplay) &&
			!(display instanceof ShapelessCraftingRecipeDisplay)) {
			return null;
		}

		Map<Item, Integer> inventory = getInventoryContents(client.player);
		ContextMap contextParams = SlotDisplayContext.fromLevel(client.level);
		ItemStack resultStack = getResultItem(display, contextParams);

		CraftingPlan plan = new CraftingPlan(recipeId, resultStack);

		boolean success = true;
		for (int made = 0; made < Math.max(1, quantity); made++) {
			Set<Item> visited = new HashSet<>();
			List<CraftingPlan.CraftingStep> steps = new ArrayList<>();
			if (!calculateDependencies(client, entry, inventory, visited, steps, contextParams, 0)) {
				success = false;
				break;
			}
			for (CraftingPlan.CraftingStep step : steps) {
				plan.addStep(step);
			}
			plan.addStep(new CraftingPlan.CraftingStep(recipeId, resultStack));
			// What this craft made is in hand for the next one.
			inventory.merge(resultStack.getItem(), resultStack.getCount(), Integer::sum);
		}
		if (!success) {
			plan.setCanCraft(false);
			if (plan.getSteps().isEmpty()) plan.addStep(new CraftingPlan.CraftingStep(recipeId, resultStack));
		}

		SmartRecipeBookMod.LOGGER.debug("Plan for {}: {} steps, canCraft={}",
			resultStack.getHoverName().getString(), plan.getSteps().size(), plan.canCraft());

		return plan;
	}

	/**
	 * Recursively calculate dependencies for a recipe
	 */
	private static boolean calculateDependencies(
			Minecraft client,
			RecipeDisplayEntry entry,
			Map<Item, Integer> inventory,
			Set<Item> visited,
			List<CraftingPlan.CraftingStep> steps,
			ContextMap contextParams,
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
			List<ItemStack> possibleStacks = slotDisplay.resolveForStacks(contextParams);
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

				// Circular dependency: skip this ingredient option
				if (visited.contains(neededItem)) continue;

				visited.add(neededItem);
				for (RecipeDisplayEntry subRecipe : findRecipesForItem(neededItem, contextParams, inventory)) {
					// Inventory and steps both roll back: a half-built plan for a candidate that
					// turned out not to work would otherwise be left in the plan that ships
					Map<Item, Integer> attempt = new HashMap<>(inventory);
					int stepsBefore = steps.size();
					if (!calculateDependencies(client, subRecipe, attempt, visited, steps, contextParams, depth + 1)) {
						steps.subList(stepsBefore, steps.size()).clear();
						continue;
					}
					ItemStack subResult = getResultItem(subRecipe.display(), contextParams);
					steps.add(new CraftingPlan.CraftingStep(subRecipe.id(), subResult));
					attempt.merge(neededItem, subResult.getCount() - 1, Integer::sum);
					inventory.clear();
					inventory.putAll(attempt);
					foundIngredient = true;
					break;
				}
				visited.remove(neededItem);
				if (foundIngredient) break;
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
			recipesForItemCache.clear();
			lastCacheClear = now;
		}
	}

	/**
	 * Every crafting recipe that produces this item, best candidate first.
	 *
	 * <p>All of them, not one. This used to answer with a single recipe and cache it per item,
	 * which quietly decided that an item had one way to be made: sticks resolved to whichever
	 * recipe the list happened to reach first, and if that was the bamboo one, a player holding
	 * logs was told a pickaxe was not craftable until they broke the logs down by hand. The
	 * caller now tries them in turn, so an item is uncraftable only when every way to make it is.
	 *
	 * <p>Ordered by whether the current simulated inventory can make it outright, which keeps the
	 * old preference for the shallow answer without letting it be the only answer. The cache is
	 * the list, not the choice: the list does not depend on what the player is carrying, so it
	 * cannot go stale the way the choice did.
	 */
	private static List<RecipeDisplayEntry> findRecipesForItem(
			Item item, ContextMap contextParams, Map<Item, Integer> inventory) {
		clearCacheIfExpired();

		List<RecipeDisplayEntry> candidates = recipesForItemCache.get(item);
		if (candidates == null) {
			candidates = new ArrayList<>();
			for (var entry : RecipeCache.getAllRecipes()) {
				RecipeDisplay display = entry.display();
				if (!(display instanceof ShapedCraftingRecipeDisplay)
					&& !(display instanceof ShapelessCraftingRecipeDisplay)) {
					continue;
				}
				if (getResultItem(display, contextParams).getItem() == item) {
					candidates.add(entry);
				}
			}
			recipesForItemCache.put(item, List.copyOf(candidates));
			candidates = recipesForItemCache.get(item);
		}
		if (candidates.size() < 2) return candidates;

		List<RecipeDisplayEntry> ordered = new ArrayList<>(candidates);
		ordered.sort(java.util.Comparator.comparing(
			e -> canCraftDirect(e.display(), contextParams, inventory) ? 0 : 1));
		return ordered;
	}

	/**
	 * Check if we have all ingredients to craft a recipe directly (no sub-crafting).
	 * Simulates ingredient consumption against a copy of the given inventory.
	 */
	public static boolean canCraftDirect(RecipeDisplay display, ContextMap contextParams, Map<Item, Integer> inventory) {
		List<SlotDisplay> ingredients = getIngredients(display);
		if (ingredients == null) return false;

		Map<Item, Integer> simInventory = new HashMap<>(inventory);

		for (SlotDisplay slot : ingredients) {
			List<ItemStack> possibleIngredients = slot.resolveForStacks(contextParams);
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
	private static ItemStack getResultItem(RecipeDisplay display, ContextMap contextParams) {
		SlotDisplay resultSlot = null;
		if (display instanceof ShapedCraftingRecipeDisplay shaped) {
			resultSlot = shaped.result();
		} else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
			resultSlot = shapeless.result();
		}

		if (resultSlot != null) {
			List<ItemStack> stacks = resultSlot.resolveForStacks(contextParams);
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
	/**
	 * Empty slots in the player's own inventory, hotbar included.
	 *
	 * <p>Empty ones only, and deliberately: a partly filled stack can absorb more of its own item
	 * but says nothing about whether it can take an intermediate, and guessing generously here is
	 * the direction that loses items rather than the direction that nags.
	 */
	public static int freeInventorySlots(LocalPlayer player) {
		int free = 0;
		for (int slot = 0; slot < INVENTORY_SLOTS; slot++) {
			if (player.getInventory().getItem(slot).isEmpty()) free++;
		}
		return free;
	}

	/**
	 * Slots a plan needs somewhere to put things down.
	 *
	 * <p>One per distinct item the plan makes. A sub-craft's output has to land in the inventory
	 * before the step that consumes it can run, so the intermediates are as real a demand on space
	 * as the thing being built - and a plan with no sub-crafting at all needs only the one slot for
	 * what it makes.
	 */
	public static int slotsNeededFor(CraftingPlan plan) {
		Set<Item> distinct = new HashSet<>();
		for (CraftingPlan.CraftingStep step : plan.getSteps()) {
			ItemStack result = step.getResult();
			if (!result.isEmpty()) distinct.add(result.getItem());
		}
		return distinct.size();
	}

	public static Map<Item, Integer> getInventoryContents(LocalPlayer player) {
		Map<Item, Integer> contents = new HashMap<>();

		for (int i = 0; i < INVENTORY_SLOTS; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty()) {
				contents.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}

		ItemStack offhand = player.getInventory().getItem(40);
		if (!offhand.isEmpty()) {
			contents.merge(offhand.getItem(), offhand.getCount(), Integer::sum);
		}

		return contents;
	}

	/**
	 * Calculate maximum craftable quantity for a recipe, considering sub-crafting.
	 * Uses binary search to find the highest quantity that can be crafted.
	 */
	public static int calculateMaxCraftable(Minecraft client, RecipeDisplayId recipeId) {
		if (client.player == null || client.level == null) return 1;

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
	private static boolean canCraftQuantity(Minecraft client, RecipeDisplayId recipeId, int quantity) {
		RecipeDisplayEntry entry = RecipeCache.getRecipe(recipeId);
		if (entry == null) return false;

		RecipeDisplay display = entry.display();
		if (!(display instanceof ShapedCraftingRecipeDisplay) &&
			!(display instanceof ShapelessCraftingRecipeDisplay)) {
			return false;
		}

		Map<Item, Integer> inventory = getInventoryContents(client.player);
		ContextMap contextParams = SlotDisplayContext.fromLevel(client.level);

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
			Minecraft client,
			RecipeDisplayEntry entry,
			Map<Item, Integer> inventory,
			Set<Item> visited,
			ContextMap contextParams,
			int depth) {

		if (depth > MAX_RECURSION_DEPTH) return false;

		RecipeDisplay display = entry.display();
		List<SlotDisplay> ingredients = getIngredients(display);
		if (ingredients == null) return false;

		for (SlotDisplay slotDisplay : ingredients) {
			List<ItemStack> possibleStacks = slotDisplay.resolveForStacks(contextParams);
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

				if (visited.contains(neededItem)) continue;

				visited.add(neededItem);
				for (RecipeDisplayEntry subRecipe : findRecipesForItem(neededItem, contextParams, inventory)) {
					// On its own copy: a sub-craft that fails half way has already spent things
					// out of the simulation, and the next candidate must not start from that
					Map<Item, Integer> attempt = new HashMap<>(inventory);
					if (!canCraftOnce(client, subRecipe, attempt, visited, contextParams, depth + 1)) {
						continue;
					}
					int produced = getResultItem(subRecipe.display(), contextParams).getCount();
					attempt.merge(neededItem, produced - 1, Integer::sum);
					inventory.clear();
					inventory.putAll(attempt);
					foundIngredient = true;
					break;
				}
				visited.remove(neededItem);
				if (foundIngredient) break;
			}

			if (!foundIngredient) return false;
		}

		return true;
	}
}
