package com.smartrecipe.mixin;

import com.smartrecipe.recipe.RecipeCache;
import com.smartrecipe.screen.RecipeBookScreens;
import com.smartrecipe.screen.RecipeMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.BrewingStandMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the book on the brewing stand.
 *
 * <p>The stand is the one vanilla station with no recipe book of its own: its screen is a plain
 * container screen, not a recipe book one, so the toggle this mod hooks everywhere else does not
 * exist here to hook. The button is added outright instead.
 *
 * <p>It appears only once brewing recipes have actually arrived. They come from this mod's server
 * half and from nowhere else (see {@code BrewingRecipesPayload}), so on a server that is not
 * running it there is no button rather than a button onto an empty book.
 *
 * <p><b>Browsing, not filling.</b> Nothing here loads the stand for you. A brew is three bottles,
 * a reagent, blaze powder and twenty seconds - there is no packet that means "make this", the way
 * there is for a crafting grid - so the book answers what goes in and leaves the doing alone.
 */
@Mixin(BrewingStandScreen.class)
public abstract class BrewingStandBookMixin extends AbstractContainerScreen<BrewingStandMenu> {

	/** Vanilla's own knowledge-book button. */
	private static final WidgetSprites BOOK_BUTTON_SPRITES = new WidgetSprites(
		Identifier.withDefaultNamespace("recipe_book/button"),
		Identifier.withDefaultNamespace("recipe_book/button_highlighted"));

	/**
	 * Top right of the stand's panel, which is the only part of it that is empty. The left holds
	 * the fuel slot, the middle the reagent and the bubble column down to the three bottles.
	 */
	private static final int BOOK_BUTTON_X = 148;
	private static final int BOOK_BUTTON_Y = 15;
	private static final int BOOK_BUTTON_W = 20;
	private static final int BOOK_BUTTON_H = 18;

	private BrewingStandBookMixin() {
		super(null, null, null);
	}

	@Inject(method = "init", at = @At("TAIL"), require = 1)
	private void smartrecipe$addBrewingBookButton(CallbackInfo ci) {
		if (!RecipeCache.hasBrewingRecipes()) return;

		BrewingStandScreen self = (BrewingStandScreen) (Object) this;
		this.addRenderableWidget(new ImageButton(
			this.leftPos + BOOK_BUTTON_X, this.topPos + BOOK_BUTTON_Y,
			BOOK_BUTTON_W, BOOK_BUTTON_H, BOOK_BUTTON_SPRITES,
			button -> Minecraft.getInstance().gui.setScreen(
				RecipeBookScreens.open(self, RecipeMode.BREWING)),
			Component.translatable("gui.recipebook.toggleRecipes.craftable")));
	}
}
