import { Box, CircularProgress, Container } from '@mui/material';
import { useStorageNavigation } from '../context/Storage/StorageNavigationProvider.jsx';
import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { ObjectsContainer } from '../components/FileBrowser/ObjectsContainer.jsx';
import { FileBrowserHeader } from '../components/FileBrowserHeader/FileBrowserHeader.jsx';
import { FileTasksModal } from '../modals/FileTasksModal/FileTasksModal.jsx';
import { FileUploadDraggableArea } from '../components/InputElements/Upload/FileUploadDraggableArea.jsx';
import { SearchBrowserHeader } from '../components/SearchBrowserHeader/SearchBrowserHeader.jsx';

const LoadingBox = () => {
  return (
    <Box
      sx={{
        width: '100%',
        pt: 10,
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
      }}
    >
      <CircularProgress />
    </Box>
  );
};

export default function Files() {
  const {
    folderContentLoading,
    loadFolder,
    isSearchMode,
  } = useStorageNavigation();
  const location = useLocation();
  const lastLoadedPathRef = useRef('');
  const isInitialMountRef = useRef(true);

  const loadFolderFromPath = () => {
    let extracted = location.pathname.replace(/^\/files/, '');
    extracted = extracted.replace('/', '');
    let decodedUrl = decodeURIComponent(extracted);

    console.log(
      '📂 Загружаем путь:',
      decodedUrl,
      'предыдущий:',
      lastLoadedPathRef.current,
    );
    lastLoadedPathRef.current = decodedUrl;
    isInitialMountRef.current = false;
    loadFolder(decodedUrl);
  };

  useEffect(() => {
    if (
      isInitialMountRef.current && lastLoadedPathRef.current !== location.pathname
    ) {
      loadFolderFromPath();
    }
  }, [location.pathname]); // Только одна зависимость

  const dragRef = useRef();

  const [isDragging, setIsDragging] = useState(false);

  return (
    <Box
      ref={dragRef}
      sx={{
        height: '100%',
      }}
    >
      {!isSearchMode ? <FileBrowserHeader /> : <SearchBrowserHeader />}

      <Container disableGutters sx={{ mt: 23, width: '100%' }}>
        <Box sx={{ p: 1, pt: 1 }}>
          {folderContentLoading ? <LoadingBox /> : <ObjectsContainer />}
        </Box>
      </Container>

      <FileUploadDraggableArea
        dragRef={dragRef}
        isDragging={isDragging}
        setIsDragging={setIsDragging}
      />

      <FileTasksModal />
    </Box>
  );
}
