package com.ae2colonies.compat;

import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.ae2colonies.blockentity.MEArchitectsCutterBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * Tooltip provider for AE2Colonies blocks.
 * Adds status info to block item tooltips and integrates with Jade/WTHIT if available.
 */
public class TooltipProvider {

    public static void onItemTooltip(ItemTooltipEvent event) {
        // Item tooltips are handled here - block overlay tooltips need Jade/WTHIT
    }
    
    /**
     * Build tooltip components for a Colony Terminal block entity.
     * Used by Jade integration and debug screen.
     */
    public static List<Component> getTerminalTooltip(ColonyTerminalBlockEntity terminal) {
        List<Component> tooltip = new java.util.ArrayList<>();
        
        boolean online = terminal.isTerminalOnline();
        tooltip.add(online 
            ? Component.literal("● Online").withStyle(ChatFormatting.GREEN)
            : Component.literal("● Offline").withStyle(ChatFormatting.RED));
        
        BlockPos whPos = terminal.getLinkedWarehousePos();
        if (whPos != null) {
            tooltip.add(Component.literal("Warehouse: [" + whPos.getX() + ", " + whPos.getY() + ", " + whPos.getZ() + "]")
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Colony #" + terminal.getLinkedColonyId())
                .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("No warehouse linked").withStyle(ChatFormatting.RED));
        }
        
        int jobs = terminal.getCraftingTracker().getActiveJobs().size();
        if (jobs > 0) {
            tooltip.add(Component.literal("Active crafts: " + jobs).withStyle(ChatFormatting.AQUA));
        }
        
        tooltip.add(Component.literal("Priority: " + terminal.getPriority()).withStyle(ChatFormatting.LIGHT_PURPLE));
        
        // Feature toggles
        tooltip.add(Component.literal(
            (terminal.isAllowDeposit() ? "✓" : "✗") + " Deposit  " +
            (terminal.isAllowWithdraw() ? "✓" : "✗") + " Withdraw  " +
            (terminal.isAllowAutocraft() ? "✓" : "✗") + " Autocraft"
        ).withStyle(ChatFormatting.DARK_GRAY));
        
        return tooltip;
    }
}
