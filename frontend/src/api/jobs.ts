import { apiFetch } from './http';
import type { ApplyResponse, JobListingDto, WorkResponse } from './types';

export function getJobs(): Promise<JobListingDto[]> {
  return apiFetch<JobListingDto[]>('/jobs');
}

export function applyForJob(username: string, job: string): Promise<ApplyResponse> {
  return apiFetch<ApplyResponse>(`/players/${encodeURIComponent(username)}/jobs/apply`, {
    method: 'POST',
    body: { job },
  });
}

export function work(username: string): Promise<WorkResponse> {
  return apiFetch<WorkResponse>(`/players/${encodeURIComponent(username)}/work`, {
    method: 'POST',
  });
}
