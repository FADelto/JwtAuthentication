package com.schedulebackendtgbot.database.repository;

import com.schedulebackendtgbot.database.entity.TokenBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, Long> {

    /**
     * Проверка наличия токена в blacklist
     */
    boolean existsByToken(String token);

    /**
     * Поиск токена в blacklist
     */
    Optional<TokenBlacklist> findByToken(String token);

    /**
     * Удаление истекших токенов из blacklist (cleanup)
     * Запускать периодически через @Scheduled
     */
    @Modifying
    @Query("DELETE FROM TokenBlacklist t WHERE t.expiresAt < :now")
    void deleteExpiredTokens(LocalDateTime now);
}
