package com.smartrecipe.screen;

/**
 * Enum representing the type of recipes to display in SmartRecipeBookScreen.
 */
public enum RecipeMode {
	CRAFTING("Crafting"),

	FURNACE("Smelting"),

	BLAST_FURNACE("Blast Furnace"),

	SMOKER("Smoking"),

	/**
	 * The brewing stand. Alone among the modes, its recipes do not come from the vanilla recipe
	 * sync - the game sends a client no brewing recipes at all - so this mode is empty unless the
	 * server is running this mod's own half. See {@code BrewingRecipesPayload}.
	 */
	BREWING("Brewing"),

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
