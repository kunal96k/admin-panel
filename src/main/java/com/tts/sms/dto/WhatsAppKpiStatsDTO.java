package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object providing top-level KPI metrics for the WhatsApp Audit Dashboard.
 * Used by SUPER_ADMIN for Meta API billing reconciliation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppKpiStatsDTO {

    /**
     * Total WhatsApp API calls processed in the selected period
     */
    private long totalCalls;

    /**
     * Total successfully delivered billable messages
     */
    private long successfulCalls;

    /**
     * Total failed calls (Meta API error, network timeout, invalid mobile)
     */
    private long failedCalls;

    /**
     * Total calls blocked by Super Admin feature toggle
     */
    private long blockedCalls;

    /**
     * Count of Utility messages (e.g. Fee Reminders - crm_fee_reminder_utility)
     */
    private long utilityCount;

    /**
     * Count of Marketing messages (e.g. Course Information - crm_course_information)
     */
    private long marketingCount;

    /**
     * Count of Course Information dispatches
     */
    private long courseInfoCount;

    /**
     * Count of Fee Reminder dispatches
     */
    private long feeReminderCount;

    public long getSuccessCount() {
        return successfulCalls;
    }

    public long getFailedCount() {
        return failedCalls;
    }

    public long getBlockedCount() {
        return blockedCalls;
    }

    public double getSuccessRate() {
        if (totalCalls == 0) {
            return 100.0;
        }
        return (double) successfulCalls * 100.0 / totalCalls;
    }
}
