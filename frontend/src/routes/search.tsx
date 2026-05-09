import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useInfiniteQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import { VideoCard } from '../components/VideoCard';
import { useInView } from 'react-intersection-observer';
import { useEffect } from 'react';

type SearchParams = {
    q?: string;
    lang?: string;
    tag?: string;
};

export const Route = createFileRoute('/search')({
    component: SearchPage,
    validateSearch: (search: Record<string, unknown>): SearchParams => {
        return {
            q: (search.q as string) || '',
            lang: (search.lang as string) || '',
            tag: (search.tag as string) || '',
        };
    },
});

import { useTitle } from '../hooks/useTitle';

function SearchPage() {
    const { q, lang, tag } = Route.useSearch();
    useTitle(tag ? `#${tag}` : q ? `Search: ${q}` : 'Search');
    const navigate = useNavigate({ from: Route.fullPath });
    const { ref, inView } = useInView();

    const { 
        data, 
        isLoading, 
        isError,
        fetchNextPage,
        hasNextPage,
        isFetchingNextPage
    } = useInfiniteQuery({
        queryKey: ['videos', 'search', q, lang, tag],
        queryFn: async ({ pageParam }) => {
            if (tag) return api.getVideosByTag(tag, pageParam, 20);
            if (q) return api.searchVideos(q, lang || '', pageParam, 20);
            return [];
        },
        initialPageParam: 0,
        getNextPageParam: (lastPage, allPages) => {
            if (!lastPage || lastPage.length < 20) return undefined;
            return allPages.length;
        },
        enabled: !!q || !!tag,
    });

    useEffect(() => {
        if (inView && hasNextPage && !isFetchingNextPage) {
            fetchNextPage();
        }
    }, [inView, fetchNextPage, hasNextPage, isFetchingNextPage]);

    const results = data?.pages?.flat() || [];

    return (
        <div className="flex flex-col gap-6 max-w-7xl mx-auto pb-12">
            <div className="flex flex-col gap-1">
                {tag ? (
                    <h1 className="text-2xl font-bold">#{tag}</h1>
                ) : (
                    <h1 className="text-2xl font-bold">Search results</h1>
                )}
                
                {tag ? (
                    <p className="text-text-secondary">Showing videos tagged with &ldquo;{tag}&rdquo;</p>
                ) : q ? (
                    <p className="text-text-secondary">Showing FTS results for &ldquo;{q}&rdquo;</p>
                ) : (
                    <p className="text-text-secondary">Type to search across title, speakers, and AI keywords.</p>
                )}
            </div>

            {!tag && (
                <div className="flex gap-2">
                    <select
                        value={lang || ''}
                        onChange={(e) => navigate({ search: (prev: any) => ({ ...prev, lang: e.target.value }) })}
                        className="bg-surface border border-surface-hover rounded-lg px-3 py-1.5 text-sm outline-none"
                    >
                        <option value="">Any Language</option>
                        <option value="en">English FTS Index</option>
                        <option value="no">Norwegian FTS Index</option>
                    </select>
                </div>
            )}

            {!q && !tag ? null : isLoading ? (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4 gap-y-8 mt-4">
                    {[...Array(8)].map((_, i) => (
                        <div key={i} className="flex flex-col gap-3">
                            <div className="aspect-video bg-surface rounded-xl animate-pulse" />
                            <div className="h-4 bg-surface rounded animate-pulse w-3/4" />
                            <div className="h-3 bg-surface rounded animate-pulse w-1/2" />
                        </div>
                    ))}
                </div>
            ) : isError ? (
                <div className="p-4 bg-red-500/10 text-red-500 rounded-lg border border-red-500/20">
                    Search request failed. Please check the backend connection.
                </div>
            ) : results?.length ? (
                <>
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4 gap-y-8 mt-4">
                        {results.map(video => (
                            <VideoCard key={video.id} video={video} />
                        ))}
                    </div>
                    <div ref={ref} className="py-10 flex items-center justify-center">
                        {isFetchingNextPage ? (
                            <div className="h-8 w-8 animate-spin rounded-full border-4 border-surface border-t-brand-500" />
                        ) : hasNextPage ? (
                            <span className="text-text-secondary text-sm">Scroll for more...</span>
                        ) : (
                            <span className="text-text-secondary text-sm">You've reached the end!</span>
                        )}
                    </div>
                </>
            ) : (
                <div className="py-20 text-center flex flex-col items-center gap-2">
                    <div className="bg-surface p-4 rounded-full inline-flex mb-2">
                        <span className="text-3xl">📭</span>
                    </div>
                    <h3 className="text-lg font-medium text-text-primary">No videos found</h3>
                    <p className="text-text-secondary max-w-md mx-auto">
                        Try using different keywords or removing the FTS language filter.
                    </p>
                </div>
            )}
        </div>
    );
}
