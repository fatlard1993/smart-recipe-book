package com.smartrecipe.crafting;

import com.smartrecipe.SmartRecipeBookMod;
import com.smartrecipe.recipe.CraftCountTracker;
import com.smartrecipe.recipe.CraftingPlan;
import com.smartrecipe.recipe.RecipeTreeCalculator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;

/**
 * Executes multi-step crafting plans by sending craft packets to the server.
 *
 * Uses a hybrid timing strategy: waits for inventory updates from the server
 * to confirm each step completed, with a tick-based timeout as a fallback
 * in case the update is missed or delayed.
 *
 * Aborts if the screen handler changes mid-execution (player closed the
 * crafting screen or opened a different one).
 */
public class AutoCraftExecutor {

	private static List<CraftingPlan.CraftingStep> steps = new ArrayList<>();
	private static int currentStepIndex = 0;
	private static boolean isExecuting = false;
	private static Minecraft currentClient = null;
	private static int ticksUntilNextStep = 0;

	// Fallback if the step's result never shows up in the inventory: a second, and then the
	// next step goes regardless. A step normally advances the moment its result has landed
	// and the grid is clear again, which is well inside this.
	private static final int TICK_TIMEOUT = 20;

	/** The step in flight: what it makes, and how many of that the inventory held before. */
	private static Item expectedItem = null;
	private static int expectedCount = 0;
	private static int countBefore = 0;

	// Set true when we receive an inventory update during execution,
	// allowing the next step to fire immediately on the next tick.
	private static boolean inventoryUpdated = false;

	// Track the syncId we started with; if it changes, the screen changed
	private static int initialSyncId = -1;

	// Track the pending step whose result has not yet been confirmed
	private static CraftingPlan.CraftingStep pendingStep = null;

	/**
	 * Execute a crafting plan, repeating it {@code quantity} times.
	 */
	public static void execute(Minecraft client, CraftingPlan plan, int quantity) {
		if (isExecuting) {
			SmartRecipeBookMod.LOGGER.warn("Already executing a crafting plan");
			return;
		}

		if (client.player == null) return;

		// Refused rather than warned, because the failure is destructive. A shift-click into a full
		// inventory moves nothing, so an intermediate stays in the grid until the next step lays
		// its own recipe over it and the crafted item is gone - materials spent for nothing, with
		// no message. Better to decline the whole plan and say why while everything is still safe
		// in the player's hands.
		int shortfall = RecipeTreeCalculator.slotsNeededFor(plan)
			- RecipeTreeCalculator.freeInventorySlots(client.player);
		if (shortfall > 0) {
			client.player.sendSystemMessage(Component.literal(shortfall == 1
					? "Not enough room to craft that: free up 1 inventory slot"
					: "Not enough room to craft that: free up " + shortfall + " inventory slots")
				.withStyle(ChatFormatting.RED));
			return;
		}

		currentClient = client;
		initialSyncId = client.player.containerMenu.containerId;

		// Planned for the whole quantity against one running inventory, so a sub-craft whose
		// spares cover the next craft is not repeated for it. Repeating the one-craft plan spent
		// materials the estimate never counted, and a run for "max" fell short of its own plan.
		CraftingPlan sized = quantity > 1
			? RecipeTreeCalculator.calculatePlan(client, plan.getTargetRecipe(), quantity) : plan;
		if (sized == null || !sized.canCraft()) sized = plan;
		steps = new ArrayList<>(sized.getSteps());
		currentStepIndex = 0;

		SmartRecipeBookMod.LOGGER.debug("Starting crafting plan: {} steps for {} craft(s)",
			steps.size(), quantity);

		isExecuting = true;
		inventoryUpdated = false;
		pendingStep = null;
		ticksUntilNextStep = 1;
	}

	private static void executeCurrentStep() {
		if (currentStepIndex >= steps.size()) {
			confirmPendingStep();
			SmartRecipeBookMod.LOGGER.debug("Crafting plan complete");
			reset();
			return;
		}

		if (currentClient == null || currentClient.player == null) {
			SmartRecipeBookMod.LOGGER.error("Client or player is null, aborting plan");
			cancel();
			return;
		}

		// Abort if the screen handler changed (player closed crafting screen)
		AbstractContainerMenu handler = currentClient.player.containerMenu;
		if (handler == null || handler.containerId != initialSyncId) {
			SmartRecipeBookMod.LOGGER.warn("Screen changed during crafting, aborting plan");
			cancel();
			return;
		}

		// Confirm the previous step now that we got an inventory update
		confirmPendingStep();

		CraftingPlan.CraftingStep step = steps.get(currentStepIndex);

		SmartRecipeBookMod.LOGGER.debug("Step {}/{}: {}",
			currentStepIndex + 1, steps.size(), step.getRecipeId());

		expectedItem = step.getResult().getItem();
		expectedCount = step.getResult().getCount();
		countBefore = countInInventory(expectedItem);

		CraftPacketSender.sendCraftRequest(step.getRecipeId(), false);
		clickCraftingResult();

		// Track this step as pending; the count is credited on confirmation
		pendingStep = step;

		currentStepIndex++;
		inventoryUpdated = false;

		if (currentStepIndex < steps.size()) {
			ticksUntilNextStep = TICK_TIMEOUT;
		} else {
			// Last step: wait for final confirmation then complete
			ticksUntilNextStep = TICK_TIMEOUT;
		}
	}

