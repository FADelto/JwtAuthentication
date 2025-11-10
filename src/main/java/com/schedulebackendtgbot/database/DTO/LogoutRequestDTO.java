package com.schedulebackendtgbot.database.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogoutRequestDTO {

    @NotBlank(message = "Токен не может быть пустым")
    private String token;

}
