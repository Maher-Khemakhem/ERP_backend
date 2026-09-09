package com.example.erp.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RegisterRequest(@NotBlank @Size(min = 3, max = 80) String username,
                                  @NotBlank @Size(max = 120) String displayName,
                                  @NotBlank @Size(min = 8, max = 200) String password) {
    }

    public record CreateUserRequest(@NotBlank @Size(min = 3, max = 80) String username,
                                    @NotBlank @Size(max = 120) String displayName,
                                    @NotBlank @Size(min = 8, max = 200) String password,
                                    @NotEmpty Set<@NotBlank String> roles) {
    }

    public record UpdateRolesRequest(@NotEmpty Set<@NotBlank String> roles) {
    }

    public record UpdateEnabledRequest(boolean enabled) {
    }

    public record SetupStatus(boolean setupRequired) {
    }

    public record UserView(UUID id, String username, String displayName,
                           Set<String> roles, boolean enabled, String createdAt) {
        static UserView from(User user) {
            return new UserView(user.getId(), user.getUsername(), user.getDisplayName(),
                    user.getRoles().stream().map(Role::name).sorted().collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)),
                    user.isEnabled(), user.getCreatedAt().toString());
        }
    }

    public record AuthResponse(String token, UserView user) {
    }

    @GetMapping("/setup-status")
    public SetupStatus setupStatus() {
        return new SetupStatus(authService.setupRequired());
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.registerFirstAdmin(request.username(), request.displayName(), request.password());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    @GetMapping("/me")
    public UserView me(Authentication authentication) {
        return UserView.from(authService.findByUsername(authentication.getName()));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserView> users() {
        return authService.listUsers().stream().map(UserView::from).toList();
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public UserView createUser(@Valid @RequestBody CreateUserRequest request) {
        return UserView.from(authService.createManagedUser(
                request.username(), request.displayName(), request.password(), AuthService.parseRoles(request.roles())));
    }

    @PatchMapping("/users/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public UserView updateRoles(@PathVariable UUID userId, @Valid @RequestBody UpdateRolesRequest request) {
        return UserView.from(authService.updateRoles(userId, AuthService.parseRoles(request.roles())));
    }

    @PatchMapping("/users/{userId}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    public UserView updateEnabled(@PathVariable UUID userId, @Valid @RequestBody UpdateEnabledRequest request) {
        return UserView.from(authService.updateEnabled(userId, request.enabled()));
    }
}
