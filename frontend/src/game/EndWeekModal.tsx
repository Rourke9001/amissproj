import type { EndWeekResponse } from '../api/types';

interface EndWeekModalProps {
  result: EndWeekResponse;
  onClose: () => void;
}

export function EndWeekModal({ result, onClose }: EndWeekModalProps) {
  return (
    <div className="end-week-overlay">
      <div className="end-week-modal" role="dialog" aria-modal="true">
        <h2>Week over — Round {result.round} begins</h2>
        <p>{result.fed ? 'You ate this week.' : 'You went hungry — the coming week is shorter.'}</p>
        {result.debtCharged && <p>Unpaid rent was charged to your debt (+R80).</p>}
        {result.rentDue && <p>Rent is due this round.</p>}
        {result.economy.event === 'BOOM' && <p>Economic boom! Prices and wages have surged.</p>}
        {result.economy.event === 'CRASH' && <p>The market crashed! Prices tumble.</p>}
        {result.economy.fired && <p>You were laid off in the downturn.</p>}
        {result.economy.wageCutTo != null && (
          <p>Your pay was cut to R{result.economy.wageCutTo}/h.</p>
        )}
        {result.economy.bankWiped && <p>Your bank savings were wiped out.</p>}
        <button type="button" onClick={onClose}>
          Close
        </button>
      </div>
    </div>
  );
}
