package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuResponseDTO {

    private Long id;
    private String mainMenu;
    private String submenu;
    private String menuUrl;
    private Integer menuOrder;
    private Boolean hasAccess;
}