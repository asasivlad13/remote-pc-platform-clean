import type {
    SupportChatMessageResponse,
    SupportFileResponse,
    SupportSessionResponse,
} from "./supportTypes";

function getApiBaseUrl(): string {
    const explicitUrl = import.meta.env.VITE_API_BASE_URL as string | undefined;

    if (explicitUrl && explicitUrl.trim()) {
        return explicitUrl.trim();
    }

    return `${window.location.protocol}//${window.location.hostname}:8080`;
}

function getAuthToken(): string {
    return localStorage.getItem("token") || "";
}

function getJsonHeaders(): HeadersInit {
    return {
        Authorization: `Bearer ${getAuthToken()}`,
        "Content-Type": "application/json",
    };
}

function getAuthHeaders(): HeadersInit {
    return {
        Authorization: `Bearer ${getAuthToken()}`,
    };
}

async function readJsonOrThrow<T>(
    response: Response,
    fallbackMessage: string,
): Promise<T> {
    if (!response.ok) {
        const text = await response.text().catch(() => "");
        let message = text || fallbackMessage;

        try {
            const json = JSON.parse(text) as {
                message?: string;
                error?: string;
                code?: string;
            };

            message = json.message || json.error || json.code || fallbackMessage;
        } catch {
            // response is not JSON
        }

        throw new Error(message);
    }

    return response.json() as Promise<T>;
}

async function postJson<T>(
    url: string,
    body: unknown,
    fallbackMessage: string,
): Promise<T> {
    const response = await fetch(url, {
        method: "POST",
        headers: getJsonHeaders(),
        body: JSON.stringify(body),
    });

    return readJsonOrThrow<T>(response, fallbackMessage);
}

async function postNoBody<T>(
    url: string,
    fallbackMessage: string,
): Promise<T> {
    const response = await fetch(url, {
        method: "POST",
        headers: getAuthHeaders(),
    });

    return readJsonOrThrow<T>(response, fallbackMessage);
}

export async function createSupportSession(
    title: string,
): Promise<SupportSessionResponse> {
    return postJson<SupportSessionResponse>(
        `${getApiBaseUrl()}/api/support/sessions`,
        {
            title,
        },
        "Не удалось создать сессию технической поддержки",
    );
}

export async function getSupportSession(
    sessionCode: string,
): Promise<SupportSessionResponse> {
    const response = await fetch(
        `${getApiBaseUrl()}/api/support/sessions/${encodeURIComponent(sessionCode)}`,
        {
            headers: getAuthHeaders(),
        },
    );

    return readJsonOrThrow<SupportSessionResponse>(
        response,
        "Не удалось загрузить сессию технической поддержки",
    );
}

export async function joinSupportSession(
    sessionCode: string,
): Promise<SupportSessionResponse> {
    const response = await fetch(
        `${getApiBaseUrl()}/api/support/sessions/${encodeURIComponent(sessionCode)}/join`,
        {
            method: "POST",
            headers: getAuthHeaders(),
        },
    );

    return readJsonOrThrow<SupportSessionResponse>(
        response,
        "Сессия техподдержки с таким кодом не найдена",
    );
}

export async function finishSupportSession(
    sessionCode: string,
): Promise<SupportSessionResponse> {
    return postNoBody<SupportSessionResponse>(
        `${getApiBaseUrl()}/api/support/sessions/${encodeURIComponent(sessionCode)}/finish`,
        "Не удалось завершить сессию технической поддержки",
    );
}

export async function requestSupportControl(
    sessionCode: string,
): Promise<SupportSessionResponse> {
    return postNoBody<SupportSessionResponse>(
        `${getApiBaseUrl()}/api/support/sessions/${encodeURIComponent(sessionCode)}/control/request`,
        "Не удалось запросить управление",
    );
}

export async function allowSupportControl(
    sessionCode: string,
): Promise<SupportSessionResponse> {
    return postNoBody<SupportSessionResponse>(
        `${getApiBaseUrl()}/api/support/sessions/${encodeURIComponent(sessionCode)}/control/allow`,
        "Не удалось разрешить управление",
    );
}

