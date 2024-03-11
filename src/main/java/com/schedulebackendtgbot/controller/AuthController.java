package com.schedulebackendtgbot.controller;

import com.schedulebackendtgbot.database.DTO.JWTRequestDTO;

import com.schedulebackendtgbot.database.DTO.RefreshJWTRequestDTO;
import com.schedulebackendtgbot.database.DTO.UserCreateDTO;
import com.schedulebackendtgbot.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.security.auth.message.AuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
@Tag(name="Контроллер авторизации")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Авторизация пользователя"
    )
    @PostMapping("login")
    public ResponseEntity<?> login(@RequestBody JWTRequestDTO authRequest) {
        try{
            return ResponseEntity.ok(authService.login(authRequest));
        } catch (AuthException e){
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @Operation(
            summary = "Регистрация пользователя"
    )
    @PostMapping("/register")
    public ResponseEntity<?> register(UserCreateDTO request) {
        try{
            return ResponseEntity.ok(authService.register(request));
        } catch (IllegalArgumentException e){
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    @Operation(
            summary = "Получить access токен"
    )
    @PostMapping("token")
    public ResponseEntity<?> getNewAccessToken(@RequestBody RefreshJWTRequestDTO request) throws AuthException {
//        final JWTResponse token = authService.getAccessToken(request.getRefreshToken());
        try{
            return ResponseEntity.ok(authService.getAccessToken(request.getRefreshToken()));
        } catch (AuthException e){
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @Operation(
            summary = "Обновить refresh токен"
    )
    @PostMapping("refresh")
    public ResponseEntity<?> getNewRefreshToken(@RequestBody RefreshJWTRequestDTO request) throws AuthException {
        try{
            return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
        } catch (AuthException e){
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

}
