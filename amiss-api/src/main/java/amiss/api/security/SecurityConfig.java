package amiss.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless HS256 JWT auth for the REST API (KAN-36).
 *
 * <p><b>Interim authorization (KAN-36 only):</b> {@code GET /api/auth/me} requires a valid
 * token; every other route stays {@code permitAll()} for now. KAN-37 locks the rest of
 * {@code /api/**} down (path-username == principal, CORS, tightened actuator details) — this
 * PR only proves the token mint/verify round-trip without breaking any existing endpoint.
 *
 * <p><b>Deliberately no {@code UserDetailsService} or {@code BCryptPasswordEncoder} bean:</b>
 * {@link AuthService} verifies credentials by calling {@code amiss.infrastructure.security
 * .PasswordHasher} directly against the {@code UserRepository} port — the exact rule {@code
 * LoginGUI} (the Swing client) uses, including its transparent legacy-plaintext-to-BCrypt
 * upgrade on first successful login. A {@code DaoAuthenticationProvider} +
 * {@code BCryptPasswordEncoder} pairing can't reproduce that upgrade path (a plaintext stored
 * value simply fails {@code BCryptPasswordEncoder#matches}), and standing up a parallel
 * {@code UserDetailsService} would give the Swing client and the API two different, divergent
 * credential rules. So Spring Security here supplies only the JWT issuing/verification
 * machinery; who's allowed to log in is decided once, by the core, for both front ends.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** HS256's minimum key size (256 bits) — anything shorter is rejected at startup. */
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationEntryPoint problemDetailEntryPoint)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        // Interim until KAN-37: every other route (including the rest of
                        // /api/auth/**) stays open so today's unauthenticated game clients
                        // keep working while the token machinery is proven out.
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> {
                    oauth2.jwt(Customizer.withDefaults());
                    oauth2.authenticationEntryPoint(problemDetailEntryPoint);
                })
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(problemDetailEntryPoint));
        return http.build();
    }

    /**
     * RFC 7807 401 for a missing/expired/invalid bearer token — the same {@code
     * application/problem+json} envelope shape {@code GlobalExceptionHandler} uses for every
     * other error, just emitted from the security filter chain instead of a controller advice
     * (authentication failures never reach the {@code DispatcherServlet}).
     */
    @Bean
    AuthenticationEntryPoint problemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "A valid bearer token is required for this request");
            problem.setTitle("Unauthenticated");
            problem.setType(URI.create("urn:amiss:unauthenticated"));
            objectMapper.writeValue(response.getOutputStream(), problem);
        };
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${amiss.security.jwt.secret}") String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(secret)));
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${amiss.security.jwt.secret}") String secret) {
        return NimbusJwtDecoder.withSecretKey(signingKey(secret)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static SecretKeySpec signingKey(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("amiss.security.jwt.secret must be at least " + MIN_SECRET_BYTES
                    + " bytes long (was " + bytes.length + "); set a longer AMISS_JWT_SECRET in real deployments");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
