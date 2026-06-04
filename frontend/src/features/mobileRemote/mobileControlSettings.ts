export const MOBILE_SCREEN_CONTROL_SETTINGS_STORAGE_KEY =
    "remoteMobileScreenControlSettingsV2";

export const MOBILE_SCREEN_CONTROL_MODE_STORAGE_KEY =
    "remoteMobileScreenControlModeV1";

export type MobileScreenControlMode = "touch_screen" | "joystick_mouse";

export type MobileScreenControlModeOption = {
    value: MobileScreenControlMode;
    title: string;
    shortTitle: string;
    description: string;
};

export type MobileScreenControlSettings = {
    mode: MobileScreenControlMode;
    joystickSize: number;
    controlsOpacity: number;
    joystickX: number;
    joystickY: number;
    mouseButtonsX: number;
    mouseButtonsY: number;
    cursorSize: number;
    joystickSpeed: number;
};

export const MOBILE_SCREEN_CONTROL_MODE_OPTIONS: MobileScreenControlModeOption[] = [
    {
        value: "touch_screen",
        title: "Сенсорное управление экраном",
        shortTitle: "Сенсор",
        description:
            "Палец по трансляции работает как сенсорный экран. Тап — ЛКМ, долгое нажатие — ПКМ.",
    },
    {
        value: "joystick_mouse",
        title: "Джойстик + ЛКМ/ПКМ",
        shortTitle: "Джойстик",
        description:
            "Слева колесо-джойстик управляет курсором поверх трансляции, справа расположены кнопки ЛКМ и ПКМ.",
    },
];

export const DEFAULT_MOBILE_SCREEN_CONTROL_SETTINGS: MobileScreenControlSettings = {
    mode: "touch_screen",
    joystickSize: 150,
    controlsOpacity: 72,
    joystickX: 16,
    joystickY: 76,
    mouseButtonsX: 84,
    mouseButtonsY: 72,
    cursorSize: 24,
    joystickSpeed: 14,
};

export function loadMobileScreenControlMode(): MobileScreenControlMode {
    return loadMobileScreenControlSettings().mode;
}

export function saveMobileScreenControlMode(
    mode: MobileScreenControlMode,
): MobileScreenControlSettings {
    const current = loadMobileScreenControlSettings();

    return saveMobileScreenControlSettings({
        ...current,
        mode,
    });
}

export function getMobileScreenControlModeLabel(
    mode: MobileScreenControlMode,
): string {
    return (
        MOBILE_SCREEN_CONTROL_MODE_OPTIONS.find((option) => option.value === mode)
            ?.shortTitle || "Сенсор"
    );
}

export function loadMobileScreenControlSettings(): MobileScreenControlSettings {
    const raw = localStorage.getItem(MOBILE_SCREEN_CONTROL_SETTINGS_STORAGE_KEY);

    if (raw) {
        try {
            return normalizeMobileScreenControlSettings(
                JSON.parse(raw) as Partial<MobileScreenControlSettings>,
            );
        } catch {
            // ignore broken JSON
        }
    }

    return normalizeMobileScreenControlSettings({
        mode: readMode(
            localStorage.getItem(MOBILE_SCREEN_CONTROL_MODE_STORAGE_KEY) ||
            localStorage.getItem("mobileControlMode") ||
            localStorage.getItem("mobileRemoteControlMode") ||
            localStorage.getItem("remoteMobileControlMode"),
        ),
        joystickSize: readNumberFromLocalStorage(
            "mobileJoystickSize",
            "mobileControlJoystickSize",
            "remoteMobileJoystickSize",
        ),
        controlsOpacity: readNumberFromLocalStorage(
            "mobileControlsOpacity",
            "mobileControlOpacity",
            "remoteMobileControlsOpacity",
        ),
        joystickX: readNumberFromLocalStorage(
            "mobileJoystickX",
            "mobileControlJoystickX",
        ),
        joystickY: readNumberFromLocalStorage(
            "mobileJoystickY",
            "mobileControlJoystickY",
        ),
        mouseButtonsX: readNumberFromLocalStorage(
            "mobileMouseButtonsX",
            "mobileControlMouseButtonsX",
        ),
        mouseButtonsY: readNumberFromLocalStorage(
            "mobileMouseButtonsY",
            "mobileControlMouseButtonsY",
        ),
        cursorSize: readNumberFromLocalStorage(
            "mobileCursorSize",
            "mobileControlCursorSize",
        ),
        joystickSpeed: readNumberFromLocalStorage(
            "mobileJoystickSpeed",
            "mobileControlJoystickSpeed",
        ),
    });
}

