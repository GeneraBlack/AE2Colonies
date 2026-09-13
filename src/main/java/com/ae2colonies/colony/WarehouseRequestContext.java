package com.ae2colonies.colony;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import org.jetbrains.annotations.Nullable;

public class WarehouseRequestContext {

    private static final ThreadLocal<IRequest<? extends IDeliverable>> CURRENT_REQUEST = new ThreadLocal<>();

    @Nullable
    public static IRequest<? extends IDeliverable> getCurrentRequest() {
        return CURRENT_REQUEST.get();
    }

    public static void setCurrentRequest(@Nullable IRequest<? extends IDeliverable> request) {
        if (request == null) {
            CURRENT_REQUEST.remove();
        } else {
            CURRENT_REQUEST.set(request);
        }
    }

    public static void clear() {
        CURRENT_REQUEST.remove();
    }
}
