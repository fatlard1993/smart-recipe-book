package com.smartrecipe.server;

import com.smartrecipe.SmartRecipeBookMod;
import com.smartrecipe.mixin.ServerRecipeManagerAccessor;
import com.smartrecipe.recipe.RecipeCatalogPayload;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

/**
 * The server half: hands the whole recipe list to clients running this mod, so their book can
 * plan through recipes they have not unlocked yet.
 *
 * <p>Runs on a client too - Fabric fires {@code main} on both sides - which is what makes the
 * payload type registered on either end of any connection this mod is on, singleplayer included.
 * The integrated server sends it to its own player the same way a dedicated one does, so there is
 * one path for the full list rather than a network one and a reach-into-the-server one.
 *
 * <p>Sent only to a client that registered a receiver for it. A vanilla client, or one without
 * this mod, is never sent anything.
 */
public class RecipeCatalogSender implements ModInitializer {

	/** The protocol's own ceiling for a custom payload. */
	private static final int MAX_PAYLOAD_BYTES = 1048576;

	/**
	 * Recipes per payload. Vanilla alone has well over a thousand displays and a modpack multiplies
	 * that; slicing keeps every payload far under the ceiling whatever is installed.
	 */
	private static final int SLICE = 256;

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().registerLarge(
			RecipeCatalogPayload.TYPE, RecipeCatalogPayload.STREAM_CODEC, MAX_PAYLOAD_BYTES);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(server, handler.getPlayer()));

		// A display id is a position in the manager's list, and a reload rebuilds the list. Every
		// id a client holds is stale the moment that happens, so the list goes out again.
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				send(server, player);
			}
		});
	}

	private static void send(MinecraftServer server, ServerPlayer player) {
		String who = player.getName().getString();

		// Said out loud, both ways: a capability-gated packet that is quietly not sent looks exactly
		// like a feature that was never built.
		if (!ServerPlayNetworking.canSend(player, RecipeCatalogPayload.TYPE)) {
			SmartRecipeBookMod.LOGGER.debug(
				"{} has no recipe catalog receiver; their client is missing this mod or is older", who);
			return;
		}

		List<RecipeDisplayEntry> entries = ((ServerRecipeManagerAccessor) server.getRecipeManager())
			.getRecipes().stream()
			.map(RecipeManager.ServerDisplayInfo::display)
			.toList();
		if (entries.isEmpty()) {
			SmartRecipeBookMod.LOGGER.warn("No recipes to send to {}", who);
			return;
		}

		for (int from = 0; from < entries.size(); from += SLICE) {
			int to = Math.min(from + SLICE, entries.size());
			ServerPlayNetworking.send(player,
				new RecipeCatalogPayload(from == 0, to == entries.size(), entries.subList(from, to)));
		}
		SmartRecipeBookMod.LOGGER.debug("Sent {} recipes to {}", entries.size(), who);
	}
}
