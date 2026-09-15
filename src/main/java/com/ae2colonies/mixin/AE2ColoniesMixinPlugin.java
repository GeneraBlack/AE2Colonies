package com.ae2colonies.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class AE2ColoniesMixinPlugin implements IMixinConfigPlugin {

    private boolean isPathfindingEditionPresent;

    @Override
    public void onLoad(String mixinPackage) {
        isPathfindingEditionPresent = checkPathfindingEdition();
    }

    private static boolean checkPathfindingEdition() {
        try {
            return net.neoforged.fml.loading.LoadingModList.get().getModFileById("colonypathingedition") != null;
        } catch (Throwable t) {
            try {
                Class.forName("com.arxyt.colonypathingedition.core.ai.worker.NewEntityAIWorkDeliveryman", false, AE2ColoniesMixinPlugin.class.getClassLoader());
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("NewEntityAIWorkDeliverymanMixin")) {
            return isPathfindingEditionPresent;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
