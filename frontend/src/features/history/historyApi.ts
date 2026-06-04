import type { ConnectionHistoryItem } from "./historyTypes";

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

function getAuthHeaders(): HeadersInit {
    const token = getAuthToken();

    if (!token) {
        return {};
    }

    return {
        Authorization: `Bearer ${token}`,
    };
}

async function readJsonOrThrow<T>(
    response: Response,
    fallbackMessage: string,
): Promise<T> {
    const text = await response.text();

    if (!response.ok) {
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

    if (!text.trim()) {
        return [] as T;
    }

    return JSON.parse(text) as T;
}

function normalizeHistoryResponse(data: unknown): ConnectionHistoryItem[] {
    if (Array.isArray(data)) {
        return data as ConnectionHistoryItem[];
    }

    if (data && typeof data === "object") {
        const object = data as {
            content?: unknown;
            items?: unknown;
            data?: unknown;
            history?: unknown;
            logs?: unknown;
        };

        if (Array.isArray(object.content)) {
            return object.content as ConnectionHistoryItem[];
        }

        if (Array.isArray(object.items)) {
            return object.items as ConnectionHistoryItem[];
        }

        if (Array.isArray(object.data)) {
            return object.data as ConnectionHistoryItem[];
        }

        if (Array.isArray(object.history)) {
            return object.history as ConnectionHistoryItem[];
        }

        if (Array.isArray(object.logs)) {
            return object.logs as ConnectionHistoryItem[];
        }
    }

    return [];
}

export async function getConnectionHistory(): Promise<ConnectionHistoryItem[]> {
    const response = await fetch(`${getApiBaseUrl()}/api/history`, {
        method: "GET",
        headers: getAuthHeaders(),
    });

    const data = await readJsonOrThrow<unknown>(
        response,
        "Не удалось загрузить историю подключений",
    );

    return normalizeHistoryResponse(data);
}