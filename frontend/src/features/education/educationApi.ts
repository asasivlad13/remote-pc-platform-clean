import { getUserDisplayName } from "../profile/userDisplayName";

import type {
    ActiveScreenShareResponse,
    EducationChatMessageResponse,
    EducationEventResponse,
    EducationFileResponse,
    EducationParticipantResponse,
    EducationSessionResponse,
} from "./educationTypes";

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
    return {
        Authorization: `Bearer ${getAuthToken()}`,
        "Content-Type": "application/json",
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
        headers: getAuthHeaders(),
        body: JSON.stringify(body),
    });

    return readJsonOrThrow<T>(response, fallbackMessage);
}

export async function createEducationSession({
                                                 teacherPcId,
                                                 title,
                                                 maxStudents,
                                                 allowStudentControl,
                                                 allowFileTransfer,
                                                 allowStudentScreenShare,
                                             }: {
    teacherPcId: number;
    title: string;
    maxStudents: number;
    allowStudentControl: boolean;
    allowFileTransfer: boolean;
    allowStudentScreenShare: boolean;
}): Promise<EducationSessionResponse> {
    return postJson<EducationSessionResponse>(
        `${getApiBaseUrl()}/api/education/sessions`,
        {
            teacherPcId,
            title,
            teacherDisplayName: getUserDisplayName("Преподаватель"),
            maxStudents,
            allowStudentControl,
            allowFileTransfer,
            allowStudentScreenShare,
        },
        "Не удалось создать учебную сессию",
    );
}

export async function getEducationSession(
    sessionCode: string,
): Promise<EducationSessionResponse> {
    const response = await fetch(
        `${getApiBaseUrl()}/api/education/sessions/${encodeURIComponent(sessionCode)}`,
        {
            headers: getAuthHeaders(),
        },
    );

    return readJsonOrThrow<EducationSessionResponse>(
        response,
        "Сессия с таким кодом не существует или уже завершилась",
    );
}

export async function getMyActiveTeacherSession(): Promise<EducationSessionResponse | null> {
    const response = await fetch(
        `${getApiBaseUrl()}/api/education/sessions/my-active/teacher`,
        {
            headers: getAuthHeaders(),
        },
    );

    if (response.status === 204) {
        return null;
    }

    return readJsonOrThrow<EducationSessionResponse>(
        response,
        "Не удалось загрузить активную сессию преподавателя",
    );
}

export async function getMyActiveStudentSession(): Promise<EducationSessionResponse | null> {
    const response = await fetch(
        `${getApiBaseUrl()}/api/education/sessions/my-active/student`,
        {
            headers: getAuthHeaders(),
        },
    );

    if (response.status === 204) {
        return null;
    }

    return readJsonOrThrow<EducationSessionResponse>(
        response,
        "Не удалось загрузить активную сессию студента",
    );
}

export async function finishEducationSession(
    sessionCode: string,
): Promise<EducationSessionResponse> {
    return postJson<EducationSessionResponse>(
        `${getApiBaseUrl()}/api/education/sessions/${encodeURIComponent(sessionCode)}/finish`,
        {},
        "Не удалось завершить учебную сессию",
    );
}

export async function joinEducationSession(
    sessionCode: string,
): Promise<EducationParticipantResponse> {
    const displayName = getUserDisplayName("Студент");

    return postJson<EducationParticipantResponse>(
        `${getApiBaseUrl()}/api/education/participants/join`,
        {
            sessionCode,
            displayName,
        },
        "Сессия с таким кодом не существует или уже завершилась",
    );
}

export async function leaveEducationSession(sessionCode: string): Promise<unknown> {
    return postJson<unknown>(
        `${getApiBaseUrl()}/api/education/participants/leave`,
        {
            sessionCode,
        },
        "Не удалось выйти из учебной сессии",
    );
}

export async function getMyEducationParticipantStatus(
    sessionCode: string,
): Promise<EducationParticipantResponse> {
    const response = await fetch(
        `${getApiBaseUrl()}/api/education/participants/my/${encodeURIComponent(sessionCode)}`,
        {
            headers: getAuthHeaders(),
        },
    );

    return readJsonOrThrow<EducationParticipantResponse>(
        response,
        "Не удалось загрузить статус участника",
    );
}

export async function getEducationParticipants(
    sessionCode: string,
): Promise<EducationParticipantResponse[]> {
    const response = await fetch(
        `${getApiBaseUrl()}/api/education/participants/${encodeURIComponent(sessionCode)}`,
        {
            headers: getAuthHeaders(),
        },
    );

    if (!response.ok) {
        return [];
    }

    return response.json() as Promise<EducationParticipantResponse[]>;
}

export async function approveEducationParticipant(
    participantId: number,
): Promise<EducationParticipantResponse> {
    return postJson<EducationParticipantResponse>(
        `${getApiBaseUrl()}/api/education/participants/${participantId}/approve`,
        {},
        "Не удалось подтвердить участника",
    );
}

export async function rejectEducationParticipant(
    participantId: number,
): Promise<EducationParticipantResponse> {
    return postJson<EducationParticipantResponse>(
        `${getApiBaseUrl()}/api/education/participants/${participantId}/reject`,
        {},
        "Не удалось отклонить участника",
    );
}

export async function requestEducationControl(
    sessionCode: string,
): Promise<EducationParticipantResponse> {
    return postJson<EducationParticipantResponse>(
        `${getApiBaseUrl()}/api/education/participants/control/request`,
        {
            sessionCode,
        },
        "Не удалось запросить управление",
    );
}

export async function grantEducationControl(
    participantId: number,
): Promise<EducationParticipantResponse> {
    return postJson<EducationParticipantResponse>(
        `${getApiBaseUrl()}/api/education/participants/${participantId}/control/grant`,
        {},
        "Не удалось разрешить управление",
    );
}

