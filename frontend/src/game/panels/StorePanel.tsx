// Dumb, reusable "shop" layout shared by every stop that sells a catalog of
// items (Monolith Burgers, Black's Market, QT Clothing). All data fetching
// and mutation wiring lives in the per-shop container components; this just
// renders rows and forwards clicks.
export interface StoreRow {
  id: string;
  label: string;
  price: number;
  detail?: string;
  actionLabel: string;
  disabled?: boolean;
}

interface StorePanelProps {
  heading: string;
  statusLine?: string;
  rows: StoreRow[];
  onAction: (id: string) => void;
  pending: boolean;
  error: string | null;
}

export function StorePanel({
  heading,
  statusLine,
  rows,
  onAction,
  pending,
  error,
}: StorePanelProps) {
  return (
    <div className="store-panel">
      <h2>{heading}</h2>
      {statusLine !== undefined && <p className="store-panel-status">{statusLine}</p>}
      <ul className="store-list">
        {rows.map((row) => (
          <li key={row.id} className="store-row">
            <span className="store-row-label">{row.label}</span>
            <span className="store-row-price">R{row.price}</span>
            {row.detail !== undefined && <span className="store-row-detail">{row.detail}</span>}
            <button
              type="button"
              onClick={() => onAction(row.id)}
              disabled={pending || row.disabled === true}
            >
              {row.actionLabel}
            </button>
          </li>
        ))}
      </ul>
      {error !== null && <p role="alert">{error}</p>}
    </div>
  );
}
