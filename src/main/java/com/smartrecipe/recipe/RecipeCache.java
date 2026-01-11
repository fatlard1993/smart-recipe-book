package com.smartrecipe.recipe;

import com.smartrecipe.SmartRecipeBookMod;
import com.smartrecipe.mixin.ServerRecipeManagerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapedCraftingRecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.util.context.ContextParameterMap;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Custom recipe cache that bypasses vanilla ClientRecipeBook.
 * Stores ALL recipes directly when received from the server.
 */
public class RecipeCache {

	// Thread-safe storage for all recipes
	private static final Map<NetworkRecipeId, RecipeDisplayEntry> recipes = new ConcurrentHashMap<>();

	// Cached collections for UI (rebuilt when recipes change)
	private static List<RecipeResultCollection> cachedCollections = null;
	private static Map<RecipeBookCategory, List<RecipeResultCollection>> cachedByCategory = null;

	// Map from result item to recipes that produce it
	private static Map<Item, List<RecipeDisplayEntry>> recipesByResult = new ConcurrentHashMap<>();

	// Track if we've loaded from integrated server this session
	private static boolean loadedFromServer = false;

	/**
	 * Clear all cached recipes (called when joining a new world)
	 */
	public static void clear() {
		SmartRecipeBookMod.LOGGER.info("RecipeCache cleared");
		recipes.clear();
		recipesByResult.clear();
		cachedCollections = null;
		cachedByCategory = null;
		loadedFromServer = false;
	}

	/**
	 * Add a single recipe to the cache
	 */
	public static void addRecipe(RecipeDisplayEntry entry) {
		recipes.put(entry.id(), entry);
		invalidateCache();
	}

	/**
	 * Add multiple recipes to the cache (bulk add for initial sync)
	 */
	public static void addRecipes(Collection<RecipeDisplayEntry> entries) {
		for (RecipeDisplayEntry entry : entries) {
			recipes.put(entry.id(), entry);
		}
		SmartRecipeBookMod.LOGGER.info("RecipeCache: Added {} recipes, total now: {}", entries.size(), recipes.size());
		invalidateCache();
	}

	/**
	 * Remove a recipe from the cache
	 */
	public static void removeRecipe(NetworkRecipeId id) {
		recipes.remove(id);
		invalidateCache();
	}

	/**
	 * Get a recipe by its ID
	 */
	public static RecipeDisplayEntry getRecipe(NetworkRecipeId id) {
		return recipes.get(id);
	}

	/**
	 * Get all recipes
	 */
	public static Collection<RecipeDisplayEntry> getAllRecipes() {
		return Collections.unmodifiableCollection(recipes.values());
	}

	/**
	 * Get the total count of recipes
	 */
	public static int getRecipeCount() {
		return recipes.size();
	}

	/**
	 * Check if cache has recipes
	 */
	public static boolean hasRecipes() {
		return !recipes.isEmpty();
	}

	/**
	 * Get all recipes as RecipeResultCollections (for UI compatibility)
	 */
	public static List<RecipeResultCollection> getOrderedResults() {
		if (cachedCollections == null) {
			rebuildCollections();
		}
		return cachedCollections;
	}

	/**
	 * Get recipes by category
	 */
	public static List<RecipeResultCollection> getResultsByCategory(RecipeBookCategory category) {
		if (cachedByCategory == null) {
			rebuildCollections();
		}
		return cachedByCategory.getOrDefault(category, Collections.emptyList());
	}

	/**
	 * Find a recipe by ID (searches our cache)
	 */
	public static RecipeDisplayEntry findRecipeById(NetworkRecipeId recipeId) {
		return recipes.get(recipeId);
	}

	/**
	 * Find recipes that produce a given item
	 */
	public static List<RecipeDisplayEntry> findRecipesForItem(Item item, World world) {
		// Rebuild result mapping if needed
		if (recipesByResult.isEmpty() && !recipes.isEmpty()) {
			rebuildResultMapping(world);
		}
		return recipesByResult.getOrDefault(item, Collections.emptyList());
	}

	/**
	 * Find any crafting recipe that produces the given item
	 */
	public static RecipeDisplayEntry findCraftingRecipeForItem(Item item, World world) {
		List<RecipeDisplayEntry> candidates = findRecipesForItem(item, world);

		// Return first crafting recipe found
		for (RecipeDisplayEntry entry : candidates) {
			RecipeDisplay display = entry.display();
			if (display instanceof ShapedCraftingRecipeDisplay ||
				display instanceof ShapelessCraftingRecipeDisplay) {
				return entry;
			}
		}

		return null;
	}

	/**
	 * Invalidate cached collections (call when recipes change)
	 */
	private static void invalidateCache() {
		cachedCollections = null;
		cachedByCategory = null;
		recipesByResult.clear();
	}

	/**
	 * Rebuild the result item mapping
	 */
	private static void rebuildResultMapping(World world) {
		recipesByResult.clear();

		if (world == null) return;

		ContextParameterMap contextParams = SlotDisplayContexts.createParameters(world);

		for (RecipeDisplayEntry entry : recipes.values()) {
			try {
				List<ItemStack> results = entry.getStacks(contextParams);
				if (!results.isEmpty() && !results.get(0).isEmpty()) {
					Item resultItem = results.get(0).getItem();
					recipesByResult.computeIfAbsent(resultItem, k -> new ArrayList<>()).add(entry);
				}
			} catch (Exception e) {
				// Skip recipes that fail to get result
			}
		}

		SmartRecipeBookMod.LOGGER.debug("RecipeCache: Built result mapping for {} unique items", recipesByResult.size());
	}

