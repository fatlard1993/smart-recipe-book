package com.smartrecipe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.world.item.crafting.RecipeManager;

@Mixin(RecipeManager.class)
public interface ServerRecipeManagerAccessor {

	@Accessor("allDisplays")
	List<RecipeManager.ServerDisplayInfo> getRecipes();
}
