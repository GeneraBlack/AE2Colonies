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

    private int shutdownSpinCount = 0;

    @org.spongepowered.asm.mixin.injection.Redirect(
            method = "stopServer",
            at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;anyMatch(Ljava/util/function/Predicate;)Z")
    )
    private boolean onStopServerAnyMatch(java.util.stream.Stream<?> stream, java.util.function.Predicate<Object> predicate) {
        boolean hasWork = stream.anyMatch(predicate);
        if (com.ae2colonies.colony.WarehouseMEBridge.isShuttingDown() && hasWork) {
            shutdownSpinCount++;
            if (shutdownSpinCount == 1) {
                AE2Colonies.LOGGER.info("[AE2Colonies] Waiting for chunk saving to finish... (iteration 1)");
            } else if (shutdownSpinCount % 50 == 0 && shutdownSpinCount <= 200) {
                AE2Colonies.LOGGER.info("[AE2Colonies] Still waiting for chunk saving... (iteration {})", shutdownSpinCount);
            }
            
            if (shutdownSpinCount > 200) {
                if (shutdownSpinCount == 201) {
                    AE2Colonies.LOGGER.error("[AE2Colonies] Bypassing infinite chunk saving spin loop after 200 iterations!");
                }
                return false;
            }
        }
        return hasWork;
    }
}
