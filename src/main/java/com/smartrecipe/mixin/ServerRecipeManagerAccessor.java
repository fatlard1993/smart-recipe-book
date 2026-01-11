package com.smartrecipe.mixin;

import net.minecraft.recipe.ServerRecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ServerRecipeManager.class)
public interface ServerRecipeManagerAccessor {

	@Accessor("recipes")
	List<ServerRecipeManager.ServerRecipe> getRecipes();
}
