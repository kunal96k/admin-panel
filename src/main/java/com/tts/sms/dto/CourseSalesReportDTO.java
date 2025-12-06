package com.tts.sms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSalesReportDTO {

    @JsonProperty("RegistrationNo")
    private String registrationNo;

    @JsonProperty("StudentName")
    private String studentName;

    @JsonProperty("StudentMobileNo")
    private String studentMobileNo;

    @JsonProperty("CreatedDate")
    private String createdDate;

    @JsonProperty("CourseAmount")
    private String courseAmount;

    @JsonProperty("TotalCourseAmount")
    private String totalCourseAmount;

    @JsonProperty("CompareDate")
    private LocalDate compareDate;
}