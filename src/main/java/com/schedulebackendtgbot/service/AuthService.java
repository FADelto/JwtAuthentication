package com.schedulebackendtgbot.service;

import com.schedulebackendtgbot.database.DTO.JWTRequestDTO;
import com.schedulebackendtgbot.database.DTO.JWTResponseDTO;
import com.schedulebackendtgbot.database.DTO.UserCreateDTO;
import com.schedulebackendtgbot.database.entity.TokenBlacklist;
import com.schedulebackendtgbot.database.entity.User;
import com.schedulebackendtgbot.database.repository.TokenBlacklistRepository;
import com.schedulebackendtgbot.database.repository.UserRepository;
import jakarta.security.auth.message.AuthException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final UserRepository userRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final JWTProvider jwtProvider;

    public JWTResponseDTO login(@NonNull JWTRequestDTO authRequest) throws AuthException {
        log.info("Попытка входа пользователя: {}", authRequest.getLogin());
        final User user = userRepository.findByUsername(authRequest.getLogin())
                .orElseThrow(() -> {
                    log.warn("Неудачная попытка входа: пользователь не найден - {}", authRequest.getLogin());
                    return new AuthException("Пользователь не найден");
                });

        if (passwordEncoder.matches(authRequest.getPassword(), user.getPassword())) {
            final String token = jwtProvider.generateToken(user);
            log.info("Успешный вход пользователя: {}", user.getUsername());
            return new JWTResponseDTO(token);
        } else {
            log.warn("Неудачная попытка входа: неверный пароль - {}", authRequest.getLogin());
            throw new AuthException("Неправильный пароль");
        }
    }

    public JWTResponseDTO register(UserCreateDTO userDTO) throws AuthException {
        log.info("Попытка регистрации пользователя: {}", userDTO.getUsername());
        User user = userService.create(userDTO);
        String token = jwtProvider.generateToken(user);
        log.info("Успешная регистрация пользователя: {}", user.getUsername());
        return new JWTResponseDTO(token);
    }

    public void logout(@NonNull String token) throws AuthException {
        try {
            // Проверяем валидность токена
            if (jwtProvider.validateToken(token)) {
                // Добавляем токен в blacklist
                LocalDateTime expiresAt = jwtProvider.getExpirationDate(token);
                String username = jwtProvider.getClaims(token).getSubject();

                TokenBlacklist blacklistedToken = TokenBlacklist.builder()
                        .token(token)
                        .blacklistedAt(LocalDateTime.now())
                        .expiresAt(expiresAt)
                        .username(username)
                        .reason("User logout")
                        .build();

                tokenBlacklistRepository.save(blacklistedToken);
                log.info("Пользователь вышел из системы: {}", username);
            }
        } catch (AuthException e) {
            // Если токен уже истек или невалиден, просто логируем
            log.warn("Попытка выхода с невалидным токеном: {}", e.getMessage());
            throw e;
        }
    }
}
