package com.schedulebackendtgbot.service;

import com.schedulebackendtgbot.database.entity.Role;
import io.jsonwebtoken.Claims;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public final class JwtUtils implements AuthenticationProvider {
    private final UserService userService;
    private final JWTProvider jwtProvider;
    private final UserDetailsService userDetailsService;

//    public Authentication authenticate(Claims claims) throws AuthenticationException {
//        String username = claims.getSubject();
//        UserDetails userDetails = userService.loadUserByUsername(username);
//        // Проверьте дополнительные условия (если необходимо)
//        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
//    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String token = (String) authentication.getCredentials();
        String username = jwtProvider.getAccessClaims(token).getSubject();
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return (UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication));
    }
    private static Role getRole(Claims claims) {
        final String role = claims.get("role", String.class);
        return Role.valueOf(role);
    }

}
