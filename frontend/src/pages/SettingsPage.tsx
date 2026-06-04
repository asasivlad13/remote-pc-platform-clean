import { useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties, PointerEvent, ReactNode } from "react";
import {
    Gamepad2,
    LayoutGrid,
    Maximize2,
    Minimize2,
    Move,
    PanelBottomClose,
    PanelBottomOpen,
    RotateCcw,
    Save,
    SlidersHorizontal,
    Smartphone,
    UserRound,
    X,
} from "lucide-react";
import { DashboardLayout } from "../shared/ui/DashboardLayout";
import {
    cloneGamepadLayout,
    DEFAULT_GAMEPAD_LAYOUT,
    GAMEPAD_LAYOUT_META,
    getGamepadLayoutLabel,
    loadGamepadLayout,
    resetGamepadLayout,
    saveGamepadLayout,
    type GamepadLayout,
    type GamepadLayoutId,
    type GamepadLayoutItem,
} from "../features/mobileRemote/gamepadLayout";
import {
    DEFAULT_MOBILE_SCREEN_CONTROL_SETTINGS,
    loadMobileScreenControlSettings,
    resetMobileScreenControlSettings,
    saveMobileScreenControlSettings,
    type MobileScreenControlMode,
    type MobileScreenControlSettings,
} from "../features/mobileRemote/mobileControlSettings";
import {
    DISPLAY_NAME_STORAGE_KEY,
    getUserDisplayName,
    saveUserDisplayName,
} from "../features/profile/userDisplayName";

type SettingsTab = "gamepad" | "mobileControl" | "profile";

export function SettingsPage() {
    const [searchValue, setSearchValue] = useState("");
    const [activeTab, setActiveTab] = useState<SettingsTab>("gamepad");

    return (
        <DashboardLayout searchValue={searchValue} onSearchChange={setSearchValue}>
            <div className="px-5 py-10 lg:px-12">
                <div className="mb-8">
                    <div className="mb-4 inline-flex items-center gap-2 rounded-2xl bg-blue-50 px-4 py-2 text-sm font-black text-blue-700">
                        <SlidersHorizontal size={18} />
                        Settings
                    </div>

                    <h1 className="text-5xl font-black tracking-tight text-slate-950 max-sm:text-4xl">
                        Настройки
                    </h1>

                    <p className="mt-4 max-w-3xl text-lg font-medium leading-8 text-slate-500">
                        Здесь настраиваются сценарии сайта: игровой контроллер, телефонное
                        управление экраном и имя, которое видно другим участникам.
                    </p>
                </div>

                <div className="grid grid-cols-[280px_minmax(0,1fr)] gap-6 max-xl:grid-cols-1">
                    <aside className="rounded-[30px] border border-slate-200 bg-white p-4 shadow-sm">
                        <SettingsNavButton
                            active={activeTab === "gamepad"}
                            icon={<Gamepad2 size={21} />}
                            title="Игровой контроллер"
                            text="Кнопки, стики и размер элементов"
                            onClick={() => setActiveTab("gamepad")}
                        />

                        <SettingsNavButton
                            active={activeTab === "mobileControl"}
                            icon={<Smartphone size={21} />}
                            title="Телефонное управление"
                            text="Сенсорный экран или джойстик"
                            onClick={() => setActiveTab("mobileControl")}
                        />

                        <SettingsNavButton
                            active={activeTab === "profile"}
                            icon={<UserRound size={21} />}
                            title="Имя в сессиях"
                            text="Как вас видят в учебе и поддержке"
                            onClick={() => setActiveTab("profile")}
                        />

                        <div className="mt-3 rounded-2xl border border-dashed border-slate-200 bg-slate-50 p-4 text-sm font-bold leading-6 text-slate-400">
                            Следующие вкладки можно будет добавить позже: интерфейс,
                            трансляция, уведомления, учебная сессия, техподдержка.
                        </div>
                    </aside>

                    <section className="min-w-0">
                        {activeTab === "gamepad" && <GamepadSettingsPanel />}
                        {activeTab === "mobileControl" && <MobileControlSettingsPanel />}
                        {activeTab === "profile" && <ProfileNameSettingsPanel />}
                    </section>
                </div>
            </div>
        </DashboardLayout>
    );
}


