package com.ae2colonies.client.screen;

import com.ae2colonies.menu.MEArchitectsCutterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class MEArchitectsCutterScreen extends AbstractContainerScreen<MEArchitectsCutterMenu> {

    public MEArchitectsCutterScreen(MEArchitectsCutterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // Main panel background
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2A2A30);
        guiGraphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFF1E1E24);

        // Player inventory background panel
        guiGraphics.fill(leftPos + 6, topPos + 82, leftPos + imageWidth - 6, topPos + imageHeight - 6, 0xFF141418);

        // Slot boxes
        // Template slot (x=19, y=34, size 18x18)
        guiGraphics.fill(leftPos + 19, topPos + 34, leftPos + 37, topPos + 52, 0xFF162536);
        guiGraphics.renderOutline(leftPos + 19, topPos + 34, 18, 18, 0xFF3888D8);

        // Material input slots (4 slots in 2x2)
        guiGraphics.fill(leftPos + 55, topPos + 25, leftPos + 73, topPos + 43, 0xFF141418);
        guiGraphics.renderOutline(leftPos + 55, topPos + 25, 18, 18, 0xFF444450);

        guiGraphics.fill(leftPos + 73, topPos + 25, leftPos + 91, topPos + 43, 0xFF141418);
        guiGraphics.renderOutline(leftPos + 73, topPos + 25, 18, 18, 0xFF444450);

        guiGraphics.fill(leftPos + 55, topPos + 43, leftPos + 73, topPos + 61, 0xFF141418);
        guiGraphics.renderOutline(leftPos + 55, topPos + 43, 18, 18, 0xFF444450);

        guiGraphics.fill(leftPos + 73, topPos + 43, leftPos + 91, topPos + 61, 0xFF141418);
        guiGraphics.renderOutline(leftPos + 73, topPos + 43, 18, 18, 0xFF444450);

        // Progress bar (between inputs and output: x=96, y=38, width=24, height=10)
        int progressX = leftPos + 96;
        int progressY = topPos + 39;
        int progressW = 24;
        int progressH = 10;
        guiGraphics.fill(progressX, progressY, progressX + progressW, progressY + progressH, 0xFF111115);
        guiGraphics.renderOutline(progressX, progressY, progressW, progressH, 0xFF555566);

        int progress = menu.getProgress();
        int maxProgress = Math.max(1, menu.getMaxProgress());
        if (progress > 0) {
            int filledWidth = (int) (((float) progress / maxProgress) * (progressW - 2));
            guiGraphics.fill(progressX + 1, progressY + 1, progressX + 1 + filledWidth, progressY + progressH - 1, 0xFF44AAFF);
        }

        // Output slot (x=125, y=34, size 20x20)
        guiGraphics.fill(leftPos + 125, topPos + 34, leftPos + 145, topPos + 54, 0xFF182218);
        guiGraphics.renderOutline(leftPos + 125, topPos + 34, 20, 20, 0xFF55AA55);

        // Player inventory slot outlines (3x9)
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                guiGraphics.renderOutline(leftPos + 7 + c * 18, topPos + 83 + r * 18, 18, 18, 0xFF2D2D36);
            }
        }
        // Player hotbar slot outlines (1x9)
        for (int c = 0; c < 9; c++) {
            guiGraphics.renderOutline(leftPos + 7 + c * 18, topPos + 141, 18, 18, 0xFF2D2D36);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 8, 0xEEEEEE, false);

        boolean online = menu.isMachineOnline();
        Component statusText = online
                ? Component.translatable("gui.ae2colonies.status_online").withColor(0x55FF55)
                : Component.translatable("gui.ae2colonies.status_offline").withColor(0xFF5555);
        guiGraphics.drawString(this.font, statusText, 8, 20, 0xFFFFFF, false);

        // Slot small hints
        guiGraphics.drawString(this.font, Component.literal("Pattern"), 16, 56, 0x7799CC, false);
        guiGraphics.drawString(this.font, Component.literal("Input"), 62, 14, 0x888888, false);
        guiGraphics.drawString(this.font, Component.literal("Out"), 128, 22, 0x77AA77, false);

        guiGraphics.drawString(this.font, this.playerInventoryTitle, 8, 72, 0xAAAAAA, false);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}