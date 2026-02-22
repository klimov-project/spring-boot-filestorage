import { API_DIRECTORY } from "../../../../UrlConstants.jsx";
import { throwSpecifyException } from "../../../../exception/ThrowSpecifyException.jsx";
import { mapToFrontFormat } from "../../../util/FormatMapper.js";

export const sendGetFolderContent = async (folderName = "", options = {}) => {
    const normalizedFolderName = folderName || "/";

    // Мок-режим
    if (import.meta.env.VITE_MOCK_FETCH_CALLS) {
        console.log("📦 [MOCK] Get folder content:", folderName || "root");
        await new Promise(resolve => setTimeout(resolve, 300)); // Симуляция задержки

        let mockedResponse = [];
        if (folderName === "") {
            mockedResponse = [
                { path: "", name: "mocked_file.txt", size: 100, type: "FILE" },
                { path: "", name: "mocked_folder1/", type: "DIRECTORY" }
            ];
        } else {
            mockedResponse = [
                { path: "", name: "mocked_inner_file.txt", size: 100, type: "FILE" }
            ];
        }

        return mockedResponse.map(mapToFrontFormat);
    }

    console.log(`📂 Запрос содержимого: "${folderName || 'корень'}"`);
    const params = new URLSearchParams({ path: normalizedFolderName });
    const url = `${API_DIRECTORY}?${params}`;

    const response = await fetch(url, {
        method: 'GET',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        signal: options.signal // Поддержка отмены запроса
    });

    if (!response.ok) {
        const errorMessage = await response.json().catch(() => ({}));
        throwSpecifyException(response.status, errorMessage);
    }

    const directory = await response.json();
    console.log(`✅ Получено ${directory.length} элементов из:`, folderName || "корень");

    return directory.map(mapToFrontFormat);
};