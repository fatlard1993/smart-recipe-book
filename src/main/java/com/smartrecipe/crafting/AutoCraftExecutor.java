package com.smartrecipe.crafting;

import com.smartrecipe.SmartRecipeBookMod;
import com.smartrecipe.recipe.CraftCountTracker;
import com.smartrecipe.recipe.CraftingPlan;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;

/**
 * Executes multi-step crafting plans by sending CraftRequestC2SPacket directly.
 * Uses tick-based delays to ensure server has time to process each step.
 */
public class AutoCraftExecutor {

	private static List<CraftingPlan.CraftingStep> steps = new ArrayList<>();
	private static int currentStepIndex = 0;
	private static boolean isExecuting = false;
	private static boolean userCraftAll = false;
	private static MinecraftClient currentClient = null;
	private static int ticksUntilNextStep = 0;
	private static final int TICKS_BETWEEN_STEPS = 3; // 3 ticks between crafts

	// For multi-craft support
	private static int totalQuantity = 1;
	private static int currentQuantityIndex = 0;
	private static List<CraftingPlan.CraftingStep> originalSteps = new ArrayList<>();

	/**
	 * Execute a crafting plan (single item)
	 */
	public static void execute(MinecraftClient client, CraftingPlan plan, boolean all) {
		execute(client, plan, all, 1);
	}

	/**
	 * Execute a crafting plan multiple times
	 */
	public static void execute(MinecraftClient client, CraftingPlan plan, boolean all, int quantity) {
		if (isExecuting) {
			SmartRecipeBookMod.LOGGER.warn("Already executing a crafting plan");
			return;
		}

		currentClient = client;
		userCraftAll = all;
		originalSteps = new ArrayList<>(plan.getSteps());
		totalQuantity = quantity;
		currentQuantityIndex = 0;

		// Build the full step list: repeat plan for each quantity
		steps = new ArrayList<>();
		for (int i = 0; i < quantity; i++) {
			steps.addAll(originalSteps);
		}
		currentStepIndex = 0;

		SmartRecipeBookMod.LOGGER.info("Starting crafting plan with {} steps x {} quantity = {} total steps",
			originalSteps.size(), quantity, steps.size());

		isExecuting = true;
		// Schedule first step for next tick
		ticksUntilNextStep = 1;
		SmartRecipeBookMod.LOGGER.info("Scheduled first step for next tick");
	}

	/**
	 * Execute the current step by sending CraftRequestC2SPacket directly
	 */
	private static void executeCurrentStep() {
		if (currentStepIndex >= steps.size()) {
			SmartRecipeBookMod.LOGGER.info("Crafting plan complete!");
			isExecuting = false;
			currentClient = null;
			return;
		}

		if (currentClient == null || currentClient.player == null) {
			SmartRecipeBookMod.LOGGER.error("Client or player is null, aborting");
			cancel();
			return;
		}

		CraftingPlan.CraftingStep step = steps.get(currentStepIndex);

		// Use craftAll=false to craft just one batch (not all available materials)
		// This prevents using more ingredients than needed
		boolean useCraftAll = false;

		SmartRecipeBookMod.LOGGER.info("Executing step {}/{}: {} (craftAll: {})",
			currentStepIndex + 1, steps.size(), step.getRecipeId(), useCraftAll);

		// Send CraftRequestC2SPacket directly - bypasses vanilla recipe book
		VanillaCraftingHelper.sendCraftRequest(step.getRecipeId(), useCraftAll);

		// Click the result slot to complete the craft
		// This moves the crafted item to inventory
		clickCraftingResult();

		// Track the crafted item for sorting purposes
		ItemStack result = step.getResult();
		if (!result.isEmpty()) {
			CraftCountTracker.increment(result.getItem(), result.getCount());
			SmartRecipeBookMod.LOGGER.info("Tracked craft: {} x{}", result.getItem().getName().getString(), result.getCount());
		}

		// Move to next step
		currentStepIndex++;

		if (currentStepIndex < steps.size()) {
			// Schedule next step after delay
			ticksUntilNextStep = TICKS_BETWEEN_STEPS;
			SmartRecipeBookMod.LOGGER.info("Waiting {} ticks before next step", ticksUntilNextStep);
		} else {
			SmartRecipeBookMod.LOGGER.info("All steps sent, crafting plan complete!");
			isExecuting = false;
			currentClient = null;
		}
	}

	/**
	 * Click the crafting result slot to complete the craft and move items to inventory
	 * Note: We send this immediately after select() - the server will queue it and
	 * process it after filling the grid. Shift-click crafts all available.
	 */
	private static void clickCraftingResult() {
		if (currentClient == null || currentClient.player == null) return;

		ScreenHandler handler = currentClient.player.currentScreenHandler;
		if (handler == null) return;

		int syncId = handler.syncId;
		short resultSlotId = 0; // Result slot is always slot 0 in crafting screens
		int stateId = handler.getRevision();

		SmartRecipeBookMod.LOGGER.info("Sending shift-click on result slot (syncId: {}, stateId: {})", syncId, stateId);

		// Send a single shift-click packet - server will craft all available items
		// We don't check client-side slot because server hasn't responded yet
		ClickSlotC2SPacket packet = new ClickSlotC2SPacket(
			syncId,
			stateId,
			resultSlotId,
			(byte) 0, // button 0 = left click
			SlotActionType.QUICK_MOVE, // shift-click to move to inventory
			new Int2ObjectArrayMap<>(), // modified stacks
			ItemStackHash.EMPTY // cursor (empty when shift-clicking)
		);
		currentClient.getNetworkHandler().sendPacket(packet);
		SmartRecipeBookMod.LOGGER.info("Click packet sent");
	}

	/**
	 * Called every client tick - handles delayed execution
	 */
	public static void onClientTick(MinecraftClient client) {
		if (!isExecuting) return;

		if (ticksUntilNextStep > 0) {
			ticksUntilNextStep--;
			if (ticksUntilNextStep == 0) {
				SmartRecipeBookMod.LOGGER.info("Delay complete, executing next step");
				executeCurrentStep();
			}
		}
	}

	/**
	 * Cancel the current crafting plan
	 */
	public static void cancel() {
		SmartRecipeBookMod.LOGGER.info("Crafting plan cancelled");
		steps.clear();
		currentStepIndex = 0;
		isExecuting = false;
		ticksUntilNextStep = 0;
		currentClient = null;
	}

	/**
	 * Check if currently executing a plan
	 */
	public static boolean isExecuting() {
		return isExecuting;
	}

	/**
	 * Get remaining steps count
	 */
	public static int getRemainingSteps() {
		return steps.size() - currentStepIndex;
	}

	/**
	 * Called when inventory is updated - can be used to speed up execution
	 * Currently we use tick-based delays, but this hook remains for future use
	 */
	public static void onInventoryUpdate() {
		// Currently using tick-based delays instead of inventory-based triggering
		// This method is kept for potential future optimization
	}
}
