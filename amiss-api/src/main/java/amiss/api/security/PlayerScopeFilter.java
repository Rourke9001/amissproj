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
 * touch their own state", matching {@code /api/players/{username}} and
 * {@code .../{username}/**} by path alone — not a per-method annotation a future endpoint
 * could forget to add.
 *
 * <p>{@link SecurityConfig} registers this with {@code addFilterAfter(...,
 * AuthorizationFilter.class)}, so it runs only after {@code authorizeHttpRequests} has let an
 * <em>authenticated</em> request through; anonymous callers are already rejected with 401.
 *
 * <p>On a mismatch this throws {@link AccessDeniedException} instead of writing the response:
 * {@code ExceptionTranslationFilter} hands it to the {@code AccessDeniedHandler} bean, so this
 * filter shares the same RFC 7807 envelope as any other access-denied path.
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
