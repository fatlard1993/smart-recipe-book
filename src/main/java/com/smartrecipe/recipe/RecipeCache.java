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
import net.minecraft.recipe.book.RecipeBookCategories;
import net.minecraft.recipe.display.FurnaceRecipeDisplay;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapedCraftingRecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.util.context.ContextParameterMap;
import net.minecraft.world.World;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Primary recipe store that bypasses vanilla ClientRecipeBook.
 * Stores ALL recipes received from the server or loaded from the integrated server.
 */
public class RecipeCache {

	private static final Map<NetworkRecipeId, RecipeDisplayEntry> recipes = new HashMap<>();

	// Cached UI collections (rebuilt when recipes change)
	private static List<RecipeResultCollection> cachedCollections = null;
	private static Map<RecipeBookCategory, List<RecipeResultCollection>> cachedByCategory = null;

	// Index: result item → recipes that produce it
	private static Map<Item, List<RecipeDisplayEntry>> recipesByResult = new HashMap<>();

	private static boolean loadedFromServer = false;

	/**
	 * Clear all state. Called on disconnect to prevent stale data across sessions.
	 */
	public static void clear() {
		recipes.clear();
		recipesByResult.clear();
		cachedCollections = null;
		cachedByCategory = null;
		loadedFromServer = false;
	}

	public static void addRecipe(RecipeDisplayEntry entry) {
		recipes.put(entry.id(), entry);
		invalidateCache();
	}

	public static void addRecipes(Collection<RecipeDisplayEntry> entries) {
		for (RecipeDisplayEntry entry : entries) {
			recipes.put(entry.id(), entry);
		}
		invalidateCache();
	}

	public static void removeRecipe(NetworkRecipeId id) {
		recipes.remove(id);
		invalidateCache();
	}

	public static RecipeDisplayEntry getRecipe(NetworkRecipeId id) {
		return recipes.get(id);
	}

	public static Collection<RecipeDisplayEntry> getAllRecipes() {
		return Collections.unmodifiableCollection(recipes.values());
	}

	public static int getRecipeCount() {
		return recipes.size();
	}

	public static boolean hasRecipes() {
		return !recipes.isEmpty();
	}

	public static List<RecipeResultCollection> getOrderedResults() {
		if (cachedCollections == null) {
			rebuildCollections();
		}
		return cachedCollections;
	}

	public static List<RecipeResultCollection> getResultsByCategory(RecipeBookCategory category) {
		if (cachedByCategory == null) {
			rebuildCollections();
		}
		return cachedByCategory.getOrDefault(category, Collections.emptyList());
	}

	/**
	 * Find recipes that produce a given item.
	 * Rebuilds the result index lazily if it was invalidated.
	 */
	public static List<RecipeDisplayEntry> findRecipesForItem(Item item, World world) {
		if (recipesByResult.isEmpty() && !recipes.isEmpty()) {
			rebuildResultMapping(world);
		}
		return recipesByResult.getOrDefault(item, Collections.emptyList());
	}

	/**
	 * Find any crafting recipe (shaped or shapeless) that produces the given item.
	 */
	public static RecipeDisplayEntry findCraftingRecipeForItem(Item item, World world) {
		for (RecipeDisplayEntry entry : findRecipesForItem(item, world)) {
			RecipeDisplay display = entry.display();
			if (display instanceof ShapedCraftingRecipeDisplay ||
				display instanceof ShapelessCraftingRecipeDisplay) {
				return entry;
			}
		}
		return null;
	}

