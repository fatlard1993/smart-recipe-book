package com.smartrecipe.mixin;

import com.smartrecipe.SmartRecipeBookMod;
import com.smartrecipe.crafting.AutoCraftExecutor;
import com.smartrecipe.recipe.RecipeCache;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.SynchronizeRecipesS2CPacket;
import net.minecraft.network.packet.s2c.play.RecipeBookAddS2CPacket;
import net.minecraft.network.packet.s2c.play.RecipeBookRemoveS2CPacket;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.NetworkRecipeId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.ArrayList;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

	private static int lastWorldHash = 0;

	/**
	 * Track slot updates to know when crafting completes
	 */
	@Inject(
		method = "onScreenHandlerSlotUpdate",
		at = @At("TAIL")
	)
	private void onSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
		AutoCraftExecutor.onInventoryUpdate();
	}

	/**
	 * Track full inventory syncs
	 */
	@Inject(
		method = "onInventory",
		at = @At("TAIL")
	)
	private void onInventorySync(InventoryS2CPacket packet, CallbackInfo ci) {
		AutoCraftExecutor.onInventoryUpdate();
	}

	/**
	 * Capture recipes when they are added to the recipe book.
	 * This is the key hook for our custom recipe cache.
	 */
	@Inject(
		method = "onRecipeBookAdd",
		at = @At("TAIL")
	)
	private void onRecipeBookAdd(RecipeBookAddS2CPacket packet, CallbackInfo ci) {
		// If replace is true, clear our cache first
		if (packet.replace()) {
			SmartRecipeBookMod.LOGGER.info("RecipeBookAddS2CPacket with replace=true, clearing cache");
			RecipeCache.clear();
		}

		// Extract all recipe display entries from the packet
		List<RecipeDisplayEntry> entries = new ArrayList<>();
		for (RecipeBookAddS2CPacket.Entry entry : packet.entries()) {
			entries.add(entry.contents());
		}

		// Add to our cache
		RecipeCache.addRecipes(entries);

		SmartRecipeBookMod.LOGGER.info("RecipeBookAddS2CPacket: captured {} recipes, cache now has {} total",
			entries.size(), RecipeCache.getRecipeCount());
	}

	/**
	 * Handle recipe removal
	 */
	@Inject(
		method = "onRecipeBookRemove",
		at = @At("TAIL")
	)
	private void onRecipeBookRemove(RecipeBookRemoveS2CPacket packet, CallbackInfo ci) {
		for (NetworkRecipeId id : packet.recipes()) {
			RecipeCache.removeRecipe(id);
		}
		SmartRecipeBookMod.LOGGER.debug("RecipeBookRemoveS2CPacket: removed {} recipes", packet.recipes().size());
	}

	/**
	 * When recipes are synced (property sets), log for debugging
	 */
	@Inject(
		method = "onSynchronizeRecipes",
		at = @At("TAIL")
	)
	private void onRecipesSync(SynchronizeRecipesS2CPacket packet, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();

		// Check if this is a new world
		int worldHash = client.world != null ? client.world.hashCode() : 0;
		if (worldHash == lastWorldHash && lastWorldHash != 0) {
			return; // Already processed for this world
		}
		lastWorldHash = worldHash;

		SmartRecipeBookMod.LOGGER.info("SynchronizeRecipesS2CPacket received (property sets sync)");
	}

}
