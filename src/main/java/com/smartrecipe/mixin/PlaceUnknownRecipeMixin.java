package com.smartrecipe.mixin;

import com.smartrecipe.SmartRecipeBookMod;
import java.util.List;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A recipe the book lays out is a recipe the player knows from then on.
 *
 * <p>Vanilla places a recipe only if the player's recipe book contains it, and a recipe gets
 * into the book through an unlock advancement that a mod may never have written. The book
 * showed such a recipe - the catalog carries every recipe the server has - and clicking it did
 * nothing, silently. Learning it on the click is the honest reading of the click: the player
 * has the ingredients in hand and is asking for the recipe by name.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlaceUnknownRecipeMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handlePlaceRecipe", at = @At("HEAD"))
    private void smartRecipeBook$learnOnPlace(ServerboundPlaceRecipePacket packet, CallbackInfo ci) {
        if (this.player == null || this.player.level().isClientSide()) return;
        RecipeManager.ServerDisplayInfo info = this.player.level().getServer().getRecipeManager()
            .getRecipeFromDisplay(packet.recipe());
        if (info == null) return;
        RecipeHolder<?> recipe = info.parent();
        if (this.player.getRecipeBook().contains(recipe.id())) return;
        this.player.awardRecipes(List.of(recipe));
        SmartRecipeBookMod.LOGGER.debug("{} learned {} by asking the book for it",
            this.player.getName().getString(), recipe.id().identifier());
    }
}
