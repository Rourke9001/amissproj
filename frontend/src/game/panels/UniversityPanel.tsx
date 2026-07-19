import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { enroll, getCourses, study } from '../../api/university';
import { errorMessage } from '../../api/http';
import type { PanelProps } from './types';

const STATUS_LABEL: Record<string, string> = {
  EARNED: 'Earned',
  AVAILABLE: 'Available',
  LOCKED: 'Locked',
};

export function UniversityPanel({ saveId, player, onNotify }: PanelProps) {
  const queryClient = useQueryClient();
  const coursesQuery = useQuery({
    queryKey: ['courses', saveId],
    queryFn: () => getCourses(saveId),
  });

  const enrollMutation = useMutation({
    mutationFn: (degreeId: number) => enroll(saveId, degreeId),
    onSuccess: (res) => {
      queryClient.setQueryData(['save', saveId], res.state);
      queryClient.invalidateQueries({ queryKey: ['courses', saveId] });
      onNotify(`Enrolled, R${res.feePaid} fee paid.`);
    },
    onError: (err) => {
      queryClient.invalidateQueries({ queryKey: ['save', saveId] });
      onNotify(errorMessage(err));
    },
  });

  const studyMutation = useMutation({
    mutationFn: () => study(saveId),
    onSuccess: (res) => {
      queryClient.setQueryData(['save', saveId], res.state);
      queryClient.invalidateQueries({ queryKey: ['courses', saveId] });
      onNotify(
        res.degreeCompleted
          ? `Studied, graduated with a degree in ${res.degreeCompleted}!`
          : `Studied, ${res.studiesRemaining} sessions to go.`,
      );
    },
    onError: (err) => {
      queryClient.invalidateQueries({ queryKey: ['save', saveId] });
      onNotify(errorMessage(err));
    },
  });

  if (coursesQuery.isPending) {
    return <p className="panel-muted">Loading course board...</p>;
  }
  if (coursesQuery.isError) {
    return <p role="alert">{errorMessage(coursesQuery.error)}</p>;
  }

  return (
    <div className="university-panel">
      <h3>Hi-Tech U</h3>
      {player.currentCourse && (
        <p className="current-course">
          {`Studying ${player.currentCourse.name} (${player.currentCourse.studiesDone}/${player.currentCourse.studiesRequired})`}{' '}
          <button
            type="button"
            disabled={studyMutation.isPending}
            onClick={() => studyMutation.mutate()}
          >
            Study
          </button>
        </p>
      )}
      <ul className="degree-list">
        {coursesQuery.data.map((course) => (
          <li key={course.id} className="degree-row">
            <span className="degree-name">{course.name}</span>
            <span className="degree-status">{STATUS_LABEL[course.status]}</span>
            {course.status === 'LOCKED' && course.prereqName && (
              <span className="degree-prereq">Requires: {course.prereqName}</span>
            )}
            {course.status === 'AVAILABLE' && !course.enrolled && (
              <button
                type="button"
                disabled={enrollMutation.isPending}
                onClick={() => enrollMutation.mutate(course.id)}
              >
                Enroll
              </button>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
