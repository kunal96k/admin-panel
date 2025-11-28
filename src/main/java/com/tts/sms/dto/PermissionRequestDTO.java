package com.tts.sms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequestDTO {

    @NotNull(message = "Menu ID is required")
    private Long menuId;

    @NotNull(message = "Access status is required")
    private Boolean hasAccess;
}