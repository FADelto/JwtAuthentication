# Полное руководство по интеграции JWT авторизации в Vue.js 3 + Pinia

## 📋 Оглавление

1. [Введение](#введение)
2. [Установка зависимостей](#установка-зависимостей)
3. [Структура проекта](#структура-проекта)
4. [Настройка Axios](#настройка-axios)
5. [Pinia Store для авторизации](#pinia-store-для-авторизации)
6. [Composables](#composables)
7. [Router Guards](#router-guards)
8. [Компоненты](#компоненты)
9. [API Reference](#api-reference)
10. [Обработка ошибок](#обработка-ошибок)
11. [Best Practices](#best-practices)
12. [Примеры использования](#примеры-использования)
13. [Troubleshooting](#troubleshooting)

---

## Введение

Это руководство описывает полную интеграцию JWT авторизации в Vue.js 3 приложение с использованием Pinia для state management.

### Особенности системы

- **JWT Token**: 30 дней (единый токен для всех API запросов)
- **Token Blacklist**: Токены добавляются в черный список при logout
- **Rate Limiting**: 15 запросов в минуту на `/api/auth/login` и `/api/auth/register`
- **CORS**: Настроен для `localhost:3000` и `localhost:5173`

---

## Установка зависимостей

```bash
# Создание нового Vue.js проекта (если еще не создан)
npm create vue@latest

# Установка необходимых зависимостей
npm install pinia axios vue-router

# Опционально: для уведомлений
npm install vue-toastification
```

### package.json

```json
{
  "name": "your-app",
  "version": "1.0.0",
  "dependencies": {
    "vue": "^3.4.0",
    "pinia": "^2.1.7",
    "axios": "^1.6.0",
    "vue-router": "^4.2.0",
    "vue-toastification": "^2.0.0-rc.5"
  }
}
```

---

## Структура проекта

```
src/
├── api/
│   ├── axios.js              # Конфигурация Axios
│   └── auth.js               # Auth API endpoints
├── stores/
│   ├── auth.js               # Pinia store для авторизации
│   └── index.js              # Pinia setup
├── composables/
│   └── useAuth.js            # Композиция для авторизации
├── router/
│   └── index.js              # Vue Router с guards
├── views/
│   ├── LoginView.vue         # Страница входа
│   ├── RegisterView.vue      # Страница регистрации
│   ├── ProfileView.vue       # Профиль пользователя
│   └── HomeView.vue          # Главная страница
├── components/
│   ├── LoginForm.vue         # Форма входа
│   ├── RegisterForm.vue      # Форма регистрации
│   └── TheNavbar.vue         # Навигация
├── utils/
│   ├── validation.js         # Валидация форм
│   └── storage.js            # Работа с localStorage
├── App.vue
└── main.js
```

---

## Настройка Axios

### `src/api/axios.js`

```javascript
import axios from 'axios';
import { useAuthStore } from '@/stores/auth';

// Базовый URL API
const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

// Создаем экземпляр axios
const api = axios.create({
  baseURL: API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000, // 10 секунд
});

// Request interceptor - добавляем токен к каждому запросу
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor - обработка ответов и ошибок
api.interceptors.response.use(
  (response) => {
    return response;
  },
  async (error) => {
    // Если получили 401 (Unauthorized), выходим из системы
    if (
      error.response?.status === 401 &&
      !error.config.url.includes('/auth/login') &&
      !error.config.url.includes('/auth/register')
    ) {
      // Токен истек или невалиден, выходим
      const authStore = useAuthStore();
      authStore.logout();

      // Перенаправляем на страницу входа
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }

    // Обработка rate limiting
    if (error.response?.status === 429) {
      console.warn('Rate limit exceeded. Please try again later.');
    }

    return Promise.reject(error);
  }
);

export default api;
```

### `src/api/auth.js`

```javascript
import api from './axios';

const AUTH_ENDPOINTS = {
  LOGIN: '/auth/login',
  REGISTER: '/auth/register',
  LOGOUT: '/auth/logout',
};

export const authApi = {
  /**
   * Вход пользователя
   * @param {Object} credentials - { login: string, password: string }
   * @returns {Promise<{type: string, token: string}>}
   */
  async login(credentials) {
    const response = await api.post(AUTH_ENDPOINTS.LOGIN, credentials);
    return response.data;
  },

  /**
   * Регистрация нового пользователя
   * @param {Object} userData - { username: string, password: string, firstname?: string, ... }
   * @returns {Promise<{type: string, token: string}>}
   */
  async register(userData) {
    const response = await api.post(AUTH_ENDPOINTS.REGISTER, userData);
    return response.data;
  },

  /**
   * Выход из системы
   * @param {string} token - JWT токен для добавления в blacklist
   * @returns {Promise<void>}
   */
  async logout(token) {
    await api.post(AUTH_ENDPOINTS.LOGOUT, { token });
  },
};

export default authApi;
```

---

## Pinia Store для авторизации

### `src/stores/index.js`

```javascript
import { createPinia } from 'pinia';

const pinia = createPinia();

export default pinia;
```

### `src/stores/auth.js`

```javascript
import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { authApi } from '@/api/auth';
import router from '@/router';

export const useAuthStore = defineStore('auth', () => {
  // State
  const token = ref(localStorage.getItem('token') || null);
  const user = ref(null);
  const loading = ref(false);
  const error = ref(null);

  // Getters
  const isAuthenticated = computed(() => !!token.value);
  const userRole = computed(() => user.value?.role || null);
  const isAdmin = computed(() => userRole.value === 'ADMIN');

  // Actions

  /**
   * Вход пользователя
   * @param {Object} credentials - { login: string, password: string }
   */
  async function login(credentials) {
    try {
      loading.value = true;
      error.value = null;

      const response = await authApi.login(credentials);

      setToken(response.token);
      await fetchUser();

      return { success: true };
    } catch (err) {
      error.value = err.response?.data?.error || 'Ошибка входа';
      console.error('Login error:', err);
      return { success: false, error: error.value };
    } finally {
      loading.value = false;
    }
  }

  /**
   * Регистрация пользователя
   * @param {Object} userData - { username: string, password: string, ... }
   */
  async function register(userData) {
    try {
      loading.value = true;
      error.value = null;

      const response = await authApi.register(userData);

      setToken(response.token);
      await fetchUser();

      return { success: true };
    } catch (err) {
      // Обработка ошибок валидации
      if (err.response?.status === 400 && typeof err.response.data === 'object') {
        // Ошибки валидации возвращаются как объект { field: message }
        error.value = Object.values(err.response.data).join(', ');
      } else {
        error.value = err.response?.data?.error || 'Ошибка регистрации';
      }

      console.error('Registration error:', err);
      return { success: false, error: error.value };
    } finally {
      loading.value = false;
    }
  }

  /**
   * Выход из системы
   */
  async function logout() {
    try {
      loading.value = true;

      if (token.value) {
        await authApi.logout(token.value);
      }
    } catch (err) {
      console.error('Logout error:', err);
    } finally {
      clearAuth();
      loading.value = false;
      router.push('/login');
    }
  }

  /**
   * Получение данных текущего пользователя
   */
  async function fetchUser() {
    try {
      // Декодируем JWT токен для получения данных пользователя
      const payload = parseJwt(token.value);

      user.value = {
        username: payload.sub,
        role: payload.role,
        firstName: payload.firstName,
      };
    } catch (err) {
      console.error('Error fetching user:', err);
    }
  }

  /**
   * Установка токена
   */
  function setToken(newToken) {
    token.value = newToken;
    localStorage.setItem('token', newToken);
  }

  /**
   * Очистка данных авторизации
   */
  function clearAuth() {
    token.value = null;
    user.value = null;

    localStorage.removeItem('token');
  }

  /**
   * Проверка авторизации при загрузке приложения
   */
  async function checkAuth() {
    if (token.value) {
      try {
        await fetchUser();
      } catch (err) {
        clearAuth();
      }
    }
  }

  // Helper functions

  /**
   * Парсинг JWT токена
   */
  function parseJwt(token) {
    try {
      const base64Url = token.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      return JSON.parse(jsonPayload);
    } catch (err) {
      console.error('Error parsing JWT:', err);
      return null;
    }
  }

  return {
    // State
    token,
    user,
    loading,
    error,

    // Getters
    isAuthenticated,
    userRole,
    isAdmin,

    // Actions
    login,
    register,
    logout,
    fetchUser,
    setToken,
    clearAuth,
    checkAuth,
  };
});
```

---

## Composables

### `src/composables/useAuth.js`

```javascript
import { computed } from 'vue';
import { useAuthStore } from '@/stores/auth';
import { useRouter } from 'vue-router';

/**
 * Композиция для работы с авторизацией
 */
export function useAuth() {
  const authStore = useAuthStore();
  const router = useRouter();

  // Computed properties
  const isAuthenticated = computed(() => authStore.isAuthenticated);
  const user = computed(() => authStore.user);
  const loading = computed(() => authStore.loading);
  const error = computed(() => authStore.error);
  const isAdmin = computed(() => authStore.isAdmin);

  // Methods

  /**
   * Вход в систему
   */
  async function login(credentials) {
    const result = await authStore.login(credentials);

    if (result.success) {
      const redirect = router.currentRoute.value.query.redirect || '/';
      router.push(redirect);
    }

    return result;
  }

  /**
   * Регистрация
   */
  async function register(userData) {
    const result = await authStore.register(userData);

    if (result.success) {
      router.push('/');
    }

    return result;
  }

  /**
   * Выход из системы
   */
  async function logout() {
    await authStore.logout();
  }

  /**
   * Проверка роли пользователя
   */
  function hasRole(role) {
    return authStore.userRole === role;
  }

  /**
   * Проверка авторизации при монтировании компонента
   */
  function requireAuth() {
    if (!isAuthenticated.value) {
      router.push({
        path: '/login',
        query: { redirect: router.currentRoute.value.fullPath },
      });
      return false;
    }
    return true;
  }

  return {
    // State
    user,
    loading,
    error,
    isAuthenticated,
    isAdmin,

    // Methods
    login,
    register,
    logout,
    hasRole,
    requireAuth,
  };
}
```

---

## Router Guards

### `src/router/index.js`

```javascript
import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guest: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { guest: true },
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('@/views/AdminView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
  },
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});

// Navigation guard
router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore();

  // Проверяем авторизацию при первой загрузке
  if (authStore.token && !authStore.user) {
    await authStore.checkAuth();
  }

  // Если требуется авторизация
  if (to.meta.requiresAuth) {
    if (!authStore.isAuthenticated) {
      next({
        path: '/login',
        query: { redirect: to.fullPath },
      });
      return;
    }

    // Проверка прав администратора
    if (to.meta.requiresAdmin && !authStore.isAdmin) {
      next('/');
      return;
    }
  }

  // Если маршрут только для гостей (не авторизованных)
  if (to.meta.guest && authStore.isAuthenticated) {
    next('/');
    return;
  }

  next();
});

export default router;
```

---

## Компоненты

### `src/components/LoginForm.vue`

```vue
<template>
  <div class="login-form">
    <h2>Вход в систему</h2>

    <form @submit.prevent="handleSubmit">
      <!-- Email/Login -->
      <div class="form-group">
        <label for="login">Email</label>
        <input
          id="login"
          v-model="form.login"
          type="text"
          placeholder="your@email.com"
          :disabled="loading"
          @blur="v$.login.$touch"
        />
        <div v-if="v$.login.$error" class="error">
          {{ v$.login.$errors[0].$message }}
        </div>
      </div>

      <!-- Password -->
      <div class="form-group">
        <label for="password">Пароль</label>
        <input
          id="password"
          v-model="form.password"
          type="password"
          placeholder="••••••••"
          :disabled="loading"
          @blur="v$.password.$touch"
        />
        <div v-if="v$.password.$error" class="error">
          {{ v$.password.$errors[0].$message }}
        </div>
      </div>

      <!-- Server Error -->
      <div v-if="error" class="error server-error">
        {{ error }}
      </div>

      <!-- Submit Button -->
      <button type="submit" :disabled="loading || v$.$invalid">
        <span v-if="!loading">Войти</span>
        <span v-else>Загрузка...</span>
      </button>

      <!-- Link to Register -->
      <div class="form-footer">
        <router-link to="/register">Нет аккаунта? Зарегистрируйтесь</router-link>
      </div>
    </form>
  </div>
</template>

<script setup>
import { reactive, computed } from 'vue';
import { useVuelidate } from '@vuelidate/core';
import { required, minLength, maxLength, email } from '@vuelidate/validators';
import { useAuth } from '@/composables/useAuth';

const { login, loading, error } = useAuth();

// Form state
const form = reactive({
  login: '',
  password: '',
});

// Validation rules
const rules = computed(() => ({
  login: {
    required,
    minLength: minLength(3),
    maxLength: maxLength(50),
  },
  password: {
    required,
    minLength: minLength(6),
  },
}));

const v$ = useVuelidate(rules, form);

// Submit handler
async function handleSubmit() {
  const isValid = await v$.value.$validate();

  if (!isValid) {
    return;
  }

  await login(form);
}
</script>

<style scoped>
.login-form {
  max-width: 400px;
  margin: 2rem auto;
  padding: 2rem;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

h2 {
  margin-bottom: 1.5rem;
  text-align: center;
  color: #333;
}

.form-group {
  margin-bottom: 1.5rem;
}

label {
  display: block;
  margin-bottom: 0.5rem;
  color: #555;
  font-weight: 500;
}

input {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
  transition: border-color 0.3s;
}

input:focus {
  outline: none;
  border-color: #4CAF50;
}

input:disabled {
  background-color: #f5f5f5;
  cursor: not-allowed;
}

.error {
  margin-top: 0.5rem;
  color: #f44336;
  font-size: 0.875rem;
}

.server-error {
  padding: 0.75rem;
  background-color: #ffebee;
  border-radius: 4px;
  margin-bottom: 1rem;
}

button {
  width: 100%;
  padding: 0.75rem;
  background-color: #4CAF50;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.3s;
}

button:hover:not(:disabled) {
  background-color: #45a049;
}

button:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

.form-footer {
  margin-top: 1rem;
  text-align: center;
}

.form-footer a {
  color: #4CAF50;
  text-decoration: none;
}

.form-footer a:hover {
  text-decoration: underline;
}
</style>
```

### `src/components/RegisterForm.vue`

```vue
<template>
  <div class="register-form">
    <h2>Регистрация</h2>

    <form @submit.prevent="handleSubmit">
      <!-- Email -->
      <div class="form-group">
        <label for="username">Email *</label>
        <input
          id="username"
          v-model="form.username"
          type="email"
          placeholder="your@email.com"
          :disabled="loading"
          @blur="v$.username.$touch"
        />
        <div v-if="v$.username.$error" class="error">
          {{ v$.username.$errors[0].$message }}
        </div>
      </div>

      <!-- Password -->
      <div class="form-group">
        <label for="password">Пароль *</label>
        <input
          id="password"
          v-model="form.password"
          type="password"
          placeholder="••••••••"
          :disabled="loading"
          @blur="v$.password.$touch"
        />
        <div v-if="v$.password.$error" class="error">
          {{ v$.password.$errors[0].$message }}
        </div>
      </div>

      <!-- Password Confirmation -->
      <div class="form-group">
        <label for="passwordConfirm">Подтверждение пароля *</label>
        <input
          id="passwordConfirm"
          v-model="form.passwordConfirm"
          type="password"
          placeholder="••••••••"
          :disabled="loading"
          @blur="v$.passwordConfirm.$touch"
        />
        <div v-if="v$.passwordConfirm.$error" class="error">
          {{ v$.passwordConfirm.$errors[0].$message }}
        </div>
      </div>

      <!-- First Name -->
      <div class="form-group">
        <label for="firstname">Имя</label>
        <input
          id="firstname"
          v-model="form.firstname"
          type="text"
          placeholder="Иван"
          :disabled="loading"
        />
      </div>

      <!-- Last Name -->
      <div class="form-group">
        <label for="lastname">Фамилия</label>
        <input
          id="lastname"
          v-model="form.lastname"
          type="text"
          placeholder="Иванов"
          :disabled="loading"
        />
      </div>

      <!-- Middle Name -->
      <div class="form-group">
        <label for="midname">Отчество</label>
        <input
          id="midname"
          v-model="form.midname"
          type="text"
          placeholder="Иванович"
          :disabled="loading"
        />
      </div>

      <!-- Server Error -->
      <div v-if="error" class="error server-error">
        {{ error }}
      </div>

      <!-- Submit Button -->
      <button type="submit" :disabled="loading || v$.$invalid">
        <span v-if="!loading">Зарегистрироваться</span>
        <span v-else>Загрузка...</span>
      </button>

      <!-- Link to Login -->
      <div class="form-footer">
        <router-link to="/login">Уже есть аккаунт? Войдите</router-link>
      </div>
    </form>
  </div>
</template>

<script setup>
import { reactive, computed } from 'vue';
import { useVuelidate } from '@vuelidate/core';
import { required, minLength, maxLength, email, sameAs } from '@vuelidate/validators';
import { useAuth } from '@/composables/useAuth';

const { register, loading, error } = useAuth();

// Form state
const form = reactive({
  username: '',
  password: '',
  passwordConfirm: '',
  firstname: '',
  lastname: '',
  midname: '',
});

// Validation rules
const rules = computed(() => ({
  username: {
    required,
    email,
    maxLength: maxLength(100),
  },
  password: {
    required,
    minLength: minLength(6),
    maxLength: maxLength(100),
  },
  passwordConfirm: {
    required,
    sameAs: sameAs(form.password),
  },
  firstname: {
    maxLength: maxLength(50),
  },
  lastname: {
    maxLength: maxLength(50),
  },
  midname: {
    maxLength: maxLength(50),
  },
}));

const v$ = useVuelidate(rules, form);

// Submit handler
async function handleSubmit() {
  const isValid = await v$.value.$validate();

  if (!isValid) {
    return;
  }

  // Удаляем passwordConfirm перед отправкой
  const { passwordConfirm, ...userData } = form;

  await register(userData);
}
</script>

<style scoped>
/* Стили аналогичны LoginForm */
</style>
```

### `src/components/TheNavbar.vue`

```vue
<template>
  <nav class="navbar">
    <div class="container">
      <router-link to="/" class="logo">
        MyApp
      </router-link>

      <div class="nav-links">
        <template v-if="isAuthenticated">
          <router-link to="/">Главная</router-link>
          <router-link to="/profile">Профиль</router-link>
          <router-link v-if="isAdmin" to="/admin">Админка</router-link>

          <div class="user-info">
            <span>{{ user?.firstName || user?.username }}</span>
            <button @click="handleLogout" class="btn-logout">
              Выйти
            </button>
          </div>
        </template>

        <template v-else>
          <router-link to="/login">Вход</router-link>
          <router-link to="/register" class="btn-register">
            Регистрация
          </router-link>
        </template>
      </div>
    </div>
  </nav>
</template>

<script setup>
import { useAuth } from '@/composables/useAuth';

const { isAuthenticated, user, logout, isAdmin } = useAuth();

async function handleLogout() {
  if (confirm('Вы уверены, что хотите выйти?')) {
    await logout();
  }
}
</script>

<style scoped>
.navbar {
  background-color: #4CAF50;
  padding: 1rem 0;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logo {
  color: white;
  font-size: 1.5rem;
  font-weight: bold;
  text-decoration: none;
}

.nav-links {
  display: flex;
  gap: 1.5rem;
  align-items: center;
}

.nav-links a {
  color: white;
  text-decoration: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.nav-links a:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

.nav-links a.router-link-active {
  background-color: rgba(255, 255, 255, 0.2);
}

.user-info {
  display: flex;
  gap: 1rem;
  align-items: center;
  color: white;
}

.btn-logout {
  padding: 0.5rem 1rem;
  background-color: rgba(255, 255, 255, 0.2);
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.btn-logout:hover {
  background-color: rgba(255, 255, 255, 0.3);
}

.btn-register {
  background-color: white;
  color: #4CAF50;
  font-weight: 500;
}

.btn-register:hover {
  background-color: #f5f5f5;
}
</style>
```

---

## API Reference

### Endpoints

| Метод | Endpoint | Описание | Требует авторизации |
|-------|----------|----------|---------------------|
| POST | `/api/auth/register` | Регистрация | Нет |
| POST | `/api/auth/login` | Вход | Нет |
| POST | `/api/auth/logout` | Выход (добавляет токен в blacklist) | Нет |

### Request/Response примеры

#### Регистрация

**Request:**
```json
POST /api/auth/register
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

**Error (400 Bad Request):**
```json
{
  "username": "Email не может быть пустым",
  "password": "Пароль должен быть от 6 до 100 символов"
}
```

**Error (409 Conflict):**
```json
{
  "error": "Эта почта уже занята"
}
```

#### Вход

**Request:**
```json
POST /api/auth/login
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

**Error (401 Unauthorized):**
```json
{
  "error": "Пользователь не найден"
}
```
или
```json
{
  "error": "Неправильный пароль"
}
```

---

## Обработка ошибок

### Глобальный обработчик ошибок

Создайте файл `src/utils/errorHandler.js`:

```javascript
import { useToast } from 'vue-toastification';

const toast = useToast();

export function handleApiError(error) {
  if (error.response) {
    const status = error.response.status;
    const data = error.response.data;

    switch (status) {
      case 400:
        // Ошибки валидации
        if (typeof data === 'object' && !data.error) {
          const errors = Object.values(data).join('\n');
          toast.error(errors);
        } else {
          toast.error(data.error || 'Ошибка валидации');
        }
        break;

      case 401:
        toast.error(data.error || 'Не авторизован');
        break;

      case 403:
        toast.error('Доступ запрещен');
        break;

      case 409:
        toast.error(data.error || 'Конфликт данных');
        break;

      case 429:
        toast.warning('Слишком много запросов. Подождите минуту.');
        break;

      case 500:
        toast.error('Ошибка сервера. Попробуйте позже.');
        break;

      default:
        toast.error('Произошла ошибка');
    }
  } else if (error.request) {
    toast.error('Нет связи с сервером');
  } else {
    toast.error(error.message || 'Произошла ошибка');
  }

  console.error('API Error:', error);
}
```

### Использование в компонентах

```javascript
import { handleApiError } from '@/utils/errorHandler';

async function someAction() {
  try {
    await api.post('/endpoint', data);
  } catch (error) {
    handleApiError(error);
  }
}
```

---

## Best Practices

### 1. Безопасность токенов

```javascript
// ✅ Правильно: Токен в localStorage (для SPA)
localStorage.setItem('token', token);

// ✅ Правильно: Токен в памяти (для максимальной безопасности)
// В production можно хранить токен только в памяти
const tokenStore = ref(null); // Вместо localStorage

// ❌ Неправильно: Токен в cookie без httpOnly (уязвимость XSS)
document.cookie = `token=${token}`;
```

### 2. Обработка истечения токена

```javascript
// ✅ Правильно: Используйте Axios interceptors для автоматического logout
api.interceptors.response.use(
  response => response,
  async error => {
    if (error.response?.status === 401) {
      // Токен истек, выходим из системы
      const authStore = useAuthStore();
      authStore.logout();
    }
    return Promise.reject(error);
  }
);

// ❌ Неправильно: Игнорирование 401 ошибок
```

### 3. Rate Limiting

```javascript
// ✅ Правильно: Debounce для кнопок
import { useDebounceFn } from '@vueuse/core';

const debouncedLogin = useDebounceFn(async () => {
  await login(credentials);
}, 1000);

// ❌ Неправильно: Без debounce (можно легко превысить лимит)
```

### 4. Валидация форм

```javascript
// ✅ Правильно: Валидация на клиенте + сервере
const rules = {
  email: { required, email, maxLength: maxLength(100) },
  password: { required, minLength: minLength(6) },
};

// ❌ Неправильно: Только на клиенте или только на сервере
```

### 5. Обработка загрузки

```vue
<!-- ✅ Правильно: Индикатор загрузки -->
<button :disabled="loading">
  <span v-if="!loading">Войти</span>
  <span v-else>Загрузка...</span>
</button>

<!-- ❌ Неправильно: Без индикатора -->
<button>Войти</button>
```

---

## Примеры использования

### Пример 1: Защищенный API запрос

```vue
<script setup>
import { ref, onMounted } from 'vue';
import api from '@/api/axios';
import { handleApiError } from '@/utils/errorHandler';

const userData = ref(null);
const loading = ref(false);

async function fetchUserData() {
  try {
    loading.value = true;
    const response = await api.get('/api/v1/user/profile');
    userData.value = response.data;
  } catch (error) {
    handleApiError(error);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  fetchUserData();
});
</script>
```

### Пример 2: Условный рендеринг по ролям

```vue
<template>
  <div>
    <!-- Только для авторизованных -->
    <div v-if="isAuthenticated">
      <p>Добро пожаловать, {{ user?.firstName }}!</p>
    </div>

    <!-- Только для администраторов -->
    <div v-if="isAdmin">
      <button @click="adminAction">Админ действие</button>
    </div>

    <!-- Только для гостей -->
    <div v-if="!isAuthenticated">
      <router-link to="/login">Войдите</router-link>
    </div>
  </div>
</template>

<script setup>
import { useAuth } from '@/composables/useAuth';

const { isAuthenticated, user, isAdmin } = useAuth();
</script>
```

### Пример 3: Программная навигация с проверкой авторизации

```javascript
import { useAuth } from '@/composables/useAuth';
import { useRouter } from 'vue-router';

const { isAuthenticated } = useAuth();
const router = useRouter();

function goToProtectedPage() {
  if (!isAuthenticated.value) {
    router.push({
      path: '/login',
      query: { redirect: '/protected' },
    });
    return;
  }

  router.push('/protected');
}
```

### Пример 4: Logout при закрытии вкладки

```javascript
// В main.js или App.vue
import { useAuthStore } from '@/stores/auth';

window.addEventListener('beforeunload', () => {
  const authStore = useAuthStore();

  if (authStore.token) {
    // Используем sendBeacon для гарантированной отправки
    navigator.sendBeacon(
      'http://localhost:8080/api/auth/logout',
      JSON.stringify({ token: authStore.token })
    );
  }
});
```

---

## Troubleshooting

### Проблема 1: "CORS error"

**Симптомы:**
```
Access to XMLHttpRequest at 'http://localhost:8080/api/auth/login' from origin 'http://localhost:5173'
has been blocked by CORS policy
```

**Решение:**
1. Убедитесь, что ваш origin добавлен в `SpringSecurityConfig.java`
2. По умолчанию разрешены: `localhost:3000` и `localhost:5173`
3. Если используете другой порт, попросите бэкенд добавить его

### Проблема 2: "Token expired" - получаю 401 ошибку

**Причина:** Токен истек (срок действия 30 дней)

**Решение:**
```javascript
// При получении 401 ошибки пользователь автоматически выходит из системы
// Убедитесь, что interceptor настроен правильно:
api.interceptors.response.use(
  response => response,
  async error => {
    if (error.response?.status === 401) {
      const authStore = useAuthStore();
      authStore.logout();
    }
    return Promise.reject(error);
  }
);

// Пользователю нужно будет войти заново
```

### Проблема 3: "429 Too Many Requests"

**Причина:** Превышен лимит 15 запросов в минуту

**Решение:**
```javascript
// Используйте debounce
import { useDebounceFn } from '@vueuse/core';

const debouncedLogin = useDebounceFn(login, 1000, { maxWait: 5000 });
```

### Проблема 4: Токен не отправляется на сервер

**Проверка:**
1. Откройте DevTools → Network
2. Найдите ваш запрос
3. Проверьте Headers → Request Headers → Authorization
4. Должно быть: `Authorization: Bearer <token>`

**Решение:**
```javascript
// Убедитесь, что токен есть
console.log('Token:', localStorage.getItem('token'));

// Убедитесь, что interceptor настроен правильно
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
```

### Проблема 5: Store не реактивен

**Симптомы:** Изменения в store не отображаются в компонентах

**Решение:**
```javascript
// ❌ Неправильно
const user = authStore.user;

// ✅ Правильно
const user = computed(() => authStore.user);

// Или используйте storeToRefs
import { storeToRefs } from 'pinia';
const { user, isAuthenticated } = storeToRefs(authStore);
```

---

## Environment Variables

### `.env.development`

```env
VITE_API_URL=http://localhost:8080/api
VITE_APP_NAME=MyApp
```

### `.env.production`

```env
VITE_API_URL=https://your-production-api.com/api
VITE_APP_NAME=MyApp
```

### Использование

```javascript
const API_URL = import.meta.env.VITE_API_URL;
```

---

## Testing

### Пример unit теста для auth store

```javascript
import { setActivePinia, createPinia } from 'pinia';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useAuthStore } from '@/stores/auth';
import { authApi } from '@/api/auth';

// Mock API
vi.mock('@/api/auth');

describe('Auth Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
  });

  it('should login successfully', async () => {
    const store = useAuthStore();

    const mockResponse = {
      type: 'Bearer',
      token: 'mock-jwt-token',
    };

    authApi.login.mockResolvedValue(mockResponse);

    await store.login({ login: 'test@test.com', password: 'password' });

    expect(store.isAuthenticated).toBe(true);
    expect(store.token).toBe('mock-jwt-token');
    expect(localStorage.getItem('token')).toBe('mock-jwt-token');
  });

  it('should handle login error', async () => {
    const store = useAuthStore();

    authApi.login.mockRejectedValue({
      response: { data: { error: 'Invalid credentials' } },
    });

    const result = await store.login({ login: 'test@test.com', password: 'wrong' });

    expect(result.success).toBe(false);
    expect(store.isAuthenticated).toBe(false);
  });

  it('should logout successfully', async () => {
    const store = useAuthStore();

    store.setToken('mock-jwt-token');
    await store.logout();

    expect(store.isAuthenticated).toBe(false);
    expect(store.token).toBe(null);
    expect(localStorage.getItem('token')).toBe(null);
  });
});
```

---

## Дополнительные возможности

### 1. Token Blacklist

Уже реализовано! При вызове `/api/auth/logout` токен добавляется в черный список и становится недействительным.

### 2. Remember Me

```vue
<script setup>
const rememberMe = ref(false);

async function login(credentials) {
  const result = await authStore.login(credentials);

  if (result.success && !rememberMe.value) {
    // Установить короткий срок жизни для сессии
    sessionStorage.setItem('sessionOnly', 'true');
  }
}

// При загрузке проверяем
onMounted(() => {
  if (sessionStorage.getItem('sessionOnly')) {
    // Выходим при закрытии вкладки
    window.addEventListener('beforeunload', () => {
      authStore.logout();
    });
  }
});
</script>
```

### 3. Двухфакторная аутентификация (2FA)

Для реализации 2FA потребуются дополнительные endpoints на бэкенде.

---

## Swagger UI

Полная документация API доступна по адресу:
```
http://localhost:8080/swagger-ui/index.html
```

---

## Контакты и поддержка

При возникновении проблем:
1. Проверьте логи в DevTools (Console и Network)
2. Проверьте Swagger UI для тестирования endpoints
3. Убедитесь, что все environment variables настроены
4. Свяжитесь с backend командой

---

**Версия:** 3.0
**Последнее обновление:** 2025-11-10
**Совместимость:**
- Vue.js: ^3.4.0
- Pinia: ^2.1.7
- Axios: ^1.6.0
- Backend API: v2.0 (Single Token)
