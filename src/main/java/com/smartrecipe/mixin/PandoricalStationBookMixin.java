package com.smartrecipe.mixin;

import com.smartrecipe.recipe.RecipeCache;
import com.smartrecipe.screen.RecipeMode;
import com.smartrecipe.screen.RecipeBookScreens;
import justfatlard.pandorical.client.screen.PandoricalContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the book on somebody else's crafting bench.
 *
 * <p>A Pandorical screen has no vanilla recipe book widget - the book is bolted to
 * {@code RecipeBookMenu} and a Pandorical menu is not one - so the usual way in, a mixin on that
 * widget's toggle, can never fire there. A modded station was left to draw a recipe browser into
 * its own screen by hand, which is a thing every such mod then does slightly differently and
 * usually badly.
 *
 * <p>So the screen says what it is, through {@code recipeStation}, and this puts a button on it.
 * Nothing here knows about fletching tables: any Pandorical screen that declares a recipe book
 * category gets the same button and the same book, filtered to that category.
 *
 * <p><b>Browsing, not filling.</b> The grid is filled by {@code ServerboundPlaceRecipePacket},
 * which the server answers only for a menu that carries a recipe book. A station menu does not,
 * so the button opens the book to look things up and stops there - the honest half of the job
 * rather than a fill that silently does nothing.
 */
@Pseudo
@Mixin(PandoricalContainerScreen.class)
public abstract class PandoricalStationBookMixin extends Screen {

	/** Vanilla's own knowledge-book button, at vanilla's own offset inside the panel. */
	private static final WidgetSprites BOOK_BUTTON_SPRITES = new WidgetSprites(
		Identifier.withDefaultNamespace("recipe_book/button"),
		Identifier.withDefaultNamespace("recipe_book/button_highlighted"));
	private static final int BOOK_BUTTON_X = 5;
	private static final int BOOK_BUTTON_Y = 33;
	private static final int BOOK_BUTTON_W = 20;
	private static final int BOOK_BUTTON_H = 18;

	private PandoricalStationBookMixin() {
		super(null);
	}

	@Inject(method = "init", at = @At("TAIL"), require = 1)
	private void smartrecipe$addStationBookButton(CallbackInfo ci) {
		PandoricalContainerScreen self = (PandoricalContainerScreen) (Object) this;

		self.getRecipeStation().ifPresent(category -> {
			if (!RecipeCache.hasRecipes()) return;

			// Where the vanilla book toggle sits on a crafting table, so the hand already goes
			// there: measured from the panel's own corner, not from the middle of the window.
			// Off screen centre it only lands correctly on a panel of one particular size, and
			// it misses entirely once the recipe book pane shoves the panel sideways.
			this.addRenderableWidget(new ImageButton(
				self.getPanelX() + BOOK_BUTTON_X, self.getPanelY() + BOOK_BUTTON_Y,
				BOOK_BUTTON_W, BOOK_BUTTON_H, BOOK_BUTTON_SPRITES,
				button -> {
					Minecraft client = Minecraft.getInstance();
					client.gui.setScreen(RecipeBookScreens.open(self, RecipeMode.STATION, category));
				},
				Component.translatable("gui.recipebook.toggleRecipes.craftable")));
		});
	}
}