function MobileControlSettingsPanel() {
    const [settings, setSettings] = useState<MobileScreenControlSettings>(() => loadMobileScreenControlSettings());
    const [savedMessage, setSavedMessage] = useState("");

    function updateSettings(patch: Partial<MobileScreenControlSettings>) {
        setSettings((current) => ({
            ...current,
            ...patch,
        }));
    }

    function save() {
        const normalized = saveMobileScreenControlSettings(settings);
        setSettings(normalized);
        setSavedMessage("Настройки телефонного управления сохранены");

        window.setTimeout(() => {
            setSavedMessage("");
        }, 2500);
    }

    function reset() {
        const normalized = resetMobileScreenControlSettings();
        setSettings(normalized);
        setSavedMessage("Настройки сброшены");

        window.setTimeout(() => {
            setSavedMessage("");
        }, 2500);
    }

    const isJoystick = settings.mode === "joystick_mouse";

    return (
        <section className="overflow-hidden rounded-[34px] border border-slate-200 bg-white shadow-sm">
            <div className="border-b border-slate-200 bg-slate-950 px-6 py-5 text-white">
                <div className="flex flex-wrap items-start justify-between gap-4">
                    <div>
                        <div className="mb-3 inline-flex items-center gap-2 rounded-xl bg-white/10 px-3 py-1 text-xs font-black uppercase tracking-wide text-blue-100">
                            <Smartphone size={15} />
                            Mobile screen control
                        </div>

                        <h2 className="text-3xl font-black">
                            Управление трансляцией с телефона
                        </h2>

                        <p className="mt-2 max-w-3xl text-sm font-semibold leading-6 text-slate-300">
                            Эти настройки работают только на телефоне, когда открыт экран удалённого ПК и включено управление.
                            В режиме джойстика слева находится колесо-джойстик для курсора, справа — ЛКМ и ПКМ.
                        </p>
                    </div>

                    <div className="flex flex-wrap gap-2">
                        <button
                            type="button"
                            onClick={reset}
                            className="inline-flex h-11 items-center justify-center gap-2 rounded-2xl border border-white/10 bg-white/10 px-4 text-sm font-black text-white transition hover:bg-white/20"
                        >
                            <RotateCcw size={18} />
                            Сброс
                        </button>

                        <button
                            type="button"
                            onClick={save}
                            className="inline-flex h-11 items-center justify-center gap-2 rounded-2xl bg-blue-600 px-4 text-sm font-black text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700"
                        >
                            <Save size={18} />
                            Сохранить
                        </button>
                    </div>
                </div>

                {savedMessage && (
                    <div className="mt-4 rounded-2xl bg-emerald-500/15 px-4 py-3 text-sm font-black text-emerald-200">
                        {savedMessage}
                    </div>
                )}
            </div>

            <div className="grid grid-cols-[minmax(0,1fr)_390px] gap-6 p-6 max-xl:grid-cols-1">
                <div className="grid gap-5">
                    <div className="grid grid-cols-2 gap-4 max-md:grid-cols-1">
                        <MobileModeCard
                            active={settings.mode === "touch_screen"}
                            title="Сенсорный экран"
                            text="Касание по трансляции сразу переносит курсор в эту точку. Тап — ЛКМ, долгое нажатие — ПКМ."
                            icon="☝️"
                            onClick={() => updateSettings({ mode: "touch_screen" })}
                        />

                        <MobileModeCard
                            active={settings.mode === "joystick_mouse"}
                            title="Джойстик + ЛКМ/ПКМ"
                            text="Слева колесо-джойстик двигает курсор по трансляции. Справа крупные кнопки ЛКМ и ПКМ."
                            icon="🕹"
                            onClick={() => updateSettings({ mode: "joystick_mouse" })}
                        />
                    </div>

                    <SettingsGroup title="Джойстик курсора">
                        <RangeEditor
                            label="Размер джойстика"
                            value={settings.joystickSize}
                            min={110}
                            max={260}
                            onChange={(value) => updateSettings({ joystickSize: value })}
                        />

                        <RangeEditor
                            label="Позиция X"
                            value={settings.joystickX}
                            min={5}
                            max={45}
                            onChange={(value) => updateSettings({ joystickX: value })}
                        />

                        <RangeEditor
                            label="Позиция Y"
                            value={settings.joystickY}
                            min={45}
                            max={92}
                            onChange={(value) => updateSettings({ joystickY: value })}
                        />
                    </SettingsGroup>

                    <SettingsGroup title="Кнопки мыши">
                        <RangeEditor
                            label="Позиция X"
                            value={settings.mouseButtonsX}
                            min={55}
                            max={95}
                            onChange={(value) => updateSettings({ mouseButtonsX: value })}
                        />

                        <RangeEditor
                            label="Позиция Y"
                            value={settings.mouseButtonsY}
                            min={38}
                            max={92}
                            onChange={(value) => updateSettings({ mouseButtonsY: value })}
                        />

                        <RangeEditor
                            label="Прозрачность"
                            value={settings.controlsOpacity}
                            min={25}
                            max={100}
                            onChange={(value) => updateSettings({ controlsOpacity: value })}
                        />
                    </SettingsGroup>

                    <SettingsGroup title="Курсор поверх трансляции">
                        <RangeEditor
                            label="Размер курсора"
                            value={settings.cursorSize}
                            min={14}
                            max={42}
                            onChange={(value) => updateSettings({ cursorSize: value })}
                        />

                        <RangeEditor
                            label="Скорость джойстика"
                            value={settings.joystickSpeed}
                            min={4}
                            max={30}
                            onChange={(value) => updateSettings({ joystickSpeed: value })}
                        />
                    </SettingsGroup>

                    <div className="rounded-3xl border border-blue-200 bg-blue-50 p-5 text-sm font-bold leading-6 text-blue-800">
                        Расположение задаётся в процентах от области трансляции. Для стандартного варианта оставьте джойстик слева,
                        а кнопки справа: X джойстика около 16, X кнопок около 84.
                    </div>
                </div>

                <aside className="rounded-[30px] border border-slate-200 bg-white p-5 shadow-sm">
                    <div className="mb-4 text-sm font-black uppercase tracking-wide text-slate-400">
                        Предпросмотр
                    </div>

                    <div className="relative aspect-[9/16] overflow-hidden rounded-[30px] border border-slate-900 bg-slate-950 text-white">
                        <div className="absolute inset-4 rounded-[24px] border border-white/10 bg-gradient-to-br from-slate-900 to-blue-950" />

                        <div
                            className="absolute h-6 w-6 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white bg-emerald-500 shadow-[0_0_18px_rgba(16,185,129,.9)]"
                            style={{
                                left: "50%",
                                top: "42%",
                                width: settings.cursorSize,
                                height: settings.cursorSize,
                                opacity: settings.controlsOpacity / 100,
                            }}
                        />

                        {isJoystick ? (
                            <>
                                <div
                                    className="absolute -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white/25 bg-slate-950/70 shadow-2xl backdrop-blur"
                                    style={{
                                        left: `${settings.joystickX}%`,
                                        top: `${settings.joystickY}%`,
                                        width: settings.joystickSize * 0.58,
                                        height: settings.joystickSize * 0.58,
                                        opacity: settings.controlsOpacity / 100,
                                    }}
                                >
                                    <div className="absolute inset-[18%] rounded-full border-2 border-dashed border-white/15" />
                                    <div className="absolute left-1/2 top-1/2 h-[42%] w-[42%] -translate-x-1/2 -translate-y-1/2 rounded-full border-4 border-white/80 bg-blue-500" />
                                </div>

                                <div
                                    className="absolute grid w-[92px] -translate-x-1/2 -translate-y-1/2 gap-2"
                                    style={{
                                        left: `${settings.mouseButtonsX}%`,
                                        top: `${settings.mouseButtonsY}%`,
                                        opacity: settings.controlsOpacity / 100,
                                    }}
                                >
                                    <div className="flex h-12 items-center justify-center rounded-2xl bg-blue-600 text-xs font-black">ЛКМ</div>
                                    <div className="flex h-12 items-center justify-center rounded-2xl border border-white/15 bg-white/15 text-xs font-black">ПКМ</div>
                                </div>
                            </>
                        ) : (
                            <div className="absolute left-5 right-5 bottom-5 rounded-2xl bg-white/10 p-3 text-xs font-bold leading-5 text-slate-200">
                                Сенсорный режим: палец по трансляции = курсор на удалённом экране.
                            </div>
                        )}

                        <div className="absolute left-4 right-4 top-4 rounded-2xl bg-white/10 p-3 text-xs font-bold leading-5 text-slate-200">
                            {isJoystick ? "Джойстик слева, кнопки справа" : "Сенсорное управление трансляцией"}
                        </div>
                    </div>
                </aside>
            </div>
        </section>
    );
}

