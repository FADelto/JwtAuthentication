# Приложение с JWT аутентификацией

## Функции:
- Тестирование приложения и документация API приложения в Swagger 
- Авторизация через логин/пароль
- Аутентификация пользователя с использованием JWT токенов
- Хранение refresh токенов и пользователей в таблицах БД
- Разделенный функционал для разных ролей пользователей

## Стек:
* Java 20
* Gradle 8.5
* Spring Boot 3.2.3
* Lombok 8.4
* PostgreSQL 42.5.1
* jjwt 0.12.5

## Для запуска:
- Если запуск будет не через Docker, то создать базу данных и указать в application.yml данные для подключения
- Можно запустить через IDE или с помощью Docker

Для запуска при помощи Docker:
  
  Создать образ:
  ```docker compose build```
  
  Запуск приложения:
  ```docker compose up```