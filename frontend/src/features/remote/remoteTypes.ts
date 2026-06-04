export type StoredFileInfo = {
    fileId: string;
    fileName: string;
    fileSize: number;
    downloadUrl: string;
    encryptionKey: string;
    iv: string;
    downloaded: boolean;
    createdAt: string | null;
    downloadedAt: string | null;
};