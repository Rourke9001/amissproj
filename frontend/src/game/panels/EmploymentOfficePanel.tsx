import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { applyForJob, getJobs } from '../../api/jobs';
import { errorMessage } from '../../api/http';
import type { PanelProps } from './types';

const REJECTION_COPY: Record<string, string> = {
  NOT_ENOUGH_EDUCATION: "You don't have the degree this position needs.",
  NOT_ENOUGH_EXPERIENCE: "You don't have enough work experience for this position.",
  POOR_WORK_HISTORY: "Your work history doesn't meet our standards for this position.",
  NO_OPENINGS: 'Sorry, there are no openings right now, try again another day.',
};

function rejectionMessage(reasons: string[]): string {
  if (reasons.length === 0) {
    return 'Application declined.';
  }
  return reasons.map((reason) => REJECTION_COPY[reason] ?? 'Application declined.').join(' ');
}

export function EmploymentOfficePanel({ saveId, onNotify }: PanelProps) {
  const [workplace, setWorkplace] = useState<string | null>(null);
  const queryClient = useQueryClient();

  const jobsQuery = useQuery({ queryKey: ['jobs'], queryFn: () => getJobs(), staleTime: Infinity });

  const workplaces = useMemo(() => {
    if (!jobsQuery.data) return [];
    const seen = new Set<string>();
    const result: string[] = [];
    for (const listing of jobsQuery.data) {
      if (!seen.has(listing.location)) {
        seen.add(listing.location);
        result.push(listing.location);
      }
    }
    return result;
  }, [jobsQuery.data]);

  const jobsAtWorkplace = useMemo(
    () => (jobsQuery.data ?? []).filter((listing) => listing.location === workplace),
    [jobsQuery.data, workplace],
  );

  const applyMutation = useMutation({
    mutationFn: (jobId: number) => applyForJob(saveId, jobId),
    onSuccess: (res) => {
      queryClient.setQueryData(['save', saveId], res.state);
      onNotify(
        res.hired ? `Hired as ${res.job} at R${res.wage}/h.` : rejectionMessage(res.reasons),
      );
    },
    onError: (err) => {
      queryClient.invalidateQueries({ queryKey: ['save', saveId] });
      onNotify(errorMessage(err));
    },
  });

  if (jobsQuery.isPending) {
    return <p className="panel-muted">Loading job listings...</p>;
  }
  if (jobsQuery.isError) {
    return <p role="alert">{errorMessage(jobsQuery.error)}</p>;
  }

  if (workplace === null) {
    return (
      <div className="employment-panel">
        <h3>Employment Office</h3>
        <p>Which workplace are you interested in?</p>
        <ul className="workplace-list">
          {workplaces.map((location) => (
            <li key={location}>
              <button type="button" onClick={() => setWorkplace(location)}>
                {location}
              </button>
            </li>
          ))}
        </ul>
      </div>
    );
  }

  return (
    <div className="employment-panel">
      <h3>{workplace}</h3>
      <button type="button" className="link-button" onClick={() => setWorkplace(null)}>
        Back to workplaces
      </button>
      <ul className="job-list">
        {jobsAtWorkplace.map((listing) => (
          <li key={listing.id} className="job-listing">
            <span>{listing.name}</span>
            <span>R{listing.wage}/h</span>
            <button
              type="button"
              disabled={applyMutation.isPending}
              onClick={() => applyMutation.mutate(listing.id)}
            >
              Apply
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
