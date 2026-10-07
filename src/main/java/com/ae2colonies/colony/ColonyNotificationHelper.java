package com.ae2colonies.colony;

import com.ae2colonies.AE2Colonies;
import com.ae2colonies.ae2.AE2IntegrationHelper;
import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.ae2colonies.blockentity.MEArchitectsCutterBlockEntity;
import com.ae2colonies.config.AE2ColoniesConfig;
import com.ae2colonies.domum.DomumOrnamentumHelper;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles sending clean, throttled in-game notifications to colony players
 * when AE2 crafting fails, lacks ingredients, or times out.
 */
public class ColonyNotificationHelper {

    // Notification cooldown per item to prevent chat spam
    private static final Map<Item, Long> NOTIFICATION_COOLDOWNS = new ConcurrentHashMap<>();

    // Cached set of items that have standard crafting recipes in Minecraft
    private static volatile Set<Item> CRAFTABLE_ITEMS = null;

    public static boolean isNotificationEnabled() {
        try {
            if (AE2ColoniesConfig.SPEC.isLoaded()) {
                return AE2ColoniesConfig.ENABLE_PLAYER_NOTIFICATIONS.get();
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    public static long getCooldownMs() {
        try {
            if (AE2ColoniesConfig.SPEC.isLoaded()) {
                return AE2ColoniesConfig.NOTIFICATION_COOLDOWN_SECONDS.get() * 1000L;
            }
        } catch (Exception ignored) {
        }
        return 30000L;
    }

    public static boolean hasCraftingRecipe(@Nullable Level level, @NotNull Item item) {
        if (level == null || level.isClientSide()) {
            return false;
        }
        if (CRAFTABLE_ITEMS == null) {
            synchronized (ColonyNotificationHelper.class) {
                if (CRAFTABLE_ITEMS == null) {
                    Set<Item> set = new HashSet<>();
                    try {
                        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
                            try {
                                ItemStack result = holder.value().getResultItem(level.registryAccess());
                                if (!result.isEmpty()) {
                                    set.add(result.getItem());
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    } catch (Exception e) {
                        AE2Colonies.LOGGER.debug("Error populating craftable items cache: {}", e.getMessage());
                    }
                    CRAFTABLE_ITEMS = set;
                }
            }
        }
        return CRAFTABLE_ITEMS != null && CRAFTABLE_ITEMS.contains(item);
    }

    public static boolean isCoolingDown(@NotNull Item item) {
        long now = System.currentTimeMillis();
        long cooldownMs = getCooldownMs();
        Long lastTime = NOTIFICATION_COOLDOWNS.get(item);
        return lastTime != null && (now - lastTime < cooldownMs);
    }

    public static boolean canNotify(@NotNull Item item) {
        long now = System.currentTimeMillis();
        long cooldownMs = getCooldownMs();
        Long lastTime = NOTIFICATION_COOLDOWNS.get(item);
        if (lastTime == null || now - lastTime >= cooldownMs) {
            NOTIFICATION_COOLDOWNS.put(item, now);
            return true;
        }
        return false;
    }

    /**
     * Checks why a crafting request for the given item cannot proceed and sends
     * an informative, throttled notification to the colony players.
     */
    public static void checkAndNotifyCraftingFailure(
            @NotNull ColonyTerminalBlockEntity terminal,
            @NotNull ItemStack stack,
            int needed
    ) {
        if (stack.isEmpty() || needed <= 0) {
            return;
        }
        if (!isNotificationEnabled()) {
            return;
        }
        if (isCoolingDown(stack.getItem())) {
            return;
        }

        // 1. Domum Ornamentum Block
        if (DomumOrnamentumHelper.isDOBlock(stack)) {
            IGrid grid = terminal.getGrid();
            if (grid == null) {
                return;
            }
            if (grid.getActiveMachines(MEArchitectsCutterBlockEntity.class).isEmpty()) {
                sendColonyNotification(
                        terminal,
                        stack.getItem(),
                        Component.translatable("message.ae2colonies.no_cutter", stack.getHoverName())
                );
                return;
            }
            Level level = terminal.getLevel();
            if (level == null) {
                return;
            }
            DomumOrnamentumHelper.DOMaterialCost cost = DomumOrnamentumHelper.getMaterialCost(level, stack);
            if (cost != null) {
                int batches = (int) Math.ceil((double) needed / cost.getYield());
                List<String> missingList = new ArrayList<>();
                for (ItemStack ingredient : cost.getIngredients()) {
                    int req = batches * ingredient.getCount();
                    int avail = AE2IntegrationHelper.getAvailableCount(grid, ingredient, false);
                    if (avail < req) {
                        int shortfall = req - avail;
                        if (!AE2IntegrationHelper.isCraftable(grid, ingredient)) {
                            missingList.add(shortfall + "x " + ingredient.getHoverName().getString());
                        }
                    }
                }
                if (!missingList.isEmpty()) {
                    sendColonyNotification(
                            terminal,
                            stack.getItem(),
                            Component.translatable("message.ae2colonies.missing_ingredients",
                                    stack.getHoverName(),
                                    String.join(", ", missingList))
                    );
                }
            }
            return;
        }

        // 2. Standard AE2 Crafting
        IGrid grid = terminal.getGrid();
        if (grid == null) {
            return;
        }

        ICraftingService craftingService = AE2IntegrationHelper.getCraftingService(grid);
        if (craftingService == null || craftingService.getCpus().isEmpty()) {
            AEItemKey key = AEItemKey.of(stack);
            boolean hasPattern = key != null && craftingService != null && !craftingService.getCraftingFor(key).isEmpty();
            boolean hasRecipe = hasCraftingRecipe(terminal.getLevel(), stack.getItem());
            if (hasPattern || hasRecipe) {
                sendColonyNotification(
                        terminal,
                        stack.getItem(),
                        Component.translatable("message.ae2colonies.no_cpu", stack.getHoverName())
                );
            } else {
                sendColonyNotification(
                        terminal,
                        stack.getItem(),
                        Component.translatable("message.ae2colonies.item_not_available", stack.getHoverName())
                );
            }
            return;
        }

        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return;
        }

        var patterns = craftingService.getCraftingFor(key);
        if (patterns.isEmpty()) {
            // No pattern in AE2: check if it has a recipe
            if (hasCraftingRecipe(terminal.getLevel(), stack.getItem())) {
                sendColonyNotification(
                        terminal,
                        stack.getItem(),
                        Component.translatable("message.ae2colonies.no_pattern", stack.getHoverName())
                );
            } else {
                sendColonyNotification(
                        terminal,
                        stack.getItem(),
                        Component.translatable("message.ae2colonies.item_not_available", stack.getHoverName())
                );
            }
            return;
        }

        // Pattern exists, but crafting cannot proceed -> find missing ingredients
        List<ItemStack> missing = AE2IntegrationHelper.findMissingIngredients(grid, stack, needed);
        if (!missing.isEmpty()) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (ItemStack mis : missing) {
                counts.merge(mis.getHoverName().getString(), mis.getCount(), Integer::sum);
            }
            List<String> parts = new ArrayList<>();
            counts.forEach((name, qty) -> parts.add(qty + "x " + name));
            String missingStr = String.join(", ", parts);

            sendColonyNotification(
                    terminal,
                    stack.getItem(),
                    Component.translatable("message.ae2colonies.missing_ingredients", stack.getHoverName(), missingStr)
            );
        } else {
            boolean allCpusBusy = craftingService.getCpus().stream().allMatch(ICraftingCPU::isBusy);
            if (allCpusBusy) {
                sendColonyNotification(
                        terminal,
                        stack.getItem(),
                        Component.translatable("message.ae2colonies.cpu_busy", stack.getHoverName())
                );
            } else {
                sendColonyNotification(
                        terminal,
                        stack.getItem(),
                        Component.translatable("message.ae2colonies.missing_ingredients", stack.getHoverName(), "?")
                );
            }
        }
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
        if (!isNotificationEnabled()) {
            return;
        }

        if (itemForCooldown != null && !canNotify(itemForCooldown)) {
            return;
        }

        Level level = terminal.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        IColony colony = null;
        IWareHouse warehouse = WarehouseMEBridge.getWarehouseForTerminal(terminal);
        if (warehouse != null) {
            colony = warehouse.getColony();
        }
        if (colony == null) {
            try {
                colony = IColonyManager.getInstance().getIColony(level, terminal.getBlockPos());
                if (colony == null) {
                    colony = IColonyManager.getInstance().getClosestIColony(level, terminal.getBlockPos());
                }
            } catch (Exception e) {
                AE2Colonies.LOGGER.debug("Error looking up colony for terminal: {}", e.getMessage());
            }
        }

        Set<Player> recipients = new HashSet<>();
        MinecraftServer server = level.getServer();

        if (server != null) {
            // 1. Placer / owner of the Colony Terminal
            try {
                if (terminal.getActionableNode() != null && terminal.getActionableNode().getOwningPlayerProfileId() != null) {
                    ServerPlayer placer = server.getPlayerList().getPlayer(terminal.getActionableNode().getOwningPlayerProfileId());
                    if (placer != null) {
                        recipients.add(placer);
                    }
                }
            } catch (Exception ignored) {
            }

            // 2. Colony owner, officers, and members
            if (colony != null) {
                try {
                    // Colony Owner
                    if (colony.getPermissions().getOwnerEntry() != null) {
                        ServerPlayer owner = server.getPlayerList().getPlayer(colony.getPermissions().getOwnerEntry().getKey());
                        if (owner != null) {
                            recipients.add(owner);
                        }
                    }
                    // All players registered in colony permissions
                    for (UUID uuid : colony.getPermissions().getPlayers().keySet()) {
                        ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                        if (sp != null) {
                            recipients.add(sp);
                        }
                    }
                    // All online colony members
                    for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                        if (colony.getPermissions().isColonyMember(sp)) {
                            recipients.add(sp);
                        }
                    }
                    // Any online player inside colony territory
                    for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                        if (sp.level() == level && colony.isCoordInColony(level, sp.blockPosition())) {
                            recipients.add(sp);
                        }
                    }
                } catch (Exception e) {
                    AE2Colonies.LOGGER.debug("Could not get colony players: {}", e.getMessage());
                }
            }

            // 3. Fallback: Any players within 256 blocks of the terminal
            if (recipients.isEmpty()) {
                for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                    if (sp.level() == level && sp.blockPosition().closerThan(terminal.getBlockPos(), 256.0)) {
                        recipients.add(sp);
                    }
                }
            }

            // 4. Ultimate fallback: ALL online players on the server
            if (recipients.isEmpty()) {
                recipients.addAll(server.getPlayerList().getPlayers());
            }
        }

        MutableComponent formatted = Component.literal("[AE2 Colonies] ")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.empty().withStyle(ChatFormatting.YELLOW).append(message));

        for (Player player : recipients) {
            player.sendSystemMessage(formatted);
        }

        AE2Colonies.LOGGER.info("[Notification] Sent to {} player(s): {}", recipients.size(), formatted.getString());
    }
}
