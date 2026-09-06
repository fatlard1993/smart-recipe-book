package com.smartrecipe.server;

import com.smartrecipe.SmartRecipeBookMod;
import com.smartrecipe.brewing.BrewingRecipeEntry;
import com.smartrecipe.brewing.BrewingRecipesPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.predicates.PotionsPredicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.PotionIngredient;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The server half: reads the brewing recipes off the recipe manager and hands them to clients
 * running this mod.
 *
 * <p>Runs on a client too - Fabric fires {@code main} on both sides - which is what makes the
 * payload type registered on either end of any connection this mod is on, singleplayer included.
 *
 * <p>Sent only to a client that registered a receiver for it. A vanilla client, or one without this
 * mod, is never sent anything; brewing simply stays absent from a book it does not have.
 */
public class BrewingRecipeSender implements ModInitializer {

	/**
	 * Built once per data pack, not once per player. Resolving the recipes walks every potion in
	 * every ingredient's holder set, and the answer is the same for everyone until a reload
	 * changes the recipes underneath it.
	 */
	private static List<BrewingRecipeEntry> cached = null;

	/** The protocol's own ceiling for a custom payload. */
	private static final int MAX_PAYLOAD_BYTES = 1048576;

	@Override
	public void onInitialize() {
		// Large, not the default cap. Vanilla's 279 brewing recipes come to roughly ten kilobytes
		// against a default ceiling of 32767, which sounds like room until a data pack adds a
		// potion line. Going over does not degrade - the packet is refused and takes the join with
		// it - so the ceiling is raised to the protocol's own limit rather than left to be
		// discovered by whoever installs the wrong pack.
		PayloadTypeRegistry.clientboundPlay().registerLarge(
			BrewingRecipesPayload.TYPE, BrewingRecipesPayload.STREAM_CODEC, MAX_PAYLOAD_BYTES);

		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> cached = null);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			String who = handler.getPlayer().getName().getString();

			// Said out loud, both ways. A capability-gated packet that is quietly not sent is
			// indistinguishable from a feature that was never built - the book is simply empty and
			// nothing anywhere says why - and this suite has lost afternoons to exactly that shape
			// of silence.
			if (!ServerPlayNetworking.canSend(handler.getPlayer(), BrewingRecipesPayload.TYPE)) {
				SmartRecipeBookMod.LOGGER.debug(
					"{} has no brewing receiver; their client is missing this mod or is older", who);
				return;
			}

			List<BrewingRecipeEntry> recipes = collect(server);
			if (recipes.isEmpty()) {
				SmartRecipeBookMod.LOGGER.warn("No brewing recipes to send to {}", who);
				return;
			}

			ServerPlayNetworking.send(handler.getPlayer(), new BrewingRecipesPayload(recipes));
			SmartRecipeBookMod.LOGGER.debug("Sent {} brewing recipes to {}", recipes.size(), who);
		});
	}

	private static List<BrewingRecipeEntry> collect(MinecraftServer server) {
		List<BrewingRecipeEntry> built = cached;
		if (built != null) return built;

		built = new ArrayList<>();
		for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			if (!(holder.value() instanceof BrewingRecipe brewing)) continue;

			List<ItemStack> inputs = resolve(brewing.getInput());
			List<ItemStack> reagents = resolve(brewing.getReagent());
			ItemStack output = brewing.getOutput().create();
			if (inputs.isEmpty() || reagents.isEmpty() || output.isEmpty()) continue;

			built.add(new BrewingRecipeEntry(inputs, reagents, output));
		}

		SmartRecipeBookMod.LOGGER.info("Collected {} brewing recipes for the recipe book", built.size());
		cached = built;
		return built;
	}

	/**
	 * Every stack a potion ingredient will accept.
	 *
	 * <p>A brewing ingredient is an item set and, optionally, a set of potions those items must be
	 * holding. Both halves matter to a reader: "potion" and "awkward potion" sit in the same slot
	 * of the same stand and only one of them is the answer, so the potion is written into the stack
	 * rather than left for the client to guess at.
	 */
	private static List<ItemStack> resolve(PotionIngredient ingredient) {
		Optional<PotionsPredicate> predicate = ingredient.potions();
		Optional<net.minecraft.core.HolderSet<Potion>> potions =
			predicate.flatMap(PotionsPredicate::potions);

		List<ItemStack> stacks = new ArrayList<>();
		for (Holder<Item> item : ingredient.ingredient().items().toList()) {
			if (potions.isEmpty()) {
				stacks.add(new ItemStack(item));
				continue;
			}
			for (Holder<Potion> potion : potions.get()) {
				ItemStack stack = new ItemStack(item);
				stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
				stacks.add(stack);
			}
		}
		return stacks;
	}
}
