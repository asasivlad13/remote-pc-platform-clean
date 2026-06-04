import type { InputHTMLAttributes } from "react";
import { clsx } from "clsx";

type TextInputProps = InputHTMLAttributes<HTMLInputElement>;

export function TextInput({ className, ...props }: TextInputProps) {
    return (
        <input
            className={clsx(
                "min-h-14 w-full rounded-2xl border border-white/15 bg-white/10 px-4 text-base text-white outline-none transition placeholder:text-white/35 focus:border-blue-400 focus:bg-white/15",
                className,
            )}
            {...props}
        />
    );
}