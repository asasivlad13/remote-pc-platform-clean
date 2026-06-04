export const GAMEPAD_LAYOUT_STORAGE_KEY = "remoteGamepadLayoutV1";

export type GamepadLayoutId =
    | "lb"
    | "lt"
    | "rb"
    | "rt"
    | "back"
    | "guide"
    | "start"
    | "leftStick"
    | "rightStick"
    | "dpad"
    | "faceButtons";

export type GamepadLayoutItem = {
    x: number;
    y: number;
    w: number;
    h: number;
};

export type GamepadLayout = Record<GamepadLayoutId, GamepadLayoutItem>;

export type GamepadLayoutMeta = {
    id: GamepadLayoutId;
    label: string;
    type: "button" | "stick" | "cluster";
};

export const GAMEPAD_LAYOUT_META: GamepadLayoutMeta[] = [
    { id: "lb", label: "LB", type: "button" },
    { id: "lt", label: "LT", type: "button" },
    { id: "rb", label: "RB", type: "button" },
    { id: "rt", label: "RT", type: "button" },
    { id: "back", label: "Back", type: "button" },
    { id: "guide", label: "Xbox", type: "button" },
    { id: "start", label: "Start", type: "button" },
    { id: "leftStick", label: "Левый стик", type: "stick" },
    { id: "rightStick", label: "Правый стик", type: "stick" },
    { id: "dpad", label: "D-Pad", type: "cluster" },
    { id: "faceButtons", label: "ABXY", type: "cluster" },
];

export const DEFAULT_GAMEPAD_LAYOUT: GamepadLayout = {
    lb: { x: 14, y: 10, w: 18, h: 9 },
    lt: { x: 34, y: 10, w: 18, h: 9 },
    back: { x: 46, y: 10, w: 7, h: 7 },
    guide: { x: 54, y: 10, w: 7, h: 7 },
    start: { x: 62, y: 10, w: 7, h: 7 },
    rb: { x: 75, y: 10, w: 18, h: 9 },
    rt: { x: 94, y: 10, w: 12, h: 9 },

    leftStick: { x: 18, y: 66, w: 22, h: 22 },
    dpad: { x: 39, y: 72, w: 13, h: 13 },
    rightStick: { x: 67, y: 66, w: 19, h: 19 },
    faceButtons: { x: 87, y: 64, w: 16, h: 16 },
};

export function loadGamepadLayout(): GamepadLayout {
    try {
        const raw = localStorage.getItem(GAMEPAD_LAYOUT_STORAGE_KEY);

        if (!raw) {
            return cloneGamepadLayout(DEFAULT_GAMEPAD_LAYOUT);
        }

        const parsed = JSON.parse(raw) as Partial<GamepadLayout>;
        return normalizeGamepadLayout(parsed);
    } catch {
        return cloneGamepadLayout(DEFAULT_GAMEPAD_LAYOUT);
    }
}

export function saveGamepadLayout(layout: GamepadLayout) {
    const normalized = normalizeGamepadLayout(layout);

    localStorage.setItem(
        GAMEPAD_LAYOUT_STORAGE_KEY,
        JSON.stringify(normalized),
    );

    window.dispatchEvent(
        new StorageEvent("storage", {
            key: GAMEPAD_LAYOUT_STORAGE_KEY,
            newValue: JSON.stringify(normalized),
        }),
    );
}

export function resetGamepadLayout(): GamepadLayout {
    const layout = cloneGamepadLayout(DEFAULT_GAMEPAD_LAYOUT);
    saveGamepadLayout(layout);
    return layout;
}

export function cloneGamepadLayout(layout: GamepadLayout): GamepadLayout {
    return JSON.parse(JSON.stringify(layout)) as GamepadLayout;
}

export function normalizeGamepadLayout(layout: Partial<GamepadLayout>): GamepadLayout {
    const result = cloneGamepadLayout(DEFAULT_GAMEPAD_LAYOUT);

    for (const meta of GAMEPAD_LAYOUT_META) {
        const item = layout[meta.id];

        if (!item) {
            continue;
        }

        result[meta.id] = {
            x: clampNumber(item.x, 0, 100, result[meta.id].x),
            y: clampNumber(item.y, 0, 100, result[meta.id].y),
            w: clampNumber(item.w, 4, 45, result[meta.id].w),
            h: clampNumber(item.h, 4, 45, result[meta.id].h),
        };
    }

    return result;
}

export function getGamepadLayoutLabel(id: GamepadLayoutId): string {
    return GAMEPAD_LAYOUT_META.find((item) => item.id === id)?.label || id;
}

function clampNumber(
    value: unknown,
    min: number,
    max: number,
    fallback: number,
): number {
    const numberValue = Number(value);

    if (!Number.isFinite(numberValue)) {
        return fallback;
    }

    return Math.max(min, Math.min(max, numberValue));
}
