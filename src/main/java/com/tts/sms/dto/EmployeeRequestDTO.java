// EmployeeRequestDTO.java
package com.tts.sms.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeRequestDTO {

    @NotBlank(message = "Employee name is required")
    @Size(max = 50)
    private String employeeName;

    @NotBlank(message = "Mobile number is required")
    private String mobileNumber;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 50)
    private String emailId;

    @Size(max = 20)
    private String designation;

    @NotBlank(message = "Gender is required")
    private String gender;

    @NotNull(message = "Date of birth is required")
    private LocalDate dateOfBirth;

    @Size(max = 100)
    private String address;

    @NotNull(message = "Role is required")
    private Long roleId;

    @Size(max = 200)
    private String zoomLink;

    // User credentials - only when creating login
    @Size(max = 50)
    private String username;

    @Size(max = 50)
    private String password;

    @Size(max = 50)
    private String confirmPassword;

    // Photo as base64 or file path
    private String photo;

    // Menu permissions
    private List<MenuPermissionDTO> menuPermissions;

    // Mobile App Permissions
    private Boolean newAdmission;
    private Boolean viewAdmission;
    private Boolean accDashboard;
    private Boolean counsellorDash;
    private Boolean todaysFollowup;
    private Boolean studyNote;
    private Boolean feeManager;
    private Boolean paymentLink;
    private Boolean timeTable;
    private Boolean timeTableAttendance;
    private Boolean overdueFollowup;
    private Boolean newEnquiry;
    private Boolean viewEnquiry;
    private Boolean batchWiseFee;
    private Boolean sendAppMsg;
    private Boolean manageCourse;
    private Boolean manageBatch;
    private Boolean shareVideo;
    private Boolean liveLecture;
    private Boolean offlineExam;
}