package com.smartrecipe.mixin;

import com.smartrecipe.crafting.AutoCraftExecutor;
import com.smartrecipe.recipe.RecipeCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import java.util.ArrayList;

/**
 * Hooks into network packet handlers to:
 * 1. Capture recipe data from the server into our RecipeCache
 * 2. Forward inventory updates to AutoCraftExecutor for execution timing
 */
@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {

	/**
	 * Track slot updates to know when crafting completes
	 */
	@Inject(
		method = "handleContainerSetSlot",
		at = @At("TAIL")
	)
	private void onSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
		AutoCraftExecutor.onInventoryUpdate();
	}

	/**
	 * Track full inventory syncs
	 */
	@Inject(
		method = "handleContainerContent",
		at = @At("TAIL")
	)
	private void onInventorySync(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
		AutoCraftExecutor.onInventoryUpdate();
	}

	/**
	 * Capture recipes when they are added to the recipe book.
	 * This is the key hook for our custom recipe cache.
	 */
	@Inject(
		method = "handleRecipeBookAdd",
		at = @At("TAIL")
	)
	private void onRecipeBookAdd(ClientboundRecipeBookAddPacket packet, CallbackInfo ci) {
		if (packet.replace()) {
			RecipeCache.clear();
		}

		List<RecipeDisplayEntry> entries = new ArrayList<>();
		for (ClientboundRecipeBookAddPacket.Entry entry : packet.entries()) {
			entries.add(entry.contents());
		}

		RecipeCache.addRecipes(entries);
	}

	@Inject(
		method = "handleRecipeBookRemove",
		at = @At("TAIL")
	)
	private void onRecipeBookRemove(ClientboundRecipeBookRemovePacket packet, CallbackInfo ci) {
		for (RecipeDisplayId id : packet.recipes()) {
			RecipeCache.removeRecipe(id);
		}
	}

}
