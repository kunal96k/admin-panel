package com.tts.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for DataTables server-side processing request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataTablesRequest {

    /**
     * Draw counter for DataTables
     */
    private Integer draw;

    /**
     * Paging first record indicator (start position in the current data set)
     */
    private Integer start;

    /**
     * Number of records that the table can display in the current draw
     */
    private Integer length;

    /**
     * Global search value
     */
    private String searchValue;

    /**
     * Column index for ordering
     */
    private Integer orderColumn;

    /**
     * Order direction (asc/desc)
     */
    private String orderDir;

    /**
     * Column name for ordering (optional, can be derived from orderColumn)
     */
    private String orderColumnName;

    public Integer getDraw() {
        return draw != null ? draw : 0;
    }

    public Integer getStart() {
        return start != null ? start : 0;
    }

    public Integer getLength() {
        return length != null && length > 0 ? length : 25;
    }

    public String getSearchValue() {
        return searchValue != null ? searchValue.trim() : "";
    }

    public Integer getOrderColumn() {
        return orderColumn != null ? orderColumn : 0;
    }

    public String getOrderDir() {
        return orderDir != null ? orderDir : "asc";
    }
}