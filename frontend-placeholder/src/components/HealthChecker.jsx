import { useEffect, useState, useRef } from 'react';
import { CircularProgress, Box } from '@mui/material';
import MaintenancePage from '../pages/MaintenancePage';

const HealthChecker = ({ children }) => {
  const [isLoading, setIsLoading] = useState(true);
  const [isBackendAlive, setIsBackendAlive] = useState(true);
  const isChecked = useRef(false);

  useEffect(() => {
    if (isChecked.current) return;
    isChecked.current = true;

    const checkHealth = async () => {
      try {
        // console.log('🔍 Checking backend health...');
        const response = await fetch('/api/health', {
          method: 'GET',
          credentials: 'include',
          signal: AbortSignal.timeout(5000),
        });

        if (response && response.ok) {
          const data = await response.text();
          // console.log('✅ Backend is up:', data);
          setIsBackendAlive(true);
        } else {
          console.warn('⚠️ Health check failed:', response?.status);
          setIsBackendAlive(false);
        }
      } catch (error) {
        console.error('❌ Backend is down:', error.message);
        setIsBackendAlive(false);
      } finally {
        setIsLoading(false);
      }
    };

    checkHealth();
  }, []);

  if (isLoading) {
    return (
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          height: '100vh',
        }}
      >
        <CircularProgress />
      </Box>
    );
  }

  // Если бэкенд не жив, показываем страницу maintenance
  if (!isBackendAlive) {
    return <MaintenancePage />;
  }

  // Если бэкенд жив - рендерим приложение
  return children;
};

export default HealthChecker;
