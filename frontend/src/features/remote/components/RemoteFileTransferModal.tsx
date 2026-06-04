import { useState } from "react";
import axios from "axios";
import { AlertTriangle, CheckCircle, FileText, Upload, X } from "lucide-react";
import { uploadFileToPc } from "../remoteApi";
import type { StoredFileInfo } from "../remoteTypes";

type RemoteFileTransferModalProps = {
    open: boolean;
    pcId: number;
    pcName: string;
    onClose: () => void;
};

export function RemoteFileTransferModal({
                                            open,
                                            pcId,
                                            pcName,
                                            onClose,
                                        }: RemoteFileTransferModalProps) {
    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [uploadedFile, setUploadedFile] = useState<StoredFileInfo | null>(null);
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);
    const [progress, setProgress] = useState<number | null>(null);

    if (!open) {
        return null;
    }

    async function handleUpload() {
        setError("");
        setUploadedFile(null);

        if (!selectedFile) {
            setError("Выберите файл для отправки");
            return;
        }

        try {
            setLoading(true);
            setProgress(0);

            const result = await uploadFileToPc(pcId, selectedFile, setProgress);

            setUploadedFile(result);
            setSelectedFile(null);
        } catch (e) {
            if (axios.isAxiosError(e)) {
                const message =
                    e.response?.data?.message ||
                    e.response?.data?.error ||
                    e.response?.data ||
                    e.message;

                setError(`Ошибка отправки файла: ${message}`);
            } else {
                setError("Не удалось отправить файл");
            }
        } finally {
            setLoading(false);

            setTimeout(() => {
                setProgress(null);
            }, 1200);
        }
    }

    function handleClose() {
        setSelectedFile(null);
        setUploadedFile(null);
        setError("");
        setProgress(null);
        onClose();
    }

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 px-5 py-8 backdrop-blur-sm">
            <section className="w-full max-w-2xl rounded-[32px] border border-slate-200 bg-white p-7 shadow-2xl">
                <header className="mb-7 flex items-start justify-between gap-5">
                    <div>
                        <div className="mb-3 inline-flex rounded-full bg-blue-50 px-4 py-2 text-sm font-black text-blue-700">
                            Передача файла
                        </div>

                        <h2 className="text-3xl font-black text-slate-950">
                            Отправка на {pcName}
                        </h2>

                        <p className="mt-2 text-sm font-semibold text-slate-500">
                            Файл будет зашифрован, загружен на сервер и отправлен агенту ПК.
                        </p>
                    </div>

                    <button
                        type="button"
                        onClick={handleClose}
                        className="flex h-12 w-12 items-center justify-center rounded-2xl border border-slate-200 bg-white text-slate-500 transition hover:bg-slate-50 hover:text-slate-950"
                    >
                        <X size={22} />
                    </button>
                </header>

                <div className="rounded-[28px] border border-dashed border-slate-300 bg-slate-50 p-6">
                    <div className="mb-5 flex h-16 w-16 items-center justify-center rounded-3xl bg-blue-100 text-blue-700">
                        <Upload size={30} />
                    </div>

                    <label className="mb-3 block text-sm font-black uppercase tracking-wide text-slate-500">
                        Файл
                    </label>

                    <input
                        type="file"
                        onChange={(event) => {
                            setSelectedFile(event.target.files?.[0] ?? null);
                            setError("");
                            setUploadedFile(null);
                            setProgress(null);
                        }}
                        className="block w-full cursor-pointer rounded-2xl border border-slate-200 bg-white p-4 text-sm font-semibold text-slate-700 file:mr-4 file:rounded-xl file:border-0 file:bg-blue-600 file:px-4 file:py-2 file:font-bold file:text-white"
                    />

                    {selectedFile && (
                        <div className="mt-5 rounded-2xl border border-slate-200 bg-white p-4">
                            <div className="flex items-start gap-3">
                                <FileText className="mt-0.5 text-blue-600" size={22} />

                                <div className="min-w-0">
                                    <div className="truncate font-black text-slate-950">
                                        {selectedFile.name}
                                    </div>
                                    <div className="mt-1 text-sm font-semibold text-slate-500">
                                        {formatFileSize(selectedFile.size)}
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}

                    {progress !== null && (
                        <div className="mt-5 rounded-2xl border border-blue-100 bg-white p-4">
                            <div className="mb-2 flex justify-between text-sm font-black text-blue-700">
                                <span>Загрузка</span>
                                <span>{progress}%</span>
                            </div>

                            <div className="h-3 overflow-hidden rounded-full bg-blue-100">
                                <div
                                    className="h-full rounded-full bg-blue-600 transition-all"
                                    style={{ width: `${progress}%` }}
                                />
                            </div>
                        </div>
                    )}
                </div>

                {error && (
                    <div className="mt-5 flex gap-3 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm font-bold text-red-700">
                        <AlertTriangle size={20} />
                        <span>{error}</span>
                    </div>
                )}

                {uploadedFile && (
                    <div className="mt-5 rounded-2xl border border-emerald-200 bg-emerald-50 p-4">
                        <div className="flex gap-3 text-emerald-700">
                            <CheckCircle size={22} />
                            <div>
                                <div className="font-black">Файл отправлен</div>

                                <div className="mt-2 grid gap-1 text-sm font-semibold">
                                    <div>Название: {uploadedFile.fileName}</div>
                                    <div>Размер: {formatFileSize(uploadedFile.fileSize)}</div>
                                    <div>ID: {uploadedFile.fileId}</div>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                <div className="mt-7 grid grid-cols-2 gap-3 max-sm:grid-cols-1">
                    <button
                        type="button"
                        onClick={handleClose}
                        className="inline-flex min-h-12 items-center justify-center rounded-2xl border border-slate-200 bg-white px-5 py-3 font-black text-slate-700 transition hover:bg-slate-50"
                    >
                        Закрыть
                    </button>

                    <button
                        type="button"
                        onClick={handleUpload}
                        disabled={loading}
                        className="inline-flex min-h-12 items-center justify-center gap-2 rounded-2xl bg-blue-600 px-5 py-3 font-black text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:shadow-none"
                    >
                        <Upload size={20} />
                        {loading ? "Отправка..." : "Отправить файл"}
                    </button>
                </div>
            </section>
        </div>
    );
}

function formatFileSize(size: number): string {
    if (size < 1024) {
        return `${size} Б`;
    }

    if (size < 1024 * 1024) {
        return `${(size / 1024).toFixed(1)} КБ`;
    }

    return `${(size / 1024 / 1024).toFixed(1)} МБ`;
}