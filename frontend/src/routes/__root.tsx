import { createRootRoute, Outlet } from '@tanstack/react-router';
import { Header } from '../components/Header';
import { Sidebar } from '../components/Sidebar';
import { ErrorComponent } from '../components/ErrorComponent';
import { ToastContainer } from '../components/ToastContainer';
import { useState } from 'react';

export const Route = createRootRoute({
    component: RootLayout,
    errorComponent: ErrorComponent,
});

function RootLayout() {
    const [sidebarOpen, setSidebarOpen] = useState(true);

    return (
        <div className="flex flex-col min-h-screen bg-background text-text-primary">
            <Header onMenuClick={() => setSidebarOpen(prev => !prev)} />

            <div className="flex flex-1 overflow-hidden">
                <Sidebar isOpen={sidebarOpen} />

                <main className="flex-1 overflow-y-auto p-4 lg:p-6 bg-background relative">
                    <Outlet />
                </main>
            </div>
            <ToastContainer />
        </div>
    );
}
