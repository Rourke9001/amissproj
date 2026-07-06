package amiss.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * IDOR guard (KAN-37): the single enforcement point for "an authenticated player may only
 * touch their own state". Every {@code /api/players/{username}} and
 * {@code /api/players/{username}/**} route is matched here by path alone, rather than by a
 * per-method annotation on each controller that a future endpoint could simply forget to add.
 *
 * <p>{@link SecurityConfig} registers this with
 * {@code addFilterAfter(..., AuthorizationFilter.class)}, so it only ever runs once the
 * {@code authorizeHttpRequests} rules have already let an <em>authenticated</em> request
 * through — an anonymous caller is rejected with 401 by that chain first and never reaches
 * this filter.
 *
 * <p>On a mismatch this throws {@link AccessDeniedException} rather than writing the HTTP
 * response itself: {@code ExceptionTranslationFilter} (upstream in the chain) catches it and
 * hands it to the {@code AccessDeniedHandler} bean, so this filter and any future
 * access-denied path share the exact same RFC 7807 envelope.
 */
public class PlayerScopeFilter extends OncePerRequestFilter {

    private static final Pattern PLAYER_PATH = Pattern.compile("^/api/players/([^/]+)(?:/.*)?$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Matcher matcher = PLAYER_PATH.matcher(path);
        if (matcher.matches() && !ownsThePath(matcher.group(1))) {
            throw new AccessDeniedException("You may only access your own player state");
        }
        filterChain.doFilter(request, response);
    }

    private static boolean ownsThePath(String rawPathSegment) {
        String pathUsername;
        try {
            pathUsername = URLDecoder.decode(rawPathSegment, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformedEncoding) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principalName = authentication == null ? null : authentication.getName();
        return pathUsername.equals(principalName);
    }
}
