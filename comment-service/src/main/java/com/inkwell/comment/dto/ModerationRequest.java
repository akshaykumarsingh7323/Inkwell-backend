package com.inkwell.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ModerationRequest {

    @NotBlank(message = "Action is required")
    @Pattern(regexp = "APPROVE|REJECT|DELETE",
             message = "Action must be one of: APPROVE, REJECT, DELETE")
    private String action;
}
