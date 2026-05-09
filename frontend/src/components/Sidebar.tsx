import { Link } from '@tanstack/react-router';
import { Home, TrendingUp, Search, Compass } from 'lucide-react';
import { cn } from '../lib/utils';

interface SidebarProps {
    isOpen: boolean;
}

export function Sidebar({ isOpen }: SidebarProps) {
    const items = [
        { name: 'Home', to: '/', icon: Home },
        { name: 'Trending', to: '/trending', icon: TrendingUp },
        { name: 'Explore', to: '/explore', icon: Compass },
    ];

    return (
        <aside
            className={cn(
                "fixed inset-y-0 left-0 z-40 mt-16 w-64 bg-background transition-transform duration-300 lg:static lg:translate-x-0 border-r border-surface-hover",
                isOpen ? "translate-x-0" : "-translate-x-full"
            )}
        >
            <div className="flex flex-col gap-1 p-3">
                {items.map((item) => (
                    <Link
                        key={item.name}
                        to={item.to}
                        activeOptions={{ exact: true }}
                        className="flex items-center gap-4 rounded-lg px-3 py-2.5 text-sm font-medium hover:bg-surface-hover text-text-primary/90 hover:text-text-primary [&.active]:bg-surface-hover [&.active]:font-semibold"
                    >
                        <item.icon className="h-5 w-5" />
                        {item.name}
                    </Link>
                ))}

                <hr className="my-2 border-surface-hover" />

                <h3 className="px-3 py-2 text-xs font-semibold text-text-secondary uppercase tracking-wider">
                    Topics
                </h3>
                {['Java', 'Kotlin', 'Architecture', 'Cloud'].map(topic => (
                    <Link
                        key={topic}
                        to="/search"
                        search={{ q: topic.toLowerCase() }}
                        className="flex items-center gap-4 rounded-lg px-3 py-2 text-sm font-medium hover:bg-surface-hover text-text-primary/90"
                    >
                        <Search className="h-4 w-4 opacity-70" />
                        {topic}
                    </Link>
                ))}
            </div>
        </aside>
    );
}
