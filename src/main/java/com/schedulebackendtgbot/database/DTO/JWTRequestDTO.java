package com.schedulebackendtgbot.database.DTO;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class JWTRequestDTO {

    private String login;
    private String password;

}