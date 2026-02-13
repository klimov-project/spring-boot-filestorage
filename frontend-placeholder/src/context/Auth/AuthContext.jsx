import React, {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
  useRef,
} from 'react';
import { checkSession } from '../../services/fetch/auth/user/CheckSession.js';
import { useLocation, useNavigate } from 'react-router-dom';
import { useNotification } from '../Notification/NotificationProvider.jsx';

const AuthContext = createContext();

export const useAuthContext = () => useContext(AuthContext);

export const AuthProvider = ({ children }) => {
  const [auth, setAuth] = useState(extractAuthUser);
  const urlLocation = useLocation();
  const navigate = useNavigate();
  const { showError } = useNotification();

  // Флаг для предотвращения повторных вызовов при монтировании
  const isMounted = useRef(false);

  function extractAuthUser() {
    const isAuth = localStorage.getItem('isAuthenticated');
    const userData = localStorage.getItem('user');

    if (isAuth && userData) {
      return { isAuthenticated: true, user: JSON.parse(userData) };
    }
    return { isAuthenticated: false, user: null };
  }

  const login = (userInfo) => {
    localStorage.setItem('isAuthenticated', 'true');
    localStorage.setItem('user', JSON.stringify(userInfo));
    setAuth({ isAuthenticated: true, user: userInfo });
  };

  const logout = () => {
    localStorage.removeItem('isAuthenticated');
    localStorage.removeItem('user');
    setAuth({ isAuthenticated: false, user: null });
  };

  // Мемоизируем функцию, чтобы избежать лишних ререндеров
  const validateSession = useCallback(async () => {
    if (auth.isAuthenticated) {
      try {
        const user = await checkSession();
        console.log('Session check:', user);

        // Проверяем, изменились ли данные пользователя
        if (user && JSON.stringify(user) !== JSON.stringify(auth.user)) {
          login(user);
        }
      } catch (error) {
        console.log('Session validation failed:', error);
        logout();

        // Не перенаправляем, если уже на странице логина
        if (urlLocation.pathname !== '/login') {
          setTimeout(() => {
            navigate('/login');
            showError('Session is expired! Please login again', 4000);
          }, 300);
        }
      }
    }
  }, [
    auth.isAuthenticated,
    auth.user,
    navigate,
    showError,
    urlLocation.pathname,
  ]);

  const validateCookieIsAlive = useCallback(async () => {
    // Проверяем только если пользователь не авторизован в localStorage
    if (!auth.isAuthenticated) {
      try {
        const user = await checkSession();
        if (user) {
          login(user);
          return true;
        }
      } catch (error) {
        console.log('No active session found');
        return false;
      }
    }
    return false;
  }, [auth.isAuthenticated]);

  // 1️⃣ Эффект для проверки сессии при монтировании (только 1 раз)
  useEffect(() => {
    // Предотвращаем двойной вызов в StrictMode
    if (isMounted.current) return;
    isMounted.current = true;

    const initAuth = async () => {
      // Сначала проверяем, есть ли живая сессия у неавторизованных
      if (!auth.isAuthenticated) {
        await validateCookieIsAlive();
      }
      // Затем валидируем существующую сессию
      await validateSession();
    };

    initAuth();
  }, []); // Пустой массив зависимостей - выполнится 1 раз

  // 2️⃣ Эффект для периодической проверки сессии при навигации
  useEffect(() => {
    // Проверяем сессию не при каждом переходе, а каждые 3 перехода
    const visits = parseInt(localStorage.getItem('pageVisits') || '0');
    const newVisits = visits + 1;

    if (newVisits >= 3) {
      validateSession();
      localStorage.setItem('pageVisits', '0');
    } else {
      localStorage.setItem('pageVisits', newVisits.toString());
    }
  }, [urlLocation.pathname, validateSession]);

  return (
    <AuthContext.Provider value={{ auth, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};
