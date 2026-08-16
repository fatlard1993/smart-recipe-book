package com.smartrecipe.screen;

import com.smartrecipe.SmartRecipeBookMod;
import com.smartrecipe.crafting.AutoCraftExecutor;
import com.smartrecipe.recipe.CraftingPlan;
import com.smartrecipe.recipe.RecipeCache;
import com.smartrecipe.recipe.RecipeTreeCalculator;
import com.smartrecipe.recipe.CraftCountTracker;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * Custom recipe book screen that shows only craftable recipes.
 * Filters by:
 * - Crafting grid size (2x2 inventory vs 3x3 crafting table)
 * - Materials available (including recursive sub-crafting)
 */
public class SmartRecipeBookScreen extends Screen {

	private static final int RECIPES_PER_ROW = 8;
	private static final int ROWS_PER_PAGE = 5;
	private static final int RECIPES_PER_PAGE = RECIPES_PER_ROW * ROWS_PER_PAGE;
	private static final int SLOT_SIZE = 25;
	private static final int SLOT_SPACING = 2;

	private final Screen parent;
	private final int craftingGridSize; // 2 for 2x2 inventory, 3 for 3x3 crafting table
	private final RecipeMode recipeMode; // CRAFTING or FURNACE
	private int currentPage = 0;
	private List<RecipeDisplayEntry> displayedRecipes = new ArrayList<>();
	private List<RecipeDisplayEntry> allRecipes = new ArrayList<>();
	private String searchQuery = "";
	private Map<Item, Integer> playerInventory = new HashMap<>();

	// Cache for recursive craftability checks (expensive to compute)
	private Map<RecipeDisplayId, Boolean> craftabilityCache = new HashMap<>();

	private EditBox searchField;
	private Button prevPageButton;
	private Button nextPageButton;

	private RecipeDisplayEntry hoveredRecipe = null;


	public SmartRecipeBookScreen(Screen parent) {
		this(parent, RecipeMode.CRAFTING);
	}

	public SmartRecipeBookScreen(Screen parent, RecipeMode mode) {
		super(Component.literal("Smart Recipe Book"));
		this.parent = parent;
		this.recipeMode = mode;

		// Determine crafting grid size from parent screen (only relevant for crafting mode)
		this.craftingGridSize = mode == RecipeMode.CRAFTING ? detectCraftingGridSize(parent) : 1;
	}

	/**
	 * Detect the crafting grid size based on the parent screen type.
	 * Uses class name matching to support mod screens without hard dependencies.
	 */
	private static int detectCraftingGridSize(Screen parent) {
		if (parent == null) return 2;

		if (parent instanceof CraftingScreen) {
			return 3; // 3x3 crafting table
		}

		// Check by class name for mod compatibility (avoids circular dependencies)
		String className = parent.getClass().getSimpleName();

		// Backpack Inventory mod screens
		if (className.equals("BackpackCraftingScreen")) {
			return 3; // 3x3 crafting table equivalent
		}
		if (className.equals("BackpackInventoryScreen")) {
			return 2; // 2x2 inventory crafting equivalent
		}

		// Default to 2x2 for inventory-style screens
		return 2;
	}

	@Override
	protected void init() {
		super.init();

		loadRecipes();

		updateInventory();

		// Clear craftability cache when re-initializing
		craftabilityCache.clear();

		int gridWidth = RECIPES_PER_ROW * (SLOT_SIZE + SLOT_SPACING);
		int gridHeight = ROWS_PER_PAGE * (SLOT_SIZE + SLOT_SPACING);
		int gridX = (this.width - gridWidth) / 2;
		int gridY = 50;

		searchField = new EditBox(
			this.font,
			gridX,
			25,
			gridWidth,
			18,
			Component.literal("Search...")
		);
		searchField.setHint(Component.literal("Search recipes..."));
		searchField.setResponder(this::onSearchChanged);
		this.addRenderableWidget(searchField);

		int navY = gridY + gridHeight + 10;

		prevPageButton = Button.builder(
			Component.literal("<"),
			button -> previousPage()
		).bounds(gridX, navY, 30, 20).build();
		this.addRenderableWidget(prevPageButton);

		nextPageButton = Button.builder(
			Component.literal(">"),
			button -> nextPage()
		).bounds(gridX + gridWidth - 30, navY, 30, 20).build();
		this.addRenderableWidget(nextPageButton);

		// Close button (X) in top-right corner
		Button closeButton = Button.builder(
			Component.literal("X"),
			button -> onClose()
		).bounds(gridX + gridWidth - 20, 5, 20, 18).build();
		this.addRenderableWidget(closeButton);

		// Apply filter - this filters to only show craftable recipes
		applyFilters();
	}

