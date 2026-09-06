package com.smartrecipe.recipe;

import com.smartrecipe.SmartRecipeBookMod;
import com.smartrecipe.brewing.BrewingRecipeEntry;
import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;

/**
 * Primary recipe store that bypasses vanilla ClientRecipeBook.
 *
 * <p>Two stores under one set of queries. {@link #recipes} is what the vanilla recipe book sync
 * delivers: the recipes this player has unlocked, kept in step by the add and remove packets.
 * {@link #catalog} is every recipe the server has, sent by this mod's own server half to a client
 * it recognises, under the same display ids. When the catalog is present the queries read it and
 * the unlocked set is only kept current; when it is absent - a server without this mod - the
 * unlocked set is all there is, and sub-crafting can only chain through recipes the player has
 * already unlocked.
 */
public class RecipeCache {

	private static final Map<RecipeDisplayId, RecipeDisplayEntry> recipes = new HashMap<>();
	private static final Map<RecipeDisplayId, RecipeDisplayEntry> catalog = new HashMap<>();

	// Cached UI collections (rebuilt when recipes change)
	private static List<RecipeCollection> cachedCollections = null;
	private static Map<RecipeBookCategory, List<RecipeCollection>> cachedByCategory = null;

	// Index: result item → recipes that produce it
	private static Map<Item, List<RecipeDisplayEntry>> recipesByResult = new HashMap<>();

	/**
	 * Clear all state. Called on disconnect to prevent stale data across sessions.
	 */
	public static void clear() {
		recipes.clear();
		catalog.clear();
		invalidateCache();
		brewing.clear();
		brewingDisplayById.clear();
		brewingDisplays = List.of();
	}

	/**
	 * Start the unlocked set over, for a sync that replaces rather than adds.
	 *
	 * <p>Only that set. The catalog and the brewing list came from this mod's server half and are
	 * replaced by it, on their own packets; a vanilla re-sync says nothing about either.
	 */
	public static void resetUnlocked() {
		recipes.clear();
		invalidateCache();
	}

	/**
	 * Take in a slice of the server's whole recipe list.
	 *
	 * <p>The first slice starts a fresh catalog, so a reload's resend - with ids that no longer
	 * mean what they did - cannot leave the old entries mixed in with the new.
	 */
	public static void receiveCatalog(boolean first, boolean last, List<RecipeDisplayEntry> entries) {
		if (first) catalog.clear();
		for (RecipeDisplayEntry entry : entries) {
			catalog.put(entry.id(), entry);
		}
		invalidateCache();
		// Info-level once per catalog: lets a player confirm from the log that the server
		// has this mod and the book knows every recipe, not only the unlocked ones
		if (last) {
			SmartRecipeBookMod.LOGGER.info("Recipe catalog received: {} recipes from server", catalog.size());
		}
	}

