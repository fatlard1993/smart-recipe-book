package com.smartrecipe.screen;

import com.smartrecipe.SmartRecipeBookMod;
import com.smartrecipe.crafting.AutoCraftExecutor;
import com.smartrecipe.recipe.CraftingPlan;
import com.smartrecipe.recipe.RecipeCache;
import com.smartrecipe.recipe.RecipeTreeCalculator;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * Preview screen shown when clicking a recipe in the recipe book.
 * Shows recipe grid, craftability status, quantity selector, and craft button.
 */
public class RecipePreviewScreen extends Screen {

	private final Screen parent;
	private final RecipeDisplayEntry recipe;
	private final ItemStack resultStack;
	private final Map<Item, Integer> playerInventory;
	private final int craftingGridSize; // 2 for inventory, 3 for crafting table
	private final boolean isFurnaceRecipe;

	private CraftingPlan craftingPlan;
	private boolean canCraft = false;
	private int craftQuantity = 1;
	private int maxCraftable = 1;

	private boolean showingConfirmation = false;
	private int confirmationTicks = 0;
	private static final int CONFIRMATION_DURATION = 15; // ~0.75 seconds

	private Button craftButton;
	private Button cancelButton;
	private Button plusButton;
	private Button minusButton;

	private static final int SLOT_SIZE = 18;
	private static final int GRID_SLOT_SIZE = 20;
	private static final int PANEL_WIDTH = 220;
	private static final int PANEL_HEIGHT = 200;

	private static class SlotInfo {
		int x, y;
		ItemStack stack;
		RecipeDisplayEntry recipe; // Recipe for this item, if it fits in current grid

		SlotInfo(int x, int y, ItemStack stack) {
			this.x = x;
			this.y = y;
			this.stack = stack;
			this.recipe = null;
		}
	}
	private List<SlotInfo> ingredientSlots = new ArrayList<>();
	private SlotInfo resultSlot = null;
	private SlotInfo hoveredSlot = null;

	// Scroll state for furnace recipes with many ingredients
	private int ingredientScrollOffset = 0;
	private int maxVisibleIngredientRows = 3;
	private int totalIngredientRows = 0;

	public RecipePreviewScreen(Screen parent, RecipeDisplayEntry recipe, int craftingGridSize) {
		super(Component.literal("Recipe Preview"));
		this.parent = parent;
		this.recipe = recipe;

		this.isFurnaceRecipe = recipe.display() instanceof FurnaceRecipeDisplay;
		this.craftingGridSize = isFurnaceRecipe ? 1 : craftingGridSize;

		Minecraft client = Minecraft.getInstance();
		if (client.level != null) {
			ContextMap contextParams = SlotDisplayContext.fromLevel(client.level);
			List<ItemStack> results = recipe.resultItems(contextParams);
			this.resultStack = results.isEmpty() ? ItemStack.EMPTY : results.get(0);
		} else {
			this.resultStack = ItemStack.EMPTY;
		}

		if (client.player != null) {
			this.playerInventory = RecipeTreeCalculator.getInventoryContents(client.player);
		} else {
			this.playerInventory = new HashMap<>();
		}
	}

	@Override
	protected void init() {
		super.init();

		calculateCraftability();

		calculateMaxCraftable();

		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;

		int quantityY = panelY + PANEL_HEIGHT - 60;

		if (!isFurnaceRecipe) {
			minusButton = Button.builder(
				Component.literal("-"),
				button -> adjustQuantity(-1)
			).bounds(panelX + 50, quantityY, 20, 20).build();
			this.addRenderableWidget(minusButton);

			plusButton = Button.builder(
				Component.literal("+"),
				button -> adjustQuantity(1)
			).bounds(panelX + 130, quantityY, 20, 20).build();
			this.addRenderableWidget(plusButton);
		}

		if (isFurnaceRecipe) {
			// For furnace recipes, just show a close button (can't auto-smelt)
			craftButton = Button.builder(
				Component.literal("Close"),
				button -> onClose()
			).bounds(panelX + 10, panelY + PANEL_HEIGHT - 30, 95, 20).build();
		} else {
			craftButton = Button.builder(
				Component.literal("Craft"),
				button -> craftRecipe()
			).bounds(panelX + 10, panelY + PANEL_HEIGHT - 30, 95, 20).build();
			craftButton.active = canCraft;
		}
		this.addRenderableWidget(craftButton);

		cancelButton = Button.builder(
			Component.literal("Cancel"),
			button -> onClose()
		).bounds(panelX + PANEL_WIDTH - 105, panelY + PANEL_HEIGHT - 30, 95, 20).build();
		this.addRenderableWidget(cancelButton);

		updateQuantityButtons();
	}

