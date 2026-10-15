# Smart Recipe Book - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Drop the jar in your client's `mods` folder alongside its declared dependencies (see `fabric.mod.json`). On a server, drop it in the server's `mods` folder as well: that is what gives clients the full recipe list and the brewing recipes. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Learning a recipe on click

The game lays a recipe out only for a player whose recipe book already contains it, and a recipe
gets into the book through an unlock advancement that a mod may never have written. The catalog
(`RecipeCatalogSender`) carries every recipe some advancement rewards, plus whatever the player's
own book already knows, so the book can show a recipe the player cannot place. `PlaceUnknownRecipeMixin`
(server side, on `handlePlaceRecipe`) awards the recipe first, so the click teaches it and lays it
out in the same motion. Recipes nothing teaches stay out of the catalog on purpose: a mod that keeps
one out of the book (hemp-craft has a few) is respected.

## Gamepad navigation

The recipe grid is painted rather than built out of widgets, which leaves it invisible to anything
reading `Screen.children()`: a gamepad navigator would reach the search box, the filter and the
pager, and not one recipe. So the screen advertises its slots through Pandorical's `NavigableScreen`
(`NavigableRecipeBookScreen`), the channel Pandorical's own screens use, and couch-controls picks
them up knowing nothing about this mod. Only the slots filled on the page are offered. Pandorical
stays a suggestion: `RecipeBookScreens` picks the navigable variant only when Pandorical is loaded,
so a client without it never constructs the class that names it.

## Building

```bash
./gradlew build
```

Output JAR is in `build/libs/`.

To run a development client:

```bash
./gradlew runClient
```
