package com.smartrecipe.mixin;

import com.smartrecipe.SmartRecipeBookMod;
import com.smartrecipe.recipe.RecipeTreeCalculator;
import com.smartrecipe.recipe.CraftingPlan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

/**
 * Extends vanilla craftability checks to account for sub-crafting.
 * If vanilla says a recipe isn't craftable, we check whether the missing
 * ingredients can themselves be crafted from available materials.
 *
 * Results are cached with a short TTL to avoid running calculatePlan
 * on every recipe every frame.
 */
@Mixin(RecipeCollection.class)
public class RecipeResultCollectionMixin {

	@Unique
	private static final Map<RecipeDisplayId, Boolean> craftabilityCache = new HashMap<>();

	@Unique
	private static long lastCacheClear = 0;

	// 2-second TTL: responsive to inventory changes without per-frame recalc
	@Unique
	private static final long CACHE_TTL_MS = 2000;

	@Inject(
		method = "isCraftable",
		at = @At("RETURN"),
		cancellable = true
	)
	private void onIsCraftable(RecipeDisplayId recipeId, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue()) {
			return;
		}

		long now = System.currentTimeMillis();
		if (now - lastCacheClear > CACHE_TTL_MS) {
			craftabilityCache.clear();
			lastCacheClear = now;
		}

		Boolean cached = craftabilityCache.get(recipeId);
		if (cached != null) {
			if (cached) cir.setReturnValue(true);
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null) {
			return;
		}

		try {
			CraftingPlan plan = RecipeTreeCalculator.calculatePlan(client, recipeId);
			boolean canCraft = plan != null && plan.isValid();
			craftabilityCache.put(recipeId, canCraft);
			if (canCraft) {
				cir.setReturnValue(true);
			}
		} catch (Exception e) {
			SmartRecipeBookMod.LOGGER.debug("Craftability check failed for {}: {}", recipeId, e.getMessage());
			craftabilityCache.put(recipeId, false);
		}
	}

	/**
	 * Clear the craftability cache (e.g., when inventory changes significantly).
	 */
	@Unique
	private static void clearCache() {
		craftabilityCache.clear();
		lastCacheClear = 0;
	}
}