function MobileModeCard({
                            active,
                            title,
                            text,
                            icon,
                            onClick,
                        }: {
    active: boolean;
    title: string;
    text: string;
    icon: string;
    onClick: () => void;
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            className={
                active
                    ? "rounded-[28px] border-2 border-blue-500 bg-blue-50 p-5 text-left shadow-sm"
                    : "rounded-[28px] border border-slate-200 bg-white p-5 text-left transition hover:bg-slate-50"
            }
        >
            <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-950 text-3xl text-white">
                {icon}
            </div>

            <div className="text-xl font-black text-slate-950">{title}</div>
            <p className="mt-2 text-sm font-semibold leading-6 text-slate-500">{text}</p>

            {active && (
                <span className="mt-4 inline-flex rounded-full bg-blue-600 px-4 py-2 text-xs font-black uppercase tracking-wide text-white">
                    Выбрано
                </span>
            )}
        </button>
    );
}

function SettingsGroup({ title, children }: { title: string; children: ReactNode }) {
    return (
        <section className="rounded-[30px] border border-slate-200 bg-slate-50 p-5">
            <h3 className="mb-4 text-lg font-black text-slate-950">{title}</h3>
            <div className="grid gap-4">{children}</div>
        </section>
    );
}


function ProfileNameSettingsPanel() {
    const [displayName, setDisplayName] = useState(() => getUserDisplayName(""));
    const [savedMessage, setSavedMessage] = useState("");

    const normalizedName = displayName.trim();

    function save() {
        saveUserDisplayName(normalizedName);
        setDisplayName(normalizedName);
        setSavedMessage("Имя сохранено");

        window.setTimeout(() => {
            setSavedMessage("");
        }, 2500);
    }

    function clear() {
        saveUserDisplayName("");
        setDisplayName("");
        setSavedMessage("Имя сброшено. Будет использоваться логин");

        window.setTimeout(() => {
            setSavedMessage("");
        }, 2500);
    }

    const previewName = normalizedName || getUserDisplayName("Студент");

    return (
        <section className="overflow-hidden rounded-[34px] border border-slate-200 bg-white shadow-sm">
            <div className="border-b border-slate-200 bg-slate-950 px-6 py-5 text-white">
                <div className="flex flex-wrap items-start justify-between gap-4">
                    <div>
                        <div className="mb-3 inline-flex items-center gap-2 rounded-xl bg-white/10 px-3 py-1 text-xs font-black uppercase tracking-wide text-blue-100">
                            <UserRound size={15} />
                            Display name
                        </div>

                        <h2 className="text-3xl font-black">
                            Настройка имени
                        </h2>

                        <p className="mt-2 max-w-3xl text-sm font-semibold leading-6 text-slate-300">
                            Это имя будет использоваться в учебной сессии и технической
                            поддержке вместо обычного логина, где это поддерживается сценарием.
                        </p>
                    </div>

                    <button
                        type="button"
                        onClick={save}
                        className="inline-flex h-11 items-center justify-center gap-2 rounded-2xl bg-blue-600 px-4 text-sm font-black text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700"
                    >
                        <Save size={18} />
                        Сохранить
                    </button>
                </div>

                {savedMessage && (
                    <div className="mt-4 rounded-2xl bg-emerald-500/15 px-4 py-3 text-sm font-black text-emerald-200">
                        {savedMessage}
                    </div>
                )}
            </div>

            <div className="grid grid-cols-[minmax(0,1fr)_360px] gap-6 p-6 max-xl:grid-cols-1">
                <div className="rounded-[30px] border border-slate-200 bg-slate-50 p-6">
                    <label className="block">
                        <span className="text-sm font-black text-slate-700">
                            Отображаемое имя
                        </span>

                        <input
                            value={displayName}
                            onChange={(event) => setDisplayName(event.target.value)}
                            maxLength={40}
                            placeholder="Например: Влад, Студент 1, Оператор"
                            className="mt-3 h-14 w-full rounded-2xl border border-slate-200 bg-white px-4 text-base font-bold text-slate-900 outline-none transition focus:border-blue-400 focus:ring-4 focus:ring-blue-100"
                        />
                    </label>

                    <div className="mt-4 rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm font-bold leading-6 text-blue-800">
                        Имя хранится локально в браузере. Оно не меняет логин аккаунта,
                        а только задаёт подпись для учебных и support-сценариев.
                    </div>

                    <div className="mt-5 flex flex-wrap gap-3">
                        <button
                            type="button"
                            onClick={save}
                            className="inline-flex h-12 items-center justify-center gap-2 rounded-2xl bg-blue-600 px-5 text-sm font-black text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700"
                        >
                            <Save size={18} />
                            Сохранить имя
                        </button>

                        <button
                            type="button"
                            onClick={clear}
                            className="inline-flex h-12 items-center justify-center gap-2 rounded-2xl border border-slate-300 bg-white px-5 text-sm font-black text-slate-700 transition hover:bg-slate-50"
                        >
                            <RotateCcw size={18} />
                            Использовать логин
                        </button>
                    </div>

                    <div className="mt-5 text-xs font-bold text-slate-400">
                        Ключ хранения: {DISPLAY_NAME_STORAGE_KEY}
                    </div>
                </div>

                <aside className="rounded-[30px] border border-slate-200 bg-white p-5 shadow-sm">
                    <div className="mb-4 text-sm font-black uppercase tracking-wide text-slate-400">
                        Предпросмотр
                    </div>

                    <div className="rounded-[28px] border border-slate-200 bg-slate-950 p-5 text-white">
                        <div className="flex items-center gap-3">
                            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-blue-600 text-xl font-black">
                                {previewName.slice(0, 1).toUpperCase()}
                            </div>

                            <div className="min-w-0">
                                <div className="truncate text-lg font-black">
                                    {previewName}
                                </div>

                                <div className="text-xs font-semibold text-slate-400">
                                    Участник сессии
                                </div>
                            </div>
                        </div>

                        <div className="mt-5 rounded-2xl bg-white/10 p-4">
                            <div className="text-xs font-black uppercase tracking-wide text-slate-400">
                                В чате
                            </div>

                            <div className="mt-3 max-w-[260px] rounded-2xl bg-blue-600 px-4 py-3 text-sm font-bold leading-5 text-white">
                                Пример сообщения от “{previewName}”
                            </div>
                        </div>
                    </div>
                </aside>
            </div>
        </section>
    );
}

