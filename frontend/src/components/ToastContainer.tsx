import { useToastStore } from '../lib/store';
import { X, AlertCircle, CheckCircle, Info } from 'lucide-react';
import { clsx } from 'clsx';

export function ToastContainer() {
    const { toasts, removeToast } = useToastStore();

    return (
        <div className="fixed bottom-4 right-4 z-[9999] flex flex-col gap-2 w-full max-w-sm pointer-events-none">
            {toasts.map((toast) => (
                <div
                    key={toast.id}
                    className={clsx(
                        "pointer-events-auto flex items-center gap-3 p-4 rounded-lg shadow-xl border animate-in slide-in-from-right-full duration-300",
                        toast.type === 'error' && "bg-error/10 border-error/20 text-error",
                        toast.type === 'success' && "bg-success/10 border-success/20 text-success",
                        toast.type === 'info' && "bg-primary/10 border-primary/20 text-primary"
                    )}
                >
                    <div className="flex-shrink-0">
                        {toast.type === 'error' && <AlertCircle className="w-5 h-5" />}
                        {toast.type === 'success' && <CheckCircle className="w-5 h-5" />}
                        {toast.type === 'info' && <Info className="w-5 h-5" />}
                    </div>
                    
                    <div className="flex-1 text-sm font-medium">
                        {toast.message}
                    </div>

                    <button
                        onClick={() => removeToast(toast.id)}
                        className="flex-shrink-0 hover:bg-black/5 p-1 rounded-full transition-colors"
                    >
                        <X className="w-4 h-4" />
                    </button>
                </div>
            ))}
        </div>
    );
}
