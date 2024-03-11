package com.schedulebackendtgbot.database.mapper;


import com.schedulebackendtgbot.database.DTO.UserReadDTO;
import com.schedulebackendtgbot.database.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserReadMapper implements Mapper<User, UserReadDTO> {

    @Override
    public UserReadDTO map(User user) {
        return new UserReadDTO(
                user.getId(),
                user.getTeacherExternalID(),
                user.getFirstname(),
                user.getMidname(),
                user.getLastname(),
                user.getUsername(),
                user.getRole());
    }

}
