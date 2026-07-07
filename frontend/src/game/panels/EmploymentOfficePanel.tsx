import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { applyForJob, getJobs } from '../../api/jobs';
import { ApiError } from '../../api/http';
import { formatMinutes } from '../formatMinutes';
import type { ApplyResponse, JobListingDto } from '../../api/types';
import type { PanelProps } from './types';

function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    return err.problem.detail ?? err.problem.title ?? err.message;
  }
  return 'Could not reach the server.';
}

function reqBadge(label: string, requirement: number, met: boolean) {
  const className = met ? 'job-req job-req--met' : 'job-req job-req--unmet';
  const text = met ? `${label} ${requirement}` : `needs ${label} ${requirement}`;
  return <span className={className}>{text}</span>;
}

export function EmploymentOfficePanel({ username, player, onNotify }: PanelProps) {
  const queryClient = useQueryClient();
  const jobsQuery = useQuery({ queryKey: ['jobs'], queryFn: getJobs, staleTime: Infinity });

  const mutation = useMutation({
    mutationFn: (job: string) => applyForJob(username, job),
    onSuccess: (res: ApplyResponse) => {
      queryClient.setQueryData(['player', username], res.state);
      const charged = formatMinutes(res.minutesCharged);
      onNotify(
        res.hired
          ? `Hired as ${res.job} at R${res.hourlyWage}/h (${charged})`
          : `Application for ${res.job} rejected — more education needed (${charged})`,
      );
    },
    onError: () => {
      // An interview can still charge time even when the application is
      // rejected as an error (e.g. wrong location), so never leave a stale
      // cached state around.
      void queryClient.invalidateQueries({ queryKey: ['player', username] });
    },
  });

  if (jobsQuery.isPending) {
    return <p>Loading jobs…</p>;
  }
  if (jobsQuery.error !== null) {
    return <p role="alert">{jobsQuery.error.message}</p>;
  }

  const jobs: JobListingDto[] = jobsQuery.data ?? [];

  return (
    <div className="employment-office-panel">
      <h2>Employment Office</h2>
      <ul className="job-list">
        {jobs.map((listing) => {
          const educationMet = player.stats.education >= listing.requiredEducation;
          const clothingMet = player.clothing >= listing.requiredClothing;
          const isCurrent = player.job?.name === listing.name;
          return (
            <li
              key={listing.name}
              className={isCurrent ? 'job-listing job-listing--current' : 'job-listing'}
            >
              <span className="job-listing-name">
                {listing.name}
                {isCurrent && ' (current)'}
              </span>
              <span className="job-listing-wage">R{listing.hourlyWage}/h</span>
              <span className="job-listing-location">{listing.location}</span>
              {reqBadge('Education', listing.requiredEducation, educationMet)}
              {reqBadge('Clothing', listing.requiredClothing, clothingMet)}
              <button
                type="button"
                onClick={() => mutation.mutate(listing.name)}
                disabled={!educationMet || mutation.isPending}
                title={
                  !educationMet
                    ? `Requires education level ${listing.requiredEducation}`
                    : undefined
                }
              >
                Apply
              </button>
            </li>
          );
        })}
      </ul>
      {mutation.error !== null && <p role="alert">{errorMessage(mutation.error)}</p>}
    </div>
  );
}
