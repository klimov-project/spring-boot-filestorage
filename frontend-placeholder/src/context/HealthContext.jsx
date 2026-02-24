import { createContext, useContext, useState } from 'react';

const HealthContext = createContext();

export const useHealth = () => {
  const context = useContext(HealthContext);
  if (!context) {
    throw new Error('useHealth must be used within a HealthProvider');
  }
  return context;
};

export const HealthProvider = ({ children }) => {
  const [isBackendAlive, setIsBackendAlive] = useState(true);
  const [isHealthChecked, setIsHealthChecked] = useState(false);

  return (
    <HealthContext.Provider
      value={{
        isBackendAlive,
        setIsBackendAlive,
        isHealthChecked,
        setIsHealthChecked,
      }}
    >
      {children}
    </HealthContext.Provider>
  );
};
