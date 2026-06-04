import { useEffect, useMemo, useState } from "react";
import type { DragEvent } from "react";
import {
    Download,
    FileText,
    Loader2,
    Send,
    UploadCloud,
    X,
} from "lucide-react";
import {
    downloadEducationFile,
    getEducationFiles,
    uploadEducationFile,
} from "../educationApi";
import type {
    EducationFileResponse,
    EducationParticipantResponse,
} from "../educationTypes";
import {
    formatEducationTime,
    getParticipantDisplayName,
} from "../educationMappers";

type EducationFilesModalProps = {
    open: boolean;
    sessionCode: string;
    participants: EducationParticipantResponse[];
    onClose: () => void;
};

export function EducationFilesModal({
                                        open,
                                        sessionCode,
                                        participants,
                                        onClose,
                                    }: EducationFilesModalProps) {
    const [files, setFiles] = useState<EducationFileResponse[]>([]);
    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [recipientId, setRecipientId] = useState<string>("all");
    const [loading, setLoading] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState("");

    const availableRecipients = useMemo(() => {
        return participants.filter((participant) => participant.id);
    }, [participants]);

    useEffect(() => {
        if (open) {
            void loadFiles();
        }
    }, [open, sessionCode]);

    async function loadFiles() {
        try {
            setLoading(true);
            setError("");

            const result = await getEducationFiles(sessionCode);
            setFiles(result);
        } catch (e) {
            setError(e instanceof Error ? e.message : "Не удалось загрузить файлы");
        } finally {
            setLoading(false);
        }
    }

    async function uploadFile() {
        if (!selectedFile) {
            setError("Выберите файл для отправки");
            return;
        }

        try {
            setUploading(true);
            setError("");

            await uploadEducationFile({
                sessionCode,
                file: selectedFile,
                recipientId: recipientId === "all" ? null : Number(recipientId),
            });

            setSelectedFile(null);
            setRecipientId("all");
            await loadFiles();
        } catch (e) {
            setError(e instanceof Error ? e.message : "Не удалось отправить файл");
        } finally {
            setUploading(false);
        }
    }

    async function downloadFile(file: EducationFileResponse) {
        const fileId = file.id || file.fileId;

        if (!fileId) {
            setError("У файла нет идентификатора для скачивания");
            return;
        }

        try {
            setError("");
            await downloadEducationFile(fileId, getFileName(file));
        } catch (e) {
            setError(e instanceof Error ? e.message : "Не удалось скачать файл");
        }
    }

    function handleDrop(event: DragEvent<HTMLDivElement>) {
        event.preventDefault();

        const file = event.dataTransfer.files?.[0];

        if (file) {
            setSelectedFile(file);
        }
    }

    if (!open) {
        return null;
    }

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-6 backdrop-blur-sm"
            onMouseDown={(event) => {
                if (event.target === event.currentTarget) {
                    onClose();
                }
            }}
        >
            <section className="w-full max-w-5xl overflow-hidden rounded-[34px] border border-slate-300 bg-white shadow-2xl">
                <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-7 py-6">
                    <div>
                        <div className="mb-2 inline-flex rounded-xl bg-blue-50 px-3 py-1 text-xs font-black uppercase tracking-wide text-blue-700">
                            Передача файлов
                        </div>

                        <h2 className="text-3xl font-black text-slate-950">
                            Файлы учебной сессии
                        </h2>

                        <p className="mt-2 text-sm font-semibold text-slate-500">
                            Можно отправлять файлы всем участникам или конкретному пользователю.
                        </p>
                    </div>

                    <button
                        type="button"
                        onClick={onClose}
                        className="flex h-11 w-11 items-center justify-center rounded-2xl border border-slate-300 bg-white text-slate-500 transition hover:bg-slate-50 hover:text-slate-900"
                    >
                        <X size={22} />
                    </button>
                </div>

                <div className="grid grid-cols-[360px_minmax(0,1fr)] gap-6 p-7 max-lg:grid-cols-1">
                    <div className="rounded-[28px] border border-slate-300 bg-slate-50 p-5">
                        <h3 className="mb-4 text-xl font-black text-slate-950">
                            Отправить файл
                        </h3>

                        <div
                            onDragOver={(event) => event.preventDefault()}
                            onDrop={handleDrop}
                            className="flex min-h-[170px] flex-col items-center justify-center rounded-[24px] border-2 border-dashed border-blue-300 bg-white p-5 text-center"
                        >
                            <UploadCloud size={44} className="mb-3 text-blue-600" />

                            <p className="font-black text-slate-800">
                                Перетащите файл сюда
                            </p>

                            <p className="mt-1 text-sm font-semibold text-slate-500">
                                или выберите вручную
                            </p>

                            <label className="mt-4 cursor-pointer rounded-2xl bg-blue-600 px-5 py-3 text-sm font-black text-white hover:bg-blue-700">
                                Выбрать файл
                                <input
                                    type="file"
                                    className="hidden"
                                    onChange={(event) => {
                                        const file = event.target.files?.[0];

                                        if (file) {
                                            setSelectedFile(file);
                                        }
                                    }}
                                />
                            </label>
                        </div>

                        {selectedFile && (
                            <div className="mt-4 rounded-2xl border border-blue-200 bg-blue-50 p-4">
                                <div className="text-sm font-black text-blue-800">
                                    {selectedFile.name}
                                </div>

                                <div className="mt-1 text-xs font-bold text-blue-600">
                                    {formatFileSize(selectedFile.size)}
                                </div>
                            </div>
                        )}

                        <div className="mt-4">
                            <label className="mb-2 block text-sm font-black text-slate-700">
                                Получатель
                            </label>

                            <select
                                value={recipientId}
                                onChange={(event) => setRecipientId(event.target.value)}
                                className="h-12 w-full rounded-2xl border border-slate-300 bg-white px-4 text-sm font-bold text-slate-800 outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                            >
                                <option value="all">Всем участникам</option>

                                {availableRecipients.map((participant) => (
                                    <option key={participant.id} value={participant.id}>
                                        {getParticipantDisplayName(participant)}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <button
                            type="button"
                            onClick={uploadFile}
                            disabled={uploading}
                            className="mt-5 inline-flex h-12 w-full items-center justify-center gap-3 rounded-2xl bg-blue-600 px-5 text-sm font-black text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700 disabled:opacity-60"
                        >
                            {uploading ? <Loader2 className="animate-spin" size={20} /> : <Send size={20} />}
                            Отправить файл
                        </button>

                        {error && (
                            <div className="mt-4 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-black text-red-700">
                                {error}
                            </div>
                        )}
                    </div>

                    <div className="rounded-[28px] border border-slate-300 bg-white p-5">
                        <div className="mb-4 flex items-center justify-between gap-4">
                            <h3 className="text-xl font-black text-slate-950">
                                Полученные и отправленные файлы
                            </h3>

                            <button
                                type="button"
                                onClick={() => void loadFiles()}
                                className="rounded-2xl border border-slate-300 bg-white px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-50"
                            >
                                Обновить
                            </button>
                        </div>

                        {loading ? (
                            <div className="rounded-2xl bg-slate-100 px-5 py-8 text-center font-black text-slate-500">
                                Загрузка файлов...
                            </div>
                        ) : files.length === 0 ? (
                            <div className="rounded-2xl bg-slate-100 px-5 py-8 text-center font-black text-slate-500">
                                Файлов пока нет
                            </div>
                        ) : (
                            <div className="grid max-h-[520px] gap-3 overflow-y-auto pr-1">
                                {files.map((file, index) => (
                                    <article
                                        key={`${file.id || file.fileId || index}`}
                                        className="flex items-center gap-4 rounded-2xl border border-slate-200 bg-slate-50 p-4"
                                    >
                                        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-blue-50 text-blue-700">
                                            <FileText size={24} />
                                        </div>

                                        <div className="min-w-0 flex-1">
                                            <h4 className="truncate font-black text-slate-950">
                                                {getFileName(file)}
                                            </h4>

                                            <p className="mt-1 text-xs font-bold text-slate-500">
                                                От: {getSenderName(file)} · Кому: {getRecipientName(file)}
                                            </p>

                                            <p className="mt-1 text-xs font-bold text-slate-400">
                                                {formatFileSize(file.sizeBytes || 0)} · {formatEducationTime(file.createdAt)}
                                            </p>
                                        </div>

                                        <button
                                            type="button"
                                            onClick={() => void downloadFile(file)}
                                            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-blue-600 text-white hover:bg-blue-700"
                                        >
                                            <Download size={20} />
                                        </button>
                                    </article>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            </section>
        </div>
    );
}

function getFileName(file: EducationFileResponse): string {
    return (
        file.originalFilename ||
        file.filename ||
        file.fileName ||
        "Файл"
    );
}

function getSenderName(file: EducationFileResponse): string {
    return (
        file.senderDisplayName ||
        file.senderFullName ||
        file.senderUsername ||
        "Пользователь"
    );
}

function getRecipientName(file: EducationFileResponse): string {
    return (
        file.recipientDisplayName ||
        file.recipientFullName ||
        file.recipientUsername ||
        "всем"
    );
}

function formatFileSize(size: number): string {
    if (!size) {
        return "размер неизвестен";
    }

    if (size < 1024) {
        return `${size} Б`;
    }

    if (size < 1024 * 1024) {
        return `${(size / 1024).toFixed(1)} КБ`;
    }

    return `${(size / 1024 / 1024).toFixed(1)} МБ`;
}