export async function rejectEducationControl(
    participantId: number,
): Promise<EducationParticipantResponse> {
    return postJson<EducationParticipantResponse>(
        `${getApiBaseUrl()}/api/education/participants/${participantId}/control/reject`,
        {},
        "Не удалось отклонить управление",
    );
}

export async function revokeEducationControl(
    participantId: number,
): Promise<EducationParticipantResponse> {
    return postJson<EducationParticipantResponse>(
        `${getApiBaseUrl()}/api/education/participants/${participantId}/control/revoke`,
        {},
        "Не удалось отозвать управление",
    );
}

export async function requestEducationScreenShare(
    sessionCode: string,
): Promise<EducationParticipantResponse> {
    return postJson<EducationParticipantResponse>(
        `${getApiBaseUrl()}/api/education/participants/screen-share/request`,
        {
            sessionCode,
        },
        "Не удалось запросить демонстрацию экрана",
    );
}

export async function stopMyEducationScreenShare(
    sessionCode: string,
): Promise<EducationParticipantResponse> {
    return postJson<EducationParticipantResponse>(
        `${getApiBaseUrl()}/api/education/participants/screen-share/stop-my`,
        {
            sessionCode,
        },
        "Не удалось остановить демонстрацию экрана",
    );
}

export async function grantEducationScreenShare(
    participantId: number,
): Promise<EducationParticipantResponse> {
    return postJson<EducationParticipantResponse>(
        `${getApiBaseUrl()}/api/education/participants/${participantId}/screen-share/grant`,
        {},
        "Не удалось разрешить демонстрацию экрана",
    );
}

export async function rejectEducationScreenShare(
    participantId: number,
): Promise<EducationParticipantResponse> {
    return postJson<EducationParticipantResponse>(
        `${getApiBaseUrl()}/api/education/participants/${participantId}/screen-share/reject`,
        {},
        "Не удалось отклонить демонстрацию экрана",
    );
}

export async function getActiveEducationScreenShare(
    sessionCode: string,
): Promise<ActiveScreenShareResponse | null> {
    const response = await fetch(
        `${getApiBaseUrl()}/api/education/screen-share/active/${encodeURIComponent(sessionCode)}`,
        {
            headers: getAuthHeaders(),
        },
    );

    if (response.status === 204 || response.status === 404) {
        return null;
    }

    if (!response.ok) {
        return null;
    }

    return response.json() as Promise<ActiveScreenShareResponse>;
}

export async function getEducationEvents(
    sessionCode: string,
): Promise<EducationEventResponse[]> {
    const eventsResponse = await fetch(
        `${getApiBaseUrl()}/api/education/events/${encodeURIComponent(sessionCode)}`,
        {
            headers: getAuthHeaders(),
        },
    );

    if (eventsResponse.ok) {
        return eventsResponse.json() as Promise<EducationEventResponse[]>;
    }

    const timelineResponse = await fetch(
        `${getApiBaseUrl()}/api/education/sessions/${encodeURIComponent(sessionCode)}/timeline`,
        {
            headers: getAuthHeaders(),
        },
    );

    if (!timelineResponse.ok) {
        return [];
    }

    return timelineResponse.json() as Promise<EducationEventResponse[]>;
}

export async function getEducationChatMessages(
    sessionCode: string,
): Promise<EducationChatMessageResponse[]> {
    const response = await fetch(
        `${getApiBaseUrl()}/api/education/chat/${encodeURIComponent(sessionCode)}`,
        {
            headers: getAuthHeaders(),
        },
    );

    if (!response.ok) {
        return [];
    }

    return response.json() as Promise<EducationChatMessageResponse[]>;
}

export async function sendEducationChatMessage(
    sessionCode: string,
    message: string,
    recipientId?: number | null,
): Promise<EducationChatMessageResponse> {
    return postJson<EducationChatMessageResponse>(
        `${getApiBaseUrl()}/api/education/chat/${encodeURIComponent(sessionCode)}`,
        {
            message,
            recipientId: recipientId ?? null,
        },
        "Не удалось отправить сообщение",
    );
}

export async function getEducationFiles(
    sessionCode: string,
): Promise<EducationFileResponse[]> {
    const response = await fetch(
        `${getApiBaseUrl()}/api/education/files/${encodeURIComponent(sessionCode)}`,
        {
            headers: {
                Authorization: `Bearer ${getAuthToken()}`,
            },
        },
    );

    if (!response.ok) {
        return [];
    }

    return response.json() as Promise<EducationFileResponse[]>;
}

export async function uploadEducationFile({
                                              sessionCode,
                                              file,
                                              recipientId,
                                          }: {
    sessionCode: string;
    file: File;
    recipientId?: number | null;
}): Promise<EducationFileResponse> {
    const formData = new FormData();
    formData.append("file", file);

    if (recipientId !== undefined && recipientId !== null) {
        formData.append("recipientId", String(recipientId));
    }

    const response = await fetch(
        `${getApiBaseUrl()}/api/education/files/${encodeURIComponent(sessionCode)}`,
        {
            method: "POST",
            headers: {
                Authorization: `Bearer ${getAuthToken()}`,
            },
            body: formData,
        },
    );

    return readJsonOrThrow<EducationFileResponse>(
        response,
        "Не удалось отправить файл",
    );
}

export async function downloadEducationFile(
    fileId: number,
    fallbackFilename = "education-file",
): Promise<void> {
    const response = await fetch(
        `${getApiBaseUrl()}/api/education/files/download/${fileId}`,
        {
            headers: {
                Authorization: `Bearer ${getAuthToken()}`,
            },
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
