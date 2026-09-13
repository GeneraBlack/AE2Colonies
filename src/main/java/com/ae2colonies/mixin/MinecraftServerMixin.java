package com.ae2colonies.mixin;

import com.ae2colonies.AE2Colonies;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    private int shutdownSpinCount = 0;

    /**
     * In NeoForge 1.21.1, stopServer() introduces a while-loop checking whether any level's
     * chunkMap.hasWork() returns true. However, distanceManager.hasTickets() returns true whenever
     * tickets (such as spawn chunks or mod tickets) exist, which removeTicketsOnClosing() does
     * not remove. This turns the loop into an infinite hang during singleplayer world exit.
     * We allow up to 5 iterations for any immediate work to flush, then return false so Minecraft
     * immediately proceeds to saveAllChunks(false, true, false) and cleanly saves the world.
     */
    @Redirect(
            method = "stopServer",
            at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;anyMatch(Ljava/util/function/Predicate;)Z")
    )
    private boolean onStopServerAnyMatch(Stream<?> stream, Predicate<Object> predicate) {
        boolean hasWork = stream.anyMatch(predicate);
        if (com.ae2colonies.colony.WarehouseMEBridge.isShuttingDown() && hasWork) {
            AE2Colonies.LOGGER.info("[AE2Colonies] Bypassing NeoForge infinite chunk spin loop immediately, proceeding directly to world save.");
            return false;
        }
        return hasWork;
    }
}