function GamepadSettingsPanel() {
    const previewRef = useRef<HTMLDivElement | null>(null);
    const draggingRef = useRef<GamepadLayoutId | null>(null);

    const [layout, setLayout] = useState<GamepadLayout>(() => loadGamepadLayout());
    const [selectedId, setSelectedId] = useState<GamepadLayoutId>("faceButtons");
    const [savedMessage, setSavedMessage] = useState("");
    const [fullscreenOpen, setFullscreenOpen] = useState(false);

    const selectedItem = layout[selectedId];

    const selectedMeta = useMemo(
        () => GAMEPAD_LAYOUT_META.find((item) => item.id === selectedId),
        [selectedId],
    );

    function showSavedMessage(message: string) {
        setSavedMessage(message);

        window.setTimeout(() => {
            setSavedMessage("");
        }, 2500);
    }

    function updateSelected(patch: Partial<GamepadLayoutItem>) {
        setLayout((current) => ({
            ...current,
            [selectedId]: {
                ...current[selectedId],
                ...patch,
            },
        }));
    }

    function handlePointerDown(
        event: PointerEvent<HTMLDivElement>,
        id: GamepadLayoutId,
    ) {
        const preview = previewRef.current;

        if (!preview) {
            return;
        }

        draggingRef.current = id;
        setSelectedId(id);

        event.currentTarget.setPointerCapture(event.pointerId);
        moveControl(event, id, preview);
        event.preventDefault();
    }

    function handlePointerMove(
        event: PointerEvent<HTMLDivElement>,
        id: GamepadLayoutId,
    ) {
        const preview = previewRef.current;

        if (!preview || draggingRef.current !== id) {
            return;
        }

        moveControl(event, id, preview);
        event.preventDefault();
    }

    function handlePointerUp(event: PointerEvent<HTMLDivElement>) {
        draggingRef.current = null;

        try {
            event.currentTarget.releasePointerCapture(event.pointerId);
        } catch {
            // ignore
        }

        event.preventDefault();
    }

    function moveControl(
        event: PointerEvent<HTMLDivElement>,
        id: GamepadLayoutId,
        preview: HTMLDivElement,
    ) {
        const rect = preview.getBoundingClientRect();

        const x = ((event.clientX - rect.left) / rect.width) * 100;
        const y = ((event.clientY - rect.top) / rect.height) * 100;

        setLayout((current) => ({
            ...current,
            [id]: {
                ...current[id],
                x: clamp(x, 0, 100),
                y: clamp(y, 0, 100),
            },
        }));
    }

    function save() {
        saveGamepadLayout(layout);
        showSavedMessage("Раскладка сохранена");
    }

    function reset() {
        const next = resetGamepadLayout();
        setLayout(next);
        showSavedMessage("Раскладка сброшена");
    }

    function useDefaultPreset() {
        const next = cloneGamepadLayout(DEFAULT_GAMEPAD_LAYOUT);
        setLayout(next);
        showSavedMessage("Загружен стандартный пресет");
    }

    return (
        <div className="grid gap-6">
            <section className="overflow-hidden rounded-[34px] border border-slate-200 bg-white shadow-sm">
                <div className="border-b border-slate-200 bg-slate-950 px-6 py-5 text-white">
                    <div className="flex flex-wrap items-start justify-between gap-4">
                        <div>
                            <div className="mb-3 inline-flex items-center gap-2 rounded-xl bg-white/10 px-3 py-1 text-xs font-black uppercase tracking-wide text-blue-100">
                                <Gamepad2 size={15} />
                                Gamepad layout
                            </div>

                            <h2 className="text-3xl font-black">
                                Настройка игрового контроллера
                            </h2>

                            <p className="mt-2 max-w-3xl text-sm font-semibold leading-6 text-slate-300">
                                Основная настройка выполняется через полноэкранный режим:
                                там область контроллера увеличена и элементы удобно двигать пальцем.
                            </p>
                        </div>

                        <div className="flex flex-wrap gap-2">
                            <button
                                type="button"
                                onClick={() => setFullscreenOpen(true)}
                                className="inline-flex h-11 items-center justify-center gap-2 rounded-2xl bg-emerald-600 px-4 text-sm font-black text-white shadow-lg shadow-emerald-600/20 transition hover:bg-emerald-700"
                            >
                                <Maximize2 size={18} />
                                Открыть на весь экран
                            </button>

                            <button
                                type="button"
                                onClick={useDefaultPreset}
                                className="inline-flex h-11 items-center justify-center gap-2 rounded-2xl border border-white/10 bg-white/10 px-4 text-sm font-black text-white transition hover:bg-white/20"
                            >
                                <LayoutGrid size={18} />
                                Пресет
                            </button>

                            <button
                                type="button"
                                onClick={reset}
                                className="inline-flex h-11 items-center justify-center gap-2 rounded-2xl border border-white/10 bg-white/10 px-4 text-sm font-black text-white transition hover:bg-white/20"
                            >
                                <RotateCcw size={18} />
                                Сброс
                            </button>

                            <button
                                type="button"
                                onClick={save}
                                className="inline-flex h-11 items-center justify-center gap-2 rounded-2xl bg-blue-600 px-4 text-sm font-black text-white shadow-lg shadow-blue-600/20 transition hover:bg-blue-700"
                            >
                                <Save size={18} />
                                Сохранить
                            </button>
                        </div>
                    </div>

                    {savedMessage && (
                        <div className="mt-4 rounded-2xl bg-emerald-500/15 px-4 py-3 text-sm font-black text-emerald-200">
                            {savedMessage}
                        </div>
                    )}
                </div>

                <div className="grid grid-cols-[minmax(0,1fr)_360px] gap-6 p-6 max-2xl:grid-cols-1">
                    <div>
                        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                            <div>
                                <h3 className="text-xl font-black text-slate-950">
                                    Быстрый предварительный вид
                                </h3>

                                <p className="mt-1 text-sm font-semibold text-slate-500">
                                    Область предпросмотра стала крупнее. Для точной настройки нажмите “Открыть на весь экран”.
                                </p>
                            </div>

                            <div className="inline-flex items-center gap-2 rounded-2xl bg-slate-100 px-3 py-2 text-xs font-black text-slate-600">
                                <Move size={16} />
                                Drag & drop
                            </div>
                        </div>

                        <div className="overflow-x-auto rounded-[30px] bg-slate-100 p-2 sm:p-3">
                            <div
                                ref={previewRef}
                                className="relative mx-auto aspect-[19/9] min-w-[920px] overflow-hidden rounded-[28px] border border-slate-800 bg-slate-950 shadow-2xl"
                            >
                                <GamepadPreviewHeader />

                                {GAMEPAD_LAYOUT_META.map((meta) => (
                                    <PreviewControl
                                        key={meta.id}
                                        id={meta.id}
                                        label={meta.label}
                                        type={meta.type}
                                        item={layout[meta.id]}
                                        selected={selectedId === meta.id}
                                        onPointerDown={handlePointerDown}
                                        onPointerMove={handlePointerMove}
                                        onPointerUp={handlePointerUp}
                                    />
                                ))}
                            </div>
                        </div>
                    </div>

                    <aside className="rounded-[30px] border border-slate-200 bg-slate-50 p-5">
                        <div className="mb-4">
                            <div className="text-sm font-black uppercase tracking-wide text-slate-400">
                                Выбранный элемент
                            </div>

                            <div className="mt-1 text-2xl font-black text-slate-950">
                                {selectedMeta?.label || selectedId}
                            </div>

                            <div className="mt-2 text-sm font-semibold text-slate-500">
                                {selectedMeta?.type === "button" && "Обычная кнопка"}
                                {selectedMeta?.type === "stick" && "Стик управления"}
                                {selectedMeta?.type === "cluster" && "Группа кнопок"}
                            </div>
                        </div>

                        <GamepadRangePanel
                            item={selectedItem}
                            onChange={updateSelected}
                        />

                        <div className="mt-6 rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm font-bold leading-6 text-blue-800">
                            Совет: на телефоне откройте полноэкранный режим, скройте панель
                            кнопкой “Панель” и двигайте элементы прямо на большой схеме.
                        </div>
                    </aside>
                </div>
            </section>

            {fullscreenOpen && (
                <FullscreenGamepadEditor
                    layout={layout}
                    selectedId={selectedId}
                    onSelect={setSelectedId}
                    onLayoutChange={setLayout}
                    onSave={save}
                    onClose={() => setFullscreenOpen(false)}
                />
            )}
        </div>
    );
}

