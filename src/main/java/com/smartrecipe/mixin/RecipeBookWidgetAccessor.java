package com.smartrecipe.mixin;

import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.recipe.NetworkRecipeId;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RecipeBookWidget.class)
public interface RecipeBookWidgetAccessor {
	@Invoker("select")
	boolean invokeSelect(RecipeResultCollection results, NetworkRecipeId recipeId, boolean craftAll);
}
