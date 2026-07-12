import { apiFetch } from './http';
import type { ApplyResponse, JobListingDto, WorkResponse } from './types';

export function getJobs(location?: string): Promise<JobListingDto[]> {
  const query = location ? `?location=${encodeURIComponent(location)}` : '';
  return apiFetch<JobListingDto[]>(`/jobs${query}`);
}

export function applyForJob(saveId: number, jobId: number): Promise<ApplyResponse> {
  return apiFetch<ApplyResponse>(`/saves/${saveId}/jobs/apply`, {
    method: 'POST',
    body: { jobId },
  });
}

export function work(saveId: number): Promise<WorkResponse> {
  return apiFetch<WorkResponse>(`/saves/${saveId}/work`, { method: 'POST' });
}
