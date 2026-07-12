interface WinBannerProps {
  onDismiss: () => void;
}

export function WinBanner({ onDismiss }: WinBannerProps) {
  return (
    <div className="win-banner-overlay" role="dialog" aria-label="You won">
      <div className="win-banner">
        <h2>You Won!</h2>
        <p>Every goal met. Keep playing to see how far you can go.</p>
        <button type="button" onClick={onDismiss}>
          Keep Playing
        </button>
      </div>
    </div>
  );
}
