package com.lexhive.lending.config;

import com.lexhive.lending.security.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Predefined API users from {@code app.security.users}. Passwords are encoded, e.g. {@code {bcrypt}$2a$10$...}. */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityUsersProperties(@NotEmpty List<@Valid User> users) {

    public record User(
            @NotBlank String username,
            @NotBlank String password,
            @NotEmpty Set<Role> roles) {
    }
}
