import { createFileRoute, Link } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import { VideoCard } from '../components/VideoCard';
import { formatDistanceToNow } from 'date-fns';

export const Route = createFileRoute('/video/$id')({
    component: VideoPage,
});

import { useTitle } from '../hooks/useTitle';

function VideoPage() {
    const { id } = Route.useParams();

    const { data: video, isLoading, isError } = useQuery({
        queryKey: ['video', id],
        queryFn: () => api.getVideo(id),
    });

    useTitle(video?.title);

    if (isLoading) {
        return (
            <div className="max-w-7xl mx-auto flex flex-col lg:flex-row gap-6 pb-12 animate-pulse">
                <div className="flex-1">
                    <div className="w-full aspect-video bg-surface rounded-xl hidden lg:block" />
                    <div className="mt-4 h-8 bg-surface rounded w-3/4" />
                    <div className="mt-2 h-4 bg-surface rounded w-1/4" />
                </div>
                <div className="lg:w-[400px] flex flex-col gap-4">
                    {[...Array(4)].map((_, i) => (
                        <div key={i} className="w-full h-24 bg-surface rounded-xl" />
                    ))}
                </div>
            </div>
        );
    }

    if (isError || !video) {
        return (
            <div className="py-20 text-center flex flex-col items-center gap-2">
                <div className="bg-surface p-4 rounded-full inline-flex mb-2">
                    <span className="text-3xl">😞</span>
                </div>
                <h3 className="text-lg font-medium text-text-primary">Video not found</h3>
                <p className="text-text-secondary">It might have been removed or the ID is invalid.</p>
            </div>
        );
    }

    const formattedDate = video.startTime
        ? formatDistanceToNow(new Date(video.startTime), { addSuffix: true })
        : '';

    return (
        <div className="max-w-7xl mx-auto flex flex-col lg:flex-row gap-6 pb-12">

            {/* Main Video Area */}
            <div className="flex-1 flex flex-col gap-4">
                {video.embedUrl ? (
                    <div className="w-full aspect-video bg-black rounded-xl overflow-hidden relative">
                        <iframe
                            src={(function(url) {
                                try {
                                    const u = new URL(url);
                                    u.searchParams.set('autoplay', '1');
                                    return u.toString();
                                } catch(e) {
                                    return url;
                                }
                            })(video.embedUrl)}
                            className="absolute inset-0 w-full h-full"
                            frameBorder="0"
                            allow="autoplay; fullscreen; picture-in-picture"
                            allowFullScreen
                        ></iframe>
                    </div>
                ) : (
                    <div className="w-full aspect-video bg-surface rounded-xl flex items-center justify-center">
                        <p className="text-text-secondary">No embed URL available.</p>
                    </div>
                )}

                <div>
                    <h1 className="text-2xl font-bold tracking-tight text-text-primary">
                        {video.title}
                    </h1>

                    <div className="flex items-center gap-4 mt-2 text-sm text-text-secondary">
                        <span className="font-semibold text-text-primary">
                            {video.viewCount || 0} views
                        </span>
                        {formattedDate && <span>{formattedDate}</span>}
                        {video.lengthMinutes && <span>{video.lengthMinutes} minutes</span>}
                        {video.room && <span>Room: {video.room}</span>}
                    </div>
                </div>

                <div className="bg-surface rounded-xl p-6 mt-2 flex flex-col gap-4">
                    <div className="flex flex-wrap gap-2 text-sm">
                        {(() => {
                            const allKw = new Set<string>();
                            if (video.keywords) video.keywords.forEach(k => allKw.add(k));
                            if (video.aiKeywordsEn) video.aiKeywordsEn.forEach(k => allKw.add(k));
                            if (video.aiKeywordsNo) video.aiKeywordsNo.forEach(k => allKw.add(k));
                            return Array.from(allKw);
                        })().map((kw, i) => (
                            <Link 
                                key={i} 
                                to="/search"
                                search={{ tag: kw }}
                                className="text-brand-500 bg-brand-500/10 hover:bg-brand-500/20 px-2 py-1 rounded transition-colors cursor-pointer"
                            >
                                #{kw}
                            </Link>
                        ))}
                    </div>

                    <div className="text-text-primary/90 leading-relaxed whitespace-pre-wrap">
                        {video.abstractText || "No abstract available for this talk."}
                    </div>

                    {video.speakers && video.speakers.length > 0 && (
                        <div className="mt-4 border-t border-surface-hover pt-4">
                            <h3 className="font-semibold mb-3">Speakers</h3>
                            <div className="flex flex-col gap-4">
                                {video.speakers.map((s, i) => (
                                    <div key={i} className="flex flex-col">
                                        <span className="font-medium text-text-primary">{s.name}</span>
                                        {s.bio && <span className="text-sm text-text-secondary mt-1 whitespace-pre-wrap">{s.bio}</span>}
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            </div>

            {/* Sidebar: Related Videos */}
            <div className="lg:w-[400px] flex flex-col gap-4">
                <h3 className="font-bold text-lg mb-1">Related Videos</h3>
                {video.related?.map((relatedVid) => (
                    <div key={relatedVid.id} className="flex gap-2">
                        <div className="w-40 flex-shrink-0">
                            <VideoCard video={relatedVid} />
                        </div>
                        <div className="flex flex-col overflow-hidden">
                            {/* Quick compact display for sidebar items */}
                            <span className="text-sm font-semibold leading-tight line-clamp-2">{relatedVid.title}</span>
                            <span className="text-xs text-text-secondary mt-1 truncate">
                                {relatedVid.speakers?.map(s => s.name).join(', ')}
                            </span>
                        </div>
                    </div>
                ))}
            </div>

        </div>
    );
}
