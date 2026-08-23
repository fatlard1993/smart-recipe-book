package com.smartrecipe.mixin;

import com.smartrecipe.crafting.AutoCraftExecutor;
import com.smartrecipe.recipe.RecipeCache;
import com.smartrecipe.recipe.RecipeTreeCalculator;
import com.smartrecipe.screen.RecipeMode;
import com.smartrecipe.screen.SmartRecipeBookScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.BlastFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.SmokerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import com.smartrecipe.recipe.CraftingPlan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts the vanilla recipe book widget.
 *
 * toggleVisibility: the recipe book button's press lambda in
 * AbstractRecipeBookScreen.initButton() calls toggleVisibility() FIRST and the
 * onRecipeBookButtonClick() hook LAST; InventoryScreen also overrides that hook
 * without calling super, so injecting into the hook is both too late (the
 * vanilla panel has already toggled) and dead code for the survival inventory.
 * toggleVisibility() has exactly one caller (the button lambda), so cancelling
 * it here both suppresses the vanilla panel and opens our screen for every
 * recipe book screen type.
 *
 * tryPlaceRecipe: when a recipe requires sub-crafting (e.g., sticks for a
 * pickaxe), cancels vanilla handling and delegates to AutoCraftExecutor.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookWidgetMixin {

	@Shadow
	protected Minecraft minecraft;

	@Shadow
	public abstract boolean isVisible();

	@Shadow
	protected abstract void setVisible(boolean visible);

	@Inject(
		method = "toggleVisibility",
		at = @At("HEAD"),
		cancellable = true
	)
	private void onToggleVisibility(CallbackInfo ci) {
		if (minecraft == null || minecraft.player == null) return;

		Screen current = minecraft.gui.screen();

		RecipeMode mode;
		if (current instanceof InventoryScreen ||
			current instanceof CraftingScreen) {
			mode = RecipeMode.CRAFTING;
		} else if (current instanceof BlastFurnaceScreen) {
			mode = RecipeMode.BLAST_FURNACE;
		} else if (current instanceof SmokerScreen) {
			mode = RecipeMode.SMOKER;
		} else if (current instanceof FurnaceScreen) {
			mode = RecipeMode.FURNACE;
		} else if (current instanceof AbstractFurnaceScreen) {
			// Fallback for any other furnace-type screens (mod compatibility)
			mode = RecipeMode.FURNACE;
		} else {
			// Unknown screen type, let vanilla handle it
			return;
		}

		if (!RecipeCache.hasRecipes()) {
			return; // Let vanilla handle it
		}

		// Vanilla's close lives past this cancel, and the flag it would have cleared is
		// persisted and sent to the server - so a panel already open when this mod arrives
		// stays open through every relog, behind our screen, shoving the inventory aside.
		if (isVisible()) setVisible(false);

		ci.cancel();
		minecraft.gui.setScreen(new SmartRecipeBookScreen(current, mode));
	}

	/**
	 * Shut the vanilla panel on the way in.
	 *
	 * <p>Closing it at the toggle only helps somebody who presses the button. A profile that
	 * had the book open before this mod was installed opens every screen with the panel
	 * already up, and nothing this mod does would ever take it down.
	 */
	@Inject(method = "init", at = @At("TAIL"))
	private void onInit(int width, int height, Minecraft client, boolean narrow, CallbackInfo ci) {
		if (!isVisible() || !RecipeCache.hasRecipes()) return;
		setVisible(false);
	}

	@Inject(
		method = "tryPlaceRecipe",
		at = @At("HEAD"),
		cancellable = true
	)
	private void onRecipeSelect(RecipeCollection results, RecipeDisplayId recipeId, boolean craftAll, CallbackInfoReturnable<Boolean> cir) {
		if (minecraft == null || minecraft.player == null) return;

		CraftingPlan plan = RecipeTreeCalculator.calculatePlan(minecraft, recipeId);
		if (plan == null || !plan.requiresSubCrafting()) {
			return;
		}

		cir.setReturnValue(true);
		AutoCraftExecutor.execute(minecraft, plan, 1);
	}
}
