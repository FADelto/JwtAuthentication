package com.schedulebackendtgbot.database.DTO;

import com.schedulebackendtgbot.database.entity.Role;


public record UserReadDTO(Long id, Integer teacherExternalID, String firstname, String midname, String lastname,
                          String username, Role role) {

}
