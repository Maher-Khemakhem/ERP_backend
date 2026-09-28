package com.example.erp.auth;

import com.example.erp.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUpApi() {
        AuthService authService = mock(AuthService.class);
        when(authService.login(anyString(), anyString())).thenThrow(new InvalidCredentialsException());

        LoginRateLimiter limiter = new LoginRateLimiter(100, 60, 3, 60, 100);
        mockMvc = standaloneSetup(new AuthController(authService, limiter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void limitsByIpAndUsername() {
        LoginRateLimiter limiter = new LoginRateLimiter(2, 60, 10, 60, 100);

        limiter.check("203.0.113.10", "cashier");
        limiter.check("203.0.113.10", "cashier");

        assertThatThrownBy(() -> limiter.check("203.0.113.10", "cashier"))
                .isInstanceOf(LoginRateLimitExceededException.class)
                .satisfies(error -> assertThat(((LoginRateLimitExceededException) error).getRetryAfterSeconds())
                        .isPositive());
    }

    @Test
    void normalizesUsernameBeforeApplyingUsernameLimit() {
        LoginRateLimiter limiter = new LoginRateLimiter(10, 60, 1, 60, 100);

        limiter.check("203.0.113.10", " Cashier ");

        assertThatThrownBy(() -> limiter.check("203.0.113.11", "cashier"))
                .isInstanceOf(LoginRateLimitExceededException.class);
    }

    @Test
    void differentIpCanUseItsOwnIpBucket() {
        LoginRateLimiter limiter = new LoginRateLimiter(1, 60, 10, 60, 100);

        limiter.check("203.0.113.10", "cashier");
        limiter.check("203.0.113.11", "cashier");

        assertThatThrownBy(() -> limiter.check("203.0.113.10", "manager"))
                .isInstanceOf(LoginRateLimitExceededException.class);
    }

    @Test
    void apiPrintsEveryLoginResponseAndReturns429AfterUsernameLimit() throws Exception {
        String requestBody = "{\"username\":\"rate-limit-test\",\"password\":\"wrong-password\"}";

        for (int attempt = 1; attempt <= 5; attempt++) {
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .with(request -> {
                                request.setRemoteAddr("127.0.0.1");
                                request.addHeader("X-Forwarded-For", "203.0.113.10");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andReturn();

            int status = result.getResponse().getStatus();
            String retryAfter = result.getResponse().getHeader("Retry-After");
            String responseBody = result.getResponse().getContentAsString();

            System.out.printf(
                    "login attempt %d -> HTTP %d, Retry-After=%s, body=%s%n",
                    attempt, status, retryAfter, responseBody);

            if (attempt <= 3) {
                assertThat(status).isEqualTo(401);
                assertThat(responseBody).contains("Invalid username or password");
            } else {
                assertThat(status).isEqualTo(429);
                assertThat(retryAfter).isNotBlank();
                assertThat(responseBody).contains("Too many login attempts");
            }
        }
    }
}