function FullscreenGamepadEditor({
                                     layout,
                                     selectedId,
                                     onSelect,
                                     onLayoutChange,
                                     onSave,
                                     onClose,
                                 }: {
    layout: GamepadLayout;
    selectedId: GamepadLayoutId;
    onSelect: (id: GamepadLayoutId) => void;
    onLayoutChange: (layout: GamepadLayout) => void;
    onSave: () => void;
    onClose: () => void;
}) {
    const overlayRef = useRef<HTMLDivElement | null>(null);
    const stageRef = useRef<HTMLDivElement | null>(null);
    const draggingRef = useRef<GamepadLayoutId | null>(null);

    const [panelOpen, setPanelOpen] = useState(false);
    const [orientationMessage, setOrientationMessage] = useState("");
    const [savedHint, setSavedHint] = useState("");

    const selectedItem = layout[selectedId];

    useEffect(() => {
        const overlay = overlayRef.current;

        async function openFullscreen() {
            try {
                await overlay?.requestFullscreen?.();
            } catch {
                // iPhone Safari может не разрешить fullscreen для div.
            }

            try {
                const orientation = screen.orientation as ScreenOrientation & {
                    lock?: (orientation: OrientationLockType) => Promise<void>;
                };

                await orientation.lock?.("landscape");
            } catch {
                setOrientationMessage(
                    "Если экран не перевернулся автоматически — поверните телефон горизонтально.",
                );
            }
        }

        void openFullscreen();

        return () => {
            try {
                const orientation = screen.orientation as ScreenOrientation & {
                    unlock?: () => void;
                };

                orientation.unlock?.();
            } catch {
                // ignore
            }

            if (document.fullscreenElement) {
                void document.exitFullscreen().catch(() => {});
            }
        };
    }, []);

    function showSavedHint() {
        setSavedHint("Сохранено");

        window.setTimeout(() => {
            setSavedHint("");
        }, 1800);
    }

    function updateSelected(patch: Partial<GamepadLayoutItem>) {
        onLayoutChange({
            ...layout,
            [selectedId]: {
                ...layout[selectedId],
                ...patch,
            },
        });
    }

    function handleSave() {
        onSave();
        showSavedHint();
    }

    function handlePointerDown(
        event: PointerEvent<HTMLDivElement>,
        id: GamepadLayoutId,
    ) {
        const stage = stageRef.current;

        if (!stage) {
            return;
        }

        draggingRef.current = id;
        onSelect(id);

        event.currentTarget.setPointerCapture(event.pointerId);
        moveControl(event, id, stage);
        event.preventDefault();
    }

    function handlePointerMove(
        event: PointerEvent<HTMLDivElement>,
        id: GamepadLayoutId,
    ) {
        const stage = stageRef.current;

        if (!stage || draggingRef.current !== id) {
            return;
        }

        moveControl(event, id, stage);
        event.preventDefault();
    }

    function handlePointerUp(event: PointerEvent<HTMLDivElement>) {
        draggingRef.current = null;

        try {
            event.currentTarget.releasePointerCapture(event.pointerId);
        } catch {
            // ignore
        }

        event.preventDefault();
    }

    function moveControl(
        event: PointerEvent<HTMLDivElement>,
        id: GamepadLayoutId,
        stage: HTMLDivElement,
    ) {
        const rect = stage.getBoundingClientRect();

        const x = ((event.clientX - rect.left) / rect.width) * 100;
        const y = ((event.clientY - rect.top) / rect.height) * 100;

        onLayoutChange({
            ...layout,
            [id]: {
                ...layout[id],
                x: clamp(x, 0, 100),
                y: clamp(y, 0, 100),
            },
        });
    }

    return (
        <div
            ref={overlayRef}
            className="fixed inset-0 z-[9999] touch-none overflow-hidden bg-slate-950 text-white"
        >
            <div className="absolute left-0 right-0 top-0 z-40 flex items-center justify-between gap-2 bg-slate-950/80 px-2 py-1.5 backdrop-blur">
                <div className="min-w-0">
                    <div className="truncate text-xs font-black sm:text-sm">
                        Настройка контроллера на весь экран
                    </div>

                    <div className="truncate text-[10px] font-semibold text-slate-400">
                        Выбрано: {getGamepadLayoutLabel(selectedId)}
                    </div>
                </div>

                <div className="flex shrink-0 gap-2">
                    <button
                        type="button"
                        onClick={() => setPanelOpen((value) => !value)}
                        className="inline-flex h-9 items-center justify-center gap-1.5 rounded-2xl border border-white/10 bg-white/10 px-2.5 text-[11px] font-black active:scale-95"
                    >
                        {panelOpen ? <PanelBottomClose size={17} /> : <PanelBottomOpen size={17} />}
                        Панель
                    </button>

                    <button
                        type="button"
                        onClick={handleSave}
                        className="inline-flex h-9 items-center justify-center gap-1.5 rounded-2xl bg-blue-600 px-2.5 text-[11px] font-black active:scale-95"
                    >
                        <Save size={17} />
                        Сохранить
                    </button>

                    <button
                        type="button"
                        onClick={onClose}
                        className="flex h-9 w-9 items-center justify-center rounded-2xl border border-white/10 bg-white/10 active:scale-95"
                    >
                        <X size={18} />
                    </button>
                </div>
            </div>

            <div
                className="absolute inset-x-0 flex items-center justify-center px-2"
                style={{
                    top: "42px",
                    bottom: "4px",
                }}
            >
                <div
                    ref={stageRef}
                    className="relative overflow-hidden rounded-[26px] border border-white/10 bg-slate-950 shadow-2xl"
                    style={{
                        width: "min(100vw - 8px, calc((100dvh - 52px) * 19 / 9))",
                        height: "min(100dvh - 52px, calc((100vw - 8px) * 9 / 19))",
                        maxWidth: "calc(100vw - 8px)",
                        maxHeight: "calc(100dvh - 52px)",
                        aspectRatio: "19 / 9",
                    }}
                >
                    <GamepadPreviewHeader />

                    {GAMEPAD_LAYOUT_META.map((meta) => (
                        <PreviewControl
                            key={meta.id}
                            id={meta.id}
                            label={meta.label}
                            type={meta.type}
                            item={layout[meta.id]}
                            selected={selectedId === meta.id}
                            onPointerDown={handlePointerDown}
                            onPointerMove={handlePointerMove}
                            onPointerUp={handlePointerUp}
                        />
                    ))}

                    <div className="pointer-events-none absolute left-1/2 top-1/2 z-0 -translate-x-1/2 -translate-y-1/2 text-center text-[clamp(8px,2dvh,12px)] font-semibold leading-tight text-slate-600">
                        GAMEPAD_STATE
                        <br />
                        30 FPS
                    </div>
                </div>
            </div>

            {orientationMessage && (
                <div className="absolute left-3 right-3 top-[44px] z-50 rounded-2xl border border-amber-400/25 bg-amber-500/15 px-4 py-2 text-center text-xs font-black text-amber-100 backdrop-blur">
                    {orientationMessage}
                </div>
            )}

            {savedHint && (
                <div className="absolute left-1/2 top-[44px] z-50 -translate-x-1/2 rounded-full bg-emerald-500 px-5 py-2 text-sm font-black text-white shadow-lg">
                    {savedHint}
                </div>
            )}

            {panelOpen && (
                <div className="absolute bottom-0 left-0 right-0 z-50 border-t border-white/10 bg-slate-950/90 p-2 backdrop-blur">
                    <div className="mb-1.5 flex items-center justify-between gap-2">
                        <div>
                            <div className="text-[10px] font-black uppercase tracking-wide text-slate-500">
                                Выбранный элемент
                            </div>
                            <div className="text-sm font-black">
                                {getGamepadLayoutLabel(selectedId)}
                            </div>
                        </div>

                        <button
                            type="button"
                            onClick={() => {
                                onLayoutChange(cloneGamepadLayout(DEFAULT_GAMEPAD_LAYOUT));
                            }}
                            className="inline-flex h-8 items-center justify-center gap-1.5 rounded-2xl border border-white/10 bg-white/10 px-2.5 text-[11px] font-black active:scale-95"
                        >
                            <RotateCcw size={15} />
                            Пресет
                        </button>
                    </div>

                    <div className="grid grid-cols-4 gap-1.5 max-sm:grid-cols-2">
                        <CompactRange
                            label="X"
                            value={selectedItem.x}
                            min={0}
                            max={100}
                            onChange={(value) => updateSelected({ x: value })}
                        />

                        <CompactRange
                            label="Y"
                            value={selectedItem.y}
                            min={0}
                            max={100}
                            onChange={(value) => updateSelected({ y: value })}
                        />

                        <CompactRange
                            label="W"
                            value={selectedItem.w}
                            min={4}
                            max={45}
                            onChange={(value) => updateSelected({ w: value })}
                        />

                        <CompactRange
                            label="H"
                            value={selectedItem.h}
                            min={4}
                            max={45}
                            onChange={(value) => updateSelected({ h: value })}
                        />
                    </div>
                </div>
            )}

            <button
                type="button"
                onClick={() => {
                    try {
                        const orientation = screen.orientation as ScreenOrientation & {
                            lock?: (orientation: OrientationLockType) => Promise<void>;
                        };

                        void orientation.lock?.("landscape");
                    } catch {
                        setOrientationMessage(
                            "Браузер не разрешил принудительный поворот. Поверните телефон вручную.",
                        );
                    }
                }}
                className="absolute bottom-3 right-3 z-50 flex h-11 items-center justify-center gap-2 rounded-2xl border border-white/10 bg-white/10 px-4 text-xs font-black backdrop-blur active:scale-95"
                style={{
                    display: panelOpen ? "none" : "flex",
                }}
            >
                <Minimize2 size={16} />
                Landscape
            </button>
        </div>
    );
}