	/**
	 * Rebuild RecipeResultCollections from our recipe cache
	 */
	private static void rebuildCollections() {
		// Group recipes by category and then by group ID
		Map<RecipeBookCategory, Map<Integer, List<RecipeDisplayEntry>>> categorized = new LinkedHashMap<>();

		for (RecipeDisplayEntry entry : recipes.values()) {
			RecipeBookCategory category = entry.category();
			int group = entry.group().orElse(-1); // -1 for ungrouped

			categorized
				.computeIfAbsent(category, k -> new LinkedHashMap<>())
				.computeIfAbsent(group, k -> new ArrayList<>())
				.add(entry);
		}

		// Build collections
		List<RecipeResultCollection> allCollections = new ArrayList<>();
		Map<RecipeBookCategory, List<RecipeResultCollection>> byCategory = new LinkedHashMap<>();

		for (Map.Entry<RecipeBookCategory, Map<Integer, List<RecipeDisplayEntry>>> categoryEntry : categorized.entrySet()) {
			RecipeBookCategory category = categoryEntry.getKey();
			List<RecipeResultCollection> categoryCollections = new ArrayList<>();

			for (List<RecipeDisplayEntry> group : categoryEntry.getValue().values()) {
				if (!group.isEmpty()) {
					RecipeResultCollection collection = new RecipeResultCollection(group);
					allCollections.add(collection);
					categoryCollections.add(collection);
				}
			}

			byCategory.put(category, categoryCollections);
		}

		cachedCollections = allCollections;
		cachedByCategory = byCategory;

		SmartRecipeBookMod.LOGGER.debug("RecipeCache: Built {} collections across {} categories",
			allCollections.size(), byCategory.size());
	}

	/**
	 * Get all crafting recipes (shaped and shapeless only)
	 */
	public static List<RecipeDisplayEntry> getCraftingRecipes() {
		return recipes.values().stream()
			.filter(entry -> {
				RecipeDisplay display = entry.display();
				return display instanceof ShapedCraftingRecipeDisplay ||
					   display instanceof ShapelessCraftingRecipeDisplay;
			})
			.collect(Collectors.toList());
	}

	/**
	 * Debug: print cache statistics
	 */
	public static void logStats() {
		SmartRecipeBookMod.LOGGER.info("RecipeCache stats: {} total recipes", recipes.size());

		// Count by category
		Map<RecipeBookCategory, Integer> byCategory = new HashMap<>();
		for (RecipeDisplayEntry entry : recipes.values()) {
			byCategory.merge(entry.category(), 1, Integer::sum);
		}

		for (Map.Entry<RecipeBookCategory, Integer> e : byCategory.entrySet()) {
			SmartRecipeBookMod.LOGGER.info("  Category {}: {} recipes", e.getKey(), e.getValue());
		}
	}

	/**
	 * Load ALL recipes from the integrated server (singleplayer only).
	 * This bypasses the recipe book unlock system to show all recipes.
	 */
	public static void loadFromIntegratedServer() {
		MinecraftClient client = MinecraftClient.getInstance();

		SmartRecipeBookMod.LOGGER.info("Attempting to load from integrated server...");

		if (client.getServer() == null) {
			SmartRecipeBookMod.LOGGER.info("No integrated server available (multiplayer or not yet loaded)");
			return;
		}

		try {
			ServerRecipeManager recipeManager = client.getServer().getRecipeManager();
			SmartRecipeBookMod.LOGGER.info("Got recipe manager: {}", recipeManager);

			ServerRecipeManagerAccessor accessor = (ServerRecipeManagerAccessor) recipeManager;
			List<ServerRecipeManager.ServerRecipe> serverRecipes = accessor.getRecipes();

			SmartRecipeBookMod.LOGGER.info("Server recipes list: {} (size: {})",
				serverRecipes != null ? "not null" : "null",
				serverRecipes != null ? serverRecipes.size() : 0);

			if (serverRecipes == null || serverRecipes.isEmpty()) {
				SmartRecipeBookMod.LOGGER.warn("Server recipe list is empty - recipes may not be initialized yet");
				return;
			}

			// Clear existing and add all recipes
			recipes.clear();
			for (ServerRecipeManager.ServerRecipe serverRecipe : serverRecipes) {
				RecipeDisplayEntry entry = serverRecipe.display();
				recipes.put(entry.id(), entry);
			}

			invalidateCache();

			SmartRecipeBookMod.LOGGER.info("Successfully loaded {} recipes from integrated server", recipes.size());
		} catch (Exception e) {
			SmartRecipeBookMod.LOGGER.error("Failed to load recipes from integrated server", e);
		}
	}

	/**
	 * Ensure recipes are loaded, loading from server if needed
	 */
	public static void ensureLoaded() {
		SmartRecipeBookMod.LOGGER.info("ensureLoaded called - current recipes: {}, loadedFromServer: {}",
			recipes.size(), loadedFromServer);

		// Always try to load from integrated server if we haven't yet
		// (the packet-captured recipes are only unlocked ones)
		if (!loadedFromServer) {
			loadFromIntegratedServer();
			if (recipes.size() > 100) { // Assume success if we got a good number
				loadedFromServer = true;
			}
		}
	}

	/**
	 * Reset the loaded flag (call when world changes)
	 */
	public static void resetLoadedFlag() {
		loadedFromServer = false;
	}
}