	private void loadRecipes() {
		// Ensure recipes are loaded (will load from integrated server in singleplayer)
		RecipeCache.ensureLoaded();

		switch (recipeMode) {
			case FURNACE:
				allRecipes = RecipeCache.getFurnaceRecipes();
				break;
			case BLAST_FURNACE:
				allRecipes = RecipeCache.getBlastFurnaceRecipes();
				break;
			case SMOKER:
				allRecipes = RecipeCache.getSmokerRecipes();
				break;
			default:
				allRecipes = RecipeCache.getCraftingRecipes();
				break;
		}
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
		if (minecraft != null && minecraft.player != null) {
			playerInventory = RecipeTreeCalculator.getInventoryContents(minecraft.player);
		}
	}

	private void onSearchChanged(String query) {
		this.searchQuery = query.toLowerCase();
		this.currentPage = 0;
		applyFilters();
	}

	private void applyFilters() {
		displayedRecipes = new ArrayList<>();

		if (minecraft == null || minecraft.level == null) return;

		ContextMap contextParams = SlotDisplayContext.fromLevel(minecraft.level);
		updateInventory();

		// Track seen items to deduplicate (show one recipe per result item)
		Set<Item> seenItems = new HashSet<>();

		for (RecipeDisplayEntry entry : allRecipes) {
			// Check if recipe fits current crafting grid (only for crafting mode)
			if (recipeMode == RecipeMode.CRAFTING && !fitsInGrid(entry)) {
				continue;
			}

			List<ItemStack> results = entry.resultItems(contextParams);
			if (results.isEmpty() || results.get(0).isEmpty()) continue;

			ItemStack resultStack = results.get(0);
			Item resultItem = resultStack.getItem();
			String itemName = resultStack.getHoverName().getString().toLowerCase();

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

		final ContextMap sortContext = contextParams;
		displayedRecipes.sort((a, b) -> {
			Item itemA = a.resultItems(sortContext).get(0).getItem();
			Item itemB = b.resultItems(sortContext).get(0).getItem();
			int countA = getCraftedCount(itemA);
			int countB = getCraftedCount(itemB);
			return Integer.compare(countB, countA); // Descending order
		});

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
	private boolean canCraftRecipeRecursive(RecipeDisplayEntry entry, ContextMap contextParams) {
		Boolean cached = craftabilityCache.get(entry.id());
		if (cached != null) {
			return cached;
		}

		// Use RecipeTreeCalculator to check if we can craft this recipe
		// It already handles recursive dependency checking
		if (minecraft == null) {
			craftabilityCache.put(entry.id(), false);
			return false;
		}

		CraftingPlan plan = RecipeTreeCalculator.calculatePlan(minecraft, entry.id());
		boolean canCraft = plan != null && plan.canCraft();

		craftabilityCache.put(entry.id(), canCraft);
		return canCraft;
	}

	private boolean canCraftRecipeDirect(RecipeDisplayEntry entry, ContextMap contextParams) {
		return RecipeTreeCalculator.canCraftDirect(entry.display(), contextParams, playerInventory);
	}

	/**
	 * Check if we have the ingredient for a furnace recipe
	 */
	private boolean canSmeltRecipe(RecipeDisplayEntry entry, ContextMap contextParams) {
		RecipeDisplay display = entry.display();

		if (!(display instanceof FurnaceRecipeDisplay furnaceDisplay)) {
			return false;
		}

		List<ItemStack> possibleIngredients = furnaceDisplay.ingredient().resolveForStacks(contextParams);
		if (possibleIngredients.isEmpty()) return false;

		for (ItemStack stack : possibleIngredients) {
			if (stack.isEmpty()) continue;
			int have = playerInventory.getOrDefault(stack.getItem(), 0);
			if (have > 0) {
				return true;
			}
		}

		return false;
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
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, this.width, this.height, 0xC0101010);

		super.extractRenderState(context, mouseX, mouseY, delta);

		if (minecraft == null || minecraft.level == null) return;

		ContextMap contextParams = SlotDisplayContext.fromLevel(minecraft.level);

		int gridWidth = RECIPES_PER_ROW * (SLOT_SIZE + SLOT_SPACING);
		int gridX = (this.width - gridWidth) / 2;
		int gridY = 50;

		String modeLabel;
		if (recipeMode.isFurnaceType()) {
			modeLabel = recipeMode.getDisplayName();
		} else {
			modeLabel = craftingGridSize == 3 ? "3x3 Crafting Table" : "2x2 Inventory";
		}
		context.centeredText(
			this.font,
			Component.literal("Recipes - " + modeLabel + " (" + displayedRecipes.size() + ")"),
			this.width / 2,
			8,
			0xFFFFFFFF
		);

		int totalPages = Math.max(1, (displayedRecipes.size() + RECIPES_PER_PAGE - 1) / RECIPES_PER_PAGE);
		context.centeredText(
			this.font,
			Component.literal("Page " + (currentPage + 1) + " / " + totalPages),
			this.width / 2,
			gridY + ROWS_PER_PAGE * (SLOT_SIZE + SLOT_SPACING) + 15,
			0xFFAAAAAA
		);

		hoveredRecipe = null;

		int startIndex = currentPage * RECIPES_PER_PAGE;
		for (int i = 0; i < RECIPES_PER_PAGE && startIndex + i < displayedRecipes.size(); i++) {
			int row = i / RECIPES_PER_ROW;
			int col = i % RECIPES_PER_ROW;

			int slotX = gridX + col * (SLOT_SIZE + SLOT_SPACING);
			int slotY = gridY + row * (SLOT_SIZE + SLOT_SPACING);

			RecipeDisplayEntry entry = displayedRecipes.get(startIndex + i);
			List<ItemStack> results = entry.resultItems(contextParams);
			if (results.isEmpty()) continue;

			ItemStack resultStack = results.get(0);

			boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
							  mouseY >= slotY && mouseY < slotY + SLOT_SIZE;

			if (hovered) {
				hoveredRecipe = entry;
			}

			// Neutral styling for all items
			int bgColor = hovered ? 0xFF444444 : 0xFF333333;
			int borderColor = 0xFF666666;

			context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, bgColor);

			context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + 1, borderColor); // top
			context.fill(slotX, slotY + SLOT_SIZE - 1, slotX + SLOT_SIZE, slotY + SLOT_SIZE, borderColor); // bottom
			context.fill(slotX, slotY, slotX + 1, slotY + SLOT_SIZE, borderColor); // left
			context.fill(slotX + SLOT_SIZE - 1, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, borderColor); // right

			int itemX = slotX + (SLOT_SIZE - 16) / 2;
			int itemY = slotY + (SLOT_SIZE - 16) / 2;
			context.item(resultStack, itemX, itemY);

			if (resultStack.getCount() > 1) {
				context.itemDecorations(this.font, resultStack, itemX, itemY);
			}
		}

