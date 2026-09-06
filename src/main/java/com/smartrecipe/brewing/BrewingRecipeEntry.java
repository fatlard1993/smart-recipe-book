package com.smartrecipe.brewing;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * One brewing recipe, already resolved to the stacks that show it.
 *
 * <p>Resolved on the server rather than sent as ingredients, because the parts a brewing recipe is
 * actually made of - {@code PotionIngredient}, a potion predicate over a {@code HolderSet<Potion>} -
 * would have to be re-walked on the client to say "awkward potion" instead of "some potion". The
 * stacks are the thing the book draws either way, so the walk happens once, where the recipe lives.
 *
 * @param inputs   the bottles that may sit in the stand: usually one, more when the recipe accepts
 *                 a tag or several potions
 * @param reagents what goes in the top slot
 * @param output   what comes out
 */
public record BrewingRecipeEntry(List<ItemStack> inputs, List<ItemStack> reagents, ItemStack output) {

	public static final StreamCodec<RegistryFriendlyByteBuf, BrewingRecipeEntry> STREAM_CODEC =
		StreamCodec.composite(
			ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), BrewingRecipeEntry::inputs,
			ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), BrewingRecipeEntry::reagents,
			ItemStack.STREAM_CODEC, BrewingRecipeEntry::output,
			BrewingRecipeEntry::new);
}
