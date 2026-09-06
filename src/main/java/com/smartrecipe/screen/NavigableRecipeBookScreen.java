package com.smartrecipe.screen;

import java.util.ArrayList;
import java.util.List;
import justfatlard.pandorical.api.NavigableScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * The recipe book, told to a gamepad.
 *
 * <p>Nothing here changes how the book looks or behaves. It exists because the recipe grid is
 * painted rather than built from widgets, so a navigator walking {@code Screen.children()} finds
 * the search box, the filter, the pager and the close button - and not a single recipe. This
 * advertises the slots through Pandorical's {@link NavigableScreen}, the same channel Pandorical's
 * own screens use, and couch-controls picks them up with no knowledge of this mod.
 *
 * <p><b>A separate class on purpose.</b> Pandorical is a suggestion here, not a requirement, and
 * {@code implements NavigableScreen} names it in the class signature - a client without Pandorical
 * would fail to load the screen at all. Kept apart, that client simply never constructs this one;
 * see {@link RecipeBookScreens}.
 */
public class NavigableRecipeBookScreen extends SmartRecipeBookScreen implements NavigableScreen {

	public NavigableRecipeBookScreen(Screen parent, RecipeMode mode) {
		super(parent, mode);
	}

	public NavigableRecipeBookScreen(Screen parent, RecipeMode mode, String stationCategory) {
		super(parent, mode, stationCategory);
	}

	@Override
	public List<NavRegion> navRegions() {
		List<int[]> slots = recipeSlotBounds();
		List<NavRegion> regions = new ArrayList<>(slots.size());

		for (int i = 0; i < slots.size(); i++) {
			int[] box = slots.get(i);
			regions.add(new NavRegion("recipe_" + i, box[0], box[1], box[2], box[3]));
		}
		return regions;
	}
}
