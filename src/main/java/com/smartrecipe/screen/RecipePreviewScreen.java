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
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapedCraftingRecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.text.Text;
import net.minecraft.util.context.ContextParameterMap;

import java.util.*;

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

	private CraftingPlan craftingPlan;
	private boolean canCraft = false;
	private boolean canCraftDirect = false;
	private int craftQuantity = 1;
	private int maxCraftable = 1;

	// Crafting confirmation state
	private boolean showingConfirmation = false;
	private int confirmationTicks = 0;
	private static final int CONFIRMATION_DURATION = 15; // ~0.75 seconds

	private ButtonWidget craftButton;
	private ButtonWidget cancelButton;
	private ButtonWidget plusButton;
	private ButtonWidget minusButton;

	// Layout constants
	private static final int SLOT_SIZE = 18;
	private static final int GRID_SLOT_SIZE = 20;
	private static final int PANEL_WIDTH = 220;
	private static final int PANEL_HEIGHT = 200;

	// Slot tracking for tooltips and clicks
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

	public RecipePreviewScreen(Screen parent, RecipeDisplayEntry recipe) {
		super(Text.literal("Recipe Preview"));
		this.parent = parent;
		this.recipe = recipe;

		// Determine crafting grid size from parent chain
		this.craftingGridSize = determineCraftingGridSize(parent);

		// Get result stack
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != null) {
			ContextParameterMap contextParams = SlotDisplayContexts.createParameters(client.world);
			List<ItemStack> results = recipe.getStacks(contextParams);
			this.resultStack = results.isEmpty() ? ItemStack.EMPTY : results.get(0);
		} else {
			this.resultStack = ItemStack.EMPTY;
		}

		// Get player inventory
		if (client.player != null) {
			this.playerInventory = RecipeTreeCalculator.getInventoryContents(client.player);
		} else {
			this.playerInventory = new HashMap<>();
		}
	}

	private int determineCraftingGridSize(Screen screen) {
		// Walk up the parent chain to find the original screen type
		Screen current = screen;
		while (current != null) {
			if (current instanceof SmartRecipeBookScreen recipeBook) {
				// SmartRecipeBookScreen knows its grid size
				return recipeBook.getCraftingGridSize();
			}
			if (current instanceof CraftingScreen) {
				return 3;
			}
			// Try to get parent if it's one of our screens
			if (current instanceof RecipePreviewScreen preview) {
				current = preview.parent;
			} else {
				break;
			}
		}
		return 2; // Default to inventory grid
	}

	@Override
	protected void init() {
		super.init();

		// Calculate craftability
		calculateCraftability();

		// Calculate max craftable
		calculateMaxCraftable();

		// Calculate panel position (centered)
		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;

		// Quantity controls
		int quantityY = panelY + PANEL_HEIGHT - 60;

		minusButton = ButtonWidget.builder(
			Text.literal("-"),
			button -> adjustQuantity(-1)
		).dimensions(panelX + 50, quantityY, 20, 20).build();
		this.addDrawableChild(minusButton);

		plusButton = ButtonWidget.builder(
			Text.literal("+"),
			button -> adjustQuantity(1)
		).dimensions(panelX + 130, quantityY, 20, 20).build();
		this.addDrawableChild(plusButton);

		// Craft button
		craftButton = ButtonWidget.builder(
			Text.literal("Craft"),
			button -> craftRecipe()
		).dimensions(panelX + 10, panelY + PANEL_HEIGHT - 30, 95, 20).build();
		craftButton.active = canCraft;
		this.addDrawableChild(craftButton);

		// Cancel button
		cancelButton = ButtonWidget.builder(
			Text.literal("Cancel"),
			button -> close()
		).dimensions(panelX + PANEL_WIDTH - 105, panelY + PANEL_HEIGHT - 30, 95, 20).build();
		this.addDrawableChild(cancelButton);

		updateQuantityButtons();
	}

	private void adjustQuantity(int delta) {
		craftQuantity = Math.max(1, Math.min(maxCraftable, craftQuantity + delta));
		updateQuantityButtons();
	}

	private void updateQuantityButtons() {
		minusButton.active = craftQuantity > 1;
		plusButton.active = craftQuantity < maxCraftable && canCraft;
	}

	private void calculateCraftability() {
		if (client == null) return;

		ContextParameterMap contextParams = SlotDisplayContexts.createParameters(client.world);

		// Check direct craftability
		canCraftDirect = canCraftRecipeDirect(contextParams);

		// Calculate full plan for recursive craftability
		craftingPlan = RecipeTreeCalculator.calculatePlan(client, recipe.id());
		canCraft = craftingPlan != null && craftingPlan.canCraft();
	}

	private void calculateMaxCraftable() {
		if (!canCraft || client == null) {
			maxCraftable = 1;
			return;
		}

		// Use RecipeTreeCalculator to calculate max craftable with sub-crafting support
		maxCraftable = RecipeTreeCalculator.calculateMaxCraftable(client, recipe.id());
		SmartRecipeBookMod.LOGGER.info("Max craftable for {}: {}", resultStack.getName().getString(), maxCraftable);
	}

	private boolean canCraftRecipeDirect(ContextParameterMap contextParams) {
		RecipeDisplay display = recipe.display();

		List<SlotDisplay> ingredientSlots;
		if (display instanceof ShapedCraftingRecipeDisplay shaped) {
			ingredientSlots = shaped.ingredients();
		} else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
			ingredientSlots = shapeless.ingredients();
		} else {
			return false;
		}

		Map<Item, Integer> tempInventory = new HashMap<>(playerInventory);

		for (SlotDisplay slot : ingredientSlots) {
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

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Draw dark overlay
		context.fill(0, 0, this.width, this.height, 0xC0101010);

		// Calculate panel position
		int panelX = (this.width - PANEL_WIDTH) / 2;
		int panelY = (this.height - PANEL_HEIGHT) / 2;

		// Draw panel background
		context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF2A2A2A);

		// Draw border
		int borderColor = canCraft ? 0xFF44AA44 : 0xFF666666;
		context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 2, borderColor);
		context.fill(panelX, panelY + PANEL_HEIGHT - 2, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, borderColor);
		context.fill(panelX, panelY, panelX + 2, panelY + PANEL_HEIGHT, borderColor);
		context.fill(panelX + PANEL_WIDTH - 2, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, borderColor);

		// Draw result name at top
		context.drawCenteredTextWithShadow(
			this.textRenderer,
			resultStack.getName(),
			panelX + PANEL_WIDTH / 2,
			panelY + 8,
			0xFFFFFF
		);

		// Draw crafting grid preview (pass mouse coords for hover detection)
		drawCraftingGrid(context, panelX, panelY + 25, mouseX, mouseY);

		// Draw quantity display (replaces craftability status)
		String quantityText = "Quantity: " + craftQuantity;
		if (maxCraftable > 1) {
			quantityText += " / " + maxCraftable;
		}
		int quantityColor = canCraft ? 0xFF44FF44 : 0xFFFF4444;

		context.drawCenteredTextWithShadow(
			this.textRenderer,
			Text.literal(quantityText),
			panelX + PANEL_WIDTH / 2,
			panelY + 105,
			quantityColor
		);

		// Draw buttons
		super.render(context, mouseX, mouseY, delta);

		// Draw crafting confirmation overlay
		if (showingConfirmation) {
			drawConfirmation(context);
		}

		// Draw tooltip for hovered item (must be last to render on top)
		if (hoveredSlot != null && !hoveredSlot.stack.isEmpty()) {
			List<Text> tooltip = new ArrayList<>();
			tooltip.add(hoveredSlot.stack.getName());

			// Show count in inventory
			int have = playerInventory.getOrDefault(hoveredSlot.stack.getItem(), 0);
			if (have > 0) {
				tooltip.add(Text.literal("§7In inventory: " + have));
			} else {
				tooltip.add(Text.literal("§cNot in inventory"));
			}

			// Show if clickable (has recipe)
			if (hoveredSlot.recipe != null) {
				tooltip.add(Text.literal("§b[Click to view recipe]"));
			}

			context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
		}
	}

	private void drawCraftingGrid(DrawContext context, int panelX, int startY, int mouseX, int mouseY) {
		if (client == null || client.world == null) return;

		// Clear slot tracking
		ingredientSlots.clear();
		resultSlot = null;
		hoveredSlot = null;

		ContextParameterMap contextParams = SlotDisplayContexts.createParameters(client.world);
		RecipeDisplay display = recipe.display();

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

		// Calculate grid position (centered in panel)
		int gridPixelWidth = gridWidth * GRID_SLOT_SIZE;
		int totalWidth = gridPixelWidth + 30 + GRID_SLOT_SIZE; // grid + arrow + result
		int gridX = panelX + (PANEL_WIDTH - totalWidth) / 2;
		int gridY = startY;

		// Draw ingredient grid
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

			// Draw slot background
			context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0xFF3A3A3A);

			// Draw ingredient
			SlotDisplay slot = displaySlots.get(i);
			List<ItemStack> possible = slot.getStacks(contextParams);
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

					// Check if mouse is hovering
					boolean hovered = mouseX >= slotX && mouseX < slotX + SLOT_SIZE &&
									  mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
					if (hovered) {
						hoveredSlot = slotInfo;
					}

					// Check if player has this ingredient
					int have = playerInventory.getOrDefault(ingredientStack.getItem(), 0);
					boolean hasIngredient = have > 0;

					// Draw item
					context.drawItem(ingredientStack, slotX + 1, slotY + 1);

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

		// Draw arrow
		int arrowX = gridX + gridPixelWidth + 5;
		int arrowY = gridY + (gridHeight * GRID_SLOT_SIZE) / 2 - 4;
		context.drawTextWithShadow(this.textRenderer, Text.literal("→"), arrowX, arrowY, 0xFFFFFF);

		// Draw result slot
		int resultX = arrowX + 20;
		int resultY = gridY + (gridHeight * GRID_SLOT_SIZE) / 2 - SLOT_SIZE / 2;

		// Track result slot
		resultSlot = new SlotInfo(resultX, resultY, resultStack);

		// Check if hovering result
		boolean resultHovered = mouseX >= resultX && mouseX < resultX + SLOT_SIZE &&
								mouseY >= resultY && mouseY < resultY + SLOT_SIZE;
		if (resultHovered) {
			hoveredSlot = resultSlot;
		}

		// Result background (highlighted)
		context.fill(resultX - 2, resultY - 2, resultX + SLOT_SIZE + 2, resultY + SLOT_SIZE + 2, 0xFF4A4A4A);
		context.fill(resultX, resultY, resultX + SLOT_SIZE, resultY + SLOT_SIZE, 0xFF3A3A3A);

		// Draw result item
		context.drawItem(resultStack, resultX + 1, resultY + 1);
		if (resultStack.getCount() > 1) {
			context.drawStackOverlay(this.textRenderer, resultStack, resultX + 1, resultY + 1);
		}
	}

	/**
	 * Find a crafting recipe that produces the given item
	 */
	private RecipeDisplayEntry findRecipeForItem(Item item, ContextParameterMap contextParams) {
		if (client == null || client.world == null) return null;

		return RecipeCache.findCraftingRecipeForItem(item, client.world);
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

	private void drawConfirmation(DrawContext context) {
		// Calculate fade based on ticks
		float alpha = Math.min(1.0f, confirmationTicks / 10.0f);
		if (confirmationTicks > CONFIRMATION_DURATION - 10) {
			alpha = (CONFIRMATION_DURATION - confirmationTicks) / 10.0f;
		}

		int alphaInt = (int)(alpha * 200);

		// Draw green overlay
		int color = (alphaInt << 24) | 0x44AA44;
		context.fill(0, 0, this.width, this.height, color);

		// Draw confirmation text
		if (alpha > 0.3f) {
			int textAlpha = (int)(alpha * 255);
			context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("✓ Crafted " + craftQuantity + "x " + resultStack.getName().getString()),
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
				close();
			}
		}
	}

	private void craftRecipe() {
		if (!canCraft || craftingPlan == null || client == null) return;

		SmartRecipeBookMod.LOGGER.info("Crafting {}x {} from preview", craftQuantity, resultStack.getName().getString());

		// If plan requires choices, show choice screen
		if (craftingPlan.hasRecipeChoices()) {
			final int quantity = craftQuantity;
			client.setScreen(new RecipeChoiceScreen(
				null,
				craftingPlan,
				(finalPlan) -> AutoCraftExecutor.execute(client, finalPlan, false, quantity)
			));
			return;
		}

		// Execute the plan with quantity
		AutoCraftExecutor.execute(client, craftingPlan, false, craftQuantity);

		// Show confirmation
		showingConfirmation = true;
		confirmationTicks = 0;

		// Disable buttons during confirmation
		craftButton.active = false;
		cancelButton.active = false;
		plusButton.active = false;
		minusButton.active = false;
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean consumed) {
		if (showingConfirmation) return true; // Block input during confirmation

		// Let parent handle widget clicks first
		if (super.mouseClicked(click, consumed)) {
			return true;
		}

		// Get mouse position from client (fallback if super didn't handle)
		double mouseX = client.mouse.getX() * width / client.getWindow().getWidth();
		double mouseY = client.mouse.getY() * height / client.getWindow().getHeight();

		if (click.button() == 0) {
			if (isMouseOverButton(minusButton, mouseX, mouseY) && minusButton.active) {
				adjustQuantity(-1);
				return true;
			}
			if (isMouseOverButton(plusButton, mouseX, mouseY) && plusButton.active) {
				adjustQuantity(1);
				return true;
			}

			// Check if clicking on an ingredient with a recipe
			if (hoveredSlot != null && hoveredSlot.recipe != null) {
				// Navigate to the recipe for this ingredient
				SmartRecipeBookMod.LOGGER.info("Navigating to recipe for: {}",
					hoveredSlot.stack.getName().getString());
				client.setScreen(new RecipePreviewScreen(this, hoveredSlot.recipe));
				return true;
			}
		}

		return false;
	}

	private boolean isMouseOverButton(ButtonWidget button, double mouseX, double mouseY) {
		return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth() &&
			   mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyInput keyInput) {
		if (showingConfirmation) return true; // Block input during confirmation

		if (keyInput.key() == 256) { // ESC
			close();
			return true;
		}
		return super.keyPressed(keyInput);
	}

	@Override
	public void close() {
		// Refresh parent recipe book to re-sort by craft count
		if (parent instanceof SmartRecipeBookScreen recipeBook) {
			recipeBook.refresh();
		}
		this.client.setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
