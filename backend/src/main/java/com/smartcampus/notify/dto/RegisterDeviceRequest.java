package com.smartcampus.notify.dto;

import com.smartcampus.notify.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
        @NotBlank(message = "Token is required")
        @Size(max = 512)
        String token,

        @NotNull(message = "Platform is required (ANDROID or IOS)")
        Platform platform
) {
}
