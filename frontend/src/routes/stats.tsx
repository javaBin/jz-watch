import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { BarChart2, Video, Database, MousePointer, Search, Link2, X, Copy, RefreshCw, Check } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useToastStore } from '../lib/store'

export const Route = createFileRoute('/stats')({
    component: StatsPage,
})

function CopyButton({ text, label }: { text: string, label: string }) {
    const [copied, setCopied] = useState(false)
    
    const { addToast } = useToastStore()
    
    const handleCopy = () => {
        if (!text) return
        navigator.clipboard.writeText(text)
        setCopied(true)
        addToast(`${label} copied to clipboard`, 'success', 2000)
        setTimeout(() => setCopied(false), 2000)
    }

    return (
        <button 
            onClick={handleCopy}
            className={`p-1.5 rounded-md border transition-all ${copied ? 'bg-green-500/10 border-green-500/50 text-green-500' : 'bg-background border-surface-hover hover:border-text-secondary/50 text-text-secondary hover:text-text-primary'}`}
            title={`Copy ${label}`}
        >
            {copied ? <Check size={14} /> : <Copy size={14} />}
        </button>
    )
}

function StatsPage() {
    const [selectedStat, setSelectedStat] = useState<string | null>(null)
    const queryClient = useQueryClient()
    const { addToast } = useToastStore()

    const { data: stats } = useQuery({
        queryKey: ['stats'],
        queryFn: async () => {
            const res = await fetch('/api/stats')
            return res.json()
        }
    })

    const [page, setPage] = useState(0)
    const limit = 20

    const { data: listData, isLoading: loadingList, isFetching: fetchingList } = useQuery({
        queryKey: ['stats', 'list', selectedStat, page],
        queryFn: async () => {
            if (!selectedStat) return null
            const offset = page * limit
            const res = await fetch(`/api/stats/${selectedStat}?limit=${limit}&offset=${offset}`)
            return res.json()
        },
        enabled: !!selectedStat
    })

    const reImportMutation = useMutation({
        mutationFn: async ({ type, id }: { type: 'vimeo' | 'ai', id: string }) => {
            const res = await fetch(`/api/import/${type}/${id}`)
            if (!res.ok) throw new Error('Failed to re-import')
            return res.json()
        },
        onSuccess: () => {
            addToast('Successfully re-imported data', 'success')
            queryClient.invalidateQueries({ queryKey: ['stats'] })
            queryClient.invalidateQueries({ queryKey: ['stats', 'list'] })
        },
        onError: (err) => {
            addToast(`Re-import failed: ${err.message}`, 'error')
        }
    })

    const rows = [
        {
            title: "Data Health",
            description: "Database integrity and missing metadata tracking",
            metrics: [
                { label: 'Total Videos', value: stats?.totalVideos, id: 'total-videos', icon: Database, color: 'text-blue-500' },
                { label: 'Missing Vimeo', value: stats?.videosMissingVimeo, id: 'missing-vimeo', icon: Video, color: 'text-red-500' },
                { label: 'Missing AI', value: stats?.videosMissingAiEnrichment, id: 'missing-ai', icon: Database, color: 'text-amber-500' },
            ]
        },
        {
            title: "Backend Impressions",
            description: "How many times video data was delivered to clients",
            metrics: [
                { label: 'Total Clicks', value: stats?.totalClicks, id: 'clicks', icon: MousePointer, color: 'text-green-500' },
                { label: 'Related Clicks', value: stats?.totalRelatedClicks, id: 'related-clicks', icon: Link2, color: 'text-pink-500' },
                { label: 'Search/Explore', value: stats?.totalSearchExploreClicks, id: 'search-explore-clicks', icon: BarChart2, color: 'text-indigo-500' },
            ]
        },
        {
            title: "User Engagement",
            description: "Direct user interactions and search behavior",
            metrics: [
                { label: 'Total Views', value: stats?.totalViews, id: 'views', icon: BarChart2, color: 'text-blue-500' },
                { label: 'Total Searches', value: stats?.totalSearches, id: 'searches', icon: Search, color: 'text-purple-500' },
                { label: 'Tag Clicks', value: stats?.totalTagClicks, id: 'tag-clicks', icon: Link2, color: 'text-orange-500' },
            ]
        }
    ]

    const handlePrintJSON = (data: any) => {
        console.log("Sleeping Pill Session Data:", JSON.parse(data));
        addToast("Sleeping Pill data printed to console", "success", 2000);
    }

    return (
        <div className="max-w-7xl mx-auto space-y-10 pb-20 animate-in fade-in slide-in-from-bottom-4 duration-500">
            <div className="flex flex-col gap-2">
                <h1 className="text-4xl font-black tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-text-primary to-text-secondary">System Dashboard</h1>
                <p className="text-text-secondary">Comprehensive overview of database health, delivery metrics, and user engagement.</p>
            </div>

            <div className="space-y-12">
                {rows.map((row, rowIndex) => (
                    <div key={rowIndex} className="space-y-4">
                        <div className="flex items-baseline gap-4">
                            <h2 className="text-xl font-bold text-text-primary/90">{row.title}</h2>
                            <span className="text-xs text-text-secondary font-medium uppercase tracking-widest">{row.description}</span>
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                            {row.metrics.map(card => (
                                <button
                                    key={card.id}
                                    onClick={() => {
                                        setSelectedStat(card.id)
                                        setPage(0)
                                    }}
                                    className={`p-6 bg-surface rounded-2xl border transition-all text-left group relative overflow-hidden ${
                                        selectedStat === card.id 
                                        ? 'border-brand-500 shadow-lg shadow-brand-500/10 ring-1 ring-brand-500' 
                                        : 'border-surface-hover hover:border-text-secondary/30 hover:shadow-xl'
                                    }`}
                                >
                                    <div className={`absolute top-0 right-0 w-24 h-24 -mr-8 -mt-8 rounded-full opacity-5 transition-transform group-hover:scale-110 ${card.color.replace('text-', 'bg-')}`} />
                                    
                                    <div className="flex items-center gap-5">
                                        <div className={`p-4 rounded-xl bg-background shadow-sm border border-surface-hover group-hover:scale-110 transition-transform ${card.color}`}>
                                            <card.icon size={24} />
                                        </div>
                                        <div className="flex-1">
                                            <p className="text-text-secondary text-xs font-bold uppercase tracking-widest mb-1">{card.label}</p>
                                            <div className="flex items-baseline gap-2">
                                                <p className="text-3xl font-black tracking-tighter">{card.value?.toLocaleString() ?? '0'}</p>
                                            </div>
                                        </div>
                                    </div>
                                </button>
                            ))}
                        </div>
                    </div>
                ))}
            </div>

            {selectedStat && (
                <div id="metric-table" className="bg-surface rounded-3xl border border-surface-hover shadow-2xl overflow-hidden animate-in slide-in-from-top-4 duration-300">
                    <div className="p-8 border-b border-surface-hover flex justify-between items-center bg-surface-hover/20 backdrop-blur-md sticky top-0 z-10">
                        <div className="flex items-center gap-4">
                            <div className="w-1.5 h-8 bg-brand-500 rounded-full shadow-[0_0_15px_rgba(var(--brand-500-rgb),0.5)]" />
                            <div>
                                <h2 className="text-2xl font-black capitalize leading-none">
                                    {selectedStat.replace(/-/g, ' ')}
                                </h2>
                                <p className="text-xs text-text-secondary mt-1 font-medium uppercase tracking-widest">Detailed breakdown of current metric</p>
                            </div>
                        </div>
                        <button 
                            onClick={() => setSelectedStat(null)} 
                            className="p-3 rounded-2xl bg-background hover:bg-surface-hover text-text-secondary hover:text-text-primary transition-all border border-surface-hover"
                        >
                            <X size={20} />
                        </button>
                    </div>

                    <div className="overflow-x-auto">
                        {loadingList ? (
                            <div className="p-32 text-center flex flex-col items-center gap-6">
                                <div className="w-12 h-12 border-4 border-brand-500 border-t-transparent rounded-full animate-spin" />
                                <div className="space-y-1">
                                    <p className="text-text-primary font-black text-xl">Aggregating Data</p>
                                    <p className="text-text-secondary text-sm">Querying database for latest snapshots...</p>
                                </div>
                            </div>
                        ) : (
                            <table className="w-full text-left border-collapse">
                                <thead>
                                    <tr className="bg-surface-hover/40 text-text-secondary text-[11px] font-black uppercase tracking-[0.2em] border-b border-surface-hover">
                                        {selectedStat === 'searches' ? (
                                            <>
                                                <th className="px-8 py-5">Search Query</th>
                                                <th className="px-8 py-5">Result Count</th>
                                                <th className="px-8 py-5">Timestamp</th>
                                            </>
                                        ) : selectedStat === 'tag-clicks' ? (
                                            <>
                                                <th className="px-8 py-5">Keyword</th>
                                                <th className="px-8 py-5">Total Clicks</th>
                                            </>
                                        ) : selectedStat.includes('clicks') || selectedStat === 'views' ? (
                                            <>
                                                <th className="px-8 py-5">Video ID</th>
                                                <th className="px-8 py-5">Video Title</th>
                                                <th className="px-8 py-5 text-right">{selectedStat === 'views' ? 'Views' : 'Impressions'}</th>
                                            </>
                                        ) : (
                                            <>
                                                <th className="px-8 py-5">Video ID</th>
                                                <th className="px-8 py-5">Video Title</th>
                                                <th className="px-8 py-5 text-center">Actions</th>
                                            </>
                                        )}
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-surface-hover/50">
                                    {listData?.map((item: any, idx: number) => (
                                        <tr key={item.id || item.videoId || idx} className="hover:bg-brand-500/[0.02] transition-colors group">
                                            {selectedStat === 'searches' ? (
                                                <>
                                                    <td className="px-8 py-5 font-bold text-text-primary">{item.query}</td>
                                                    <td className="px-8 py-5">
                                                        <span className="px-3 py-1 rounded-full bg-background border border-surface-hover text-xs font-black">
                                                            {item.resultsCount}
                                                        </span>
                                                    </td>
                                                    <td className="px-8 py-5 text-xs text-text-secondary font-medium">{item.createdAt}</td>
                                                </>
                                            ) : selectedStat === 'tag-clicks' ? (
                                                <>
                                                    <td className="px-8 py-5 font-black text-brand-500">#{item.tag}</td>
                                                    <td className="px-8 py-5">
                                                        <span className="text-xl font-black tabular-nums">{item.count.toLocaleString()}</span>
                                                    </td>
                                                </>
                                            ) : selectedStat.includes('clicks') || selectedStat === 'views' ? (
                                                <>
                                                    <td className="px-8 py-5">
                                                        <code className="text-[10px] bg-background px-2 py-1 rounded-lg border border-surface-hover text-text-secondary font-mono">
                                                            {item.videoId}
                                                        </code>
                                                    </td>
                                                    <td className="px-8 py-5 font-bold text-sm text-text-primary">{item.title}</td>
                                                    <td className="px-8 py-5 text-right">
                                                        <span className="text-xl font-black tabular-nums bg-clip-text text-transparent bg-gradient-to-br from-text-primary to-text-secondary">
                                                            {item.count.toLocaleString()}
                                                        </span>
                                                    </td>
                                                </>
                                            ) : (
                                                <>
                                                    <td className="px-8 py-5">
                                                        <code className="text-[10px] bg-background px-2 py-1 rounded-lg border border-surface-hover text-text-secondary font-mono">
                                                            {item.id}
                                                        </code>
                                                    </td>
                                                    <td className="px-8 py-5 font-bold text-sm text-text-primary">{item.title}</td>
                                                    <td className="px-8 py-5">
                                                        <div className="flex justify-center gap-3">
                                                            <button 
                                                                onClick={() => reImportMutation.mutate({ type: 'vimeo', id: item.id })}
                                                                disabled={reImportMutation.isPending}
                                                                className="flex items-center gap-2 px-3 py-2 rounded-xl bg-background border border-surface-hover hover:border-green-500/50 hover:text-green-500 transition-all group/btn text-xs font-bold"
                                                                title="Re-import Vimeo Data"
                                                            >
                                                                <RefreshCw size={14} className={`${reImportMutation.isPending ? 'animate-spin' : 'group-hover/btn:rotate-180 transition-transform duration-700'}`} />
                                                                Vimeo
                                                            </button>
                                                            <button 
                                                                onClick={() => reImportMutation.mutate({ type: 'ai', id: item.id })}
                                                                disabled={reImportMutation.isPending}
                                                                className="flex items-center gap-2 px-3 py-2 rounded-xl bg-background border border-surface-hover hover:border-brand-500/50 hover:text-brand-500 transition-all group/btn text-xs font-bold"
                                                                title="Re-run AI Enrichment"
                                                            >
                                                                <Database size={14} className={`${reImportMutation.isPending ? 'animate-spin' : 'group-hover/btn:scale-110 transition-transform'}`} />
                                                                AI
                                                            </button>
                                                            <button 
                                                                onClick={() => handlePrintJSON(item.sleepingPillData)}
                                                                className="flex items-center gap-2 px-3 py-2 rounded-xl bg-background border border-surface-hover hover:border-blue-500/50 hover:text-blue-500 transition-all group/btn text-xs font-bold"
                                                                title="Print raw JSON to console"
                                                            >
                                                                <Copy size={14} />
                                                                JSON
                                                            </button>
                                                        </div>
                                                    </td>
                                                </>
                                            )}
                                        </tr>
                                    ))}
                                    {(!listData || listData.length === 0) && !loadingList && (
                                        <tr>
                                            <td colSpan={5} className="px-8 py-32 text-center">
                                                <div className="flex flex-col items-center gap-3 opacity-30">
                                                    <Database size={48} />
                                                    <p className="text-xl font-black italic uppercase tracking-widest">No Records Found</p>
                                                </div>
                                            </td>
                                        </tr>
                                    )}
                                </tbody>
                            </table>
                        )}
                    </div>

                    <div className="p-8 border-t border-surface-hover flex items-center justify-between bg-surface-hover/10">
                        <div className="flex items-center gap-4">
                            <div className="flex -space-x-2">
                                {[...Array(3)].map((_, i) => (
                                    <div key={i} className="w-6 h-6 rounded-full border-2 border-surface bg-surface-hover animate-pulse" style={{ animationDelay: `${i * 150}ms` }} />
                                ))}
                            </div>
                            <p className="text-xs text-text-secondary font-bold uppercase tracking-widest">
                                Page <span className="text-text-primary">{page + 1}</span> 
                                {fetchingList && <span className="ml-2 text-brand-500 animate-pulse">Checking for updates...</span>}
                            </p>
                        </div>
                        <div className="flex gap-4">
                            <button
                                onClick={() => {
                                    setPage(p => Math.max(0, p - 1));
                                    document.getElementById('metric-table')?.scrollIntoView({ behavior: 'smooth' });
                                }}
                                disabled={page === 0 || loadingList}
                                className="px-6 py-3 rounded-2xl bg-background border border-surface-hover text-xs font-black uppercase tracking-widest hover:bg-surface-hover disabled:opacity-30 disabled:cursor-not-allowed transition-all active:scale-95"
                            >
                                Previous
                            </button>
                            <button
                                onClick={() => {
                                    setPage(p => p + 1);
                                    document.getElementById('metric-table')?.scrollIntoView({ behavior: 'smooth' });
                                }}
                                disabled={!listData || listData.length < limit || loadingList}
                                className="px-6 py-3 rounded-2xl bg-brand-500 text-white text-xs font-black uppercase tracking-widest hover:bg-brand-600 disabled:opacity-30 disabled:bg-surface-hover disabled:text-text-secondary disabled:cursor-not-allowed transition-all active:scale-95 shadow-lg shadow-brand-500/20"
                            >
                                Next
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}
