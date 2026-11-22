package com.cosmiclogic.dbshots.mixin;

import com.cosmiclogic.dbshots.Hold;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The client test tool respawns as soon as a death screen opens; this keeps the death screen up until the shots are taken. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "respawn", at = @At("HEAD"), cancellable = true)
    private void dbshots$holdDeathScreen(CallbackInfo ci) {
        if (Hold.death) ci.cancel();
    }
}
