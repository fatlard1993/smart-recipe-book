package com.smartrecipe.recipe;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

/**
 * A slice of the server's whole recipe list, on its way to a client that asked to be told.
 *
 * <p>The vanilla recipe book sync carries only what the player has unlocked, and a recipe unlocks
 * when its ingredient is first held. That is enough for a book that only shows, and not enough for
 * one that chains: a lodestone needs chiseled stone bricks, which need stone brick slabs, which
 * need stone bricks, and a player holding only stone has unlocked the first and last of those
 * recipes and neither of the middle two. The tree could not be planned because the client had
 * never heard of the rungs. This packet tells it about every recipe the server has, under the
 * server's own display ids, so a plan can be built from the whole ladder.
 *
 * <p>Knowing a recipe is not the same as being allowed to place it - the server refuses to lay
 * out a recipe the player has not unlocked - but a chain unlocks itself as it runs: each step's
 * output is the next step's ingredient, and the unlock fires inside the click that moves that
 * output into the inventory, before the next placement is read.
 *
 * <p>Sent in slices, {@code first} marking the one that starts a fresh list and {@code last} the
 * one that completes it, so no single payload has to carry a modpack's worth of recipes at once.
 */
public record RecipeCatalogPayload(boolean first, boolean last, List<RecipeDisplayEntry> recipes)
		implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<RecipeCatalogPayload> TYPE =
		new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath("smart-recipe-book", "recipe_catalog"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RecipeCatalogPayload> STREAM_CODEC =
		StreamCodec.composite(
			ByteBufCodecs.BOOL, RecipeCatalogPayload::first,
			ByteBufCodecs.BOOL, RecipeCatalogPayload::last,
			RecipeDisplayEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), RecipeCatalogPayload::recipes,
			RecipeCatalogPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
