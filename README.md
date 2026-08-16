# Smart Recipe Book

A client-side Fabric mod that completely replaces Minecraft's recipe book with a smarter, more useful alternative. No more digging through cluttered recipe lists or manually crafting intermediate materials.

## What It Does

Open your inventory, crafting table, or any furnace and click the recipe book button. Instead of the vanilla recipe book, you'll get a clean interface that actually helps you craft.

**The main idea:** If you have the raw materials to make something (even through multiple crafting steps), this mod shows it as craftable and handles the intermediate crafting for you.

This is a **client-side only** mod: install it on your client, no server installation needed. In singleplayer, it shows all recipes regardless of unlock status. On multiplayer servers, it shows recipes the server has sent to your client.

## Features

### Smart Recipe Display

- **Grid-aware filtering** - In your inventory (2x2 grid), you only see recipes you can actually craft there. At a crafting table (3x3), you see everything.
- **Real craftability** - Recipes show as craftable when you have the materials, even if you need to craft intermediate items first.
- **Craftable-only toggle** - Craftable-only is the default; the button under the page arrows switches to the full list. Craftability counts sub-crafting, and the choice sticks until you change it.
- **Usage tracking** - Recipes you craft frequently appear first (resets each session).
- **Search** - Type to filter recipes by name in real time.
- **Deduplication** - One recipe per result item, keeping the list clean.

### One-Click Complex Crafting

Want to craft a lantern but only have iron ingots, logs, and coal? The mod figures out the full chain - ingots to nuggets, logs to planks to sticks, sticks and coal to torches, then finally the lantern - and handles it all when you click.

- Calculates the full crafting tree automatically
- Identifies what intermediate items you need to make
- Executes each crafting step in sequence
- Choose how many to craft with the quantity selector

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

## Installation

Drop the jar in your client's `mods` folder alongside its declared dependencies (see `fabric.mod.json`). No server-side installation needed. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Building

```bash
./gradlew build
```

Output JAR is in `build/libs/`.

To run a development client:

```bash
./gradlew runClient
```

## License

MIT, see [LICENSE](LICENSE).
