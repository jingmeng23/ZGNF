package com.gas.ecommerce.modules.user.dto;
import jakarta.validation.constraints.*;
public record FeedbackRequest(@NotBlank @Size(max=2000) String content,@Size(max=320) String contact) {}
