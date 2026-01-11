package com.smartrecipe.screen;

import com.smartrecipe.recipe.CraftingPlan;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Screen shown when there are multiple recipe choices for sub-components.
 * Allows user to select which recipe to use for each item.
 */
public class RecipeChoiceScreen extends Screen {

	private final Screen parent;
	private final CraftingPlan plan;
	private final Consumer<CraftingPlan> onConfirm;
	private int currentChoiceIndex = 0;
	private List<Map.Entry<ItemStack, List<NetworkRecipeId>>> choices;

	public RecipeChoiceScreen(Screen parent, CraftingPlan plan, Consumer<CraftingPlan> onConfirm) {
		super(Text.translatable("smart-recipe-book.recipe_choice.title"));
		this.parent = parent;
		this.plan = plan;
		this.onConfirm = onConfirm;
		this.choices = plan.getRecipeChoices().entrySet().stream().toList();
	}

	@Override
	protected void init() {
		super.init();

		if (choices.isEmpty()) {
			// No choices needed, just confirm
			close();
			onConfirm.accept(plan);
			return;
		}

		// Show current choice
		Map.Entry<ItemStack, List<NetworkRecipeId>> currentChoice = choices.get(currentChoiceIndex);
		ItemStack item = currentChoice.getKey();
		List<NetworkRecipeId> recipes = currentChoice.getValue();

		int buttonY = this.height / 2 - (recipes.size() * 25) / 2;

		for (int i = 0; i < recipes.size(); i++) {
			final NetworkRecipeId recipeId = recipes.get(i);
			final int index = i;

			this.addDrawableChild(ButtonWidget.builder(
				Text.literal(recipeId.toString()),
				button -> selectRecipe(item, recipeId)
			).dimensions(this.width / 2 - 100, buttonY + (i * 25), 200, 20).build());
		}

		// Cancel button
		this.addDrawableChild(ButtonWidget.builder(
			Text.translatable("gui.cancel"),
			button -> close()
		).dimensions(this.width / 2 - 100, this.height - 30, 200, 20).build());
	}

	private void selectRecipe(ItemStack item, NetworkRecipeId recipeId) {
		// Update the plan with the selected recipe
		// TODO: Actually update the plan's steps with the selected recipe

		currentChoiceIndex++;

		if (currentChoiceIndex >= choices.size()) {
			// All choices made, execute the plan
			close();
			onConfirm.accept(plan);
		} else {
			// Show next choice
			clearAndInit();
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		if (!choices.isEmpty() && currentChoiceIndex < choices.size()) {
			Map.Entry<ItemStack, List<NetworkRecipeId>> currentChoice = choices.get(currentChoiceIndex);
			ItemStack item = currentChoice.getKey();

			// Draw title
			context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.translatable("smart-recipe-book.recipe_choice.select", item.getName()),
				this.width / 2,
				20,
				0xFFFFFF
			);

			// Draw the item
			context.drawItem(item, this.width / 2 - 8, 40);
		}
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}
}
