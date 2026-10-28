package com.example.solosystem.mixin.client;

import com.example.solosystem.client.ForeignToasts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Watches the game's toast manager so quest notifications can avoid overlapping advancement / mod toasts.
 * The target is given as a string and every part is optional (require = 0, config not required),
 * so if the game ever renames it the mod still loads - the avoidance just switches itself off.
 */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager")
public abstract class ToastManagerMixin {
	@Inject(method = "addToast", at = @At("HEAD"), require = 0)
	private void solosystem$toastAdded(@Coerce Object toast, CallbackInfo ci) {
		if (toast != null && !toast.getClass().getName().startsWith("com.example.solosystem")) ForeignToasts.onAdded();
	}
}
