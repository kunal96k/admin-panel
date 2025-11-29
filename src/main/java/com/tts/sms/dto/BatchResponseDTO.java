package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchResponseDTO {

    private Long id;
    private String batchNo;
    private String batchName;
    private Integer batchSize;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String timing;
    private Boolean isSunday;
    private Boolean isMonday;
    private Boolean isTuesday;
    private Boolean isWednesday;
    private Boolean isThursday;
    private Boolean isFriday;
    private Boolean isSaturday;
    private String courseName;
    private Long courseId;
    private String status;
    private Boolean isActive;
    private String createdBy;
    private String updatedBy;

    // Helper method to get formatted timing
    public String getTiming() {
        if (startTime != null && endTime != null) {
            return formatTime(startTime) + " To " + formatTime(endTime);
        }
        return "12:00 AM To 12:00 AM";
    }

    private String formatTime(LocalTime time) {
        int hour = time.getHour();
        int minute = time.getMinute();
        String ampm = hour >= 12 ? "PM" : "AM";
        int displayHour = hour == 0 ? 12 : (hour > 12 ? hour - 12 : hour);
        return String.format("%d:%02d %s", displayHour, minute, ampm);
    }
}
