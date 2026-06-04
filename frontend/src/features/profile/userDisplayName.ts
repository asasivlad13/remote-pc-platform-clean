export const DISPLAY_NAME_STORAGE_KEY = "remoteUserDisplayNameV1";
export const DISPLAY_NAMES_BY_USER_KEY = "remoteDisplayNamesByUsernameV1";

function normalize(value: string | null | undefined): string {
    return String(value || "").trim();
}

function getCurrentUsername(): string {
    const directUsername =
        normalize(localStorage.getItem("username")) ||
        normalize(localStorage.getItem("authUsername")) ||
        normalize(localStorage.getItem("user"));

    if (directUsername) {
        return directUsername;
    }

    /*
     * Запасной вариант для Zustand/других auth-хранилищ.
     * Нужен на случай, если логин лежит не в localStorage.username,
     * а внутри JSON-объекта.
     */
    for (let i = 0; i < localStorage.length; i += 1) {
        const key = localStorage.key(i);

        if (!key) {
            continue;
        }

        const rawValue = localStorage.getItem(key);

        if (!rawValue || !rawValue.includes("username")) {
            continue;
        }

        try {
            const parsed = JSON.parse(rawValue);
            const username = findUsernameInObject(parsed);

            if (username) {
                return username;
            }
        } catch {
            // value is not JSON
        }
    }

    return "";
}

function findUsernameInObject(value: unknown): string {
    if (!value || typeof value !== "object") {
        return "";
    }

    const objectValue = value as Record<string, unknown>;

    const direct =
        normalize(objectValue.username as string | undefined) ||
        normalize(objectValue.authUsername as string | undefined) ||
        normalize(objectValue.user as string | undefined);

    if (direct) {
        return direct;
    }

    for (const nestedValue of Object.values(objectValue)) {
        const nestedUsername = findUsernameInObject(nestedValue);

        if (nestedUsername) {
            return nestedUsername;
        }
    }

    return "";
}

function getUserSpecificDisplayNameKey(username: string): string {
    return `${DISPLAY_NAME_STORAGE_KEY}:${username}`;
}

function readDisplayNameMap(): Record<string, string> {
    const rawValue = localStorage.getItem(DISPLAY_NAMES_BY_USER_KEY);

    if (!rawValue) {
        return {};
    }

    try {
        const parsed = JSON.parse(rawValue) as Record<string, unknown>;
        const result: Record<string, string> = {};

        Object.entries(parsed).forEach(([username, displayName]) => {
            const normalizedUsername = normalize(username);
            const normalizedDisplayName = normalize(displayName as string | undefined);

            if (normalizedUsername && normalizedDisplayName) {
                result[normalizedUsername] = normalizedDisplayName;
            }
        });

        return result;
    } catch {
        return {};
    }
}

function writeDisplayNameMap(map: Record<string, string>) {
    localStorage.setItem(DISPLAY_NAMES_BY_USER_KEY, JSON.stringify(map));
}

export function getUserDisplayName(fallback = "Пользователь"): string {
    const username = getCurrentUsername();

    if (username) {
        const userSpecificName = normalize(
            localStorage.getItem(getUserSpecificDisplayNameKey(username)),
        );

        if (userSpecificName) {
            return userSpecificName;
        }

        const displayNameMap = readDisplayNameMap();
        const mappedName = normalize(displayNameMap[username]);

        if (mappedName) {
            return mappedName;
        }

        /*
         * ВАЖНО:
         * Старый общий ключ remoteUserDisplayNameV1 не используем,
         * когда известен username. Иначе при переключении teacher1/student1
         * имя одного аккаунта перетирает имя другого.
         */
        return username;
    }

    const oldGlobalName = normalize(localStorage.getItem(DISPLAY_NAME_STORAGE_KEY));

    if (oldGlobalName) {
        return oldGlobalName;
    }

    return fallback;
}

export function saveUserDisplayName(displayName: string) {
    const username = getCurrentUsername();
    const normalizedName = normalize(displayName);

    if (!username) {
        if (!normalizedName) {
            localStorage.removeItem(DISPLAY_NAME_STORAGE_KEY);
            return;
        }

        localStorage.setItem(DISPLAY_NAME_STORAGE_KEY, normalizedName);
        return;
    }

    const userSpecificKey = getUserSpecificDisplayNameKey(username);
    const displayNameMap = readDisplayNameMap();

    if (!normalizedName) {
        localStorage.removeItem(userSpecificKey);
        delete displayNameMap[username];
        writeDisplayNameMap(displayNameMap);
        return;
    }

    localStorage.setItem(userSpecificKey, normalizedName);
    displayNameMap[username] = normalizedName;
    writeDisplayNameMap(displayNameMap);
}

/*
 * Удобная функция для проверки в консоли браузера:
 * console.log(debugUserDisplayNameStorage())
 */
export function debugUserDisplayNameStorage() {
    const username = getCurrentUsername();

    return {
        username,
        displayName: getUserDisplayName(),
        userSpecificKey: username ? getUserSpecificDisplayNameKey(username) : "",
        userSpecificValue: username
            ? localStorage.getItem(getUserSpecificDisplayNameKey(username))
            : null,
        map: readDisplayNameMap(),
        legacyGlobalValue: localStorage.getItem(DISPLAY_NAME_STORAGE_KEY),
    };
}
