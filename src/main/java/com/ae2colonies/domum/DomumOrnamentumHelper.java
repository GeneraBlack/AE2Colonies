package com.ae2colonies.domum;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.recipe.ModRecipeTypes;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipe;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DomumOrnamentumHelper {

    public static class DOMaterialCost {
        private final List<ItemStack> ingredients;
        private final int yield;

        public DOMaterialCost(List<ItemStack> ingredients, int yield) {
            this.ingredients = ingredients;
            this.yield = yield;
        }

        public List<ItemStack> getIngredients() {
            return ingredients;
        }

        public int getYield() {
            return yield;
        }
    }

    /**
     * Checks whether an ItemStack represents a Domum Ornamentum materially textured block.
     */
    public static boolean isDOBlock(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock() instanceof IMateriallyTexturedBlock;
        }
        return false;
    }

    /**
     * Returns the IMateriallyTexturedBlock if the stack represents one, or null otherwise.
     */
    @Nullable
    public static IMateriallyTexturedBlock getDOBlock(@Nullable ItemStack stack) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
            if (blockItem.getBlock() instanceof IMateriallyTexturedBlock mtb) {
                return mtb;
            }
        }
        return null;
    }

    /**
     * Computes the raw ingredients required to craft 1 batch of this DO block, as well as the output yield per batch.
     */
    @Nullable
    public static DOMaterialCost getMaterialCost(@Nullable Level level, @NotNull ItemStack stack) {
        IMateriallyTexturedBlock mtb = getDOBlock(stack);
        if (mtb == null) {
            return null;
        }

        BlockItem blockItem = (BlockItem) stack.getItem();
        MaterialTextureData data = MaterialTextureData.readFromItemStack(stack);
        Map<ResourceLocation, Block> textured = data.getTexturedComponents();

        List<ItemStack> ingredients = new ArrayList<>();
        Collection<IMateriallyTexturedBlockComponent> components = mtb.getComponents();

        for (IMateriallyTexturedBlockComponent comp : components) {
            Block block = textured.get(comp.getId());
            if (block == null && !comp.isOptional()) {
                block = comp.getDefault();
            }
            if (block != null && block != Blocks.AIR) {
                Item item = block.asItem();
                if (item != Items.AIR) {
                    ingredients.add(new ItemStack(item, 1));
                }
            }
        }

        if (ingredients.isEmpty()) {
            return null;
        }

        int yield = 1;
        if (level != null) {
            Collection<RecipeHolder<ArchitectsCutterRecipe>> recipes =
                    level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.ARCHITECTS_CUTTER.get());
            for (RecipeHolder<ArchitectsCutterRecipe> holder : recipes) {
                if (holder.value().getBlock() == blockItem.getBlock()) {
                    yield = Math.max(1, holder.value().getCount());
                    break;
                }
            }
        }

        return new DOMaterialCost(ingredients, yield);
    }

    /**
     * Crafts a result ItemStack given a template DO block and the input ingredient items in slot order.
     */
    @NotNull
    public static ItemStack craftDOBlock(
            @Nullable Level level,
            @NotNull ItemStack template,
            @NotNull List<ItemStack> inputItems
    ) {
        IMateriallyTexturedBlock mtb = getDOBlock(template);
        if (mtb == null) {
            return ItemStack.EMPTY;
        }

        BlockItem blockItem = (BlockItem) template.getItem();
        List<IMateriallyTexturedBlockComponent> components = new ArrayList<>(mtb.getComponents());

        MaterialTextureData.Builder builder = MaterialTextureData.builder();
        int matchedCount = 0;

        for (int i = 0; i < components.size(); i++) {
            IMateriallyTexturedBlockComponent comp = components.get(i);
            ItemStack inStack = (i < inputItems.size()) ? inputItems.get(i) : ItemStack.EMPTY;

            if (!inStack.isEmpty() && inStack.getItem() instanceof BlockItem inBi) {
                Block b = inBi.getBlock();
                builder.setComponent(comp.getId(), b);
                matchedCount++;
            } else if (!comp.isOptional()) {
                MaterialTextureData templateData = MaterialTextureData.readFromItemStack(template);
                Block templateBlock = templateData.getTexturedComponents().get(comp.getId());
                if (templateBlock != null) {
                    builder.setComponent(comp.getId(), templateBlock);
                } else {
                    builder.setComponent(comp.getId(), comp.getDefault());
                }
            }
        }

        if (matchedCount == 0 && !inputItems.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int yield = 1;
        DataComponentPatch patch = DataComponentPatch.EMPTY;

        if (level != null) {
            Collection<RecipeHolder<ArchitectsCutterRecipe>> recipes =
                    level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.ARCHITECTS_CUTTER.get());
            for (RecipeHolder<ArchitectsCutterRecipe> holder : recipes) {
                if (holder.value().getBlock() == blockItem.getBlock()) {
                    yield = Math.max(1, holder.value().getCount());
                    patch = holder.value().getComponentPatch();
                    break;
                }
            }
        }

        ItemStack result = new ItemStack(blockItem.getBlock(), yield);
        builder.writeToItemStack(result);
        if (patch != null && !patch.isEmpty()) {
            result.applyComponents(patch);
        }

        return result;
    }

    /**
     * Synthesizes an exact copy of the requested DO block with the specified count, preserving all texture data.
     */
    @NotNull
    public static ItemStack synthesizeDOBlock(@NotNull ItemStack requestedTemplate, int count) {
        if (!isDOBlock(requestedTemplate)) {
            return ItemStack.EMPTY;
        }
        ItemStack result = requestedTemplate.copy();
        result.setCount(count);
        return result;
    }
}