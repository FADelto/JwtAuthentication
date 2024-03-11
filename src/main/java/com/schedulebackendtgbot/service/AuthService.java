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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final JWTProvider jwtProvider;

    public JWTResponseDTO login(@NonNull JWTRequestDTO authRequest) throws AuthException {
        final User user = userRepository.findByUsername(authRequest.getLogin())
                .orElseThrow(() -> new AuthException("Пользователь не найден"));
        if (passwordEncoder.matches(authRequest.getPassword(), user.getPassword())) {
            final String accessToken = jwtProvider.generateAccessToken(user);
            Token token = tokenRepository.findValidTokenByUser(user.getId());
            String refreshToken;
            if(jwtProvider.validateRefreshToken(token.getToken())) {
                refreshToken = token.getToken();
            }else{
                revokeAllUserTokens(user);
                refreshToken = jwtProvider.generateRefreshToken(user);
                tokenRepository.save(new Token(refreshToken, false, false, user));
            }
            return new JWTResponseDTO(accessToken, refreshToken);
        } else {
            throw new AuthException("Неправильный пароль");
        }
    }

    public JWTResponseDTO register(UserCreateDTO userDTO) {
        User user = userService.create(userDTO);
        String accessToken = jwtProvider.generateAccessToken(user);
        String refreshToken = jwtProvider.generateRefreshToken(user);
        tokenRepository.save(new Token(refreshToken, false, false, user));
        return new JWTResponseDTO(accessToken, refreshToken);
    }

    public JWTResponseDTO getAccessToken(@NonNull String refreshToken) throws AuthException {
        if (jwtProvider.validateRefreshToken(refreshToken)) {
            final Claims claims = jwtProvider.getRefreshClaims(refreshToken);
            final String login = claims.getSubject();
            final String saveRefreshToken = String.valueOf(tokenRepository.findValidTokenByUser(userRepository.findByUsername(login).orElseThrow().getId()).token);
            if (saveRefreshToken.equals(refreshToken)) {
                final User user = userRepository.findByUsername(login)
                        .orElseThrow(() -> new AuthException("Пользователь не найден"));
                final String accessToken = jwtProvider.generateAccessToken(user);
                return new JWTResponseDTO(accessToken, null);
            }
        }
        throw new AuthException("Невалидный JWT токен");
    }
    public JWTResponseDTO refresh(@NonNull String refreshToken) throws AuthException {
        if (jwtProvider.validateRefreshToken(refreshToken)) {
            final Claims claims = jwtProvider.getRefreshClaims(refreshToken);
            final String login = claims.getSubject();
            final String saveRefreshToken = String.valueOf(tokenRepository.findValidTokenByUser(userRepository.findByUsername(login).orElseThrow().getId()));
            //final String saveRefreshToken = refreshStorage.get(login);
            if (saveRefreshToken != null && saveRefreshToken.equals(refreshToken)) {
                final User user = userRepository.findByUsername(login)
                        .orElseThrow(() -> new AuthException("Пользователь не найден"));
                final String accessToken = jwtProvider.generateAccessToken(user);
                final String newRefreshToken = jwtProvider.generateRefreshToken(user);
                tokenRepository.save(new Token(refreshToken, false, false, user));
                return new JWTResponseDTO(accessToken, newRefreshToken);
            }
        }
        throw new AuthException("Невалидный JWT токен");
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