export function saveMobileScreenControlSettings(
    settings: Partial<MobileScreenControlSettings>,
): MobileScreenControlSettings {
    const normalized = normalizeMobileScreenControlSettings(settings);
    const serialized = JSON.stringify(normalized);

    localStorage.setItem(MOBILE_SCREEN_CONTROL_SETTINGS_STORAGE_KEY, serialized);
    localStorage.setItem(MOBILE_SCREEN_CONTROL_MODE_STORAGE_KEY, normalized.mode);

    /*
     * Legacy keys are kept so older pages, including TouchpadRemotePage.tsx,
     * still read the same selected mode/settings after this newer settings file
     * is installed.
     */
    localStorage.setItem(
        "mobileControlMode",
        normalized.mode === "joystick_mouse" ? "joystick" : "touch",
    );
    localStorage.setItem(
        "mobileRemoteControlMode",
        normalized.mode === "joystick_mouse" ? "joystick" : "touch",
    );
    localStorage.setItem("mobileJoystickSize", String(normalized.joystickSize));
    localStorage.setItem("mobileControlsOpacity", String(normalized.controlsOpacity));
    localStorage.setItem("mobileJoystickX", String(normalized.joystickX));
    localStorage.setItem("mobileJoystickY", String(normalized.joystickY));
    localStorage.setItem("mobileMouseButtonsX", String(normalized.mouseButtonsX));
    localStorage.setItem("mobileMouseButtonsY", String(normalized.mouseButtonsY));
    localStorage.setItem("mobileCursorSize", String(normalized.cursorSize));
    localStorage.setItem("mobileJoystickSpeed", String(normalized.joystickSpeed));

    window.dispatchEvent(
        new StorageEvent("storage", {
            key: MOBILE_SCREEN_CONTROL_SETTINGS_STORAGE_KEY,
            newValue: serialized,
        }),
    );

    window.dispatchEvent(
        new StorageEvent("storage", {
            key: MOBILE_SCREEN_CONTROL_MODE_STORAGE_KEY,
            newValue: normalized.mode,
        }),
    );

    return normalized;
}

export function resetMobileScreenControlSettings(): MobileScreenControlSettings {
    return saveMobileScreenControlSettings(DEFAULT_MOBILE_SCREEN_CONTROL_SETTINGS);
}

export function normalizeMobileScreenControlSettings(
    settings: Partial<MobileScreenControlSettings>,
): MobileScreenControlSettings {
    const mode = readMode(settings.mode) || DEFAULT_MOBILE_SCREEN_CONTROL_SETTINGS.mode;

    return {
        mode,
        joystickSize: clampNumber(
            settings.joystickSize,
            110,
            260,
            DEFAULT_MOBILE_SCREEN_CONTROL_SETTINGS.joystickSize,
        ),
        controlsOpacity: clampNumber(
            settings.controlsOpacity,
            25,
            100,
            DEFAULT_MOBILE_SCREEN_CONTROL_SETTINGS.controlsOpacity,
        ),
        joystickX: clampNumber(
            settings.joystickX,
            5,
            45,
            DEFAULT_MOBILE_SCREEN_CONTROL_SETTINGS.joystickX,
        ),
        joystickY: clampNumber(
            settings.joystickY,
            45,
            92,
            DEFAULT_MOBILE_SCREEN_CONTROL_SETTINGS.joystickY,
        ),
        mouseButtonsX: clampNumber(
            settings.mouseButtonsX,
            55,
            95,
            DEFAULT_MOBILE_SCREEN_CONTROL_SETTINGS.mouseButtonsX,
        ),
        mouseButtonsY: clampNumber(
            settings.mouseButtonsY,
            38,
            92,
            DEFAULT_MOBILE_SCREEN_CONTROL_SETTINGS.mouseButtonsY,
        ),
        cursorSize: clampNumber(
            settings.cursorSize,
            14,
            42,
            DEFAULT_MOBILE_SCREEN_CONTROL_SETTINGS.cursorSize,
        ),
        joystickSpeed: clampNumber(
            settings.joystickSpeed,
            4,
            30,
            DEFAULT_MOBILE_SCREEN_CONTROL_SETTINGS.joystickSpeed,
        ),
    };
}

function readMode(value: unknown): MobileScreenControlMode | undefined {
    const normalized = String(value || "").toLowerCase();

    if (normalized === "joystick_mouse" || normalized === "joystick") {
        return "joystick_mouse";
    }

    if (normalized === "touch_screen" || normalized === "touch") {
        return "touch_screen";
    }

    return undefined;
}

function readNumberFromLocalStorage(...keys: string[]): number | undefined {
    for (const key of keys) {
        const rawValue = localStorage.getItem(key);

        if (rawValue === null || rawValue === "") {
            continue;
        }

        const numberValue = Number(rawValue);

        if (Number.isFinite(numberValue)) {
            return numberValue;
        }
    }

    return undefined;
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
