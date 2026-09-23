package com.example.erp.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final long jwtExpirationSeconds;
    private final String jwtIssuer;
    private final String jwtAudience;

    AuthService(UserRepository userRepository,
                PasswordEncoder passwordEncoder,
                JwtEncoder jwtEncoder,
                @Value("${app.security.jwt-expiration-seconds:900}") long jwtExpirationSeconds,
                @Value("${app.security.jwt-issuer}") String jwtIssuer,
                @Value("${app.security.jwt-audience}") String jwtAudience) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtExpirationSeconds = jwtExpirationSeconds;
        this.jwtIssuer = jwtIssuer;
        this.jwtAudience = jwtAudience;
    }

    @Transactional(readOnly = true)
    public boolean setupRequired() {
        return userRepository.count() == 0;
    }

    @Transactional
    public AuthController.AuthResponse registerFirstAdmin(String username, String displayName, String password) {
        if (!setupRequired()) {
            throw new IllegalStateException("The initial administrator has already been created");
        }
        User user = createUser(username, displayName, password, Set.of(Role.ADMIN));
        return issueToken(user, Set.of(Role.ADMIN));
    }

    @Transactional(readOnly = true)
    public AuthController.AuthResponse login(String username, String password) {
        User user = userRepository.findByUsernameIgnoreCase(username == null ? "" : username.trim())
                .orElseThrow(InvalidCredentialsException::new);
        if (!user.isEnabled() || !passwordEncoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return issueToken(user, user.getRoles());
    }

    @Transactional
    public AuthController.AuthResponse registerCustomer(String username, String displayName, String password) {
        User user = createUser(username, displayName, password, Set.of(Role.CUSTOMER));
        return issueToken(user, Set.of(Role.CUSTOMER));
    }

    @Transactional(readOnly = true)
    public AuthController.AuthResponse loginCustomer(String username, String password) {
        User user = userRepository.findByUsernameIgnoreCase(username == null ? "" : username.trim())
                .orElseThrow(InvalidCredentialsException::new);
        if (!user.isEnabled()
                || !user.getRoles().contains(Role.CUSTOMER)
                || !passwordEncoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return issueToken(user, user.getRoles());
    }

    @Transactional(readOnly = true)
    public UUID customerIdForUsername(String username) {
        User user = findByUsername(username);
        if (!user.isEnabled() || !user.getRoles().contains(Role.CUSTOMER)) {
            throw new org.springframework.security.access.AccessDeniedException("Customer account required");
        }
        return user.getId();
    }

    @Transactional
    public User createManagedUser(String username, String displayName, String password, Set<Role> roles) {
        return createUser(username, displayName, password, roles);
    }

    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return userRepository.findAllByOrderByUsernameAsc();
    }

    @Transactional
    public User updateRoles(UUID userId, Set<Role> roles) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.replaceRoles(roles);
        return userRepository.save(user);
    }

    @Transactional
    public User updateEnabled(UUID userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setEnabled(enabled);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private User createUser(String username, String displayName, String password, Set<Role> roles) {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase();
        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new IllegalArgumentException("Username is already in use");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters");
        }
        User user = User.create(normalizedUsername, displayName, passwordEncoder.encode(password), roles);
        return userRepository.saveAndFlush(user);
    }

    private AuthController.AuthResponse issueToken(User user, Set<Role> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtIssuer)
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtExpirationSeconds))
                .claim("aud", List.of(jwtAudience))
                .claim("jti", UUID.randomUUID().toString())
                .claim("userId", user.getId().toString())
                .claim("roles", roles.stream().map(Role::name).sorted().toList())
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthController.AuthResponse(token, AuthController.UserView.from(user));
    }

    public static Set<Role> parseRoles(Set<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }
        Set<Role> roles = new LinkedHashSet<>();
        for (String roleName : roleNames) {
            try {
                roles.add(Role.valueOf(roleName.trim().toUpperCase()));
            } catch (Exception ex) {
                throw new IllegalArgumentException("Unknown role: " + roleName);
            }
        }
        return roles;
    }
}
