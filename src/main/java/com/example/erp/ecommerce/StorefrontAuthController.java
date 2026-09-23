package com.example.erp.ecommerce;

import com.example.erp.auth.AuthController;
import com.example.erp.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/store/auth")
public class StorefrontAuthController {

    private final AuthService authService;
    private final com.example.erp.auth.LoginRateLimiter loginRateLimiter;

    StorefrontAuthController(AuthService authService,
                             com.example.erp.auth.LoginRateLimiter loginRateLimiter) {
        this.authService = authService;
        this.loginRateLimiter = loginRateLimiter;
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 80) String username,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(min = 8, max = 200) String password) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    @PostMapping("/register")
    public AuthController.AuthResponse register(@Valid @RequestBody RegisterRequest request,
                                                HttpServletRequest httpRequest) {
        loginRateLimiter.check(clientIp(httpRequest), request.username());
        return authService.registerCustomer(request.username(), request.displayName(), request.password());
    }

    @PostMapping("/login")
    public AuthController.AuthResponse login(@Valid @RequestBody LoginRequest request,
                                             HttpServletRequest httpRequest) {
        loginRateLimiter.check(clientIp(httpRequest), request.username());
        return authService.loginCustomer(request.username(), request.password());
    }

    private static String clientIp(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if ("127.0.0.1".equals(remoteAddress)
                || "0:0:0:0:0:0:0:1".equals(remoteAddress)
                || "::1".equals(remoteAddress)) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String[] addresses = forwardedFor.split(",");
                String clientAddress = addresses[addresses.length - 1].trim();
                if (!clientAddress.isBlank()) {
                    return clientAddress;
                }
            }
        }
        return remoteAddress;
    }
}
