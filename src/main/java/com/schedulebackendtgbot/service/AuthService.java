package com.schedulebackendtgbot.service;

import com.schedulebackendtgbot.database.DTO.JWTRequestDTO;
import com.schedulebackendtgbot.database.DTO.JWTResponseDTO;

import com.schedulebackendtgbot.database.DTO.UserCreateDTO;
import com.schedulebackendtgbot.database.entity.Token;
import com.schedulebackendtgbot.database.entity.User;
import com.schedulebackendtgbot.database.repository.TokenRepository;
import com.schedulebackendtgbot.database.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.security.auth.message.AuthException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final JWTProvider jwtProvider;

    public JWTResponseDTO login(@NonNull JWTRequestDTO authRequest) throws AuthException {
        log.info("Попытка входа пользователя: {}", authRequest.getLogin());
        final User user = userRepository.findByUsername(authRequest.getLogin())
                .orElseThrow(() -> {
                    log.warn("Неудачная попытка входа: пользователь не найден - {}", authRequest.getLogin());
                    return new AuthException("Пользователь не найден");
                });
        if (passwordEncoder.matches(authRequest.getPassword(), user.getPassword())) {
            final String accessToken = jwtProvider.generateAccessToken(user);
            Token token = tokenRepository.findValidTokenByUser(user.getId());
            String refreshToken;

            // Проверяем существует ли валидный токен и не истек ли он
            if(token != null && jwtProvider.validateRefreshToken(token.getToken())) {
                refreshToken = token.getToken();
            } else {
                // Если токена нет или он истек, создаем новый
                revokeAllUserTokens(user);
                refreshToken = jwtProvider.generateRefreshToken(user);
                tokenRepository.save(new Token(refreshToken, false, false, user));
            }
            log.info("Успешный вход пользователя: {}", user.getUsername());
            return new JWTResponseDTO(accessToken, refreshToken);
        } else {
            log.warn("Неудачная попытка входа: неверный пароль - {}", authRequest.getLogin());
            throw new AuthException("Неправильный пароль");
        }
    }

    public JWTResponseDTO register(UserCreateDTO userDTO) throws AuthException {
        log.info("Попытка регистрации пользователя: {}", userDTO.getUsername());
        User user = userService.create(userDTO);
        String accessToken = jwtProvider.generateAccessToken(user);
        String refreshToken = jwtProvider.generateRefreshToken(user);
        tokenRepository.save(new Token(refreshToken, false, false, user));
        log.info("Успешная регистрация пользователя: {}", user.getUsername());
        return new JWTResponseDTO(accessToken, refreshToken);
    }

    public JWTResponseDTO getAccessToken(@NonNull String refreshToken) throws AuthException {
        if (jwtProvider.validateRefreshToken(refreshToken)) {
            final Claims claims = jwtProvider.getRefreshClaims(refreshToken);
            final String login = claims.getSubject();
            final User user = userRepository.findByUsername(login)
                    .orElseThrow(() -> new AuthException("Пользователь не найден"));
            final Token savedToken = tokenRepository.findValidTokenByUser(user.getId());

            if (savedToken != null && savedToken.getToken().equals(refreshToken)) {
                final String accessToken = jwtProvider.generateAccessToken(user);
                return new JWTResponseDTO(accessToken, savedToken.getToken());
            }
        }
        throw new AuthException("Невалидный JWT токен");
    }

    public JWTResponseDTO refresh(@NonNull String refreshToken) throws AuthException {
        if (jwtProvider.validateRefreshToken(refreshToken)) {
            final Claims claims = jwtProvider.getRefreshClaims(refreshToken);
            final String login = claims.getSubject();
            final User user = userRepository.findByUsername(login)
                    .orElseThrow(() -> new AuthException("Пользователь не найден"));
            final Token savedToken = tokenRepository.findValidTokenByUser(user.getId());

            if (savedToken != null && savedToken.getToken().equals(refreshToken)) {
                final String accessToken = jwtProvider.generateAccessToken(user);
                revokeAllUserTokens(user);
                final String newRefreshToken = jwtProvider.generateRefreshToken(user);
                tokenRepository.save(new Token(newRefreshToken, false, false, user));
                return new JWTResponseDTO(accessToken, newRefreshToken);
            }
        }
        throw new AuthException("Невалидный JWT токен");
    }

    public void logout(@NonNull String refreshToken) throws AuthException {
        if (jwtProvider.validateRefreshToken(refreshToken)) {
            final Claims claims = jwtProvider.getRefreshClaims(refreshToken);
            final String login = claims.getSubject();
            final User user = userRepository.findByUsername(login)
                    .orElseThrow(() -> new AuthException("Пользователь не найден"));
            revokeAllUserTokens(user);
            log.info("Пользователь вышел из системы: {}", user.getUsername());
        } else {
            log.warn("Попытка выхода с невалидным токеном");
            throw new AuthException("Невалидный JWT токен");
        }
    }

    private void revokeAllUserTokens(User user) {
        var validUserTokens = tokenRepository.findAllValidTokenByUser(user.getId());
        if (validUserTokens.isEmpty())
            return;
        validUserTokens.forEach(token -> {
            token.setExpired(true);
            token.setRevoked(true);
        });
        tokenRepository.saveAll(validUserTokens);
    }
}
