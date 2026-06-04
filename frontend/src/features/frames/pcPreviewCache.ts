const PREVIEW_PREFIX = "remodesk.pc-preview.";

export function normalizePcPreviewKey(value: string | number | null | undefined): string {
    return String(value || "")
        .toLowerCase()
        .replace(/[^a-z0-9]/g, "");
}

function getPreviewKeys(pcId: number | string, macAddress?: string | null): string[] {
    const keys: string[] = [];

    const macKey = normalizePcPreviewKey(macAddress);
    const idKey = normalizePcPreviewKey(pcId);

    if (macKey) {
        keys.push(`${PREVIEW_PREFIX}mac.${macKey}`);
    }

    if (idKey) {
        keys.push(`${PREVIEW_PREFIX}id.${idKey}`);
    }

    return keys;
}

export function savePcPreview({
                                  pcId,
                                  macAddress,
                                  dataUrl,
                              }: {
    pcId: number | string;
    macAddress?: string | null;
    dataUrl: string;
}) {
    const keys = getPreviewKeys(pcId, macAddress);

    keys.forEach((key) => {
        try {
            localStorage.setItem(key, dataUrl);
        } catch {
            // localStorage может быть переполнен, тогда просто не сохраняем preview
        }
    });
}

export function getPcPreview(pcId: number | string, macAddress?: string | null): string | null {
    const keys = getPreviewKeys(pcId, macAddress);

    for (const key of keys) {
        const value = localStorage.getItem(key);

        if (value) {
            return value;
        }
    }

    return null;
}