		// Draw tooltip for hovered recipe with craftability info
		if (hoveredRecipe != null) {
			List<ItemStack> results = hoveredRecipe.resultItems(contextParams);
			if (!results.isEmpty()) {
				List<Component> tooltip = new ArrayList<>();
				tooltip.add(results.get(0).getHoverName());

				if (recipeMode.isFurnaceType()) {
					boolean hasIngredient = canSmeltRecipe(hoveredRecipe, contextParams);
					if (hasIngredient) {
						tooltip.add(Component.literal("✓ Can smelt now").withStyle(ChatFormatting.GREEN));
					} else {
						tooltip.add(Component.literal("✗ Missing ingredient").withStyle(ChatFormatting.RED));
					}
				} else {
					boolean directlyCraftable = canCraftRecipeDirect(hoveredRecipe, contextParams);
					if (directlyCraftable) {
						tooltip.add(Component.literal("✓ Can craft now").withStyle(ChatFormatting.GREEN));
					} else {
						Boolean cached = craftabilityCache.get(hoveredRecipe.id());
						if (cached == null) {
							CraftingPlan plan = RecipeTreeCalculator.calculatePlan(minecraft, hoveredRecipe.id());
							cached = plan != null && plan.canCraft();
							craftabilityCache.put(hoveredRecipe.id(), cached);
						}

						if (cached) {
							tooltip.add(Component.literal("⚡ Requires sub-crafting").withStyle(ChatFormatting.YELLOW));
						} else {
							tooltip.add(Component.literal("✗ Missing materials").withStyle(ChatFormatting.RED));
						}
					}
				}

				context.setComponentTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean consumed) {
		if (super.mouseClicked(click, consumed)) {
			return true;
		}

		if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && hoveredRecipe != null) {
			openRecipePreview(hoveredRecipe);
			return true;
		}

		return false;
	}

	private void openRecipePreview(RecipeDisplayEntry entry) {
		if (minecraft == null) return;
		minecraft.gui.setScreen(new RecipePreviewScreen(this, entry, craftingGridSize));
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent keyInput) {
		if (keyInput.isEscape()) {
			onClose();
			return true;
		}

		if (searchField.isFocused()) {
			return searchField.keyPressed(keyInput);
		}

		return super.keyPressed(keyInput);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public void tick() {
		super.tick();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/**
	 * Get the crafting grid size (2 for inventory, 3 for crafting table)
	 */
	public int getCraftingGridSize() {
		return craftingGridSize;
	}
}
