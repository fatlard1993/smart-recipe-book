package com.smartrecipe.mixin;

import com.smartrecipe.crafting.AutoCraftExecutor;
import com.smartrecipe.recipe.RecipeCache;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
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
			RecipeCache.clear();
		}

		// Extract all recipe display entries from the packet
		List<RecipeDisplayEntry> entries = new ArrayList<>();
		for (RecipeBookAddS2CPacket.Entry entry : packet.entries()) {
			entries.add(entry.contents());
		}

		// Add to our cache
		RecipeCache.addRecipes(entries);
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
	}

}
