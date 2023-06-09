package be.kuleuven.distributedsystems.cloud.auth;

import be.kuleuven.distributedsystems.cloud.entities.User;
import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        try {
            if (header == null || !header.startsWith("Bearer") ) {
                throw new ServletException("Invalid header"); // Throw exception for invalid header
            }

            // Extract token from header, removing "Bearer"
            String token = header.split(" ")[1];

            // Decode the token
            DecodedJWT jwt = JWT.decode(token);

            // Extract email and role from the token
            String email = jwt.getClaim("email").asString();
            String role = jwt.getClaim("role").asString(); // Adjust this according to your token structure
            String uid = jwt.getClaim("user_id").asString();

            // Print all claims
            //System.out.println("Claims in the JWT token:");
            //for (String claimName : jwt.getClaims().keySet()) {
            //    System.out.println(claimName + ": " + jwt.getClaim(claimName).asString());
            //}

            // Set the authentication object to let the security context know that the user associated with the request is authenticated
            User user = new User(email, role, uid);
            SecurityContext context = SecurityContextHolder.getContext();
            context.setAuthentication(new FirebaseAuthentication(user));

        } catch (JWTDecodeException exception) {
            // Handle invalid token
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid token");
            return;
        } catch (ServletException exception) {
            // Handle invalid header
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid header");
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api");
    }

    public static class FirebaseAuthentication implements Authentication {
        private final User user;

        FirebaseAuthentication(User user) {
            this.user = user;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            if (user.isManager()) {
                return List.of(new SimpleGrantedAuthority("manager"));
            } else {
                return new ArrayList<>();
            }
        }

        @Override
        public Object getCredentials() {
            return this.user;
        }

        @Override
        public Object getDetails() {
            return null;
        }

        @Override
        public User getPrincipal() {
            return this.user;
        }

        @Override
        public boolean isAuthenticated() {
            return true;
        }

        @Override
        public void setAuthenticated(boolean b) throws IllegalArgumentException {

        }


        @Override
        public String getName() {
            return this.user.getEmail();
        }

        public String getUid() {
            return this.user.getUid();
        }
    }
}