# Гайд по интеграции JWT авторизации для фронтендера

> 📚 **Специализированные гайды:**
> - **[Vue.js 3 + Pinia Guide](FRONTEND_VUE_GUIDE.md)** - Полное руководство для Vue.js с примерами кода
> - Этот документ содержит общие примеры на Vanilla JS и React

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

Система использует единый JWT токен:
- **Token**: Долгоживущий токен (30 дней), используется для доступа к защищенным ресурсам
- При выходе из системы токен добавляется в **blacklist** для предотвращения повторного использования
- Для инвалидации токена необходимо выполнить logout

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
  "type": "Bearer",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
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
  "type": "Bearer",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Validation:**
- `login`: обязательно, 3-50 символов
- `password`: обязательно, минимум 6 символов

---

### 3. Выход из системы (Logout)

**Endpoint:** `POST /api/auth/logout`

**Request Body:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200 OK):** Пустой ответ

> ⚠️ **Важно:** После logout токен добавляется в blacklist и не может быть использован повторно.

---

## Endpoints

| Метод | Endpoint | Описание | Требует авторизации |
|-------|----------|----------|---------------------|
| POST | `/api/auth/register` | Регистрация пользователя | Нет |
| POST | `/api/auth/login` | Вход в систему | Нет |
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

    const { token } = await response.json();

    // Сохраняем токен
    localStorage.setItem('token', token);

    return { token };
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

    const { token } = await response.json();

    // Сохраняем токен
    localStorage.setItem('token', token);

    return { token };
  } catch (error) {
    console.error('Login error:', error);
    throw error;
  }
}

// 3. Запрос к защищенному endpoint
async function fetchWithAuth(url, options = {}) {
  const token = localStorage.getItem('token');

  if (!token) {
    // Нет токена - перенаправляем на логин
    window.location.href = '/login';
    return;
  }

  const response = await fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });

  // Если токен истек или невалиден (401), выполняем logout
  if (response.status === 401) {
    await logout();
    return;
  }

  return response;
}

// 4. Выход
async function logout() {
  const token = localStorage.getItem('token');

  if (token) {
    try {
      await fetch('http://localhost:8080/api/auth/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ token })
      });
    } catch (error) {
      console.error('Logout error:', error);
    }
  }

  // Очищаем локальное хранилище
  localStorage.removeItem('token');

  // Перенаправляем на логин
  window.location.href = '/login';
}

// 5. Пример использования
async function getUserData() {
  try {
    const response = await fetchWithAuth('http://localhost:8080/api/v1/user/profile');

    if (!response) {
      return; // Уже произошел logout
    }

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
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Добавляем interceptor для обработки ошибок авторизации
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    // Если получили 401, выполняем logout
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
      return Promise.reject(error);
    }

    return Promise.reject(error);
  }
);

// Auth service
export const authService = {
  async register(userData) {
    const response = await axios.post(`${API_URL}/auth/register`, userData);
    const { token } = response.data;

    localStorage.setItem('token', token);

    return response.data;
  },

  async login(credentials) {
    const response = await axios.post(`${API_URL}/auth/login`, credentials);
    const { token } = response.data;

    localStorage.setItem('token', token);

    return response.data;
  },

  async logout() {
    const token = localStorage.getItem('token');

    if (token) {
      await axios.post(`${API_URL}/auth/logout`, { token });
    }

    localStorage.removeItem('token');
  },

  isAuthenticated() {
    return !!localStorage.getItem('token');
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
    const token = localStorage.getItem('token');
    if (token) {
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
- Использовать `localStorage` для хранения токена
- Для повышенной безопасности можно использовать память (переменные) вместо localStorage
- При использовании памяти пользователь будет разлогинен при обновлении страницы

#### ❌ Не рекомендуется
- Хранить токены в cookies без `httpOnly` флага
- Передавать токены в URL
- Хранить токен в глобальных переменных без защиты

### 2. Безопасность

```javascript
// ✅ Правильно: Токен в Authorization header
fetch('/api/v1/user/profile', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});

// ❌ Неправильно: Токен в URL
fetch(`/api/v1/user/profile?token=${token}`);
```

### 3. Обработка истечения токена

```javascript
// ✅ Правильно: Logout при 401
api.interceptors.response.use(
  response => response,
  async error => {
    if (error.response?.status === 401) {
      // Токен истек или невалиден - выполняем logout
      localStorage.removeItem('token');
      window.location.href = '/login';
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
  const token = localStorage.getItem('token');
  if (token) {
    // Используем navigator.sendBeacon для гарантированной отправки
    navigator.sendBeacon(
      'http://localhost:8080/api/auth/logout',
      JSON.stringify({ token })
    );
  }
});
```

### 5. Rate Limiting

Система имеет rate limiting:
- **15 запросов в минуту** на `/api/auth/login` и `/api/auth/register` с одного IP

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

### Проблема: "Token expired" (401)
**Решение:** Токен имеет срок действия 30 дней. После истечения необходимо выполнить повторный login. Убедитесь, что вы корректно обрабатываете 401 ошибки и перенаправляете пользователя на страницу входа.

### Проблема: "429 Too Many Requests"
**Решение:** Подождите 1 минуту или добавьте debounce на кнопки логина/регистрации.

### Проблема: Токен не отправляется на сервер
**Решение:**
1. Проверьте, что токен есть в localStorage: `localStorage.getItem('token')`
2. Проверьте, что заголовок `Authorization` установлен правильно: `Bearer <token>`
3. Проверьте в DevTools Network tab, что заголовок отправляется

### Проблема: Токен работал, но перестал после logout
**Решение:** После logout токен добавляется в blacklist и не может быть использован повторно. Необходимо выполнить новый login для получения нового токена.

---

## Контакты

Если у вас возникли вопросы или проблемы:
1. Проверьте логи в браузере (Console и Network tabs)
2. Проверьте Swagger UI для тестирования endpoint'ов
3. Свяжитесь с бэкенд командой

---

**Версия:** 2.0
**Последнее обновление:** 2025-11-10

## Изменения в версии 2.0

- Упрощена схема аутентификации: теперь используется единый JWT токен вместо пары access/refresh токенов
- Токен имеет срок действия 30 дней
- Удалены endpoints `/api/auth/token` и `/api/auth/refresh`
- Добавлен механизм blacklist для инвалидации токенов при logout
- Упрощена обработка ошибок: при 401 выполняется автоматический logout
- Изменен формат ответа: `{ type: "Bearer", token: "..." }` вместо `{ accessToken, refreshToken }`
