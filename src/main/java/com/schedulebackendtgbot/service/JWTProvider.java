package com.schedulebackendtgbot.service;

import com.schedulebackendtgbot.database.entity.User;
import com.schedulebackendtgbot.database.repository.TokenBlacklistRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.security.auth.message.AuthException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Component
public class JWTProvider {

    private final SecretKey jwtSecret;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    public JWTProvider(
            @Value("${jwt.secret.access}") String jwtSecret,
            TokenBlacklistRepository tokenBlacklistRepository) {
        this.jwtSecret = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        this.tokenBlacklistRepository = tokenBlacklistRepository;
    }

    /**
     * Генерация JWT токена с увеличенным сроком жизни (30 дней)
     */
    public String generateToken(@NonNull User user) {
        final LocalDateTime now = LocalDateTime.now();
        final Instant expirationInstant = now.plusDays(30).atZone(ZoneId.systemDefault()).toInstant();
        final Date expiration = Date.from(expirationInstant);

        return Jwts.builder()
                .subject(user.getUsername())
                .expiration(expiration)
                .signWith(jwtSecret)
                .claim("role", user.getRole())
                .claim("firstName", user.getFirstname())
                .claim("userId", user.getId())
                .compact();
    }

    /**
     * Валидация токена с проверкой blacklist
     */
    public boolean validateToken(@NonNull String token) throws AuthException {
        // Сначала проверяем blacklist
        if (tokenBlacklistRepository.existsByToken(token)) {
            log.warn("Token is blacklisted");
            throw new AuthException("Токен отозван");
        }

        return validateTokenSignature(token);
    }

    /**
     * Валидация подписи токена
     */
    private boolean validateTokenSignature(@NonNull String token) throws AuthException {
        try {
            Jwts.parser()
                    .verifyWith(jwtSecret)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException expEx) {
            log.error("Token expired", expEx);
            throw new AuthException("Время работы токена истекло");
        } catch (UnsupportedJwtException unsEx) {
            log.error("Unsupported jwt", unsEx);
            throw new AuthException("Неподдерживаемый токен");
        } catch (MalformedJwtException mjEx) {
            log.error("Malformed jwt", mjEx);
            throw new AuthException("Неправильный токен");
        } catch (SignatureException sEx) {
            log.error("Invalid signature", sEx);
            throw new AuthException("Неверная подпись токена");
        } catch (Exception e) {
            log.error("invalid token", e);
            throw new AuthException("Неправильный токен");
        }
    }

    /**
     * Получение claims из токена
     */
    public Claims getClaims(@NonNull String token) {
        return Jwts.parser()
                .verifyWith(jwtSecret)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Получение даты истечения токена
     */
    public LocalDateTime getExpirationDate(@NonNull String token) {
        Claims claims = getClaims(token);
        Date expiration = claims.getExpiration();
        return expiration.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
