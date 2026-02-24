import './App.css';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { BaseLayout } from './pages/BaseLayout.jsx';
import { GlobalProvider } from './context/GlobalProvider.jsx';
import { SignIn } from './pages/SignIn.jsx';
import { SignUp } from './pages/SignUp.jsx';
import Index from './pages/Index.jsx';
import UnavailableAfterLoginRoute from './context/Auth/UnavailableAfterLoginRoute.jsx';
import AvailableAfterLoginRoute from './context/Auth/AvailableAfterLoginRoute.jsx';
import Files from './pages/Files.jsx';
import ErrorPage from './pages/ErrorPage.jsx';
import MaintenancePage from './pages/MaintenancePage.jsx';
import HealthChecker from './components/HealthChecker.jsx';

function App() {
  return (
    <BrowserRouter basename={import.meta.env.VITE_BASE}>
      <HealthChecker>
        <GlobalProvider>
          <Routes>
            <Route path="/maintenance" element={<MaintenancePage />} />
            <Route element={<BaseLayout />}>
              <Route index element={<Index />} />
              <Route path="*" element={<ErrorPage />} />

              <Route
                path="login"
                element={
                  <UnavailableAfterLoginRoute>
                    <SignIn />
                  </UnavailableAfterLoginRoute>
                }
              />

              <Route
                path="registration"
                element={
                  <UnavailableAfterLoginRoute>
                    <SignUp />
                  </UnavailableAfterLoginRoute>
                }
              />

              <Route
                path="files/*"
                element={
                  <AvailableAfterLoginRoute>
                    <Files />
                  </AvailableAfterLoginRoute>
                }
              />
            </Route>
          </Routes>
        </GlobalProvider>
      </HealthChecker>
    </BrowserRouter>
  );
}

export default App;
