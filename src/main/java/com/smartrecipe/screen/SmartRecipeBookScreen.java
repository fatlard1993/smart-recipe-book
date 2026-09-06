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
import com.smartrecipe.brewing.BrewingRecipeEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

	/**
	 * The book's own frame, and the spacing inside it.
	 *
	 * <p>Everything here used to be measured from the top of the window - a grid pinned at y=50
	 * with the controls trailing under it - so the screen read as a handful of widgets floating on
	 * a dimmed world rather than a thing with edges, and left a band of dead space below itself on
	 * any tall window. The panel is sized from its contents and centred, so it is the same shape
	 * at every resolution.
	 */
	private static final int PAD = 10;
	private static final int ROW_GAP = 8;
	private static final int CONTROL_H = 20;
	/** The spacing is between slots, not after the last one, so one gap comes back off each. */
	private static final int GRID_W = RECIPES_PER_ROW * (SLOT_SIZE + SLOT_SPACING) - SLOT_SPACING;
	private static final int GRID_H = ROWS_PER_PAGE * (SLOT_SIZE + SLOT_SPACING) - SLOT_SPACING;
	/** Wide enough for the longer of the two labels and the icon's gutter, and no wider. */
	private static final int TOGGLE_W = 96;

	private static final int PANEL_FILL = 0xF018181A;
	private static final int PANEL_EDGE = 0xFF000000;
	private static final int PANEL_LIGHT = 0xFF5A5A60;
	private static final int PANEL_DARK = 0xFF3A3A3E;

	// Laid out by layout(), which both init and rendering call: the two used to work the
	// geometry out separately from the same numbers, which is a drift waiting to happen
	private int panelX, panelY, panelW, panelH;
	private int gridX, gridY, titleY, controlsY, footerY;
	private int pagerX, pagerW;

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

	/**
	 * Craftable-only by default: the point of this book is showing what you can
	 * actually make, and the full list is the exception you opt into.
	 *
	 * <p>Static so the choice survives closing the screen. A filter you have to
	 * re-set every time you open a chest is a filter nobody uses.
	 */
	private static boolean craftableOnly = true;

	private EditBox searchField;
	private Button prevPageButton;
	private Button nextPageButton;
	private Button craftableToggle;

	private RecipeDisplayEntry hoveredRecipe = null;

	/** Which station's recipes to show, when the mode is {@link RecipeMode#STATION}. */
	private final String stationCategory;


	public SmartRecipeBookScreen(Screen parent) {
		this(parent, RecipeMode.CRAFTING);
	}

	public SmartRecipeBookScreen(Screen parent, RecipeMode mode) {
		this(parent, mode, null);
	}

	/**
	 * @param stationCategory the recipe book category to show, for {@link RecipeMode#STATION};
	 *                        ignored by every other mode
	 */
	public SmartRecipeBookScreen(Screen parent, RecipeMode mode, String stationCategory) {
		super(Component.literal("Smart Recipe Book"));
		this.parent = parent;
		this.recipeMode = mode;
		this.stationCategory = stationCategory;

		// Determine crafting grid size from parent screen (only relevant for crafting mode).
		// A station is a bench too, so it gets the same three-by-three assumption.
		this.craftingGridSize =
			mode == RecipeMode.CRAFTING || mode == RecipeMode.STATION
				? detectCraftingGridSize(parent) : 1;
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

	/**
	 * The on-screen box of every recipe slot on this page, as {x, y, w, h}.
	 *
	 * <p>The grid is drawn straight onto the screen rather than built out of widgets, which is
	 * cheap and right for a few dozen icons but leaves it invisible to anything that reads
	 * {@code Screen.children()} - a gamepad navigator included, which could reach the search box
	 * and the pager and not one recipe. This is how the grid says where it is, without becoming
	 * forty widgets to do it.
	 *
	 * <p>Only the slots actually filled on this page: a navigator that could land on the empty
	 * tail of the last row would be stepping onto nothing.
	 */
	public List<int[]> recipeSlotBounds() {
		layout();

		List<int[]> bounds = new ArrayList<>();
		int startIndex = currentPage * RECIPES_PER_PAGE;
		int shown = Math.min(RECIPES_PER_PAGE, Math.max(0, displayedRecipes.size() - startIndex));

		for (int i = 0; i < shown; i++) {
			int row = i / RECIPES_PER_ROW;
			int col = i % RECIPES_PER_ROW;
			bounds.add(new int[] {
				gridX + col * (SLOT_SIZE + SLOT_SPACING),
				gridY + row * (SLOT_SIZE + SLOT_SPACING),
				SLOT_SIZE, SLOT_SIZE
			});
		}
		return bounds;
	}

	/** The frame: a dark panel bevelled the way a vanilla slot is, lit top-left. */
	private void drawPanel(GuiGraphicsExtractor context) {
		context.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_FILL);
		context.fill(panelX, panelY, panelX + panelW, panelY + 1, PANEL_EDGE);
		context.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, PANEL_EDGE);
		context.fill(panelX, panelY, panelX + 1, panelY + panelH, PANEL_EDGE);
		context.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, PANEL_EDGE);
		context.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, PANEL_LIGHT);
		context.fill(panelX + 1, panelY + 1, panelX + 2, panelY + panelH - 1, PANEL_LIGHT);
		context.fill(panelX + 1, panelY + panelH - 2, panelX + panelW - 1, panelY + panelH - 1, PANEL_DARK);
		context.fill(panelX + panelW - 2, panelY + 1, panelX + panelW - 1, panelY + panelH - 1, PANEL_DARK);
	}

	/** Measured in init to centre the pager and drawn in render: one wording, one width. */
	private String pageLabel() {
		int totalPages = Math.max(1, (displayedRecipes.size() + RECIPES_PER_PAGE - 1) / RECIPES_PER_PAGE);
		return "Page " + (currentPage + 1) + " / " + totalPages;
	}

	/** Where everything sits, from the contents outward. Cheap, and the single source of it. */
	private void layout() {
		panelW = GRID_W + PAD * 2;
		panelH = PAD + this.font.lineHeight + ROW_GAP - 2 + CONTROL_H + ROW_GAP
			+ GRID_H + ROW_GAP + CONTROL_H + PAD;
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;

		titleY = panelY + PAD;
		controlsY = titleY + this.font.lineHeight + ROW_GAP - 2;
		gridX = panelX + PAD;
		gridY = controlsY + CONTROL_H + ROW_GAP;
		footerY = gridY + GRID_H + ROW_GAP;

		// Room for the widest page label this is likely to wear, not for the one it is wearing:
		// sized to the live text, the arrows would step sideways the moment a page count gained
		// a digit, and the label is drawn centred on the panel either way.
		pagerW = CONTROL_H + ROW_GAP + this.font.width("Page 00 / 00") + ROW_GAP + CONTROL_H;
		pagerX = panelX + (panelW - pagerW) / 2;
	}

	@Override
	protected void init() {
		super.init();

		loadRecipes();

		updateInventory();

		// Clear craftability cache when re-initializing
		craftabilityCache.clear();

		layout();

		// Search and the filter share the header's second row: a full-width box with the filter
		// stranded two rows below it was most of the screen's wasted height.
		int searchW = GRID_W - TOGGLE_W - ROW_GAP;
		searchField = new EditBox(
			this.font,
			gridX,
			controlsY,
			searchW,
			CONTROL_H,
			Component.literal("Search...")
		);
		searchField.setHint(Component.literal("Search recipes..."));
		searchField.setResponder(this::onSearchChanged);
		this.addRenderableWidget(searchField);

		craftableToggle = Button.builder(
			craftableToggleLabel(),
			button -> toggleCraftableOnly()
		).bounds(gridX + GRID_W - TOGGLE_W, controlsY, TOGGLE_W, CONTROL_H).build();
		this.addRenderableWidget(craftableToggle);

		// The pager is one control, so its parts stand together: the arrows used to be pinned to
		// the far corners of the grid with the page number adrift in the middle of them.
		prevPageButton = Button.builder(
			Component.literal("<"),
			button -> previousPage()
		).bounds(pagerX, footerY, CONTROL_H, CONTROL_H).build();
		this.addRenderableWidget(prevPageButton);

		nextPageButton = Button.builder(
			Component.literal(">"),
			button -> nextPage()
		).bounds(pagerX + pagerW - CONTROL_H, footerY, CONTROL_H, CONTROL_H).build();
		this.addRenderableWidget(nextPageButton);

		Button closeButton = Button.builder(
			Component.literal("X"),
			button -> onClose()
		).bounds(panelX + panelW - PAD - CONTROL_H, titleY - 5, CONTROL_H, CONTROL_H).build();
		this.addRenderableWidget(closeButton);

		// Typing is what this screen is for, so the caret is already in the box: the book opens
		// on a list too long to read and the way through it is a word. Set last, after every
		// widget is in, because that is what decides which one holds focus.
		this.setInitialFocus(searchField);

		// Apply filter - this filters to only show craftable recipes
		applyFilters();
	}

	private void loadRecipes() {
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
			case STATION:
				allRecipes = stationCategory == null
					? java.util.List.of() : RecipeCache.getStationRecipes(stationCategory);
				break;
			case BREWING:
				allRecipes = RecipeCache.getBrewingRecipes();
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

	/**
	 * Coloured to match the grid, and padded to leave room for the icon drawn over
	 * it. Green button, green slots: the same green means the same thing in both
	 * places, which is most of what makes it learnable without reading.
	 */
	/** The leading spaces are the icon's gutter: the item is drawn into them by the renderer. */
	private Component craftableToggleLabel() {
		return craftableOnly
			? Component.literal("   Can make").withStyle(ChatFormatting.GREEN)
			: Component.literal("   Everything").withStyle(ChatFormatting.GRAY);
	}

	private void toggleCraftableOnly() {
		craftableOnly = !craftableOnly;
		craftableToggle.setMessage(craftableToggleLabel());
		// Back to page one: the page you were on probably does not exist in the
		// filtered list, and landing on an empty page reads as a broken filter.
		currentPage = 0;
		applyFilters();
	}

	private void applyFilters() {
		displayedRecipes = new ArrayList<>();

		if (minecraft == null || minecraft.level == null) return;

		ContextMap contextParams = SlotDisplayContext.fromLevel(minecraft.level);
		updateInventory();

		// Track seen results to deduplicate (show one recipe per result)
		Set<Object> seenItems = new HashSet<>();

		for (RecipeDisplayEntry entry : allRecipes) {
			// Check if recipe fits current crafting grid (only for crafting mode)
			if ((recipeMode == RecipeMode.CRAFTING || recipeMode == RecipeMode.STATION)
				&& !fitsInGrid(entry)) {
				continue;
			}

			List<ItemStack> results = entry.resultItems(contextParams);
			if (results.isEmpty() || results.get(0).isEmpty()) continue;

			ItemStack resultStack = results.get(0);
			Item resultItem = resultStack.getItem();
			String itemName = resultStack.getHoverName().getString().toLowerCase();

			if (!searchQuery.isEmpty() && !itemName.contains(searchQuery)) {
				continue;
			}

			// Craftability is otherwise only resolved on hover, because a full
			// recursive plan per recipe is expensive. Running it across the whole
			// list is the price of the filter, and it is paid once per recipe:
			// the results land in craftabilityCache, which the hover path and the
			// next filter pass both read.
			if (craftableOnly && !canCraftRecipeRecursive(entry, contextParams)) {
				continue;
			}

			// Skip if we already have a recipe for this result (deduplicate)
			Object resultKey = dedupeKey(resultStack);
			if (seenItems.contains(resultKey)) {
				continue;
			}
			seenItems.add(resultKey);

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

		// A brew is not a craft and has no recipe tree to walk: the calculator would look this id
		// up among the server's recipes, not find it, and report every potion unmakeable.
		if (recipeMode == RecipeMode.BREWING) {
			boolean canBrew = canBrewRecipe(entry);
			craftabilityCache.put(entry.id(), canBrew);
			return canBrew;
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

	/**
	 * What tells two results apart in the list.
	 *
	 * <p>The item alone everywhere but brewing, where it is not enough to tell anything apart:
	 * every potion in the game is the same three items - potion, splash, lingering - and which
	 * potion it is lives in a component. Keyed by item, the whole brewing list collapses to three
	 * rows.
	 */
	private Object dedupeKey(ItemStack stack) {
		if (recipeMode != RecipeMode.BREWING) return stack.getItem();
		return java.util.List.of(stack.getItem(),
			java.util.Optional.ofNullable(stack.get(DataComponents.POTION_CONTENTS)));
	}

	/** Whether the player is holding both halves of a brew: a bottle it starts from and a reagent. */
	private boolean canBrewRecipe(RecipeDisplayEntry entry) {
		BrewingRecipeEntry brew = RecipeCache.getBrewing(entry.id());
		if (brew == null) return false;
		return holdsAnyOf(brew.inputs()) && holdsAnyOf(brew.reagents());
	}

	/**
	 * Read against the real stacks rather than the item-keyed inventory map the rest of the screen
	 * uses. That map cannot answer this: it counts potions by the item, so it knows you hold four
	 * potions and not one of which kind, and brewing turns entirely on which kind.
	 */
	private boolean holdsAnyOf(List<ItemStack> options) {
		if (minecraft == null || minecraft.player == null) return false;

		for (int slot = 0; slot < 36; slot++) {
			ItemStack held = minecraft.player.getInventory().getItem(slot);
			if (held.isEmpty()) continue;
			for (ItemStack option : options) {
				if (ItemStack.isSameItemSameComponents(held, option)) return true;
			}
		}
		return false;
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

		layout();
		drawPanel(context);

		super.extractRenderState(context, mouseX, mouseY, delta);

		if (minecraft == null || minecraft.level == null) return;

		ContextMap contextParams = SlotDisplayContext.fromLevel(minecraft.level);

		// Name first and hard, the qualifiers after it and quiet: the heading used to be one
		// long line of equal weight centred over a screen whose every other edge is flush left.
		context.text(this.font, Component.literal("Recipes"), gridX, titleY, 0xFFFFFFFF, false);
		String modeLabel = recipeMode.isFurnaceType()
			? recipeMode.getDisplayName()
			: (craftingGridSize == 3 ? "3x3 Crafting Table" : "2x2 Inventory");
		context.text(this.font,
			Component.literal(modeLabel + "  -  " + displayedRecipes.size()),
			gridX + this.font.width("Recipes") + 8, titleY, 0xFF8C8C92, false);

		// An item over the toggle, so the button says what it does with a picture as
		// well as a word. A crafting table for "only what I can make", a book for
		// "the whole book". Drawn after the widget so it sits on top of it.
		if (craftableToggle != null) {
			ItemStack toggleIcon = new ItemStack(craftableOnly ? Items.CRAFTING_TABLE : Items.BOOK);
			context.item(toggleIcon, craftableToggle.getX() + 4, craftableToggle.getY() + 2);
		}

		context.centeredText(
			this.font,
			Component.literal(pageLabel()),
			panelX + panelW / 2,
			footerY + (CONTROL_H - this.font.lineHeight) / 2 + 1,
			0xFFBEBEC4
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

			// Can you make this right now? Said in colour, because the answer used
			// to live in a hover tooltip and a child who cannot read yet has no way
			// to reach it. Green and lit means yes; dim and grey means not yet.
			// Brightness carries the same message as hue, so it still reads for a
			// colour-blind player and on a washed-out TV.
			//
			// Only the visible page is asked, and the answers are cached, so this
			// costs one recursive plan per recipe per screen rather than per frame.
			// With the craftable-only filter on, everything here is craftable by
			// definition and the question is not worth asking.
			boolean canMake = craftableOnly || canCraftRecipeRecursive(entry, contextParams);

			int bgColor;
			int borderColor;
			if (canMake) {
				bgColor = hovered ? 0xFF2F5A34 : 0xFF264A2A;
				borderColor = hovered ? 0xFF7FD98A : 0xFF5CA867;
			} else {
				bgColor = hovered ? 0xFF3A3A3A : 0xFF262626;
				borderColor = 0xFF4A4A4A;
			}

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
