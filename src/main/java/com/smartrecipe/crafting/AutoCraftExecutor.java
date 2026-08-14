package com.smartrecipe.crafting;

import com.smartrecipe.SmartRecipeBookMod;
import com.smartrecipe.recipe.CraftCountTracker;
import com.smartrecipe.recipe.CraftingPlan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
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

	// Fallback timeout if inventory update never arrives.
	// 3 ticks (~150ms) is enough for LAN/singleplayer; on laggy servers
	// the inventory update hook will fire first and skip the wait.
	private static final int TICK_TIMEOUT = 3;

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

		currentClient = client;
		initialSyncId = client.player.containerMenu.containerId;

		List<CraftingPlan.CraftingStep> originalSteps = plan.getSteps();
		steps = new ArrayList<>();
		for (int i = 0; i < quantity; i++) {
			steps.addAll(originalSteps);
		}
		currentStepIndex = 0;

		SmartRecipeBookMod.LOGGER.debug("Starting crafting plan: {} steps x {} = {} total",
			originalSteps.size(), quantity, steps.size());

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

		if (inventoryUpdated) {
			inventoryUpdated = false;
			ticksUntilNextStep = 0;
			executeCurrentStep();
			return;
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
