package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceBatchSyncRequestDTO {
    private LocalDate fromDate;
    private LocalDate toDate;
    private Integer totalRecords;
    private List<AttendanceStudentSyncDTO> students;
}
