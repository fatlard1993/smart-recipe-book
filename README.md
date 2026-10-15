# Smart Recipe Book

A Fabric mod that replaces the vanilla recipe book with one that shows what you can make from what you are carrying, and crafts the intermediate items for you.

## Installing

The book is a client mod: install it on your client. The server half is optional and comes in the same jar:

| Installed on | You get |
|---|---|
| Client only | The book, listing the recipes you have unlocked. Sub-crafting chains only through those. |
| Client and server | Every recipe an advancement can teach, unlocked or not, so sub-crafting chains through all of them. Clicking a recipe you have not unlocked teaches it to you. The brewing stand gets a book. |
| Singleplayer | The same as client and server. |

A player connecting without the mod gets the vanilla book and is sent nothing. Pandorical is optional; with it on the client, modded crafting stations get the book and the book works with gamepad navigation (see below).

## Opening it

Open your inventory, a crafting table or any furnace and click the recipe book button. The vanilla recipe-unlocked toasts no longer appear.

## The recipe list

- **Grid-aware** - In your inventory (2x2 grid) you only see recipes that fit there. At a crafting table (3x3) you see everything.
- **Real craftability** - A recipe counts as craftable when you have the materials, even if intermediate items have to be crafted first.
- **Coloured by craftability** - Every slot is lit green if you can make it now and dim grey if not. Brightness carries the same message as hue, so it reads for colour-blind players and on a washed-out screen. The filter button matches, and shows a crafting table or a book.
- **Craftable-only toggle** - Craftable-only is the default; the button under the page arrows switches to the full list. The choice holds for the rest of the session.
- **Usage order** - Recipes you craft often come first. The counts reset each session.
- **Search** - Type to filter recipes by name as you type.
- **One entry per item** - When more than one recipe makes an item, the preview says so ("TNT (1 of 2)") and the arrows by the title step through them. It opens on one you can make now, and Craft uses the one on show.

## One-click crafting

Want a lantern but only have iron ingots, logs and coal? The book works out the chain (ingots to nuggets, logs to planks to sticks, sticks and coal to torches, then the lantern) and crafts each step in order when you click Craft.

- Sub-crafts use crafting-table recipes only, and a sub-craft never spends the item the plan is making.
- It warns when the plan needs more free inventory slots than you have.
- Choose how many with the quantity buttons: `-` / `+` step by one, `--` / `++` by two, and **Max** goes straight to as many as your materials allow.

## Recipe preview

Click any recipe to see:

- Every ingredient with its quantity.
- Its availability: no tint means you have it, yellow means you do not but something makes it, red means you do not and nothing makes it.
- Click an ingredient to jump to its recipe.
- Scroll through recipes with many input options.

## Furnaces

Each furnace shows only its own recipes:

- **Furnace** - all smelting recipes
- **Blast Furnace** - ores and metal items
- **Smoker** - food

Furnace recipes are for looking up: the preview shows every valid input (gold ingot from ore, raw gold or deepslate ore, say), and the furnace is loaded by hand.

## Brewing stand

With the mod on the server, the brewing stand gets a book button listing the brews the server has: what goes in and what comes out. Like furnaces, it is for looking up; the stand is loaded by hand. On a server without the mod there is no button.

## Other mods

- **Pandorical crafting stations** - Any Pandorical station screen that declares a recipe book category (fletch-craft's fletching table, for one) gets the book button, filtered to that category. Craft asks the station to lay the recipe out in its own grid; there is no sub-crafting there.
- **[couch-controls](https://github.com/fatlard1993/couch-controls)** - With Pandorical installed, a gamepad can move between the recipes on the page as well as the search box, filter and pager.
- **Backpack Inventory** - The book detects its crafting grid size.

## Development

Installing and building are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
