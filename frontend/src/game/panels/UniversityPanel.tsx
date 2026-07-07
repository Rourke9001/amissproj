import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { enroll, getCourses, study } from '../../api/university';
import { getJobs } from '../../api/jobs';
import { ApiError } from '../../api/http';
import { formatMinutes } from '../formatMinutes';
import type { EnrollResponse, StudyResponse } from '../../api/types';
import type { PanelProps } from './types';

function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    return err.problem.detail ?? err.problem.title ?? err.message;
  }
  return 'Could not reach the server.';
}

export function UniversityPanel({ username, player, onNotify }: PanelProps) {
  const queryClient = useQueryClient();
  const coursesQuery = useQuery({
    queryKey: ['courses'],
    queryFn: getCourses,
    staleTime: Infinity,
  });
  const jobsQuery = useQuery({ queryKey: ['jobs'], queryFn: getJobs, staleTime: Infinity });

  const enrollMutation = useMutation({
    mutationFn: () => enroll(username),
    onSuccess: (res: EnrollResponse) => {
      queryClient.setQueryData(['player', username], res.state);
      onNotify(`Enrolled at Hi-Tech U (R${res.feePaid})`);
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: ['player', username] });
    },
  });

  const studyMutation = useMutation({
    mutationFn: () => study(username),
    onSuccess: (res: StudyResponse) => {
      queryClient.setQueryData(['player', username], res.state);
      onNotify(
        res.degreeCompleted !== null
          ? `Degree completed: ${res.degreeCompleted} — education level ${res.educationLevel}!`
          : `Studied — ${res.progress}/${coursesQuery.data?.studiesPerDegree ?? res.progress} (${formatMinutes(res.minutesCharged)})`,
      );
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: ['player', username] });
    },
  });

  if (coursesQuery.isPending || jobsQuery.isPending) {
    return <p>Loading courses…</p>;
  }
  if (coursesQuery.error !== null) {
    return <p role="alert">{coursesQuery.error.message}</p>;
  }
  if (jobsQuery.error !== null) {
    return <p role="alert">{jobsQuery.error.message}</p>;
  }

  const courses = coursesQuery.data;
  const jobs = jobsQuery.data ?? [];
  if (courses === undefined) {
    return null;
  }

  const enrolled = player.stats.educationProgress > 0;
  const error = enrollMutation.error ?? studyMutation.error;

  return (
    <div className="university-panel">
      <h2>Hi-Tech U</h2>
      <ul className="degree-list">
        {courses.degrees.map((degree) => {
          const completed = player.stats.education >= degree.level;
          const isNext = player.stats.education + 1 === degree.level;
          const unlockedJobs = jobs
            .filter((j) => j.requiredEducation === degree.level)
            .map((j) => j.name)
            .join(', ');
          const className = completed
            ? 'degree degree--completed'
            : isNext
              ? 'degree degree--next'
              : 'degree';
          return (
            <li key={degree.level} className={className}>
              <span className="degree-label">
                Level {degree.level}: {degree.name}
                {completed && ' (completed)'}
                {isNext && ' (in progress)'}
              </span>
              {unlockedJobs !== '' && <p className="degree-jobs">Unlocks: {unlockedJobs}</p>}
            </li>
          );
        })}
      </ul>
      {player.stats.education >= 8 ? (
        <p>All degrees completed.</p>
      ) : enrolled ? (
        <>
          <p>
            Studies: {player.stats.educationProgress}/{courses.studiesPerDegree}
          </p>
          <button
            type="button"
            onClick={() => studyMutation.mutate()}
            disabled={studyMutation.isPending}
          >
            Study ({formatMinutes(courses.studyMinutes)})
          </button>
        </>
      ) : (
        <button
          type="button"
          onClick={() => enrollMutation.mutate()}
          disabled={enrollMutation.isPending}
        >
          Enroll (R{courses.enrollFee})
        </button>
      )}
      {error !== null && <p role="alert">{errorMessage(error)}</p>}
    </div>
  );
}
