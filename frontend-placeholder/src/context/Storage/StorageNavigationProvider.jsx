import React, { createContext, useContext, useRef, useState } from 'react';
import { sendGetFolderContent } from '../../services/fetch/auth/storage/SendGetFolderContent.js';
import { useStorageSelection } from './StorageSelectionProvider.jsx';
import { Box } from '@mui/material';
import ConflictException from '../../exception/ConflictException.jsx';
import NotFoundException from '../../exception/NotFoundException.jsx';
import BadRequestException from '../../exception/BadRequestException.jsx';
import { useNotification } from '../Notification/NotificationProvider.jsx';
import { useNavigate } from 'react-router-dom';

const CloudStorageContext = createContext();

export const useStorageNavigation = () => useContext(CloudStorageContext);

export const StorageNavigationProvider = ({ children }) => {
  const { setSelectedIds } = useStorageSelection();

  const [folderContentLoading, setFolderContentLoading] = useState(false);
  const [folderPath, setFolderPath] = useState(['']);
  const currentFolder = folderPath[folderPath.length - 1];
  const isRootFolder = currentFolder === '';
  const currentPath = folderPath.join('');
  const currentPathRef = useRef();

  const [folderContent, setFolderContent] = useState([]);
  const [searchedContent, setSearchedContent] = useState([]);
  const [searchName, setSearchName] = useState('');

  const { showWarn, showError } = useNotification();
  const navigate = useNavigate();

  const isSearchMode = searchedContent.length > 0;

  const goToPrevFolder = async () => {
    setFolderContentLoading(true);

    if (folderPath.length === 1) {
      return;
    }
    const updatedPath = folderPath.slice(0, -1);
    setFolderPath(updatedPath);
    await updateCurrentFolderContent(updatedPath);
    setFolderContentLoading(false);
  };

  const goToFolder = async (folderName) => {
    setFolderContentLoading(true);

    const updatedPath = [...folderPath, folderName];
    setFolderPath(updatedPath);
    await updateCurrentFolderContent(updatedPath);

    setFolderContentLoading(false);
  };

  const createSpoofObject = (object) => {
    setFolderContent([...folderContent, object]);
  };

  const getObjectByPath = (path) => {
    return folderContent.find((object) => object.path === path);
  };

  const updateCurrentFolderContent = async (path = ['']) => {
    setSelectedIds([]);
    const fullPath = path.join('');
    try {
      let content = await sendGetFolderContent(fullPath);
      setFolderContent(content);

      const base = import.meta.env.VITE_BASE;
      window.history.pushState(null, '', base + 'files/' + fullPath);
    } catch (error) {
      switch (true) {
        case error instanceof ConflictException:
        case error instanceof NotFoundException:
        case error instanceof BadRequestException:
          navigate('/files');
          showWarn(error.message);
          break;
        default:
          showError('Не удалось создать папку. Попробуйте позже');
          console.log('Unknown error occurred! ', error);
      }
    }
  };

  const loadFolder = async (url = '') => {
    setSelectedIds([]);
    setFolderContentLoading(true);
    try {
      let content = await sendGetFolderContent(url);
      setFolderContent(content);

      const base = import.meta.env.VITE_BASE;
      window.history.pushState(null, '', base + 'files/' + url);
    } catch (error) {
      switch (true) {
        case error instanceof ConflictException:
        case error instanceof NotFoundException:
        case error instanceof BadRequestException:
          navigate('/files');
          showWarn(error.message);
          break;
        default:
          showError('Не удалось создать папку. Попробуйте позже');
          console.log('Unknown error occurred! ', error);
      }
    } finally {
      if (url === '') {
        setFolderPath(['']);
      } else {
        const parts = url.split('/').filter(Boolean);
        const result = parts.map((part) => `${part}/`);
        setFolderPath(['', ...result]);
      }
      setFolderContentLoading(false);
    }
  };

  return (
    <CloudStorageContext.Provider
      value={{
        folderContentLoading,
        folderContent,
        folderPath,
        isRootFolder,
        currentFolder,
        currentPath,
        goToPrevFolder,
        goToFolder,
        loadFolder,
        currentPathRef,
        createSpoofObject,

        searchedContent,
        setSearchedContent,
        isSearchMode,
        searchName,
        setSearchName,

        getObjectByPath,
      }}
    >
      {children}
      <Box
        ref={currentPathRef}
        sx={{ color: 'transparent' }}
        className={'hiddenPath'}
      >
        {currentPath}
      </Box>
    </CloudStorageContext.Provider>
  );
};
