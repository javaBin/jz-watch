import { useEffect, useState, useRef, useCallback } from 'react';
import { useNavigate } from '@tanstack/react-router';
import { AlertCircle, Home, RefreshCcw, Timer } from 'lucide-react';
import { useToastStore } from '../lib/store';

interface ErrorComponentProps {
  error: Error;
  reset?: () => void;
}

export function ErrorComponent({ error, reset }: ErrorComponentProps) {
  const [uuid] = useState(() => crypto.randomUUID());
  const navigate = useNavigate();
  const [reported, setReported] = useState(false);
  const [countdown, setCountdown] = useState(15);
  const redirectedRef = useRef(false);
  const addToast = useToastStore(state => state.addToast);

  const handleGoHome = useCallback(() => {
    if (redirectedRef.current) return;
    redirectedRef.current = true;
    
    addToast(`An error occurred (ID: ${uuid}). You've been returned to the home page.`, 'error', 10000);
    navigate({ to: '/' });
  }, [uuid, addToast, navigate]);

  useEffect(() => {
    if (reported) return;

    const reportError = async () => {
      try {
        await fetch('/api/errors/report', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            uuid,
            message: error.message,
            stack: error.stack,
            url: window.location.href,
            timestamp: new Date().toISOString(),
          }),
        });
        setReported(true);
      } catch (e) {
        console.error('Failed to report error to backend', e);
      }
    };

    reportError();
  }, [error, uuid, reported]);

  useEffect(() => {
    const timer = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          handleGoHome();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [handleGoHome]);

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] p-6 text-center animate-in fade-in duration-500">
      <div className="bg-error/10 p-4 rounded-full mb-6">
        <AlertCircle className="w-12 h-12 text-error" />
      </div>
      
      <h1 className="text-3xl font-bold mb-4 text-text-primary">Oops! Something went wrong</h1>
      <p className="text-text-secondary mb-8 max-w-md mx-auto">
        We've encountered an unexpected error. Our team has been notified.
      </p>

      <div className="bg-card border border-border p-6 rounded-xl shadow-lg mb-8 max-w-lg w-full text-left">
        <div className="flex flex-col gap-2">
          <span className="text-xs font-semibold uppercase tracking-wider text-text-secondary">Error ID</span>
          <code className="bg-background px-3 py-2 rounded border border-border text-primary font-mono text-sm break-all">
            {uuid}
          </code>
        </div>
        
        <div className="mt-4 flex flex-col gap-2">
          <span className="text-xs font-semibold uppercase tracking-wider text-text-secondary">Message</span>
          <p className="text-sm text-text-primary font-medium">{error.message}</p>
        </div>
      </div>

      <div className="flex flex-wrap gap-4 justify-center">
        <button
          onClick={handleGoHome}
          className="flex items-center gap-2 bg-primary hover:bg-primary/90 text-primary-foreground px-6 py-2.5 rounded-full font-medium transition-all transform hover:scale-105 active:scale-95 shadow-md"
        >
          <Home className="w-4 h-4" />
          Go to Home Page
        </button>
        
        {reset && (
          <button
            onClick={() => reset()}
            className="flex items-center gap-2 bg-secondary hover:bg-secondary/90 text-secondary-foreground px-6 py-2.5 rounded-full font-medium transition-all transform hover:scale-105 active:scale-95 shadow-md"
          >
            <RefreshCcw className="w-4 h-4" />
            Try Again
          </button>
        )}
      </div>

      <div className="mt-8 flex items-center gap-2 text-text-secondary text-sm">
        <Timer className="w-4 h-4" />
        <span>Redirecting to home in {countdown}s...</span>
      </div>

      <p className="mt-4 text-xs text-text-secondary">
        If you contact support, please provide the Error ID above.
      </p>
    </div>
  );
}