function GamepadPreviewHeader() {
    return (
        <>
            <div className="absolute left-4 top-3 z-10 text-sm font-black text-white">
                Xbox-геймпад
            </div>

            <div className="absolute left-4 top-8 z-10 text-xs font-semibold text-slate-400">
                🟢 Подключено к Teacher-PC
            </div>

            <div className="absolute inset-x-0 top-[58px] z-10 h-px bg-white/10" />
        </>
    );
}

function PreviewControl({
                            id,
                            label,
                            type,
                            item,
                            selected,
                            onPointerDown,
                            onPointerMove,
                            onPointerUp,
                        }: {
    id: GamepadLayoutId;
    label: string;
    type: "button" | "stick" | "cluster";
    item: GamepadLayoutItem;
    selected: boolean;
    onPointerDown: (event: PointerEvent<HTMLDivElement>, id: GamepadLayoutId) => void;
    onPointerMove: (event: PointerEvent<HTMLDivElement>, id: GamepadLayoutId) => void;
    onPointerUp: (event: PointerEvent<HTMLDivElement>) => void;
}) {
    const style = layoutStyle(item, type !== "button");

    if (type === "stick") {
        return (
            <div
                style={style}
                onPointerDown={(event) => onPointerDown(event, id)}
                onPointerMove={(event) => onPointerMove(event, id)}
                onPointerUp={onPointerUp}
                onPointerCancel={onPointerUp}
                className={
                    selected
                        ? "absolute z-30 cursor-grab rounded-full border-4 border-blue-400 bg-white/10 shadow-[0_0_0_4px_rgba(59,130,246,.25)]"
                        : "absolute z-20 cursor-grab rounded-full border-2 border-white/25 bg-white/10"
                }
            >
                <div className="absolute inset-[22%] rounded-full border-2 border-dashed border-white/20" />
                <div className="absolute left-1/2 top-1/2 h-[35%] w-[35%] -translate-x-1/2 -translate-y-1/2 rounded-full border-4 border-white/80 bg-blue-500" />
                <div className="absolute -bottom-6 left-1/2 -translate-x-1/2 whitespace-nowrap text-xs font-black text-white">
                    {label}
                </div>
            </div>
        );
    }

    if (id === "faceButtons") {
        return (
            <div
                style={style}
                onPointerDown={(event) => onPointerDown(event, id)}
                onPointerMove={(event) => onPointerMove(event, id)}
                onPointerUp={onPointerUp}
                onPointerCancel={onPointerUp}
                className={
                    selected
                        ? "absolute z-30 cursor-grab rounded-full border-4 border-blue-400 shadow-[0_0_0_4px_rgba(59,130,246,.25)]"
                        : "absolute z-20 cursor-grab rounded-full border-2 border-white/25"
                }
            >
                <PreviewFaceButton className="left-[34%] top-0 bg-amber-500" label="Y" />
                <PreviewFaceButton className="left-0 top-[34%] bg-blue-500" label="X" />
                <PreviewFaceButton className="right-0 top-[34%] bg-red-500" label="B" />
                <PreviewFaceButton className="bottom-0 left-[34%] bg-emerald-500" label="A" />
            </div>
        );
    }

    if (id === "dpad") {
        return (
            <div
                style={style}
                onPointerDown={(event) => onPointerDown(event, id)}
                onPointerMove={(event) => onPointerMove(event, id)}
                onPointerUp={onPointerUp}
                onPointerCancel={onPointerUp}
                className={
                    selected
                        ? "absolute z-30 grid cursor-grab grid-cols-3 grid-rows-3 gap-1 rounded-2xl border-4 border-blue-400 p-1 shadow-[0_0_0_4px_rgba(59,130,246,.25)]"
                        : "absolute z-20 grid cursor-grab grid-cols-3 grid-rows-3 gap-1 rounded-2xl border-2 border-white/25 p-1"
                }
            >
                <PreviewSmallButton className="col-start-2 row-start-1" label="▲" />
                <PreviewSmallButton className="col-start-1 row-start-2" label="◀" />
                <PreviewSmallButton className="col-start-3 row-start-2" label="▶" />
                <PreviewSmallButton className="col-start-2 row-start-3" label="▼" />
            </div>
        );
    }

    return (
        <div
            style={style}
            onPointerDown={(event) => onPointerDown(event, id)}
            onPointerMove={(event) => onPointerMove(event, id)}
            onPointerUp={onPointerUp}
            onPointerCancel={onPointerUp}
            className={
                selected
                    ? "absolute z-30 flex cursor-grab items-center justify-center rounded-2xl border-4 border-blue-400 bg-white/20 px-2 text-sm font-black text-white shadow-[0_0_0_4px_rgba(59,130,246,.25)]"
                    : "absolute z-20 flex cursor-grab items-center justify-center rounded-2xl border border-white/15 bg-white/15 px-2 text-sm font-black text-white"
            }
        >
            {label}
        </div>
    );
}

