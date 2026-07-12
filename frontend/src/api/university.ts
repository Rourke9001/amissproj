import { apiFetch } from './http';
import type { CourseDto, EnrollResponse, StudyResponse } from './types';

export function getCourses(saveId: number): Promise<CourseDto[]> {
  return apiFetch<CourseDto[]>(`/saves/${saveId}/courses`);
}

export function enroll(saveId: number, degreeId: number): Promise<EnrollResponse> {
  return apiFetch<EnrollResponse>(`/saves/${saveId}/courses/${degreeId}/enroll`, {
    method: 'POST',
  });
}

export function study(saveId: number): Promise<StudyResponse> {
  return apiFetch<StudyResponse>(`/saves/${saveId}/courses/study`, { method: 'POST' });
}
