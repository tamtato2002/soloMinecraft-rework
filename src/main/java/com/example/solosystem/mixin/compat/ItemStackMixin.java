package com.example.solosystem.mixin.compat;

import com.example.solosystem.ExpEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game calls ItemStack.onCraftedBy(player, amount) once for every craft / smelt / stonecutter result that a player
 * takes - for a normal click AND for Shift+Click ("take everything"), with the real amount.
 * (The old hook on the result slot's onTake saw an already-emptied stack when Shift+Click was used, so those
 * crafts were never counted.) This also covers result slots added by other mods.
 *
 * This mixin lives in its own config with "required": false, so a future rename can never stop the game from starting.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "onCraftedBy", at = @At("HEAD"), require = 0)
	private void solosystem$crafted(Player player, int amount, CallbackInfo ci) {
		if (!(player instanceof ServerPlayer sp) || amount <= 0) return;
		ItemStack self = (ItemStack) (Object) this;
		ExpEvents.onResultTaken(sp, self, amount);
	}
}
