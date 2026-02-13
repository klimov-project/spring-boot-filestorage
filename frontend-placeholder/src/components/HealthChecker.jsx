import { useEffect, useRef } from 'react';

const HealthChecker = () => {
  const isChecked = useRef(false);

  useEffect(() => {
    // Проверяем только один раз за всё время жизни приложения
    if (isChecked.current) return;
    isChecked.current = true;

    const checkHealth = async () => {
      try {
        const response = await fetch('/api/health', {
          method: 'GET',
          credentials: 'include',
        });

        if (response.ok) {
          const data = await response.text();
          console.log('✅ Backend is up:', data);
        } else {
          console.warn('⚠️ Health check failed:', response.status);
        }
      } catch (error) {
        console.error('❌ Backend is down:', error.message);
      }
    };

    checkHealth();
  }, []); // Пустой массив - выполнится 1 раз

  return null; // Компонент ничего не рендерит
};

export default HealthChecker;
