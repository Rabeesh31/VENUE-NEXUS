package com.venuenexus.venue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDepartmentRequest(
        @NotBlank(message = "Department name is required")
        @Size(max = 255, message = "Department name must not exceed 255 characters")
        String name,

        @NotBlank(message = "Department type is required")
        @Size(max = 100, message = "Department type must not exceed 100 characters")
        String departmentType
) {
}

