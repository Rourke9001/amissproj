package amiss.api.web;

import amiss.api.security.AuthResult;
import amiss.api.security.AuthService;
import amiss.api.web.dto.LoginRequest;
import amiss.api.web.dto.LoginResponse;
import amiss.api.web.dto.MeResponse;
import amiss.api.web.dto.RegisterRequest;
import amiss.api.web.dto.RegisterResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Register/login/me (KAN-36): mints and verifies the stateless HS256 JWT {@link
 * amiss.api.security.SecurityConfig} wires up. Register/login are the only {@code /api/**}
 * routes that stay {@code permitAll()} — every other route, including {@code GET
 * /api/auth/me}, requires the token minted here (KAN-37).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request.username(), request.password());
        return new RegisterResponse(request.username());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authService.login(request.username(), request.password());
        return new LoginResponse(result.accessToken(), "Bearer", result.expiresInSeconds());
    }

    /** The acceptance probe: 200 + the token's subject when authenticated, 401 problem+json otherwise. */
    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return new MeResponse(authentication.getName());
    }
}
