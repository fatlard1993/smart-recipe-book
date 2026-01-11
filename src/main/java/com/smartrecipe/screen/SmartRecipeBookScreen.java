package com.smartrecipe.screen;

import com.smartrecipe.SmartRecipeBookMod;
import com.smartrecipe.crafting.AutoCraftExecutor;
import com.smartrecipe.recipe.CraftingPlan;
import com.smartrecipe.recipe.RecipeCache;
import com.smartrecipe.recipe.RecipeTreeCalculator;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapedCraftingRecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.screen.slot.Slot;
import com.smartrecipe.recipe.CraftCountTracker;
import net.minecraft.text.Text;
import net.minecraft.util.context.ContextParameterMap;

import java.util.*;

/**
 * Custom recipe book screen that shows only craftable recipes.
 * Filters by:
 * - Crafting grid size (2x2 inventory vs 3x3 crafting table)
 * - Materials available (including recursive sub-crafting)
 */
public class SmartRecipeBookScreen extends Screen {

	// Grid layout constants
	private static final int RECIPES_PER_ROW = 8;
	private static final int ROWS_PER_PAGE = 5;
	private static final int RECIPES_PER_PAGE = RECIPES_PER_ROW * ROWS_PER_PAGE;
	private static final int SLOT_SIZE = 25;
	private static final int SLOT_SPACING = 2;

	// Screen state
	private final Screen parent;
	private final int craftingGridSize; // 2 for 2x2 inventory, 3 for 3x3 crafting table
	private int currentPage = 0;
	private List<RecipeDisplayEntry> displayedRecipes = new ArrayList<>();
	private List<RecipeDisplayEntry> allCraftingRecipes = new ArrayList<>();
	private String searchQuery = "";
	private Map<Item, Integer> playerInventory = new HashMap<>();

	// Cache for recursive craftability checks (expensive to compute)
	private Map<NetworkRecipeId, Boolean> craftabilityCache = new HashMap<>();

	// UI components
	private TextFieldWidget searchField;
	private ButtonWidget prevPageButton;
	private ButtonWidget nextPageButton;

	// Hover state
	private RecipeDisplayEntry hoveredRecipe = null;


	public SmartRecipeBookScreen(Screen parent) {
		super(Text.literal("Smart Recipe Book"));
		this.parent = parent;

		// Determine crafting grid size from parent screen
		if (parent instanceof CraftingScreen) {
			this.craftingGridSize = 3; // 3x3 crafting table
		} else {
			this.craftingGridSize = 2; // 2x2 inventory crafting
		}

		SmartRecipeBookMod.LOGGER.info("SmartRecipeBookScreen opened with {}x{} grid", craftingGridSize, craftingGridSize);
	}

	@Override
	protected void init() {
		super.init();

		// Load recipes from cache
		loadRecipes();

		// Update player inventory
		updateInventory();

		// Clear craftability cache when re-initializing
		craftabilityCache.clear();

		// Calculate grid position (centered)
		int gridWidth = RECIPES_PER_ROW * (SLOT_SIZE + SLOT_SPACING);
		int gridHeight = ROWS_PER_PAGE * (SLOT_SIZE + SLOT_SPACING);
		int gridX = (this.width - gridWidth) / 2;
		int gridY = 50;

		// Search field
		searchField = new TextFieldWidget(
			this.textRenderer,
			gridX,
			25,
			gridWidth,
			18,
			Text.literal("Search...")
		);
		searchField.setPlaceholder(Text.literal("Search recipes..."));
		searchField.setChangedListener(this::onSearchChanged);
		this.addDrawableChild(searchField);

		// Page navigation
		int navY = gridY + gridHeight + 10;

		prevPageButton = ButtonWidget.builder(
			Text.literal("<"),
			button -> previousPage()
		).dimensions(gridX, navY, 30, 20).build();
		this.addDrawableChild(prevPageButton);

		nextPageButton = ButtonWidget.builder(
			Text.literal(">"),
			button -> nextPage()
		).dimensions(gridX + gridWidth - 30, navY, 30, 20).build();
		this.addDrawableChild(nextPageButton);

		// Close button (X) in top-right corner
		ButtonWidget closeButton = ButtonWidget.builder(
			Text.literal("X"),
			button -> close()
		).dimensions(gridX + gridWidth - 20, 5, 20, 18).build();
		this.addDrawableChild(closeButton);

		// Apply filter - this filters to only show craftable recipes
		applyFilters();
	}

