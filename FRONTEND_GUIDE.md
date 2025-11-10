# Гайд по интеграции JWT авторизации для фронтендера

## Оглавление
1. [Обзор API](#обзор-api)
2. [Процесс авторизации](#процесс-авторизации)
3. [Endpoints](#endpoints)
4. [Примеры кода](#примеры-кода)
5. [Обработка ошибок](#обработка-ошибок)
6. [Best Practices](#best-practices)

---

## Обзор API

Base URL: `http://your-api-url/api/auth`

Все запросы и ответы используют формат **JSON**.

### Токены

Система использует два типа токенов:
- **Access Token**: Короткоживущий токен (30 минут), используется для доступа к защищенным ресурсам
- **Refresh Token**: Долгоживущий токен (30 дней), используется для обновления access token

---

## Процесс авторизации

### 1. Регистрация нового пользователя

**Endpoint:** `POST /api/auth/register`

**Request Body:**
```json
{
  "username": "user@example.com",
  "password": "password123",
  "firstname": "Иван",
  "midname": "Иванович",
  "lastname": "Иванов",
  "groupName": "ИСТ-401"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Validation:**
- `username`: обязательно, email формат, макс 100 символов
- `password`: обязательно, минимум 6 символов
- `firstname`, `midname`, `lastname`: необязательно, макс 50 символов

---

### 2. Вход в систему

**Endpoint:** `POST /api/auth/login`

**Request Body:**
```json
{
  "login": "user@example.com",
  "password": "password123"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Validation:**
- `login`: обязательно, 3-50 символов
- `password`: обязательно, минимум 6 символов

---

### 3. Обновление Access Token

**Endpoint:** `POST /api/auth/token`

**Request Body:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

### 4. Обновление Refresh Token

**Endpoint:** `POST /api/auth/refresh`

**Request Body:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

> ⚠️ **Важно:** При вызове `/refresh` оба токена обновляются. Старый refresh token становится невалидным.

---

### 5. Выход из системы (Logout)

**Endpoint:** `POST /api/auth/logout`

**Request Body:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200 OK):** Пустой ответ

---

## Endpoints

| Метод | Endpoint | Описание | Требует авторизации |
|-------|----------|----------|---------------------|
| POST | `/api/auth/register` | Регистрация пользователя | Нет |
| POST | `/api/auth/login` | Вход в систему | Нет |
| POST | `/api/auth/token` | Получить новый access token | Нет |
| POST | `/api/auth/refresh` | Обновить оба токена | Нет |
| POST | `/api/auth/logout` | Выход из системы | Нет |
| GET/POST | `/api/v1/user/**` | Пользовательские endpoint'ы | Да |
| GET/POST | `/api/v1/admin/**` | Админские endpoint'ы | Да (роль ADMIN) |

---

## Примеры кода

### JavaScript (Fetch API)

```javascript
// 1. Регистрация
async function register(userData) {
  try {
    const response = await fetch('http://localhost:8080/api/auth/register', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(userData)
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.error || 'Registration failed');
    }

    const { accessToken, refreshToken } = await response.json();

    // Сохраняем токены
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);

    return { accessToken, refreshToken };
  } catch (error) {
    console.error('Registration error:', error);
    throw error;
  }
}

// 2. Вход
async function login(credentials) {
  try {
    const response = await fetch('http://localhost:8080/api/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(credentials)
    });

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.error || 'Login failed');
    }

    const { accessToken, refreshToken } = await response.json();

    // Сохраняем токены
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);

    return { accessToken, refreshToken };
  } catch (error) {
    console.error('Login error:', error);
    throw error;
  }
}

// 3. Запрос к защищенному endpoint с автоматическим обновлением токена
async function fetchWithAuth(url, options = {}) {
  let accessToken = localStorage.getItem('accessToken');

  const makeRequest = async (token) => {
    return fetch(url, {
      ...options,
      headers: {
        ...options.headers,
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });
  };

  let response = await makeRequest(accessToken);

  // Если access token истек (401), пытаемся обновить его
  if (response.status === 401) {
    const refreshToken = localStorage.getItem('refreshToken');

    if (!refreshToken) {
      // Нет refresh token - перенаправляем на логин
      window.location.href = '/login';
      return;
    }

    try {
      // Обновляем access token
      const refreshResponse = await fetch('http://localhost:8080/api/auth/token', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ refreshToken })
      });

      if (!refreshResponse.ok) {
        throw new Error('Token refresh failed');
      }

      const { accessToken: newAccessToken, refreshToken: newRefreshToken } = await refreshResponse.json();

      // Сохраняем новые токены
      localStorage.setItem('accessToken', newAccessToken);
      localStorage.setItem('refreshToken', newRefreshToken);

      // Повторяем исходный запрос с новым токеном
      response = await makeRequest(newAccessToken);
    } catch (error) {
      console.error('Token refresh failed:', error);
      // Перенаправляем на логин
      window.location.href = '/login';
      return;
    }
  }

  return response;
}

// 4. Выход
async function logout() {
  const refreshToken = localStorage.getItem('refreshToken');

  if (refreshToken) {
    try {
      await fetch('http://localhost:8080/api/auth/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ refreshToken })
      });
    } catch (error) {
      console.error('Logout error:', error);
    }
  }

  // Очищаем локальное хранилище
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');

  // Перенаправляем на логин
  window.location.href = '/login';
}

// 5. Пример использования
async function getUserData() {
  try {
    const response = await fetchWithAuth('http://localhost:8080/api/v1/user/profile');

    if (!response.ok) {
      throw new Error('Failed to fetch user data');
    }

    return await response.json();
  } catch (error) {
    console.error('Error fetching user data:', error);
    throw error;
  }
}
```

---

### React + Axios

```javascript
import axios from 'axios';

const API_URL = 'http://localhost:8080/api';

// Создаем экземпляр axios
const api = axios.create({
  baseURL: API_URL,
  headers: {
    'Content-Type': 'application/json'
  }
});

// Добавляем interceptor для автоматической подстановки токена
api.interceptors.request.use(
  (config) => {
    const accessToken = localStorage.getItem('accessToken');
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Добавляем interceptor для автоматического обновления токена
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Если получили 401 и еще не пытались обновить токен
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        const refreshToken = localStorage.getItem('refreshToken');

        if (!refreshToken) {
          throw new Error('No refresh token');
        }

        const response = await axios.post(`${API_URL}/auth/token`, {
          refreshToken
        });

        const { accessToken, refreshToken: newRefreshToken } = response.data;

        localStorage.setItem('accessToken', accessToken);
        localStorage.setItem('refreshToken', newRefreshToken);

        // Обновляем токен в исходном запросе
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;

        return api(originalRequest);
      } catch (refreshError) {
        // Если не удалось обновить токен, выходим
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

// Auth service
export const authService = {
  async register(userData) {
    const response = await axios.post(`${API_URL}/auth/register`, userData);
    const { accessToken, refreshToken } = response.data;

    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);

    return response.data;
  },

  async login(credentials) {
    const response = await axios.post(`${API_URL}/auth/login`, credentials);
    const { accessToken, refreshToken } = response.data;

    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);

    return response.data;
  },

  async logout() {
    const refreshToken = localStorage.getItem('refreshToken');

    if (refreshToken) {
      await axios.post(`${API_URL}/auth/logout`, { refreshToken });
    }

    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
  },

  isAuthenticated() {
    return !!localStorage.getItem('accessToken');
  }
};

export default api;
```

---

### React Hook для авторизации

```javascript
import { useState, useEffect, createContext, useContext } from 'react';
import { authService } from './api';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Проверяем, есть ли токен при загрузке
    const accessToken = localStorage.getItem('accessToken');
    if (accessToken) {
      // Можно дополнительно загрузить данные пользователя
      setUser({ authenticated: true });
    }
    setLoading(false);
  }, []);

  const login = async (credentials) => {
    const data = await authService.login(credentials);
    setUser({ authenticated: true, ...data });
  };

  const register = async (userData) => {
    const data = await authService.register(userData);
    setUser({ authenticated: true, ...data });
  };

  const logout = async () => {
    await authService.logout();
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, login, register, logout, loading }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
};
```

---

## Обработка ошибок

### Коды ответов

| Код | Описание | Действие |
|-----|----------|----------|
| 200 | Успешно | Продолжить работу |
| 400 | Ошибка валидации | Показать ошибки валидации |
| 401 | Не авторизован | Обновить токен или перейти на логин |
| 403 | Доступ запрещен | Показать сообщение о недостатке прав |
| 409 | Конфликт (email занят) | Показать сообщение пользователю |
| 429 | Слишком много запросов | Подождать и повторить позже |
| 500 | Ошибка сервера | Показать общее сообщение об ошибке |

### Примеры ошибок

**Валидация (400):**
```json
{
  "login": "Логин должен быть от 3 до 50 символов",
  "password": "Пароль не может быть пустым"
}
```

**Ошибка авторизации (401):**
```json
{
  "error": "Пользователь не найден"
}
```

**Rate Limit (429):**
```json
{
  "error": "Слишком много запросов. Попробуйте позже."
}
```

---

## Best Practices

### 1. Хранение токенов

#### ✅ Рекомендуется
- Использовать `localStorage` для refresh token
- Использовать память (переменные) для access token в production
- В development можно использовать `localStorage` для обоих

#### ❌ Не рекомендуется
- Хранить токены в cookies без `httpOnly` флага
- Хранить токены в sessionStorage для long-lived sessions
- Передавать токены в URL

### 2. Безопасность

```javascript
// ✅ Правильно: Токен в Authorization header
fetch('/api/v1/user/profile', {
  headers: {
    'Authorization': `Bearer ${accessToken}`
  }
});

// ❌ Неправильно: Токен в URL
fetch(`/api/v1/user/profile?token=${accessToken}`);
```

### 3. Обработка истечения токена

```javascript
// ✅ Правильно: Автоматическое обновление
api.interceptors.response.use(
  response => response,
  async error => {
    if (error.response?.status === 401) {
      // Попытка обновить токен
      await refreshToken();
      // Повторить запрос
      return api(error.config);
    }
    return Promise.reject(error);
  }
);

// ❌ Неправильно: Игнорирование 401
// Не обрабатывать ошибки авторизации
```

### 4. Logout при закрытии вкладки

```javascript
// Отправляем logout при закрытии вкладки
window.addEventListener('beforeunload', () => {
  const refreshToken = localStorage.getItem('refreshToken');
  if (refreshToken) {
    // Используем navigator.sendBeacon для гарантированной отправки
    navigator.sendBeacon(
      'http://localhost:8080/api/auth/logout',
      JSON.stringify({ refreshToken })
    );
  }
});
```

### 5. Rate Limiting

Система имеет rate limiting:
- **5 запросов в минуту** на `/api/auth/login` и `/api/auth/register` с одного IP

При превышении лимита вы получите `429 Too Many Requests`.

**Рекомендации:**
- Добавьте debounce на кнопку логина
- Показывайте пользователю сообщение о необходимости подождать
- Не делайте автоматические retry без задержки

```javascript
// Пример debounce для кнопки логина
const debouncedLogin = debounce(login, 1000, { leading: true, trailing: false });
```

### 6. CORS

CORS настроен на следующие origins:
- `http://localhost:3000` (React)
- `http://localhost:5173` (Vite)

**Если ваш фронтенд работает на другом порту**, обратитесь к бэкенд разработчику для добавления вашего origin в `SpringSecurityConfig.java:31`.

---

## Swagger UI

Полная документация API доступна по адресу:
```
http://localhost:8080/swagger-ui/index.html
```

Здесь вы можете:
- Посмотреть все endpoints
- Протестировать запросы
- Увидеть примеры запросов и ответов

---

## Troubleshooting

### Проблема: "CORS error"
**Решение:** Убедитесь, что ваш origin добавлен в `SpringSecurityConfig`. По умолчанию разрешены только `localhost:3000` и `localhost:5173`.

### Проблема: "Token expired" при каждом запросе
**Решение:** Проверьте, что вы правильно обновляете и сохраняете новые токены после рефреша.

### Проблема: "429 Too Many Requests"
**Решение:** Подождите 1 минуту или добавьте debounce на кнопки логина/регистрации.

### Проблема: Токен не отправляется на сервер
**Решение:**
1. Проверьте, что токен есть в localStorage: `localStorage.getItem('accessToken')`
2. Проверьте, что заголовок `Authorization` установлен правильно: `Bearer <token>`
3. Проверьте в DevTools Network tab, что заголовок отправляется

---

## Контакты

Если у вас возникли вопросы или проблемы:
1. Проверьте логи в браузере (Console и Network tabs)
2. Проверьте Swagger UI для тестирования endpoint'ов
3. Свяжитесь с бэкенд командой

---

**Версия:** 1.0
**Последнее обновление:** 2025-01-10
