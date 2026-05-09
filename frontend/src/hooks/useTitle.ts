import { useEffect } from 'react';

export function useTitle(title?: string) {
    useEffect(() => {
        const baseTitle = 'JavaZone Watch';
        document.title = title ? `${title} | ${baseTitle}` : baseTitle;
    }, [title]);
}
