package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchSearchDTO {
    private String searchTerm;
    private String status;
    private Long courseId;
    private Integer page = 0;
    private Integer size = 25;
}
