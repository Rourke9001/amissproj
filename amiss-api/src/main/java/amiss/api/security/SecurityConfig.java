package amiss.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
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
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless HS256 JWT security for the REST API (KAN-36 issues/verifies the token; KAN-37
 * gates every route with it).
 *
 * <p><b>Routes:</b> register, login, {@code GET /api/highscores}, actuator health and
 * {@code /error} are public; everything else requires a bearer token. {@code OPTIONS} is also
 * {@code permitAll()} — a CORS preflight carries no {@code Authorization} header, so it would
 * otherwise 401 before the browser sends the real request (the documented Spring Security
 * CORS gotcha).
 *
 * <p><b>Save scoping (IDOR prevention):</b> unlike the retired {@code PlayerScopeFilter} (a
 * single filter regex over the flat {@code /api/players/{username}} segment), ownership of a
 * {@code /api/saves/{saveId}...} route is enforced per controller via {@code
 * amiss.api.web.SaveScope}, since the id sits inside a path template that varies by
 * controller. It throws the same {@link AccessDeniedException} type, so the failure still
 * leaves as RFC 7807 problem JSON through this class's {@code AccessDeniedHandler}.
 *
 * <p><b>CORS:</b> allowed origins come from {@code amiss.cors.allowed-origins}
 * (config change, not code change). Credentials are off — the JWT travels in the
 * {@code Authorization} header, never a cookie. Security headers stay at Spring's
 * defaults (nosniff, frame DENY, no-cache) deliberately.
 *
 * <p><b>Deliberately no {@code UserDetailsService}/{@code BCryptPasswordEncoder}:</b>
 * {@link AuthService} calls the core's {@code PasswordHasher} directly so the API and the
 * Swing {@code LoginGUI} share one credential rule — including the transparent
 * legacy-plaintext-to-BCrypt upgrade on first login, which a {@code DaoAuthenticationProvider}
 * cannot reproduce (a plaintext stored value simply fails {@code matches}). Spring Security
 * here supplies only the JWT machinery.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** HS256's minimum key size (256 bits) — anything shorter is rejected at startup. */
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationEntryPoint problemDetailEntryPoint,
            AccessDeniedHandler problemDetailAccessDeniedHandler) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/highscores").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> {
                    oauth2.jwt(Customizer.withDefaults());
                    oauth2.authenticationEntryPoint(problemDetailEntryPoint);
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemDetailEntryPoint)
                        .accessDeniedHandler(problemDetailAccessDeniedHandler));
        return http.build();
    }

    /**
     * RFC 7807 401 for a missing/expired/invalid bearer token. Emitted from the filter chain
     * rather than controller advice — authentication failures never reach the
     * {@code DispatcherServlet} — but uses the same envelope as {@code GlobalExceptionHandler}.
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

    /**
     * RFC 7807 403 for an authenticated-but-not-allowed request — today only {@code
     * SaveScope}'s ownership check, but any future {@code AccessDeniedException} takes the
     * same path. The {@code detail} comes from the exception's message; {@code type}/{@code
     * title} stay fixed.
     */
    @Bean
    AccessDeniedHandler problemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            ProblemDetail problem =
                    ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, accessDeniedException.getMessage());
            problem.setTitle("Forbidden");
            problem.setType(URI.create("urn:amiss:forbidden"));
            objectMapper.writeValue(response.getOutputStream(), problem);
        };
    }

    /**
     * CORS for the SPA: only {@code amiss.cors.allowed-origins} (comma-separated; defaults to
     * the Vite dev server) may call the API, with {@code GET}/{@code POST}/{@code OPTIONS} and
     * the {@code Authorization}/{@code Content-Type} headers only.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${amiss.cors.allowed-origins}") String[] allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
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
