import { API_USER_INFO } from "../../../../UrlConstants.jsx";
import UnauthorizedException from "../../../../exception/UnauthorizedException.jsx";

// Функция для проверки, доступен ли бэкенд
const isBackendAvailable = () => {
    return window.__BACKEND_ALIVE__ !== false;
};

export const checkSession = async () => {
    // Проверяем, жив ли бэкенд перед выполнением запроса
    if (!isBackendAvailable()) {
        console.log("⏭️ Skipping session check - backend is down");
        // Можно либо вернуть заглушку, либо выбросить специальное исключение
        throw new Error("Backend is unavailable");
    }

    if (import.meta.env.VITE_MOCK_FETCH_CALLS) {
        console.log("Mocked fetch call for check session");
        return {
            username: "mocked_user"
        };
    }

    try {
        const response = await fetch(API_USER_INFO, {
            method: 'GET',
            credentials: 'include'
        });

        console.log("Проверка сессии: ");
        console.log(response);

        if (!response.ok) {
            console.log("Ошибка со статусом: " + response.status);
            // Проверяем, не связана ли ошибка с недоступностью сервера
            if (response.status >= 500) {
                window.__BACKEND_ALIVE__ = false;
            }
            const error = await response.json();
            throw new UnauthorizedException(error.detail);
        }

        window.__BACKEND_ALIVE__ = true;
        return await response.json();
    } catch (error) {
        // Если ошибка сети (бэкенд недоступен)
        if (error.name === 'TypeError' && error.message === 'Failed to fetch') {
            window.__BACKEND_ALIVE__ = false;
            throw new Error("Backend is unavailable");
        }
        throw error;
    }
};