	/**
	 * Credit the craft count for the pending step once we have
	 * confirmation (inventory update or timeout).
	 */
	/** Whether the step in flight has finished: its result in the inventory, the grid empty. */
	private static boolean settled(Minecraft client) {
		if (client.player == null || expectedItem == null) return true;
		if (countInInventory(expectedItem) < countBefore + expectedCount) return false;
		AbstractContainerMenu handler = client.player.containerMenu;
		int grid = handler instanceof net.minecraft.world.inventory.AbstractCraftingMenu crafting
			? crafting.getGridWidth() * crafting.getGridHeight() : 4;
		for (int slot = 1; slot <= grid && slot < handler.slots.size(); slot++) {
			if (!handler.getSlot(slot).getItem().isEmpty()) return false;
		}
		return true;
	}

	private static int countInInventory(Item item) {
		if (currentClient == null || currentClient.player == null) return 0;
		int count = 0;
		var inventory = currentClient.player.getInventory();
		for (int slot = 0; slot < 36; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.getItem() == item) count += stack.getCount();
		}
		ItemStack offhand = inventory.getItem(40);
		if (offhand.getItem() == item) count += offhand.getCount();
		return count;
	}

	private static void confirmPendingStep() {
		if (pendingStep == null) return;

		ItemStack result = pendingStep.getResult();
		if (!result.isEmpty()) {
			CraftCountTracker.increment(result.getItem(), result.getCount());
		}
		pendingStep = null;
	}

	/**
	 * Shift-click the crafting result slot to move items to inventory.
	 * Sent immediately after the craft request; the server queues it
	 * and processes it after filling the grid.
	 */
	private static void clickCraftingResult() {
		if (currentClient == null || currentClient.player == null) return;

		AbstractContainerMenu handler = currentClient.player.containerMenu;
		if (handler == null) return;

		int syncId = handler.containerId;
		short resultSlotId = 0; // Result slot is always slot 0 in crafting screens
		int stateId = handler.getStateId();

		ServerboundContainerClickPacket packet = new ServerboundContainerClickPacket(
			syncId,
			stateId,
			resultSlotId,
			(byte) 0,
			ContainerInput.QUICK_MOVE,
			new Int2ObjectArrayMap<>(),
			HashedStack.EMPTY
		);
		currentClient.getConnection().send(packet);
	}

	/**
	 * Called every client tick. Advances execution when either:
	 * - An inventory update arrived (server confirmed the craft), or
	 * - The tick timeout elapsed (fallback for missed updates).
	 */
	public static void onClientTick(Minecraft client) {
		if (!isExecuting) return;

		// An update is a sign, not a settlement: the first one after a step is often the grid
		// being filled, before the result has been taken. Go on only once the result has
		// landed and the grid is clear, or when the wait runs out.
		if (inventoryUpdated) {
			inventoryUpdated = false;
			if (pendingStep == null || settled(client)) {
				ticksUntilNextStep = 0;
				executeCurrentStep();
				return;
			}
		}

		if (ticksUntilNextStep > 0) {
			ticksUntilNextStep--;
			if (ticksUntilNextStep == 0) {
				if (currentStepIndex >= steps.size()) {
					// Final step timed out: complete anyway
					confirmPendingStep();
					SmartRecipeBookMod.LOGGER.debug("Crafting plan complete");
					reset();
				} else {
					executeCurrentStep();
				}
			}
		}
	}

	/**
	 * Called when the server sends an inventory or slot update.
	 * Signals that the previous craft step likely completed.
	 */
	public static void onInventoryUpdate() {
		if (isExecuting) {
			inventoryUpdated = true;
		}
	}

	public static void cancel() {
		SmartRecipeBookMod.LOGGER.debug("Crafting plan cancelled");
		reset();
	}

	/**
	 * Reset all execution state. Called on completion, cancellation,
	 * and world disconnect to prevent stale state.
	 */
	public static void reset() {
		steps.clear();
		currentStepIndex = 0;
		isExecuting = false;
		ticksUntilNextStep = 0;
		inventoryUpdated = false;
		currentClient = null;
		initialSyncId = -1;
		pendingStep = null;
	}

	public static boolean isExecuting() {
		return isExecuting;
	}

	public static int getRemainingSteps() {
		return steps.size() - currentStepIndex;
	}
}
