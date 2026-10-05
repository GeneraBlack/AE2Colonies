package com.ae2colonies.subrequest;

/**
 * Lifecycle states of a delegated crafting job.
 */
public enum DelegatedJobState {
    /**
     * Initial state while checking recipe ingredients and planning.
     */
    ANALYZING,

    /**
     * Waiting for colony workers to produce and deliver missing ingredients.
     */
    WAITING_FOR_COLONY,

    /**
     * All required ingredients have arrived in ME storage, ready for AE2 job submission.
     */
    INGREDIENTS_ARRIVED,

    /**
     * AE2 autocrafting job has been submitted and is actively running.
     */
    AE2_CRAFTING,

    /**
     * The final target item has been successfully crafted.
     */
    COMPLETED,

    /**
     * The job timed out or was cancelled by the user/colony.
     */
    CANCELLED,

    /**
     * The job failed during AE2 submission or execution.
     */
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }
}
