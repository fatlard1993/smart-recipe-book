package com.smartrecipe.screen;

/**
 * Enum representing the type of recipes to display in SmartRecipeBookScreen.
 */
public enum RecipeMode {
	/**
	 * Crafting recipes (shaped and shapeless) for inventory and crafting table.
	 */
	CRAFTING("Crafting"),

	/**
	 * All smelting recipes for regular furnace.
	 */
	FURNACE("Smelting"),

	/**
	 * Ore/metal smelting recipes for blast furnace (faster ore processing).
	 */
	BLAST_FURNACE("Blast Furnace"),

	/**
	 * Food cooking recipes for smoker (faster food cooking).
	 */
	SMOKER("Smoking");

	private final String displayName;

	RecipeMode(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Check if this mode is any type of furnace (for shared furnace logic)
	 */
	public boolean isFurnaceType() {
		return this == FURNACE || this == BLAST_FURNACE || this == SMOKER;
	}
}
