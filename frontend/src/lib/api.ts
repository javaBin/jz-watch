export interface SpeakerResponse {
    name: string;
    bio?: string;
}

export interface VideoResponse {
    id: string;
    conferenceId: string;
    title: string;
    titleEn?: string;
    titleNo?: string;
    abstractText?: string;
    abstractEn?: string;
    abstractNo?: string;
    intendedAudience?: string;
    vimeoId: string;
    embedUrl?: string;
    language?: string;
    format?: string;
    lengthMinutes?: number;
    room?: string;
    startTime?: string;
    endTime?: string;
    keywords: string[];
    aiKeywords: string[];
    baseScore: number;
    viewBoost: number;
    totalScore: number;
    viewCount?: number;
    speakers: SpeakerResponse[];
    related?: VideoResponse[];
}

// Ensure predictable base URL (proxied via Vite in dev)
const API_BASE = '/api';

// Utility to grab the current lang state dynamically from local storage / Zustand
const getLangSuffix = (): string => {
    try {
        const stored = localStorage.getItem('javazone-lang-storage');
        if (stored) {
            const parsed = JSON.parse(stored);
            if (parsed?.state?.lang === 'no') return '&lang=no';
            return '&lang=en';
        }
    } catch (e) {
        // ignore JSON parse errors
    }
    return '&lang=en';
};

const getLangParam = (): string => {
    return getLangSuffix().replace('&', '?');
};

export const api = {
    async getVideos(page = 0, size = 20, sort = 'popularity'): Promise<VideoResponse[]> {
        const res = await fetch(`${API_BASE}/videos?page=${page}&size=${size}&sort=${sort}${getLangSuffix()}`);
        if (!res.ok) throw new Error('Failed to fetch videos');
        return res.json();
    },

    async getTrendingVideos(page = 0, size = 20): Promise<VideoResponse[]> {
        const res = await fetch(`${API_BASE}/videos/trending?page=${page}&size=${size}${getLangSuffix()}`);
        if (!res.ok) throw new Error('Failed to fetch trending videos');
        return res.json();
    },

    async getVideosByTag(tag: string, page = 0, size = 20): Promise<VideoResponse[]> {
        const res = await fetch(`${API_BASE}/videos/tag?tag=${encodeURIComponent(tag)}&page=${page}&size=${size}${getLangSuffix()}`);
        if (!res.ok) throw new Error('Failed to fetch videos by tag');
        return res.json();
    },

    async searchVideos(q: string, lang = '', page = 0, size = 20): Promise<VideoResponse[]> {
        const res = await fetch(`${API_BASE}/videos/search?q=${encodeURIComponent(q)}&lang=${lang}&page=${page}&size=${size}`);
        if (!res.ok) throw new Error('Failed to search videos');
        return res.json();
    },

    async getVideo(id: string): Promise<VideoResponse> {
        const res = await fetch(`${API_BASE}/videos/${id}${getLangParam()}`);
        if (!res.ok) {
            if (res.status === 404) throw new Error('Video not found');
            throw new Error('Failed to fetch video details');
        }
        return res.json();
    },

    async recordPlayEvent(videoId: string, userUuid: string): Promise<void> {
        // Only fire off, don't necessarily await/throw heavily for tracking
        fetch(`${API_BASE}/events/video`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                videoId,
                userUuid,
                eventType: 'play'
            })
        }).catch(console.error);
    }
};
