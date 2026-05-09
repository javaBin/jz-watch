import { createFileRoute } from '@tanstack/react-router';
import { useQuery, useInfiniteQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import { VideoCard } from '../components/VideoCard';
import { useInView } from 'react-intersection-observer';
import { useEffect } from 'react';

import { useTitle } from '../hooks/useTitle';

export const Route = createFileRoute('/')({
  component: IndexPage,
});

function IndexPage() {
  useTitle();
  const { data: trending, isLoading: trendingLoading } = useQuery({
    queryKey: ['videos', 'trending', 'preview'],
    queryFn: () => api.getTrendingVideos(0, 4),
  });

  const {
    data: latest,
    isLoading: latestLoading,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage
  } = useInfiniteQuery({
    queryKey: ['videos', 'latest'],
    queryFn: ({ pageParam }) => api.getVideos(pageParam, 20),
    initialPageParam: 0,
    getNextPageParam: (lastPage, allPages) => {
      if (!lastPage || lastPage.length < 20) return undefined;
      return allPages.length;
    },
  });

  const { ref, inView } = useInView();

  useEffect(() => {
    if (inView && hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [inView, fetchNextPage, hasNextPage, isFetchingNextPage]);

  const latestVideos = latest?.pages?.flat() || [];

  return (
    <div className="flex flex-col gap-10 max-w-7xl mx-auto pb-12">

      <section>
        <h2 className="text-2xl font-bold tracking-tight mb-4">Trending Now</h2>
        {trendingLoading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="aspect-video bg-surface rounded-xl animate-pulse" />
            ))}
          </div>
        ) : trending?.length ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {trending.slice(0, 4).map(video => (
              <VideoCard key={video.id} video={video} />
            ))}
          </div>
        ) : (
          <p className="text-text-secondary text-sm">No trending videos yet.</p>
        )}
      </section>

      <section>
        <h2 className="text-2xl font-bold tracking-tight mb-4">Dive In</h2>
        {latestLoading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4 gap-y-8">
            {[...Array(12)].map((_, i) => (
              <div key={i} className="flex flex-col gap-3">
                <div className="aspect-video bg-surface rounded-xl animate-pulse" />
                <div className="h-4 bg-surface rounded animate-pulse w-3/4" />
                <div className="h-3 bg-surface rounded animate-pulse w-1/2" />
              </div>
            ))}
          </div>
        ) : latestVideos.length ? (
          <>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4 gap-y-8">
              {latestVideos.map(video => (
                <VideoCard key={video.id} video={video} />
              ))}
            </div>

            {/* Infinite Scroll trigger area */}
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
          <p className="text-text-secondary text-sm">No videos found.</p>
        )}
      </section>

    </div>
  );
}
