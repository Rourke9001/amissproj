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
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless HS256 JWT auth for the REST API, fully locked down (KAN-36 minted/verified the
 * token; KAN-37 below is what actually gates every route with it).
 *
 * <p><b>Authorization rules:</b> {@code POST /api/auth/register}, {@code POST /api/auth/login},
 * {@code GET /api/highscores}, {@code /actuator/health(/**)} and {@code /error} stay
 * {@code permitAll()} — the public trio a game client needs before it has ever logged in, plus
 * the ops health probe. CORS preflight ({@code OPTIONS}) is also {@code permitAll()}: a
 * preflight carries no {@code Authorization} header, so without this rule a legitimate
 * cross-origin request from an allowed origin would 401 before the browser ever sends the
 * real request (the documented Spring Security CORS gotcha). Every other {@code /api/**} route
 * — including the catalog GETs ({@code board}/{@code jobs}/{@code courses}/{@code food}) and
 * {@code GET /api/auth/me} — requires a valid bearer token; {@code anyRequest().authenticated()}
 * closes off anything outside {@code /api/**} too.
 *
 * <p><b>Player scoping (IDOR prevention):</b> authentication alone isn't enough — a valid token
 * for "alice" must not be able to read or mutate "bob"'s state. {@link PlayerScopeFilter} is the
 * one central place that enforces path-username == principal for every
 * {@code /api/players/{username}...} route; see its javadoc for why a filter beats per-method
 * annotations. Its {@link AccessDeniedException} is rendered by {@link
 * #problemDetailAccessDeniedHandler}, the same RFC 7807 shape {@code
 * problemDetailAuthenticationEntryPoint} uses for 401s.
 *
 * <p><b>CORS:</b> {@link #corsConfigurationSource} is driven entirely by {@code
 * amiss.cors.allowed-origins} (see {@code application.yml}) so the future SPA's origin is a
 * config change, not a code change. Credentials are off — the JWT travels in the
 * {@code Authorization} header, never a cookie, so there is nothing for the browser to send
 * "with credentials".
 *
 * <p><b>Security headers:</b> deliberately no custom {@code headers(...)} block — Spring
 * Security's defaults ({@code X-Content-Type-Options: nosniff}, {@code X-Frame-Options: DENY},
 * cache-control on every response) are applied automatically by {@link HttpSecurity} and are
 * the accepted baseline for this API.
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
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationEntryPoint problemDetailEntryPoint,
            AccessDeniedHandler problemDetailAccessDeniedHandler, PlayerScopeFilter playerScopeFilter)
            throws Exception {
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
                        .accessDeniedHandler(problemDetailAccessDeniedHandler))
                .addFilterAfter(playerScopeFilter, AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    PlayerScopeFilter playerScopeFilter() {
        return new PlayerScopeFilter();
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

    /**
     * RFC 7807 403 for an authenticated-but-not-allowed request — today that's only {@link
     * PlayerScopeFilter}'s IDOR check, but any future {@code AccessDeniedException} (a
     * method-security annotation, say) leaves through this exact same envelope. The
     * {@code detail} comes from the thrown exception's message, so callers stay in control of
     * the wording while the {@code type}/{@code title} stay fixed.
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
     * CORS policy for the future SPA: only {@code amiss.cors.allowed-origins} (comma-separated;
     * defaults to the Vite dev server) may call the API, only with {@code GET}/{@code POST}/
     * {@code OPTIONS}, and only sending {@code Authorization}/{@code Content-Type}. No
     * credentials — the JWT lives in the {@code Authorization} header, not a cookie.
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