	private void loadRecipes() {
		// Ensure recipes are loaded (will load from integrated server in singleplayer)
		RecipeCache.ensureLoaded();

		// Get all crafting recipes from our cache
		allCraftingRecipes = RecipeCache.getCraftingRecipes();
		SmartRecipeBookMod.LOGGER.info("SmartRecipeBookScreen: loaded {} crafting recipes", allCraftingRecipes.size());
	}

	/**
	 * Refresh the recipe list (re-sort by craft count).
	 * Called when returning from recipe preview screen.
	 */
	public void refresh() {
		updateInventory();
		craftabilityCache.clear();
		applyFilters();
	}

	/**
	 * Get the crafted count for an item from our tracker.
	 */
	private int getCraftedCount(Item item) {
		return CraftCountTracker.getCount(item);
	}

	private void updateInventory() {
		if (client != null && client.player != null) {
			playerInventory = RecipeTreeCalculator.getInventoryContents(client.player);
		}
	}

	private void onSearchChanged(String query) {
		this.searchQuery = query.toLowerCase();
		this.currentPage = 0;
		applyFilters();
	}

	private void applyFilters() {
		displayedRecipes = new ArrayList<>();

		if (client == null || client.world == null) return;

		ContextParameterMap contextParams = SlotDisplayContexts.createParameters(client.world);
		updateInventory();

		// Track seen items to deduplicate (show one recipe per result item)
		Set<Item> seenItems = new HashSet<>();

		for (RecipeDisplayEntry entry : allCraftingRecipes) {
			// Check if recipe fits current crafting grid
			if (!fitsInGrid(entry)) {
				continue;
			}

			// Get result item for filtering
			List<ItemStack> results = entry.getStacks(contextParams);
			if (results.isEmpty() || results.get(0).isEmpty()) continue;

			ItemStack resultStack = results.get(0);
			Item resultItem = resultStack.getItem();
			String itemName = resultStack.getName().getString().toLowerCase();

			// Apply search filter only - craftability is checked on hover
			if (!searchQuery.isEmpty() && !itemName.contains(searchQuery)) {
				continue;
			}

			// Skip if we already have a recipe for this item (deduplicate)
			if (seenItems.contains(resultItem)) {
				continue;
			}
			seenItems.add(resultItem);

			displayedRecipes.add(entry);
		}

		// Sort by vanilla craft statistics (most crafted first)
		final ContextParameterMap sortContext = contextParams;

		// Debug: check a few items' stats before sorting
		int itemsWithStats = 0;
		for (RecipeDisplayEntry entry : displayedRecipes) {
			Item item = entry.getStacks(sortContext).get(0).getItem();
			int count = getCraftedCount(item);
			if (count > 0) {
				itemsWithStats++;
				if (itemsWithStats <= 5) {
					SmartRecipeBookMod.LOGGER.info("Stats found: {} = {}", item.getName().getString(), count);
				}
			}
		}
		SmartRecipeBookMod.LOGGER.info("Total items with stats > 0: {}", itemsWithStats);

		displayedRecipes.sort((a, b) -> {
			Item itemA = a.getStacks(sortContext).get(0).getItem();
			Item itemB = b.getStacks(sortContext).get(0).getItem();
			int countA = getCraftedCount(itemA);
			int countB = getCraftedCount(itemB);
			return Integer.compare(countB, countA); // Descending order
		});

		// Debug: show first 5 items after sorting
		SmartRecipeBookMod.LOGGER.info("After sorting - first 5 items:");
		for (int i = 0; i < Math.min(5, displayedRecipes.size()); i++) {
			Item item = displayedRecipes.get(i).getStacks(sortContext).get(0).getItem();
			int count = getCraftedCount(item);
			SmartRecipeBookMod.LOGGER.info("  {}: {} (count={})", i + 1, item.getName().getString(), count);
		}

		SmartRecipeBookMod.LOGGER.info("Showing {} unique recipes for {}x{} grid",
			displayedRecipes.size(), craftingGridSize, craftingGridSize);

		// Update page navigation
		updatePageButtons();
	}

