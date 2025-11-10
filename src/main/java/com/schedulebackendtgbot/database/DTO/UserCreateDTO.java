package com.schedulebackendtgbot.database.DTO;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserCreateDTO {
    @NotBlank(message = "Email не может быть пустым")
    @Email(message = "Некорректный формат email")
    @Size(max = 100, message = "Email не должен превышать 100 символов")
    String username;

    @NotBlank(message = "Пароль не может быть пустым")
    @Size(min = 6, max = 100, message = "Пароль должен быть от 6 до 100 символов")
    String password;

    @Size(max = 50, message = "Имя не должно превышать 50 символов")
    String firstname;

    @Size(max = 50, message = "Отчество не должно превышать 50 символов")
    String midname;

    @Size(max = 50, message = "Фамилия не должна превышать 50 символов")
    String lastname;

    String groupName;

    public UserCreateDTO(String username, String password, String firstname, String midname,
                             String lastname, String groupName) {
        this.username = username;
        this.password = password;
        this.firstname = firstname;
        this.midname = midname;
        this.lastname = lastname;
        this.groupName = groupName;
    }



}
