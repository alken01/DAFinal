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
import java.util.Objects;


@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        // Print the Authorization header
        //System.out.println("Authorization header: " + header);

        String headerStartString = "Bearer ";

        if (header == null || !header.startsWith(headerStartString)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(headerStartString.length()); // Extract token from header remove bearer


        try {
            // decode the token
            DecodedJWT jwt = JWT.decode(token);
            //System.out.println("Authorization jwt: " + jwt);

            // extract email and role from the token
            String email = jwt.getClaim("email").asString();
            //System.out.println("Authorization mail: " + email);
            String role = jwt.getClaim("role").asString(); // adjust this according to your token structure
            // if role is not 'manager', assign it to be 'user'
            if(Objects.isNull(role)){
                role = "user";
            }
            //System.out.println("Authorization role: " + role);

            // create user and set in the authentication
            User user = new User(email, role);
            SecurityContext context = SecurityContextHolder.getContext();
            context.setAuthentication(new FirebaseAuthentication(user));

        } catch (JWTDecodeException exception){
            //Invalid token
            //System.out.println("Invalid token");
        }

        filterChain.doFilter(request, response);
    }


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api");
    }

    private static class FirebaseAuthentication implements Authentication {
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
            return null;
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
            return null;
        }
    }
}