	private static void invalidateCache() {
		cachedCollections = null;
		cachedByCategory = null;
		recipesByResult.clear();
	}

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
				SmartRecipeBookMod.LOGGER.debug("Skipped recipe during result mapping: {}", e.getMessage());
			}
		}

		SmartRecipeBookMod.LOGGER.debug("RecipeCache: Built result mapping for {} unique items", recipesByResult.size());
	}

	private static void rebuildCollections() {
		Map<RecipeBookCategory, Map<Integer, List<RecipeDisplayEntry>>> categorized = new LinkedHashMap<>();

		for (RecipeDisplayEntry entry : recipes.values()) {
			RecipeBookCategory category = entry.category();
			int group = entry.group().orElse(-1);

			categorized
				.computeIfAbsent(category, k -> new LinkedHashMap<>())
				.computeIfAbsent(group, k -> new ArrayList<>())
				.add(entry);
		}

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

	// --- Recipe type filters ---

	public static List<RecipeDisplayEntry> getCraftingRecipes() {
		return recipes.values().stream()
			.filter(entry -> {
				RecipeDisplay display = entry.display();
				return display instanceof ShapedCraftingRecipeDisplay ||
					   display instanceof ShapelessCraftingRecipeDisplay;
			})
			.collect(Collectors.toList());
	}

	public static List<RecipeDisplayEntry> getFurnaceRecipes() {
		return recipes.values().stream()
			.filter(entry -> entry.display() instanceof FurnaceRecipeDisplay)
			.collect(Collectors.toList());
	}

	public static List<RecipeDisplayEntry> getBlastFurnaceRecipes() {
		RecipeBookCategory blastFurnaceBlocks = RecipeBookCategories.BLAST_FURNACE_BLOCKS;
		RecipeBookCategory blastFurnaceMisc = RecipeBookCategories.BLAST_FURNACE_MISC;

		List<RecipeDisplayEntry> result = new ArrayList<>();
		for (RecipeDisplayEntry entry : recipes.values()) {
			if (!(entry.display() instanceof FurnaceRecipeDisplay)) continue;
			RecipeBookCategory category = entry.category();
			if (category == blastFurnaceBlocks || category == blastFurnaceMisc) {
				result.add(entry);
			}
		}
		return result;
	}

	public static List<RecipeDisplayEntry> getSmokerRecipes() {
		RecipeBookCategory smokerFood = RecipeBookCategories.SMOKER_FOOD;

		List<RecipeDisplayEntry> result = new ArrayList<>();
		for (RecipeDisplayEntry entry : recipes.values()) {
			if (!(entry.display() instanceof FurnaceRecipeDisplay)) continue;
			if (entry.category() == smokerFood) {
				result.add(entry);
			}
		}
		return result;
	}

	public static RecipeDisplayEntry findFurnaceRecipeForItem(Item item, World world) {
		for (RecipeDisplayEntry entry : findRecipesForItem(item, world)) {
			if (entry.display() instanceof FurnaceRecipeDisplay) {
				return entry;
			}
		}
		return null;
	}

	public static List<RecipeDisplayEntry> findAllFurnaceRecipesForItem(Item item, World world) {
		List<RecipeDisplayEntry> furnaceRecipes = new ArrayList<>();
		for (RecipeDisplayEntry entry : findRecipesForItem(item, world)) {
			if (entry.display() instanceof FurnaceRecipeDisplay) {
				furnaceRecipes.add(entry);
			}
		}
		return furnaceRecipes;
	}

	// --- Server loading ---

	/**
	 * Load ALL recipes from the integrated server (singleplayer only).
	 * Bypasses the recipe book unlock system to show all recipes.
	 */
	public static void loadFromIntegratedServer() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.getServer() == null) return;

		try {
			ServerRecipeManager recipeManager = client.getServer().getRecipeManager();
			ServerRecipeManagerAccessor accessor = (ServerRecipeManagerAccessor) recipeManager;
			List<ServerRecipeManager.ServerRecipe> serverRecipes = accessor.getRecipes();

			if (serverRecipes == null || serverRecipes.isEmpty()) return;

			recipes.clear();
			for (ServerRecipeManager.ServerRecipe serverRecipe : serverRecipes) {
				RecipeDisplayEntry entry = serverRecipe.display();
				recipes.put(entry.id(), entry);
			}

			invalidateCache();
		} catch (Exception e) {
			SmartRecipeBookMod.LOGGER.error("Failed to load recipes from integrated server", e);
		}
	}

	/**
	 * Ensure recipes are loaded, loading from integrated server if needed.
	 * Packet-captured recipes only include unlocked ones; the integrated server
	 * gives us the full set for singleplayer.
	 */
	public static void ensureLoaded() {
		if (!loadedFromServer) {
			loadFromIntegratedServer();
			if (!recipes.isEmpty()) {
				loadedFromServer = true;
			}
		}
	}
}
