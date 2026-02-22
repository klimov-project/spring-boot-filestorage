import React, { createContext, useContext, useState } from 'react';

const CloudStorageContext = createContext();

export const useStorageSelection = () => useContext(CloudStorageContext);

function normalizePath(path) {
  return typeof path === 'string' && path.startsWith('/')
    ? path.slice(1)
    : path;
}

export const StorageSelectionProvider = ({ children }) => {
  const [isSelectionMode, setSelectionMode] = useState(false);
  const [selectedIds, setSelectedIds] = useState([]);
  const [isCutMode, setCutMode] = useState(false);
  const [bufferIds, setBufferIds] = useState([]);

  const setSelectedIdsNormalized = (ids) => {
    if (Array.isArray(ids)) {
      setSelectedIds(ids.map(normalizePath));
    } else if (typeof ids === 'function') {
      setSelectedIds((prev) => {
        const result = ids(prev);
        return Array.isArray(result) ? result.map(normalizePath) : [];
      });
    } else {
      setSelectedIds([]);
    }
  };

  const startCutting = () => {
    setBufferIds(selectedIds);
    setCutMode(true);
    setSelectedIds([]);
    setSelectionMode(false);
  };

  const endCutting = () => {
    setBufferIds([]);
    setCutMode(false);
  };

  return (
    <CloudStorageContext.Provider
      value={{
        isSelectionMode,
        setSelectionMode,
        selectedIds,
        setSelectedIds: setSelectedIdsNormalized,
        bufferIds,
        isCutMode,
        startCutting,
        endCutting,
      }}
    >
      {children}
    </CloudStorageContext.Provider>
  );
};
