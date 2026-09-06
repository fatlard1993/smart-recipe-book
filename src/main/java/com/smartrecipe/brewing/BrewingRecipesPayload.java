package com.smartrecipe.brewing;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The server's brewing recipes, on their way to a client that asked to be told.
 *
 * <p>This packet exists because brewing is the one station whose recipes the game never tells a
 * client about. Everything else in the book arrives through the vanilla recipe book sync, but
 * {@code BrewingRecipe} does not override {@code display()} - the interface default returns no
 * displays at all - so brewing produces nothing for that sync to carry and nothing for the
 * integrated-server path to read either. Without this the client can only learn which items are
 * valid reagents, never what any of them brew into.
 */
public record BrewingRecipesPayload(List<BrewingRecipeEntry> recipes) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<BrewingRecipesPayload> TYPE =
		new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath("smart-recipe-book", "brewing_recipes"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BrewingRecipesPayload> STREAM_CODEC =
		BrewingRecipeEntry.STREAM_CODEC.apply(ByteBufCodecs.list())
			.map(BrewingRecipesPayload::new, BrewingRecipesPayload::recipes);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
