package com.inkwell.newsletter.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for sending a targeted newsletter campaign.
 * All filter fields are optional — omitting them sends to all ACTIVE subscribers.
 */
@Data
@NoArgsConstructor
public class CampaignRequest {

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Content is required")
    private String content;

    /**
     * Optional status filter. Accepted values: ACTIVE, PENDING, UNSUBSCRIBED.
     * Defaults to ACTIVE if omitted.
     */
    private String status;

    /**
     * Optional list of preference tags to filter subscribers by.
     * A subscriber matches if their preferences contain ANY of the listed tags.
     * Example: ["TECH", "BUSINESS"]
     */
    private List<String> tags;
}
