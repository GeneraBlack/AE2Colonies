package com.ae2colonies.mixin;

import com.ae2colonies.AE2Colonies;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Shadow
    public abstract Iterable<ServerLevel> getAllLevels();

    @Inject(
            method = "stopServer",
            at = @At("HEAD")
    )
    private void onStopServerHead(CallbackInfo ci) {
        AE2Colonies.LOGGER.info("[AE2Colonies] stopServer() entered");
        com.ae2colonies.colony.WarehouseMEBridge.beginShutdown();

        // Log which levels have pending chunk work
        try {
            for (ServerLevel level : getAllLevels()) {
                if (level != null) {
                    boolean hasWork = level.getChunkSource().chunkMap.hasWork();
                    AE2Colonies.LOGGER.info("[AE2Colonies] Level {} hasWork={}", level.dimension().location(), hasWork);
                }
            }
        } catch (Exception e) {
            AE2Colonies.LOGGER.warn("[AE2Colonies] Error checking chunk work: {}", e.getMessage());
        }
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

    /**
     * Inject into waitUntilNextTick to log each spin-loop iteration during shutdown.
     * This helps identify why the chunk-flush loop never exits.
     */
    @Inject(
            method = "waitUntilNextTick",
            at = @At("HEAD")
    )
    private void onWaitUntilNextTick(CallbackInfo ci) {
        if (com.ae2colonies.colony.WarehouseMEBridge.isShuttingDown()) {
            // Log every 20 calls to avoid flooding
            try {
                for (ServerLevel level : getAllLevels()) {
                    if (level != null && level.getChunkSource().chunkMap.hasWork()) {
                        AE2Colonies.LOGGER.info("[AE2Colonies] Shutdown spin: Level {} STILL has pending chunk work", level.dimension().location());
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