	private void adjustQuantity(int delta) {
		craftQuantity = Math.max(1, Math.min(maxCraftable, craftQuantity + delta));
		updateQuantityButtons();
	}

	private void updateQuantityButtons() {
		if (minusButton != null) {
			minusButton.active = craftQuantity > 1;
		}
		if (plusButton != null) {
			plusButton.active = craftQuantity < maxCraftable && canCraft;
		}
	}

	private void calculateCraftability() {
		if (minecraft == null) return;

		ContextMap contextParams = SlotDisplayContext.fromLevel(minecraft.level);

		if (isFurnaceRecipe) {
			canCraft = hasSmeltingIngredient(contextParams);
			craftingPlan = null;
		} else {
			craftingPlan = RecipeTreeCalculator.calculatePlan(minecraft, recipe.id());
			canCraft = craftingPlan != null && craftingPlan.canCraft();
		}
	}

	private boolean hasSmeltingIngredient(ContextMap contextParams) {
		RecipeDisplay display = recipe.display();
		if (!(display instanceof FurnaceRecipeDisplay furnaceDisplay)) {
			return false;
		}

		List<ItemStack> possibleIngredients = furnaceDisplay.ingredient().resolveForStacks(contextParams);
		for (ItemStack stack : possibleIngredients) {
			if (!stack.isEmpty() && playerInventory.getOrDefault(stack.getItem(), 0) > 0) {
				return true;
			}
		}
		return false;
	}

	private void calculateMaxCraftable() {
		if (!canCraft || minecraft == null) {
			maxCraftable = 1;
			return;
		}

		// Use RecipeTreeCalculator to calculate max craftable with sub-crafting support
		maxCraftable = RecipeTreeCalculator.calculateMaxCraftable(minecraft, recipe.id());
		SmartRecipeBookMod.LOGGER.debug("Max craftable for {}: {}", resultStack.getHoverName().getString(), maxCraftable);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, this.width, this.height, 0xC0101010);

		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;

