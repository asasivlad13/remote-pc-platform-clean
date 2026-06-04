import type { PcDetailsResponse } from "./pcTypes";

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
            // Ответ не JSON
        }

        if (
            message.includes("Authorization header is missing") ||
            message.includes("JWT") ||
            message.includes("token")
        ) {
            throw new Error("Сессия входа устарела. Выйдите и войдите заново.");
        }

        throw new Error(message);
    }

    if (!text.trim()) {
        return [] as T;
    }

    try {
        return JSON.parse(text) as T;
    } catch {
        throw new Error("Сервер вернул некорректный ответ.");
    }
}

function normalizePcsResponse(data: unknown): PcDetailsResponse[] {
    if (Array.isArray(data)) {
        return data as PcDetailsResponse[];
    }

    if (data && typeof data === "object") {
        const object = data as {
            content?: unknown;
            items?: unknown;
            pcs?: unknown;
            data?: unknown;
            computers?: unknown;
        };

        if (Array.isArray(object.content)) {
            return object.content as PcDetailsResponse[];
        }

        if (Array.isArray(object.items)) {
            return object.items as PcDetailsResponse[];
        }

        if (Array.isArray(object.pcs)) {
            return object.pcs as PcDetailsResponse[];
        }

        if (Array.isArray(object.data)) {
            return object.data as PcDetailsResponse[];
        }

        if (Array.isArray(object.computers)) {
            return object.computers as PcDetailsResponse[];
        }
    }

    return [];
}

export async function getMyPcs(): Promise<PcDetailsResponse[]> {
    const response = await fetch(`${getApiBaseUrl()}/pcs`, {
        method: "GET",
        headers: getAuthHeaders(),
    });

    const data = await readJsonOrThrow<unknown>(
        response,
        "Не удалось загрузить список ПК",
    );

    return normalizePcsResponse(data);
}

export async function getPcs(): Promise<PcDetailsResponse[]> {
    return getMyPcs();
}

export async function getAllPcs(): Promise<PcDetailsResponse[]> {
    return getMyPcs();
}

export async function fetchPcs(): Promise<PcDetailsResponse[]> {
    return getMyPcs();
}

export async function getPcById(pcId: number | string): Promise<PcDetailsResponse> {
    const response = await fetch(
        `${getApiBaseUrl()}/pcs/${encodeURIComponent(String(pcId))}`,
        {
            method: "GET",
            headers: getAuthHeaders(),
        },
    );

    return readJsonOrThrow<PcDetailsResponse>(
        response,
        "Не удалось загрузить данные ПК",
    );
}

export async function getPcDetails(pcId: number | string): Promise<PcDetailsResponse> {
    return getPcById(pcId);
}

export async function getPc(pcId: number | string): Promise<PcDetailsResponse> {
    return getPcById(pcId);
}