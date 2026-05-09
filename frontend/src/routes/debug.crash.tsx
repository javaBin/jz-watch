import { createFileRoute } from '@tanstack/react-router';

export const Route = createFileRoute('/debug/crash')({
  component: CrashComponent,
});

function CrashComponent() {
  throw new Error('This is a simulated crash for testing the ErrorComponent!');
  
  return (
    <div>This should never render</div>
  );
}
