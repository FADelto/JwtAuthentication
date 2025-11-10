package com.schedulebackendtgbot.service;

import com.schedulebackendtgbot.database.repository.TokenBlacklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Сервис для периодической очистки истекших токенов из blacklist
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final TokenBlacklistRepository tokenBlacklistRepository;

    /**
     * Очистка истекших токенов из blacklist
     * Запускается каждый день в 3:00 ночи
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Запуск очистки истекших токенов из blacklist");
        try {
            LocalDateTime now = LocalDateTime.now();
            tokenBlacklistRepository.deleteExpiredTokens(now);
            log.info("Очистка истекших токенов завершена успешно");
        } catch (Exception e) {
            log.error("Ошибка при очистке истекших токенов: {}", e.getMessage(), e);
        }
    }
}
