import { apiClient } from "../../shared/api/apiClient";
import type { StoredFileInfo } from "./remoteTypes";

export async function uploadFileToPc(
    pcId: number,
    file: File,
    onProgress?: (progress: number) => void,
): Promise<StoredFileInfo> {
    const formData = new FormData();

    formData.append("pcId", String(pcId));
    formData.append("file", file);

    const response = await apiClient.post<StoredFileInfo>("/api/files/upload", formData, {
        onUploadProgress: (event) => {
            if (!event.total || !onProgress) {
                return;
            }

            const progress = Math.round((event.loaded * 100) / event.total);
            onProgress(progress);
        },
    });

    return response.data;
}