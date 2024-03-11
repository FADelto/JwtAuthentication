# Приложение с JWT аутентификацией

### Использованы гайды:
- [Реализация JWT в Spring Boot](https://struchkov.dev/blog/ru/jwt-implementation-in-spring/)
- [Документирование SpringBoot API с помощью Swagger](https://struchkov.dev/blog/ru/api-swagger/#%D0%B0%D0%B2%D1%82%D0%BE%D1%80%D0%B8%D0%B7%D0%B0%D1%86%D0%B8%D1%8F-%D1%81-%D0%B8%D1%81%D0%BF%D0%BE%D0%BB%D1%8C%D0%B7%D0%BE%D0%B2%D0%B0%D0%BD%D0%B8%D0%B5%D0%BC-jwt)

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
