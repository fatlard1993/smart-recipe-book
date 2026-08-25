package com.smartrecipe.mixin;

import com.smartrecipe.recipe.RecipeCache;
import com.smartrecipe.screen.RecipeMode;
import com.smartrecipe.screen.SmartRecipeBookScreen;
import justfatlard.pandorical.client.screen.PandoricalContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
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
@Mixin(PandoricalContainerScreen.class)
public abstract class PandoricalStationBookMixin extends Screen {

	private PandoricalStationBookMixin() {
		super(null);
	}

	@Inject(method = "init", at = @At("TAIL"), require = 1)
	private void smartrecipe$addStationBookButton(CallbackInfo ci) {
		PandoricalContainerScreen self = (PandoricalContainerScreen) (Object) this;

		self.getRecipeStation().ifPresent(category -> {
			if (!RecipeCache.hasRecipes()) return;

			// Top-left of the panel, where the vanilla book toggle sits on a crafting table, so
			// the hand already goes there.
			this.addRenderableWidget(Button.builder(Component.literal("☰"), button -> {
				Minecraft client = Minecraft.getInstance();
				client.gui.setScreen(new SmartRecipeBookScreen(self, RecipeMode.STATION, category));
			}).bounds(this.width / 2 - 100, this.height / 2 - 84, 20, 20).build());
		});
	}
}
