package com.ae2colonies.mixin;

import com.ae2colonies.AE2Colonies;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Inject(
            method = "stopServer",
            at = @At("HEAD")
    )
    private void onStopServerHead(CallbackInfo ci) {
        AE2Colonies.LOGGER.info("[AE2Colonies] stopServer() entered");
        com.ae2colonies.colony.WarehouseMEBridge.beginShutdown();
    }

    @Inject(
            method = "saveAllChunks",
            at = @At("HEAD")
    )
    private void onSaveAllChunksHead(boolean silent, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
        AE2Colonies.LOGGER.info("[AE2Colonies] saveAllChunks(silent={}, flush={}, force={}) entering", silent, flush, force);
    }

    @Inject(
            method = "saveAllChunks",
            at = @At("RETURN")
    )
    private void onSaveAllChunksReturn(boolean silent, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
        AE2Colonies.LOGGER.info("[AE2Colonies] saveAllChunks() completed");
    }

    @Inject(
            method = "stopServer",
            at = @At("RETURN")
    )
    private void onStopServerReturn(CallbackInfo ci) {
        AE2Colonies.LOGGER.info("[AE2Colonies] stopServer() completed");
    }
}
