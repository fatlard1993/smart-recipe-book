# Smart Recipe Book

A Fabric mod that completely replaces Minecraft's recipe book with a smarter, more useful alternative. No more digging through cluttered recipe lists or manually crafting intermediate materials.

## What It Does

Open your inventory, crafting table, or any furnace and click the recipe book button. Instead of the vanilla recipe book, you'll get a clean interface that actually helps you craft.

**The main idea:** If you have the raw materials to make something (even through multiple crafting steps), this mod shows it as craftable and handles the intermediate crafting for you.

## Features

### Smart Recipe Display

- **Grid-aware filtering** - In your inventory (2x2 grid), you only see recipes you can actually craft there. At a crafting table (3x3), you see everything.
- **Real craftability** - Recipes show as craftable when you have the materials, even if you need to craft intermediate items first.
- **Usage tracking** - Recipes you craft frequently appear first.

### One-Click Complex Crafting

Want to craft a lantern but only have iron ingots, logs, and coal? The mod figures out the full chain - ingots to nuggets, logs to planks to sticks, sticks and coal to torches, then finally the lantern - and handles it all when you click.

- Calculates the full crafting tree automatically
- Identifies what intermediate items you need to make
- Executes each crafting step in sequence

### Furnace Support

Works with all furnace types, showing only relevant recipes:

- **Furnace** - All smelting recipes
- **Blast Furnace** - Ores and metal items only
- **Smoker** - Food items only

For smelting recipes with multiple valid inputs (like Gold Ingot from ore, raw gold, or deepslate ore), the preview shows all options.

### Recipe Preview

Click any recipe to see:

- All required ingredients with quantities
- Alternative ingredients where applicable
- Scroll through recipes with many input options
- Click ingredients to jump to their recipes

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.18.1+
- Fabric API
- Java 21+

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/)
2. Download [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download Smart Recipe Book from Releases
4. Drop both JARs in your `mods` folder

## Building

```bash
./gradlew build
```

Output JAR is in `build/libs/`.

## License

MIT