function PreviewFaceButton({ label, className }: { label: string; className: string }) {
    return (
        <div
            className={`absolute flex h-[32%] w-[32%] items-center justify-center rounded-full border-4 border-white/70 text-lg font-black text-white ${className}`}
        >
            {label}
        </div>
    );
}

function PreviewSmallButton({ label, className }: { label: string; className: string }) {
    return (
        <div
            className={`flex items-center justify-center rounded-xl border border-white/15 bg-white/15 text-sm font-black text-white ${className}`}
        >
            {label}
        </div>
    );
}

function GamepadRangePanel({
                               item,
                               onChange,
                           }: {
    item: GamepadLayoutItem;
    onChange: (patch: Partial<GamepadLayoutItem>) => void;
}) {
    return (
        <div className="grid gap-4">
            <RangeEditor
                label="Позиция X"
                value={item.x}
                min={0}
                max={100}
                onChange={(value) => onChange({ x: value })}
            />

            <RangeEditor
                label="Позиция Y"
                value={item.y}
                min={0}
                max={100}
                onChange={(value) => onChange({ y: value })}
            />

            <RangeEditor
                label="Ширина"
                value={item.w}
                min={4}
                max={45}
                onChange={(value) => onChange({ w: value })}
            />

            <RangeEditor
                label="Высота"
                value={item.h}
                min={4}
                max={45}
                onChange={(value) => onChange({ h: value })}
            />
        </div>
    );
}

