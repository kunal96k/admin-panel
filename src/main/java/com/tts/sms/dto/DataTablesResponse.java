package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for DataTables server-side processing response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataTablesResponse<T> {

    /**
     * The draw counter that this object is a response to
     */
    private Integer draw;

    /**
     * Total records, before filtering
     */
    private Integer recordsTotal;

    /**
     * Total records, after filtering
     */
    private Integer recordsFiltered;

    /**
     * The data to be displayed in the table
     */
    private List<T> data;

    /**
     * Optional error message
     */
    private String error;
}