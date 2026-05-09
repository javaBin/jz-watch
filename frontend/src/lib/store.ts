import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface LanguageState {
    lang: 'en' | 'no';
    setLang: (lang: 'en' | 'no') => void;
    toggleLang: () => void;
}

export const useLanguageStore = create<LanguageState>()(
    persist(
        (set) => ({
            lang: 'en',
            setLang: (lang) => set({ lang }),
            toggleLang: () => set((state) => ({ lang: state.lang === 'en' ? 'no' : 'en' })),
        }),
        {
            name: 'javazone-lang-storage',
        }
    )
);

interface Toast {
    id: string;
    message: string;
    type: 'success' | 'error' | 'info';
    duration?: number;
}

interface ToastState {
    toasts: Toast[];
    addToast: (message: string, type: Toast['type'], duration?: number) => void;
    removeToast: (id: string) => void;
}

export const useToastStore = create<ToastState>((set) => ({
    toasts: [],
    addToast: (message, type, duration = 10000) => {
        const id = Math.random().toString(36).substring(2, 9);
        set((state) => ({
            toasts: [...state.toasts, { id, message, type, duration }],
        }));
        if (duration > 0) {
            setTimeout(() => {
                set((state) => ({
                    toasts: state.toasts.filter((t) => t.id !== id),
                }));
            }, duration);
        }
    },
    removeToast: (id) =>
        set((state) => ({
            toasts: state.toasts.filter((t) => t.id !== id),
        })),
}));
