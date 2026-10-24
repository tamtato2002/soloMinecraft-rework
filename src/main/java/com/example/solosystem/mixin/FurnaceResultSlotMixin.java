package com.example.solosystem.mixin;

import com.example.solosystem.ExpEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {
	@Inject(method = "onTake", at = @At("HEAD"), require = 0)
	private void solosystem$onTake(Player player, ItemStack stack, CallbackInfo ci) {
		if (player instanceof ServerPlayer sp) ExpEvents.onSmelt(sp, stack);
	}
}