	/** Everything a query should see: the whole catalog when the server sent one, else the unlocked set. */
	private static Collection<RecipeDisplayEntry> view() {
		return catalog.isEmpty() ? recipes.values() : catalog.values();
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

	public static void removeRecipe(RecipeDisplayId id) {
		recipes.remove(id);
		invalidateCache();
	}

	public static RecipeDisplayEntry getRecipe(RecipeDisplayId id) {
		RecipeDisplayEntry entry = catalog.get(id);
		return entry != null ? entry : recipes.get(id);
	}

	public static Collection<RecipeDisplayEntry> getAllRecipes() {
		return Collections.unmodifiableCollection(view());
	}

	public static int getRecipeCount() {
		return view().size();
	}

	public static boolean hasRecipes() {
		return !view().isEmpty();
	}

	public static List<RecipeCollection> getOrderedResults() {
		if (cachedCollections == null) {
			rebuildCollections();
		}
		return cachedCollections;
	}

	public static List<RecipeCollection> getResultsByCategory(RecipeBookCategory category) {
		if (cachedByCategory == null) {
			rebuildCollections();
		}
		return cachedByCategory.getOrDefault(category, Collections.emptyList());
	}

	/**
	 * Find recipes that produce a given item.
	 * Rebuilds the result index lazily if it was invalidated.
	 */
	public static List<RecipeDisplayEntry> findRecipesForItem(Item item, Level world) {
		if (recipesByResult.isEmpty() && hasRecipes()) {
			rebuildResultMapping(world);
		}
		return recipesByResult.getOrDefault(item, Collections.emptyList());
	}

	/**
	 * Find any crafting recipe (shaped or shapeless) that produces the given item.
	 */
	public static RecipeDisplayEntry findCraftingRecipeForItem(Item item, Level world) {
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

	private static void rebuildResultMapping(Level world) {
		recipesByResult.clear();
		if (world == null) return;

		ContextMap contextParams = SlotDisplayContext.fromLevel(world);

		for (RecipeDisplayEntry entry : view()) {
			try {
				List<ItemStack> results = entry.resultItems(contextParams);
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

		for (RecipeDisplayEntry entry : view()) {
			RecipeBookCategory category = entry.category();
			int group = entry.group().orElse(-1);

			categorized
				.computeIfAbsent(category, k -> new LinkedHashMap<>())
				.computeIfAbsent(group, k -> new ArrayList<>())
				.add(entry);
		}

		List<RecipeCollection> allCollections = new ArrayList<>();
		Map<RecipeBookCategory, List<RecipeCollection>> byCategory = new LinkedHashMap<>();

		for (Map.Entry<RecipeBookCategory, Map<Integer, List<RecipeDisplayEntry>>> categoryEntry : categorized.entrySet()) {
			RecipeBookCategory category = categoryEntry.getKey();
			List<RecipeCollection> categoryCollections = new ArrayList<>();

			for (List<RecipeDisplayEntry> group : categoryEntry.getValue().values()) {
				if (!group.isEmpty()) {
					RecipeCollection collection = new RecipeCollection(group);
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

	/**
	 * What a crafting table can actually make.
	 *
	 * <p>Shape is not enough on its own. A modded station's recipes can be shaped exactly like a
	 * workbench's - a fletching table's are a width, a height and nine ingredients - and they
	 * arrive here looking identical, but putting them in a workbench's book offers the player
	 * something that will not craft when they click it. The recipe's own book category is what
	 * separates the two, and a category outside the vanilla namespace means the recipe belongs to
	 * a station somewhere else.
	 */
	public static List<RecipeDisplayEntry> getCraftingRecipes() {
		return view().stream()
			.filter(entry -> {
				RecipeDisplay display = entry.display();
				return display instanceof ShapedCraftingRecipeDisplay ||
					   display instanceof ShapelessCraftingRecipeDisplay;
			})
			.filter(RecipeCache::isVanillaStation)
			.collect(Collectors.toList());
	}

	/**
	 * Everything one named station can make.
	 *
	 * <p>The mirror of {@link #getCraftingRecipes()}: that one drops every category a workbench
	 * cannot craft, and this one keeps exactly the category asked for.
	 */
	public static List<RecipeDisplayEntry> getStationRecipes(String categoryId) {
		return view().stream()
			.filter(entry -> {
				var key = net.minecraft.core.registries.BuiltInRegistries.RECIPE_BOOK_CATEGORY
					.getKey(entry.category());
				return key != null && key.toString().equals(categoryId);
			})
			.collect(Collectors.toList());
	}

	/** Whether this recipe belongs to a station the vanilla book speaks for. */
	private static boolean isVanillaStation(RecipeDisplayEntry entry) {
		var key = net.minecraft.core.registries.BuiltInRegistries.RECIPE_BOOK_CATEGORY
			.getKey(entry.category());
		return key == null || key.getNamespace().equals("minecraft");
	}

	public static List<RecipeDisplayEntry> getFurnaceRecipes() {
		return view().stream()
			.filter(entry -> entry.display() instanceof FurnaceRecipeDisplay)
			.collect(Collectors.toList());
	}

	// --- Brewing ---

	/**
	 * Brewing lives in its own store, deliberately apart from {@link #recipes}.
	 *
	 * <p>Everything in that map arrived from the vanilla recipe sync and can be handed back to the
	 * server by id - to place, to preview, to plan a sub-craft. These cannot: they are this mod's
	 * own packet, and their ids mean nothing to anyone else. Keeping them out of the map is what
	 * stops a brewing id ever reaching a server that would not recognise it, and keeps the furnace
	 * and crafting queries above from sweeping them up by accident.
	 */
	private static final Map<RecipeDisplayId, BrewingRecipeEntry> brewing = new LinkedHashMap<>();
	/** The same recipes as book entries, under the same ids, so neither has to be found by position. */
	private static final Map<RecipeDisplayId, RecipeDisplayEntry> brewingDisplayById = new LinkedHashMap<>();
	private static List<RecipeDisplayEntry> brewingDisplays = List.of();

	/**
	 * Brew time in ticks, for the display only. Vanilla's stand takes 400 and nothing here can
	 * change that, so it is stated rather than sent.
	 */
	private static final int BREW_TICKS = 400;

	/**
	 * Replace the brewing recipes with what the server just sent.
	 *
	 * <p>The ids are minted here and counted DOWN from -1. A server's own display ids are indices
	 * into its display list and so are never negative, which means a brewing id cannot collide with
	 * a real one however many recipes either side has.
	 */
	public static void setBrewingRecipes(List<BrewingRecipeEntry> entries) {
		brewing.clear();
		brewingDisplayById.clear();

		int nextId = -1;
		for (BrewingRecipeEntry entry : entries) {
			RecipeDisplayId id = new RecipeDisplayId(nextId--);
			brewing.put(id, entry);
			brewingDisplayById.put(id, new RecipeDisplayEntry(id, brewingDisplay(entry),
				OptionalInt.empty(), RecipeBookCategories.CRAFTING_MISC, Optional.empty()));
		}
		brewingDisplays = List.copyOf(brewingDisplayById.values());

		SmartRecipeBookMod.LOGGER.info("Received {} brewing recipes from server", brewing.size());
	}

	/**
	 * A brewing recipe wearing a furnace display.
	 *
	 * <p>Not a pun on the word: a furnace display is the game's one shape for "one input, one
	 * second thing, one result, at this station", which is exactly a brewing stand's three-slot
	 * arithmetic. Borrowing it means the book's list, search, sorting and result rendering all
	 * carry brewing without knowing it exists. Only the preview, which has to name the reagent
	 * rather than call it fuel, asks what it really is.
	 */
	private static RecipeDisplay brewingDisplay(BrewingRecipeEntry entry) {
		return new FurnaceRecipeDisplay(
			stacks(entry.inputs()),
			stacks(entry.reagents()),
			new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(entry.output())),
			new SlotDisplay.ItemSlotDisplay(Items.BREWING_STAND.builtInRegistryHolder()),
			BREW_TICKS,
			0.0F);
	}

	private static SlotDisplay stacks(List<ItemStack> options) {
		if (options.size() == 1) return slot(options.get(0));
		return new SlotDisplay.Composite(options.stream()
			.map(RecipeCache::slot)
			.toList());
	}

	private static SlotDisplay slot(ItemStack stack) {
		return new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(stack));
	}

	/** Every brewing recipe, as book entries. Empty until the server sends them. */
	public static List<RecipeDisplayEntry> getBrewingRecipes() {
		return brewingDisplays;
	}

	/** The brewing recipe behind a book entry, or null when the entry is not one. */
	public static BrewingRecipeEntry getBrewing(RecipeDisplayId id) {
		return brewing.get(id);
	}

	/**
	 * The book entry for whatever brews this stack, or null when nothing does.
	 *
	 * <p>Matched on components as well as item, because the item alone says "a potion" and the
	 * question is always which one.
	 */
	public static RecipeDisplayEntry findBrewingRecipeFor(ItemStack stack) {
		if (stack.isEmpty()) return null;

		for (Map.Entry<RecipeDisplayId, BrewingRecipeEntry> entry : brewing.entrySet()) {
			if (ItemStack.isSameItemSameComponents(entry.getValue().output(), stack)) {
				return brewingDisplayById.get(entry.getKey());
			}
		}
		return null;
	}

	public static boolean hasBrewingRecipes() {
		return !brewing.isEmpty();
	}

	public static List<RecipeDisplayEntry> getBlastFurnaceRecipes() {
		RecipeBookCategory blastFurnaceBlocks = RecipeBookCategories.BLAST_FURNACE_BLOCKS;
		RecipeBookCategory blastFurnaceMisc = RecipeBookCategories.BLAST_FURNACE_MISC;

		List<RecipeDisplayEntry> result = new ArrayList<>();
		for (RecipeDisplayEntry entry : view()) {
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
		for (RecipeDisplayEntry entry : view()) {
			if (!(entry.display() instanceof FurnaceRecipeDisplay)) continue;
			if (entry.category() == smokerFood) {
				result.add(entry);
			}
		}
		return result;
	}

	public static RecipeDisplayEntry findFurnaceRecipeForItem(Item item, Level world) {
		for (RecipeDisplayEntry entry : findRecipesForItem(item, world)) {
			if (entry.display() instanceof FurnaceRecipeDisplay) {
				return entry;
			}
		}
		return null;
	}

	public static List<RecipeDisplayEntry> findAllFurnaceRecipesForItem(Item item, Level world) {
		List<RecipeDisplayEntry> furnaceRecipes = new ArrayList<>();
		for (RecipeDisplayEntry entry : findRecipesForItem(item, world)) {
			if (entry.display() instanceof FurnaceRecipeDisplay) {
				furnaceRecipes.add(entry);
			}
		}
		return furnaceRecipes;
	}
}
