import { apiFetch } from './http';
import type { CoursesDto, EnrollResponse, StudyResponse } from './types';

export function getCourses(): Promise<CoursesDto> {
  return apiFetch<CoursesDto>('/courses');
}

export function enroll(username: string): Promise<EnrollResponse> {
  return apiFetch<EnrollResponse>(`/players/${encodeURIComponent(username)}/enroll`, {
    method: 'POST',
  });
}

export function study(username: string): Promise<StudyResponse> {
  return apiFetch<StudyResponse>(`/players/${encodeURIComponent(username)}/study`, {
    method: 'POST',
  });
}