		context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF2A2A2A);

		int borderColor = canCraft ? 0xFF44AA44 : 0xFF666666;
		context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 2, borderColor);
		context.fill(panelX, panelY + PANEL_HEIGHT - 2, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, borderColor);
		context.fill(panelX, panelY, panelX + 2, panelY + PANEL_HEIGHT, borderColor);
		context.fill(panelX + PANEL_WIDTH - 2, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, borderColor);

		context.centeredText(
			this.font,
			resultStack.getHoverName(),
			panelX + PANEL_WIDTH / 2,
			panelY + 8,
			0xFFFFFFFF
		);

		drawCraftingGrid(context, panelX, panelY + 25, mouseX, mouseY);

		if (!isFurnaceRecipe) {
			String quantityText = "Quantity: " + craftQuantity;
			if (maxCraftable > 1) {
				quantityText += " / " + maxCraftable;
			}
			int quantityColor = canCraft ? 0xFF44FF44 : 0xFFFF4444;

			context.centeredText(
				this.font,
				Component.literal(quantityText),
				panelX + PANEL_WIDTH / 2,
				panelY + 105,
				quantityColor
			);
		}

		super.extractRenderState(context, mouseX, mouseY, delta);

		if (showingConfirmation) {
			drawConfirmation(context);
		}

		// Draw tooltip for hovered item (must be last to render on top)
		if (hoveredSlot != null && !hoveredSlot.stack.isEmpty()) {
			List<Component> tooltip = new ArrayList<>();
			tooltip.add(hoveredSlot.stack.getHoverName());

			// Show count in inventory
			int have = playerInventory.getOrDefault(hoveredSlot.stack.getItem(), 0);
			if (have > 0) {
				tooltip.add(Component.literal("In inventory: " + have).withStyle(ChatFormatting.GRAY));
			} else {
				tooltip.add(Component.literal("Not in inventory").withStyle(ChatFormatting.RED));
			}

			if (hoveredSlot.recipe != null) {
				if (isFurnaceRecipe) {
					tooltip.add(Component.literal("[Click to see how to smelt this]").withStyle(ChatFormatting.AQUA));
				} else {
					tooltip.add(Component.literal("[Click to view recipe]").withStyle(ChatFormatting.AQUA));
				}
			}

			context.setComponentTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
		}
	}

	private void drawCraftingGrid(GuiGraphicsExtractor context, int panelX, int startY, int mouseX, int mouseY) {
		if (minecraft == null || minecraft.level == null) return;

		ingredientSlots.clear();
		resultSlot = null;
		hoveredSlot = null;

		ContextMap contextParams = SlotDisplayContext.fromLevel(minecraft.level);
		RecipeDisplay display = recipe.display();

		// Handle furnace recipes separately
		if (display instanceof FurnaceRecipeDisplay furnaceDisplay) {
			drawFurnaceRecipe(context, panelX, startY, mouseX, mouseY, furnaceDisplay, contextParams);
			return;
		}

		int gridWidth, gridHeight;
		List<SlotDisplay> displaySlots;

		if (display instanceof ShapedCraftingRecipeDisplay shaped) {
			gridWidth = shaped.width();
			gridHeight = shaped.height();
			displaySlots = shaped.ingredients();
		} else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
			displaySlots = shapeless.ingredients();
			// Arrange shapeless in a compact grid
			int count = displaySlots.size();
			gridWidth = Math.min(count, 3);
			gridHeight = (count + 2) / 3;
		} else {
			return;
		}

		int gridPixelWidth = gridWidth * GRID_SLOT_SIZE;
		int totalWidth = gridPixelWidth + 30 + GRID_SLOT_SIZE; // grid + arrow + result
		int gridX = panelX + (PANEL_WIDTH - totalWidth) / 2;
		int gridY = startY;

		for (int i = 0; i < displaySlots.size(); i++) {
			int col, row;
			if (display instanceof ShapedCraftingRecipeDisplay) {
				col = i % gridWidth;
				row = i / gridWidth;
			} else {
				col = i % 3;
				row = i / 3;
			}

			int slotX = gridX + col * GRID_SLOT_SIZE;
			int slotY = gridY + row * GRID_SLOT_SIZE;

			context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0xFF3A3A3A);

			// Draw ingredient
			SlotDisplay slot = displaySlots.get(i);
			List<ItemStack> possible = slot.resolveForStacks(contextParams);
			if (!possible.isEmpty()) {
				ItemStack ingredientStack = null;
				for (ItemStack stack : possible) {
					if (!stack.isEmpty()) {
						ingredientStack = stack;
						break;
					}
				}

				if (ingredientStack != null) {
					// Track this slot
					SlotInfo slotInfo = new SlotInfo(slotX, slotY, ingredientStack);

					// Find recipe for this item that fits current grid
					RecipeDisplayEntry itemRecipe = findRecipeForItem(ingredientStack.getItem(), contextParams);
					if (itemRecipe != null && fitsInGrid(itemRecipe)) {
						slotInfo.recipe = itemRecipe;
					}
					ingredientSlots.add(slotInfo);

					boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
									  mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
					if (hovered) {
						hoveredSlot = slotInfo;
					}

					// Check if player has this ingredient
					int have = playerInventory.getOrDefault(ingredientStack.getItem(), 0);
					boolean hasIngredient = have > 0;

					context.item(ingredientStack, slotX + 1, slotY + 1);

					// Draw overlay based on status
					if (!hasIngredient) {
						if (slotInfo.recipe != null) {
							// Can be crafted - yellow overlay (25% opacity)
							context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0x40FFAA00);
						} else {
							// Missing and no recipe - red overlay (25% opacity)
							context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0x40FF4444);
						}
					}
				}
			}
		}

		int arrowX = gridX + gridPixelWidth + 5;
		int arrowY = gridY + (gridHeight * GRID_SLOT_SIZE) / 2 - 4;
		context.text(this.font, Component.literal("→"), arrowX, arrowY, 0xFFFFFFFF);

		int resultX = arrowX + 20;
		int resultY = gridY + (gridHeight * GRID_SLOT_SIZE) / 2 - SLOT_SIZE / 2;

		resultSlot = new SlotInfo(resultX, resultY, resultStack);

		boolean resultHovered = mouseX >= resultX && mouseX < resultX + SLOT_SIZE &&
								mouseY >= resultY && mouseY < resultY + SLOT_SIZE;
		if (resultHovered) {
			hoveredSlot = resultSlot;
		}

		context.fill(resultX - 2, resultY - 2, resultX + SLOT_SIZE + 2, resultY + SLOT_SIZE + 2, 0xFF4A4A4A);
		context.fill(resultX, resultY, resultX + SLOT_SIZE, resultY + SLOT_SIZE, 0xFF3A3A3A);

		context.item(resultStack, resultX + 1, resultY + 1);
		if (resultStack.getCount() > 1) {
			context.itemDecorations(this.font, resultStack, resultX + 1, resultY + 1);
		}
	}

	/**
	 * Draw furnace recipe showing all possible ingredients → result
	 * Collects ingredients from ALL furnace recipes that produce this result
	 */
	private void drawFurnaceRecipe(GuiGraphicsExtractor context, int panelX, int startY, int mouseX, int mouseY,
								   FurnaceRecipeDisplay furnaceDisplay, ContextMap contextParams) {
		// Find ALL furnace recipes that produce this result and collect their ingredients
		List<ItemStack> allIngredients = new ArrayList<>();
		Set<Item> seenItems = new HashSet<>();

		List<RecipeDisplayEntry> allFurnaceRecipes = RecipeCache.findAllFurnaceRecipesForItem(
			resultStack.getItem(), minecraft.level);

		for (RecipeDisplayEntry entry : allFurnaceRecipes) {
			if (entry.display() instanceof FurnaceRecipeDisplay fd) {
				for (ItemStack stack : fd.ingredient().resolveForStacks(contextParams)) {
					if (!stack.isEmpty() && !seenItems.contains(stack.getItem())) {
						seenItems.add(stack.getItem());
						allIngredients.add(stack);
					}
				}
			}
		}

		// Fallback to current recipe's ingredients if no recipes found
		if (allIngredients.isEmpty()) {
			for (ItemStack stack : furnaceDisplay.ingredient().resolveForStacks(contextParams)) {
				if (!stack.isEmpty() && !seenItems.contains(stack.getItem())) {
					seenItems.add(stack.getItem());
					allIngredients.add(stack);
				}
			}
		}

		if (allIngredients.isEmpty()) return;

		int ingredientCount = allIngredients.size();
		int ingredientsPerRow = 4; // Always 4 per row for consistency
		totalIngredientRows = (ingredientCount + ingredientsPerRow - 1) / ingredientsPerRow;

		int maxScrollOffset = Math.max(0, totalIngredientRows - maxVisibleIngredientRows);
		ingredientScrollOffset = Math.max(0, Math.min(ingredientScrollOffset, maxScrollOffset));

		// Layout dimensions (only show visible rows)
		int visibleRows = Math.min(totalIngredientRows, maxVisibleIngredientRows);
		int ingredientGridWidth = ingredientsPerRow * (SLOT_SIZE + 2);
		int ingredientGridHeight = visibleRows * (SLOT_SIZE + 2);

		int totalWidth = ingredientGridWidth + 25 + 20 + 25 + SLOT_SIZE; // ingredients + arrow + fire + arrow + result
		int startX = panelX + (PANEL_WIDTH - totalWidth) / 2;
		int baseY = startY + 10;

		// Draw scroll indicators if needed
		boolean canScrollUp = ingredientScrollOffset > 0;
		boolean canScrollDown = ingredientScrollOffset < maxScrollOffset;

		if (canScrollUp) {
			context.centeredText(this.font, Component.literal("▲ scroll"),
				startX + ingredientGridWidth / 2, baseY - 12, 0xFFAAAA00);
		}
		if (canScrollDown) {
			context.centeredText(this.font, Component.literal("▼ scroll"),
				startX + ingredientGridWidth / 2, baseY + ingredientGridHeight + 2, 0xFFAAAA00);
		}

		boolean hasAnyIngredient = false;
		int startIndex = ingredientScrollOffset * ingredientsPerRow;
		int endIndex = Math.min(allIngredients.size(), startIndex + (maxVisibleIngredientRows * ingredientsPerRow));

		for (int i = startIndex; i < endIndex; i++) {
			int visibleIndex = i - startIndex;
			int row = visibleIndex / ingredientsPerRow;
			int col = visibleIndex % ingredientsPerRow;

			int slotX = startX + col * (SLOT_SIZE + 2);
			int slotY = baseY + row * (SLOT_SIZE + 2);

			ItemStack ingredientStack = allIngredients.get(i);

			context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0xFF3A3A3A);

			// Track this slot
			SlotInfo slotInfo = new SlotInfo(slotX, slotY, ingredientStack);

			// Find FURNACE recipe for ingredient (to enable click navigation to other smelting recipes)
			RecipeDisplayEntry itemRecipe = findFurnaceRecipeForItem(ingredientStack.getItem(), contextParams);
			if (itemRecipe != null) {
				slotInfo.recipe = itemRecipe;
			}
			ingredientSlots.add(slotInfo);

			// Check if hovering
			boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
							  mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
			if (hovered) {
				hoveredSlot = slotInfo;
			}

			// Check if player has ingredient
			int have = playerInventory.getOrDefault(ingredientStack.getItem(), 0);
			boolean hasIngredient = have > 0;
			if (hasIngredient) hasAnyIngredient = true;

			context.item(ingredientStack, slotX + 1, slotY + 1);

			// Draw overlay based on status
			if (!hasIngredient) {
				if (slotInfo.recipe != null) {
					// Can be smelted from something - yellow overlay
					context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0x40FFAA00);
				} else {
					// Missing and no furnace recipe - red overlay
					context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0x40FF4444);
				}
			}
		}

		// Also check items not currently visible for "hasAnyIngredient" status
		for (ItemStack stack : allIngredients) {
			if (playerInventory.getOrDefault(stack.getItem(), 0) > 0) {
				hasAnyIngredient = true;
				break;
			}
		}

		int ingredientCenterY = baseY + ingredientGridHeight / 2;

		int arrow1X = startX + ingredientGridWidth + 5;
		context.text(this.font, Component.literal("→"), arrow1X, ingredientCenterY - 4, 0xFFFFFFFF);

		int fireX = arrow1X + 18;
		context.text(this.font, Component.literal("*"), fireX, ingredientCenterY - 4, 0xFFFFAA00);

		int arrow2X = fireX + 18;
		context.text(this.font, Component.literal("→"), arrow2X, ingredientCenterY - 4, 0xFFFFFFFF);

		int resultX = arrow2X + 18;
		int resultY = ingredientCenterY - SLOT_SIZE / 2;

		resultSlot = new SlotInfo(resultX, resultY, resultStack);

		boolean resultHovered = mouseX >= resultX && mouseX < resultX + SLOT_SIZE &&
								mouseY >= resultY && mouseY < resultY + SLOT_SIZE;
		if (resultHovered) {
			hoveredSlot = resultSlot;
		}

		context.fill(resultX - 2, resultY - 2, resultX + SLOT_SIZE + 2, resultY + SLOT_SIZE + 2, 0xFF4A4A4A);
		context.fill(resultX, resultY, resultX + SLOT_SIZE, resultY + SLOT_SIZE, 0xFF3A3A3A);

		context.item(resultStack, resultX + 1, resultY + 1);
		if (resultStack.getCount() > 1) {
			context.itemDecorations(this.font, resultStack, resultX + 1, resultY + 1);
		}

		int statusY = baseY + ingredientGridHeight + (canScrollDown ? 14 : 2);
		String countText = "(" + allIngredients.size() + " sources)";
		context.centeredText(this.font, Component.literal(countText),
			panelX + PANEL_WIDTH / 2, statusY, 0xFF888888);

		String statusText;
		int statusColor;
		if (hasAnyIngredient) {
			statusText = "✓ Have ingredient";
			statusColor = 0xFF44FF44;
		} else {
			statusText = "✗ Missing ingredients";
			statusColor = 0xFFFF4444;
		}
		context.centeredText(this.font, Component.literal(statusText),
			panelX + PANEL_WIDTH / 2, statusY + 12, statusColor);
	}

	/**
	 * Find a furnace recipe that produces the given item
	 */
	private RecipeDisplayEntry findFurnaceRecipeForItem(Item item, ContextMap contextParams) {
		if (minecraft == null || minecraft.level == null) return null;

		return RecipeCache.findFurnaceRecipeForItem(item, minecraft.level);
	}

	/**
	 * Find a crafting recipe that produces the given item
	 */
	private RecipeDisplayEntry findRecipeForItem(Item item, ContextMap contextParams) {
		if (minecraft == null || minecraft.level == null) return null;

		return RecipeCache.findCraftingRecipeForItem(item, minecraft.level);
	}

	/**
	 * Check if a recipe fits in the current crafting grid
	 */
	private boolean fitsInGrid(RecipeDisplayEntry entry) {
		RecipeDisplay display = entry.display();

		if (display instanceof ShapedCraftingRecipeDisplay shaped) {
			return shaped.width() <= craftingGridSize && shaped.height() <= craftingGridSize;
		} else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
			return shapeless.ingredients().size() <= craftingGridSize * craftingGridSize;
		}
		return false;
	}

	private void drawConfirmation(GuiGraphicsExtractor context) {
		float alpha = Math.min(1.0f, confirmationTicks / 10.0f);
		if (confirmationTicks > CONFIRMATION_DURATION - 10) {
			alpha = (CONFIRMATION_DURATION - confirmationTicks) / 10.0f;
		}

		int alphaInt = (int)(alpha * 200);

		int color = (alphaInt << 24) | 0x44AA44;
		context.fill(0, 0, this.width, this.height, color);

		if (alpha > 0.3f) {
			int textAlpha = (int)(alpha * 255);
			context.centeredText(
				this.font,
				Component.literal("✓ Crafted " + craftQuantity + "x " + resultStack.getHoverName().getString()),
				this.width / 2,
				this.height / 2,
				(textAlpha << 24) | 0xFFFFFF
			);
		}
	}

	@Override
	public void tick() {
		super.tick();

		if (showingConfirmation) {
			confirmationTicks++;
			if (confirmationTicks >= CONFIRMATION_DURATION) {
				showingConfirmation = false;
				onClose();
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (isFurnaceRecipe && totalIngredientRows > maxVisibleIngredientRows) {
			// Scroll ingredients list
			int maxScrollOffset = totalIngredientRows - maxVisibleIngredientRows;
			if (verticalAmount > 0) {
				// Scroll up
				ingredientScrollOffset = Math.max(0, ingredientScrollOffset - 1);
			} else if (verticalAmount < 0) {
				// Scroll down
				ingredientScrollOffset = Math.min(maxScrollOffset, ingredientScrollOffset + 1);
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private void craftRecipe() {
		if (!canCraft || craftingPlan == null || minecraft == null) return;

		SmartRecipeBookMod.LOGGER.debug("Crafting {}x {} from preview", craftQuantity, resultStack.getHoverName().getString());

		AutoCraftExecutor.execute(minecraft, craftingPlan, craftQuantity);

		showingConfirmation = true;
		confirmationTicks = 0;

		craftButton.active = false;
		cancelButton.active = false;
		if (plusButton != null) plusButton.active = false;
		if (minusButton != null) minusButton.active = false;
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean consumed) {
		if (showingConfirmation) return true; // Block input during confirmation

		if (super.mouseClicked(click, consumed)) {
			return true;
		}

		// MouseButtonEvent carries GUI-space coordinates already
		double mouseX = click.x();
		double mouseY = click.y();

		if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			if (minusButton != null && isMouseOverButton(minusButton, mouseX, mouseY) && minusButton.active) {
				adjustQuantity(-1);
				return true;
			}
			if (plusButton != null && isMouseOverButton(plusButton, mouseX, mouseY) && plusButton.active) {
				adjustQuantity(1);
				return true;
			}

			// Check if clicking on an ingredient with a recipe
			if (hoveredSlot != null && hoveredSlot.recipe != null) {
				// Navigate to the recipe for this ingredient
				SmartRecipeBookMod.LOGGER.debug("Navigating to recipe for: {}",
					hoveredSlot.stack.getHoverName().getString());
				minecraft.gui.setScreen(new RecipePreviewScreen(this, hoveredSlot.recipe, craftingGridSize));
				return true;
			}
		}

		return false;
	}

	private boolean isMouseOverButton(Button button, double mouseX, double mouseY) {
		return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth() &&
			   mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent keyInput) {
		if (showingConfirmation) return true; // Block input during confirmation

		if (keyInput.isEscape()) {
			onClose();
			return true;
		}
		return super.keyPressed(keyInput);
	}

	@Override
	public void onClose() {
		// Refresh parent recipe book to re-sort by craft count
		if (parent instanceof SmartRecipeBookScreen recipeBook) {
			recipeBook.refresh();
		}
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
