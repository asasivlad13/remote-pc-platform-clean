export type PcFramesResponse = Record<string, string>;

function getApiBaseUrl(): string {
    const explicitUrl = import.meta.env.VITE_API_BASE_URL as string | undefined;

    if (explicitUrl && explicitUrl.trim()) {
        return explicitUrl.trim();
    }

    return `${window.location.protocol}//${window.location.hostname}:8080`;
}

export async function getMyPcsFrames(): Promise<PcFramesResponse> {
    const token = localStorage.getItem("token");

    const response = await fetch(`${getApiBaseUrl()}/api/frames/my-pcs-frames`, {
        headers: {
            Authorization: `Bearer ${token}`,
        },
    });

    if (!response.ok) {
        throw new Error("Не удалось загрузить последние кадры ПК");
    }

    return response.json() as Promise<PcFramesResponse>;
}