export async function denySupportControl(
    sessionCode: string,
): Promise<SupportSessionResponse> {
    return postNoBody<SupportSessionResponse>(
        `${getApiBaseUrl()}/api/support/sessions/${encodeURIComponent(sessionCode)}/control/deny`,
        "Не удалось запретить управление",
    );
}

export async function getSupportChatMessages(
    sessionCode: string,
): Promise<SupportChatMessageResponse[]> {
    const response = await fetch(
        `${getApiBaseUrl()}/api/support/sessions/${encodeURIComponent(sessionCode)}/chat`,
        {
            headers: getAuthHeaders(),
        },
    );

    if (!response.ok) {
        return [];
    }

    return response.json() as Promise<SupportChatMessageResponse[]>;
}

export async function sendSupportChatMessage(
    sessionCode: string,
    message: string,
): Promise<SupportChatMessageResponse> {
    return postJson<SupportChatMessageResponse>(
        `${getApiBaseUrl()}/api/support/sessions/${encodeURIComponent(sessionCode)}/chat`,
        {
            message,
        },
        "Не удалось отправить сообщение",
    );
}

export async function getSupportFiles(
    sessionCode: string,
): Promise<SupportFileResponse[]> {
    const response = await fetch(
        `${getApiBaseUrl()}/api/support/sessions/${encodeURIComponent(sessionCode)}/files`,
        {
            headers: getAuthHeaders(),
        },
    );

    if (!response.ok) {
        return [];
    }

    return response.json() as Promise<SupportFileResponse[]>;
}

export async function uploadSupportFile({
                                            sessionCode,
                                            file,
                                        }: {
    sessionCode: string;
    file: File;
}): Promise<SupportFileResponse> {
    const formData = new FormData();
    formData.append("file", file);

    const response = await fetch(
        `${getApiBaseUrl()}/api/support/sessions/${encodeURIComponent(sessionCode)}/files/upload`,
        {
            method: "POST",
            headers: getAuthHeaders(),
            body: formData,
        },
    );

    return readJsonOrThrow<SupportFileResponse>(
        response,
        "Не удалось отправить файл",
    );
}

export async function acceptSupportFile(
    sessionCode: string,
    fileId: number,
): Promise<SupportFileResponse> {
    return postNoBody<SupportFileResponse>(
        `${getApiBaseUrl()}/api/support/sessions/${encodeURIComponent(sessionCode)}/files/${fileId}/accept`,
        "Не удалось принять файл",
    );
}

export async function rejectSupportFile(
    sessionCode: string,
    fileId: number,
): Promise<SupportFileResponse> {
    return postNoBody<SupportFileResponse>(
        `${getApiBaseUrl()}/api/support/sessions/${encodeURIComponent(sessionCode)}/files/${fileId}/reject`,
        "Не удалось отклонить файл",
    );
}

export async function downloadSupportFile(
    sessionCode: string,
    fileId: number,
    fallbackFilename = "support-file",
): Promise<void> {
    const response = await fetch(
        `${getApiBaseUrl()}/api/support/sessions/${encodeURIComponent(sessionCode)}/files/${fileId}/download`,
        {
            headers: getAuthHeaders(),
        },
    );

    if (!response.ok) {
        const text = await response.text().catch(() => "");
        throw new Error(text || "Не удалось скачать файл");
    }

    const blob = await response.blob();
    const objectUrl = window.URL.createObjectURL(blob);

    const contentDisposition = response.headers.get("Content-Disposition") || "";
    const filename = extractFilename(contentDisposition) || fallbackFilename;

    const link = document.createElement("a");
    link.href = objectUrl;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();

    window.URL.revokeObjectURL(objectUrl);
}

function extractFilename(contentDisposition: string): string | null {
    const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);

    if (utf8Match?.[1]) {
        return decodeURIComponent(utf8Match[1]);
    }

    const regularMatch = contentDisposition.match(/filename="?([^"]+)"?/i);

    if (regularMatch?.[1]) {
        return regularMatch[1];
    }

    return null;
}