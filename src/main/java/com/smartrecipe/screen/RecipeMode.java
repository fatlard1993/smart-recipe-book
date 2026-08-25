package com.smartrecipe.screen;

/**
 * Enum representing the type of recipes to display in SmartRecipeBookScreen.
 */
public enum RecipeMode {
	CRAFTING("Crafting"),

	FURNACE("Smelting"),

	BLAST_FURNACE("Blast Furnace"),

	SMOKER("Smoking"),

	/** Somebody else's crafting bench, named by the recipe book category it works from. */
	STATION("Station");

	private final String displayName;

	RecipeMode(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

	public boolean isFurnaceType() {
		return this == FURNACE || this == BLAST_FURNACE || this == SMOKER;
	}
}
