# Smart Recipe Book

A Fabric mod that completely replaces Minecraft's recipe book with a smarter, more useful alternative. No more digging through cluttered recipe lists or manually crafting intermediate materials.

## A recipe you click is a recipe you know

The game lays a recipe out only for a player whose recipe book already contains it, and a recipe
gets into the book through an unlock advancement that a mod may never have written. The book
would show such a recipe, because the catalog carries every recipe the server has, and clicking it
did nothing at all. Now the click teaches it: with the ingredients in hand and the recipe asked for
by name, the server awards it and lays it out in the same motion.

## On a gamepad

The recipe grid is painted rather than built out of widgets, which is right for a few dozen icons
and leaves it invisible to anything reading `Screen.children()` - a gamepad navigator would reach
the search box, the filter and the pager, and not one recipe.

So the screen advertises its slots through Pandorical's `NavigableScreen`, the same channel
Pandorical's own screens use, and couch-controls picks them up knowing nothing about this mod.
Only the slots actually filled on the page are offered; landing on the empty tail of the last row
is landing on nothing. Pandorical stays a suggestion rather than a requirement: the class that
names it is separate, and a client without it never constructs that one.

## What It Does

Open your inventory, crafting table, or any furnace and click the recipe book button. Instead of the vanilla recipe book, you'll get a clean interface that actually helps you craft.

**The main idea:** If you have the raw materials to make something (even through multiple crafting steps), this mod shows it as craftable and handles the intermediate crafting for you.

Install it on your client. In singleplayer, it shows all recipes regardless of unlock status. On a multiplayer server, install it there too and you get the same: the server sends the whole recipe list, so sub-crafting can chain through recipes you have not unlocked yet. On a server without it, the book only knows the recipes you have unlocked, and a chain can only run through those.

## Features

### Smart Recipe Display

- **Grid-aware filtering** - In your inventory (2x2 grid), you only see recipes you can actually craft there. At a crafting table (3x3), you see everything.
- **Real craftability** - Recipes show as craftable when you have the materials, even if you need to craft intermediate items first.
- **Readable without reading** - Every slot is coloured by whether you can make it right now: lit green for yes, dim grey for not yet. Brightness carries the same message as hue, so it survives colour-blindness and a washed-out TV. The filter button matches, and wears a crafting table or a book, so a child who cannot read the label can still work the screen.
- **Craftable-only toggle** - Craftable-only is the default; the button under the page arrows switches to the full list. Craftability counts sub-crafting, and the choice sticks until you change it.
- **Usage tracking** - Recipes you craft frequently appear first (resets each session).
- **Search** - Type to filter recipes by name in real time.
- **Deduplication** - One recipe per result item, keeping the list clean.

### One-Click Complex Crafting

Want to craft a lantern but only have iron ingots, logs, and coal? The mod figures out the full chain - ingots to nuggets, logs to planks to sticks, sticks and coal to torches, then finally the lantern - and handles it all when you click.

- Calculates the full crafting tree automatically
- Identifies what intermediate items you need to make
- Executes each crafting step in sequence
- Choose how many to craft with the quantity selector: `-` / `+` step one at a time, `--` / `++` two at a time, and **Max** goes straight to as many as your materials allow

### Furnace Support

Works with all furnace types, showing only relevant recipes:

- **Furnace** - All smelting recipes
- **Blast Furnace** - Ores and metal items only
- **Smoker** - Food items only

For smelting recipes with multiple valid inputs (like Gold Ingot from ore, raw gold, or deepslate ore), the preview shows all options.

### Recipe Preview

Click any recipe to see:

- All required ingredients with quantities
- Color-coded availability (green = have it, yellow = can sub-craft, red = missing)
- Click ingredients to jump to their recipes
- Scroll through recipes with many input options

### Mod Compatibility

- Compatible with Backpack Inventory mod (detects crafting grid size)

## Development

Installing and building are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
