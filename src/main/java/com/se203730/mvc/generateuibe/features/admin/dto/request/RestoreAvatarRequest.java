package com.se203730.mvc.generateuibe.features.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The URL must already belong to the current user's server-side avatar history. */
public record RestoreAvatarRequest(@NotBlank @Size(max = 2048) String avatarUrl) {}
