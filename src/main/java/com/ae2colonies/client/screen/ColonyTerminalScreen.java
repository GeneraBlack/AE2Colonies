package com.ae2colonies.client.screen;

import com.ae2colonies.ae2.ColonyCraftingTracker;
import com.ae2colonies.blockentity.ColonyTerminalBlockEntity;
import com.ae2colonies.menu.ColonyTerminalMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ColonyTerminalScreen extends AbstractContainerScreen<ColonyTerminalMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("ae2colonies", "textures/gui/colony_terminal.png");

    private Button btnDeposit;
    private Button btnWithdraw;
    private Button btnAutocraft;

    public ColonyTerminalScreen(ColonyTerminalMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
    }

    @Override
    protected void init() {
        super.init();
        int startX = leftPos + 10;
        int startY = topPos + 48;

        btnDeposit = Button.builder(getDepositText(), b -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ColonyTerminalMenu.BUTTON_TOGGLE_DEPOSIT);
            }
        }).bounds(startX, startY, 156, 18).build();
        addRenderableWidget(btnDeposit);

        btnWithdraw = Button.builder(getWithdrawText(), b -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ColonyTerminalMenu.BUTTON_TOGGLE_WITHDRAW);
            }
        }).bounds(startX, startY + 22, 156, 18).build();
        addRenderableWidget(btnWithdraw);

        btnAutocraft = Button.builder(getAutocraftText(), b -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ColonyTerminalMenu.BUTTON_TOGGLE_AUTOCRAFT);
            }
        }).bounds(startX, startY + 44, 156, 18).build();
        addRenderableWidget(btnAutocraft);
    }

    private Component getDepositText() {
        return Component.translatable(
                "gui.ae2colonies.toggle_deposit",
                menu.isAllowDeposit()
                        ? Component.translatable("gui.ae2colonies.enabled").withColor(0x55FF55)
                        : Component.translatable("gui.ae2colonies.disabled").withColor(0xFF5555)
        );
    }

    private Component getWithdrawText() {
        return Component.translatable(
                "gui.ae2colonies.toggle_withdraw",
                menu.isAllowWithdraw()
                        ? Component.translatable("gui.ae2colonies.enabled").withColor(0x55FF55)
                        : Component.translatable("gui.ae2colonies.disabled").withColor(0xFF5555)
        );
    }

    private Component getAutocraftText() {
        return Component.translatable(
                "gui.ae2colonies.toggle_autocraft",
                menu.isAllowAutocraft()
                        ? Component.translatable("gui.ae2colonies.enabled").withColor(0x55FF55)
                        : Component.translatable("gui.ae2colonies.disabled").withColor(0xFF5555)
        );
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (btnDeposit != null) btnDeposit.setMessage(getDepositText());
        if (btnWithdraw != null) btnWithdraw.setMessage(getWithdrawText());
        if (btnAutocraft != null) btnAutocraft.setMessage(getAutocraftText());
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2A2A30);
        guiGraphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0xFF1E1E24);
        guiGraphics.fill(leftPos + 6, topPos + 138, leftPos + imageWidth - 6, topPos + imageHeight - 6, 0xFF141418);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 8, 0xEEEEEE, false);

        boolean online = menu.isTerminalOnline();
        Component statusText = online
                ? Component.translatable("gui.ae2colonies.status_online").withColor(0x55FF55)
                : Component.translatable("gui.ae2colonies.status_offline").withColor(0xFF5555);
        guiGraphics.drawString(this.font, statusText, 8, 20, 0xFFFFFF, false);

        if (menu.hasLinkedWarehouse() && menu.getLinkedWarehousePos() != null) {
            BlockPos wh = menu.getLinkedWarehousePos();
            guiGraphics.drawString(this.font,
                    Component.literal("Warehouse: [" + wh.getX() + ", " + wh.getY() + ", " + wh.getZ() + "]"),
                    8, 32, 0xAAAAAA, false);
        } else {
            guiGraphics.drawString(this.font,
                    Component.translatable("gui.ae2colonies.no_warehouse").withColor(0xFFAA00),
                    8, 32, 0xFFFFFF, false);
        }

        // Active craft jobs count
        int activeJobsCount = menu.getActiveCraftsCount();
        String craftInfo = "Active Crafts: " + activeJobsCount;
        guiGraphics.drawString(this.font, craftInfo, 8, 118, 0x88CCFF, false);

        guiGraphics.drawString(this.font, this.playerInventoryTitle, 8, 128, 0xAAAAAA, false);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
