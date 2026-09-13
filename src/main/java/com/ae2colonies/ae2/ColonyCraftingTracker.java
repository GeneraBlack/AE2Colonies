package com.ae2colonies.ae2;

import appeng.api.networking.crafting.ICraftingLink;
import appeng.crafting.CraftingLink;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ColonyCraftingTracker {

    public static class CraftingJobInfo {
        private final ICraftingLink link;
        private final String tokenString;
        private final ItemStack stack;
        private final long amountRequested;
        private long amountCrafted;
        private final String requesterName;
        private final long startTime;
        private boolean finished;
        private boolean canceled;

        public CraftingJobInfo(
                ICraftingLink link,
                String tokenString,
                ItemStack stack,
                long amountRequested,
                String requesterName
        ) {
            this.link = link;
            this.tokenString = tokenString;
            this.stack = stack;
            this.amountRequested = amountRequested;
            this.amountCrafted = 0;
            this.requesterName = requesterName;
            this.startTime = System.currentTimeMillis();
            this.finished = false;
            this.canceled = false;
        }

        public ICraftingLink getLink() {
            return link;
        }

        public String getTokenString() {
            return tokenString;
        }

        public ItemStack getStack() {
            return stack;
        }

        public long getAmountRequested() {
            return amountRequested;
        }

        public long getAmountCrafted() {
            return amountCrafted;
        }

        public void addCrafted(long amount) {
            this.amountCrafted += amount;
        }

        public String getRequesterName() {
            return requesterName;
        }

        public long getStartTime() {
            return startTime;
        }

        public boolean isFinished() {
            return finished || (link != null && link.isDone());
        }

        public void setFinished(boolean finished) {
            this.finished = finished;
        }

        public boolean isCanceled() {
            return canceled || (link != null && link.isCanceled());
        }

        public void setCanceled(boolean canceled) {
            this.canceled = canceled;
        }

        public CompoundTag writeToNBT(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            if (link != null) {
                CompoundTag linkTag = new CompoundTag();
                link.writeToNBT(linkTag);
                tag.put("Link", linkTag);
            }
            tag.putString("Token", tokenString);
            tag.put("Stack", stack.save(provider));
            tag.putLong("Requested", amountRequested);
            tag.putLong("Crafted", amountCrafted);
            tag.putString("Requester", requesterName);
            tag.putLong("StartTime", startTime);
            tag.putBoolean("Finished", finished);
            tag.putBoolean("Canceled", canceled);
            return tag;
        }

        public static CraftingJobInfo readFromNBT(CompoundTag tag, HolderLookup.Provider provider, appeng.api.networking.crafting.ICraftingRequester requester) {
            ICraftingLink link = null;
            if (tag.contains("Link", Tag.TAG_COMPOUND)) {
                link = new CraftingLink(tag.getCompound("Link"), requester);
            }
            String token = tag.getString("Token");
            ItemStack stack = ItemStack.parseOptional(provider, tag.getCompound("Stack"));
            long requested = tag.getLong("Requested");
            long crafted = tag.getLong("Crafted");
            String reqName = tag.getString("Requester");
            CraftingJobInfo info = new CraftingJobInfo(link, token, stack, requested, reqName);
            info.amountCrafted = crafted;
            info.finished = tag.getBoolean("Finished");
            info.canceled = tag.getBoolean("Canceled");
            return info;
        }
    }

    private final Map<UUID, CraftingJobInfo> jobsByLinkId = new ConcurrentHashMap<>();
    private final Map<String, CraftingJobInfo> jobsByToken = new ConcurrentHashMap<>();

    public void trackJob(
            @NotNull ICraftingLink link,
            @NotNull String tokenString,
            @NotNull ItemStack stack,
            long amount,
            @NotNull String requesterName
    ) {
        CraftingJobInfo info = new CraftingJobInfo(link, tokenString, stack, amount, requesterName);
        jobsByLinkId.put(link.getCraftingID(), info);
        jobsByToken.put(tokenString, info);
    }

    @Nullable
    public CraftingJobInfo getJobByLink(@Nullable ICraftingLink link) {
        if (link == null) {
            return null;
        }
        return jobsByLinkId.get(link.getCraftingID());
    }

    @Nullable
    public CraftingJobInfo getJobByToken(@NotNull String tokenString) {
        return jobsByToken.get(tokenString);
    }

    public void onCrafted(@NotNull ICraftingLink link, long amount) {
        CraftingJobInfo info = getJobByLink(link);
        if (info != null) {
            info.addCrafted(amount);
        }
    }

    public void onJobComplete(@NotNull ICraftingLink link) {
        CraftingJobInfo info = getJobByLink(link);
        if (info != null) {
            info.setFinished(true);
        }
    }

    public void onJobCanceled(@NotNull ICraftingLink link) {
        CraftingJobInfo info = getJobByLink(link);
        if (info != null) {
            info.setCanceled(true);
        }
    }

    public void removeJob(@NotNull ICraftingLink link) {
        CraftingJobInfo info = jobsByLinkId.remove(link.getCraftingID());
        if (info != null) {
            jobsByToken.remove(info.getTokenString());
        }
    }

    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        ImmutableSet.Builder<ICraftingLink> builder = ImmutableSet.builder();
        for (CraftingJobInfo info : jobsByLinkId.values()) {
            if (!info.isFinished() && !info.isCanceled() && info.getLink() != null) {
                builder.add(info.getLink());
            }
        }
        return builder.build();
    }

    public List<CraftingJobInfo> getActiveJobs() {
        List<CraftingJobInfo> list = new ArrayList<>();
        for (CraftingJobInfo info : jobsByLinkId.values()) {
            if (!info.isFinished() && !info.isCanceled()) {
                list.add(info);
            }
        }
        return Collections.unmodifiableList(list);
    }

    public void writeToNBT(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (CraftingJobInfo info : jobsByLinkId.values()) {
            list.add(info.writeToNBT(provider));
        }
        tag.put("ColonyCraftingJobs", list);
    }

    public void readFromNBT(CompoundTag tag, HolderLookup.Provider provider, appeng.api.networking.crafting.ICraftingRequester requester) {
        jobsByLinkId.clear();
        jobsByToken.clear();
        if (tag.contains("ColonyCraftingJobs", Tag.TAG_LIST)) {
            ListTag list = tag.getList("ColonyCraftingJobs", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CraftingJobInfo info = CraftingJobInfo.readFromNBT(list.getCompound(i), provider, requester);
                if (info.getLink() != null) {
                    jobsByLinkId.put(info.getLink().getCraftingID(), info);
                }
                jobsByToken.put(info.getTokenString(), info);
            }
        }
    }
}
