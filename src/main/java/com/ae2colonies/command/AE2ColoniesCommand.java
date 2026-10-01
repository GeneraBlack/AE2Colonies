package com.ae2colonies.command;

import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.ae2colonies.colony.WarehouseMEBridge;
import com.ae2colonies.ae2.ColonyCraftingTracker;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public class AE2ColoniesCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ae2colonies")
                .requires(cs -> cs.hasPermission(2))
                .then(Commands.literal("status")
                    .executes(AE2ColoniesCommand::showStatus)
                )
        );
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<ColonyTerminalBlockEntity> allTerminals = WarehouseMEBridge.getAllTerminals();
        
        if (allTerminals.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[AE2Colonies] No Colony Terminals registered.").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        
        source.sendSuccess(() -> Component.literal("=== AE2Colonies Status ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Registered terminals: " + allTerminals.size()).withStyle(ChatFormatting.AQUA), false);
        
        for (ColonyTerminalBlockEntity terminal : allTerminals) {
            BlockPos pos = terminal.getBlockPos();
            boolean online = terminal.isTerminalOnline();
            BlockPos whPos = terminal.getLinkedWarehousePos();
            int colonyId = terminal.getLinkedColonyId();
            int activeJobs = terminal.getCraftingTracker().getActiveJobs().size();
            int priority = terminal.getPriority();
            
            MutableComponent msg = Component.literal("  Terminal [")
                .append(Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.WHITE))
                .append("] ");
            
            if (online) {
                msg.append(Component.literal("ONLINE").withStyle(ChatFormatting.GREEN));
            } else {
                msg.append(Component.literal("OFFLINE").withStyle(ChatFormatting.RED));
            }
            
            if (whPos != null) {
                msg.append(Component.literal(" → WH[").withStyle(ChatFormatting.GRAY))
                   .append(Component.literal(whPos.getX() + "," + whPos.getY() + "," + whPos.getZ()).withStyle(ChatFormatting.WHITE))
                   .append(Component.literal("] Colony #" + colonyId).withStyle(ChatFormatting.GRAY));
            } else {
                msg.append(Component.literal(" (no warehouse)").withStyle(ChatFormatting.RED));
            }
            
            msg.append(Component.literal(" P:" + priority).withStyle(ChatFormatting.LIGHT_PURPLE));
            
            if (activeJobs > 0) {
                msg.append(Component.literal(" Jobs:" + activeJobs).withStyle(ChatFormatting.YELLOW));
            }
            
            final MutableComponent finalMsg = msg;
            source.sendSuccess(() -> finalMsg, false);
        }
        
        return allTerminals.size();
    }
}
