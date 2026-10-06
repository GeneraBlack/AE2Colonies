package com.ae2colonies.colony;

import com.ae2colonies.AE2Colonies;
import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.ae2colonies.config.AE2ColoniesConfig;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles sending clean, throttled in-game notifications to colony players
 * when AE2 crafting fails, lacks ingredients, or times out.
 */
public class ColonyNotificationHelper {

    // Notification cooldown per item to prevent chat spam
    private static final Map<Item, Long> NOTIFICATION_COOLDOWNS = new ConcurrentHashMap<>();

    public static boolean canNotify(@NotNull Item item) {
        long now = System.currentTimeMillis();
        long cooldownMs = AE2ColoniesConfig.NOTIFICATION_COOLDOWN_SECONDS.get() * 1000L;
        Long lastTime = NOTIFICATION_COOLDOWNS.get(item);
        if (lastTime == null || now - lastTime >= cooldownMs) {
            NOTIFICATION_COOLDOWNS.put(item, now);
            return true;
        }
        return false;
    }

    /**
     * Sends a formatted notification to colony players.
     *
     * @param terminal        The terminal where the failure occurred
     * @param itemForCooldown Optional item to throttle notifications for (prevents chat spam)
     * @param message         The notification message component
     */
    public static void sendColonyNotification(
            @NotNull ColonyTerminalBlockEntity terminal,
            @Nullable Item itemForCooldown,
            @NotNull Component message
    ) {
        if (!AE2ColoniesConfig.ENABLE_PLAYER_NOTIFICATIONS.get()) {
            return;
        }

        if (itemForCooldown != null && !canNotify(itemForCooldown)) {
            return;
        }

        IWareHouse warehouse = WarehouseMEBridge.getWarehouseForTerminal(terminal);
        if (warehouse == null) {
            return;
        }

        IColony colony = warehouse.getColony();
        if (colony == null || terminal.getLevel() == null) {
            return;
        }

        Set<Player> recipients = new HashSet<>();
        try {
            Collection<Player> important = colony.getImportantMessageEntityPlayers();
            if (important != null) {
                recipients.addAll(important);
            }
        } catch (Exception e) {
            AE2Colonies.LOGGER.debug("Could not get important message players: {}", e.getMessage());
        }

        try {
            Collection<Player> general = colony.getMessagePlayerEntities();
            if (general != null) {
                recipients.addAll(general);
            }
        } catch (Exception e) {
            AE2Colonies.LOGGER.debug("Could not get general message players: {}", e.getMessage());
        }

        // Fallback: if no colony officers registered, find players near the terminal
        if (recipients.isEmpty() && terminal.getLevel() != null) {
            for (Player player : terminal.getLevel().players()) {
                if (player.blockPosition().closerThan(terminal.getBlockPos(), 64.0)) {
                    recipients.add(player);
                }
            }
        }

        MutableComponent formatted = Component.literal("[AE2 Colonies] ")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.empty().withStyle(ChatFormatting.YELLOW).append(message));

        for (Player player : recipients) {
            player.sendSystemMessage(formatted);
        }
    }
}