function RangeEditor({
                         label,
                         value,
                         min,
                         max,
                         onChange,
                     }: {
    label: string;
    value: number;
    min: number;
    max: number;
    onChange: (value: number) => void;
}) {
    return (
        <label className="block rounded-2xl border border-slate-200 bg-white p-4">
            <div className="mb-3 flex items-center justify-between gap-3">
                <span className="text-sm font-black text-slate-700">{label}</span>
                <span className="rounded-xl bg-slate-100 px-3 py-1 text-xs font-black text-slate-600">
                    {Math.round(value)}
                </span>
            </div>

            <input
                type="range"
                min={min}
                max={max}
                step={1}
                value={value}
                onChange={(event) => onChange(Number(event.target.value))}
                className="w-full"
            />
        </label>
    );
}

function CompactRange({
                          label,
                          value,
                          min,
                          max,
                          onChange,
                      }: {
    label: string;
    value: number;
    min: number;
    max: number;
    onChange: (value: number) => void;
}) {
    return (
        <label className="grid grid-cols-[26px_minmax(0,1fr)_34px] items-center gap-2 rounded-xl bg-white/10 px-2 py-1.5">
            <span className="text-xs font-black text-slate-300">{label}</span>

            <input
                type="range"
                min={min}
                max={max}
                step={1}
                value={value}
                onChange={(event) => onChange(Number(event.target.value))}
                className="w-full"
            />

            <span className="text-right text-xs font-black text-slate-300">
                {Math.round(value)}
            </span>
        </label>
    );
}

function SettingsNavButton({
                               active,
                               icon,
                               title,
                               text,
                               onClick,
                           }: {
    active: boolean;
    icon: ReactNode;
    title: string;
    text: string;
    onClick: () => void;
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            className={
                active
                    ? "flex w-full gap-4 rounded-2xl bg-blue-50 p-4 text-left text-blue-700"
                    : "flex w-full gap-4 rounded-2xl p-4 text-left text-slate-600 transition hover:bg-slate-50 hover:text-slate-950"
            }
        >
            <div
                className={
                    active
                        ? "flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-blue-600 text-white"
                        : "flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-slate-100 text-slate-500"
                }
            >
                {icon}
            </div>

            <div>
                <div className="font-black">{title}</div>
                <div className="mt-1 text-xs font-semibold opacity-70">{text}</div>
            </div>
        </button>
    );
}

function layoutStyle(item: GamepadLayoutItem, square: boolean): CSSProperties {
    return {
        left: `${item.x}%`,
        top: `${item.y}%`,
        width: `${item.w}%`,
        height: square ? undefined : `${item.h}%`,
        aspectRatio: square ? "1 / 1" : undefined,
        transform: "translate(-50%, -50%)",
    };
}

function clamp(value: number, min: number, max: number): number {
    return Math.max(min, Math.min(max, value));
}
