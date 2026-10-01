package com.ae2colonies.colony;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import org.jetbrains.annotations.Nullable;

public class WarehouseRequestContext {

    private static final ThreadLocal<RequestEntry> CURRENT_REQUEST = new ThreadLocal<>();

    private static class RequestEntry {
        final IRequest<? extends IDeliverable> request;
        final long timestamp;

        RequestEntry(IRequest<? extends IDeliverable> request) {
            this.request = request;
            this.timestamp = System.currentTimeMillis();
        }
    }

    @Nullable
    public static IRequest<? extends IDeliverable> getCurrentRequest() {
        RequestEntry entry = CURRENT_REQUEST.get();
        if (entry == null) return null;
        // Auto-expire after 5 seconds to prevent ThreadLocal leaks
        if (System.currentTimeMillis() - entry.timestamp > 5000) {
            CURRENT_REQUEST.remove();
            return null;
        }
        return entry.request;
    }

    public static void setCurrentRequest(@Nullable IRequest<? extends IDeliverable> request) {
        if (request == null) {
            CURRENT_REQUEST.remove();
        } else {
            CURRENT_REQUEST.set(new RequestEntry(request));
        }
    }

    public static void clear() {
        CURRENT_REQUEST.remove();
    }
}
