package com.tts.sms.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class CustomLocalDateDeserializer extends JsonDeserializer<LocalDate> {

    private static final DateTimeFormatter[] FORMATTERS = {
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),   // 19-11-2025
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),   // 2025-11-19
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),   // 19/11/2025
            DateTimeFormatter.ISO_LOCAL_DATE             // ISO format
    };

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String dateStr = p.getText();

        if (dateStr == null || dateStr.trim().isEmpty()) {
            return LocalDate.now();
        }

        dateStr = dateStr.trim();

        // Try each format
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }

        // If all formats fail, return current date
        return LocalDate.now();
    }
}