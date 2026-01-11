package com.smartrecipe.mixin;

import net.minecraft.client.toast.RecipeToast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.recipe.display.RecipeDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to disable vanilla recipe unlock toast notifications.
 * Since we show all recipes regardless of unlock status, these toasts are unnecessary.
 */
@Mixin(RecipeToast.class)
public class RecipeToastMixin {

	@Inject(method = "show", at = @At("HEAD"), cancellable = true)
	private static void cancelRecipeToast(ToastManager toastManager, RecipeDisplay display, CallbackInfo ci) {
		// Cancel the toast notification entirely
		ci.cancel();
	}
}