	/**
	 * Check if a recipe fits in the current crafting grid
	 */
	private boolean fitsInGrid(RecipeDisplayEntry entry) {
		RecipeDisplay display = entry.display();

		if (display instanceof ShapedCraftingRecipeDisplay shaped) {
			// Shaped recipes have explicit dimensions
			int width = shaped.width();
			int height = shaped.height();
			return width <= craftingGridSize && height <= craftingGridSize;
		} else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
			// Shapeless recipes just need enough slots
			int ingredientCount = shapeless.ingredients().size();
			int maxSlots = craftingGridSize * craftingGridSize;
			return ingredientCount <= maxSlots;
		}

		return false;
	}

	/**
	 * Check if a recipe can be crafted with current inventory,
	 * including recursive sub-crafting of ingredients.
	 * Uses caching to avoid recalculating expensive checks.
	 */
	private boolean canCraftRecipeRecursive(RecipeDisplayEntry entry, ContextParameterMap contextParams) {
		// Check cache first
		Boolean cached = craftabilityCache.get(entry.id());
		if (cached != null) {
			return cached;
		}

		// Use RecipeTreeCalculator to check if we can craft this recipe
		// It already handles recursive dependency checking
		if (client == null) {
			craftabilityCache.put(entry.id(), false);
			return false;
		}

		CraftingPlan plan = RecipeTreeCalculator.calculatePlan(client, entry.id());
		boolean canCraft = plan != null && plan.canCraft();

		craftabilityCache.put(entry.id(), canCraft);
		return canCraft;
	}

	/**
	 * Simple direct check if we have materials for a recipe (no sub-crafting)
	 * Used for display purposes (green border)
	 */
	private boolean canCraftRecipeDirect(RecipeDisplayEntry entry, ContextParameterMap contextParams) {
		RecipeDisplay display = entry.display();

		List<net.minecraft.recipe.display.SlotDisplay> ingredients;
		if (display instanceof ShapedCraftingRecipeDisplay shaped) {
			ingredients = shaped.ingredients();
		} else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
			ingredients = shapeless.ingredients();
		} else {
			return false;
		}

		// Create temporary inventory to simulate consumption
		Map<Item, Integer> tempInventory = new HashMap<>(playerInventory);

		for (var slot : ingredients) {
			List<ItemStack> possible = slot.getStacks(contextParams);
			if (possible.isEmpty()) continue;

			boolean found = false;
			for (ItemStack stack : possible) {
				if (stack.isEmpty()) continue;
				int have = tempInventory.getOrDefault(stack.getItem(), 0);
				if (have > 0) {
					tempInventory.put(stack.getItem(), have - 1);
					found = true;
					break;
				}
			}

			if (!found) return false;
		}

		return true;
	}

	private void updatePageButtons() {
		int totalPages = (displayedRecipes.size() + RECIPES_PER_PAGE - 1) / RECIPES_PER_PAGE;
		if (totalPages == 0) totalPages = 1;

		prevPageButton.active = currentPage > 0;
		nextPageButton.active = currentPage < totalPages - 1;
	}

	private void previousPage() {
		if (currentPage > 0) {
			currentPage--;
			updatePageButtons();
		}
	}

	private void nextPage() {
		int totalPages = (displayedRecipes.size() + RECIPES_PER_PAGE - 1) / RECIPES_PER_PAGE;
		if (currentPage < totalPages - 1) {
			currentPage++;
			updatePageButtons();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Draw dark background
		context.fill(0, 0, this.width, this.height, 0xC0101010);

		super.render(context, mouseX, mouseY, delta);

		if (client == null || client.world == null) return;

		ContextParameterMap contextParams = SlotDisplayContexts.createParameters(client.world);

		// Calculate grid position
		int gridWidth = RECIPES_PER_ROW * (SLOT_SIZE + SLOT_SPACING);
		int gridX = (this.width - gridWidth) / 2;
		int gridY = 50;

		// Draw title
		String gridLabel = craftingGridSize == 3 ? "3x3 Crafting Table" : "2x2 Inventory";
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("Recipes - " + gridLabel + " (" + displayedRecipes.size() + ")"),
			this.width / 2,
			8,
			0xFFFFFF
		);

		// Draw page info
		int totalPages = Math.max(1, (displayedRecipes.size() + RECIPES_PER_PAGE - 1) / RECIPES_PER_PAGE);
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal("Page " + (currentPage + 1) + " / " + totalPages),
			this.width / 2,
			gridY + ROWS_PER_PAGE * (SLOT_SIZE + SLOT_SPACING) + 15,
			0xAAAAAA
		);

		// Reset hover state
		hoveredRecipe = null;

		// Draw recipe grid
		int startIndex = currentPage * RECIPES_PER_PAGE;
		for (int i = 0; i < RECIPES_PER_PAGE && startIndex + i < displayedRecipes.size(); i++) {
			int row = i / RECIPES_PER_ROW;
			int col = i % RECIPES_PER_ROW;

			int slotX = gridX + col * (SLOT_SIZE + SLOT_SPACING);
			int slotY = gridY + row * (SLOT_SIZE + SLOT_SPACING);

			RecipeDisplayEntry entry = displayedRecipes.get(startIndex + i);
			List<ItemStack> results = entry.getStacks(contextParams);
			if (results.isEmpty()) continue;

			ItemStack resultStack = results.get(0);

			// Check if mouse is hovering
			boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
							  mouseY >= slotY && mouseY < slotY + SLOT_SIZE;

			if (hovered) {
				hoveredRecipe = entry;
			}

			// Neutral styling for all items
			int bgColor = hovered ? 0xFF444444 : 0xFF333333;
			int borderColor = 0xFF666666;

			context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, bgColor);

			// Draw border
			context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + 1, borderColor); // top
			context.fill(slotX, slotY + SLOT_SIZE - 1, slotX + SLOT_SIZE, slotY + SLOT_SIZE, borderColor); // bottom
			context.fill(slotX, slotY, slotX + 1, slotY + SLOT_SIZE, borderColor); // left
			context.fill(slotX + SLOT_SIZE - 1, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, borderColor); // right

			// Draw item
			int itemX = slotX + (SLOT_SIZE - 16) / 2;
			int itemY = slotY + (SLOT_SIZE - 16) / 2;
			context.drawItem(resultStack, itemX, itemY);

			// Draw count if > 1
			if (resultStack.getCount() > 1) {
				context.drawStackOverlay(this.textRenderer, resultStack, itemX, itemY);
			}
		}

		// Draw tooltip for hovered recipe with craftability info
		if (hoveredRecipe != null) {
			List<ItemStack> results = hoveredRecipe.getStacks(contextParams);
			if (!results.isEmpty()) {
				// Build custom tooltip with craftability info
				List<Text> tooltip = new ArrayList<>();
				tooltip.add(results.get(0).getName());

				boolean directlyCraftable = canCraftRecipeDirect(hoveredRecipe, contextParams);
				if (directlyCraftable) {
					tooltip.add(Text.literal("§a✓ Can craft now").styled(s -> s));
				} else {
					// Check if craftable with sub-crafting (lazy evaluation)
					Boolean cached = craftabilityCache.get(hoveredRecipe.id());
					if (cached == null) {
						// Calculate on hover
						CraftingPlan plan = RecipeTreeCalculator.calculatePlan(client, hoveredRecipe.id());
						cached = plan != null && plan.canCraft();
						craftabilityCache.put(hoveredRecipe.id(), cached);
					}

					if (cached) {
						tooltip.add(Text.literal("§e⚡ Requires sub-crafting").styled(s -> s));
					} else {
						tooltip.add(Text.literal("§c✗ Missing materials").styled(s -> s));
					}
				}

				context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean consumed) {
		if (super.mouseClicked(click, consumed)) {
			return true;
		}

		// Check if clicking on a recipe slot
		if (click.button() == 0 && hoveredRecipe != null) {
			craftRecipe(hoveredRecipe);
			return true;
		}

		return false;
	}

	private void craftRecipe(RecipeDisplayEntry entry) {
		if (client == null) return;

		SmartRecipeBookMod.LOGGER.info("Opening recipe preview for: {}", entry.id());

		// Open recipe preview screen
		client.setScreen(new RecipePreviewScreen(this, entry));
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyInput keyInput) {
		// Close on escape
		if (keyInput.key() == 256) { // ESC
			close();
			return true;
		}

		// Let search field handle input
		if (searchField.isFocused()) {
			return searchField.keyPressed(keyInput);
		}

		return super.keyPressed(keyInput);
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}

	@Override
	public void tick() {
		super.tick();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/**
	 * Get the crafting grid size (2 for inventory, 3 for crafting table)
	 */
	public int getCraftingGridSize() {
		return craftingGridSize;
	}
}
