// TypeScript mirrors of the Spring API's Java record DTOs.

export interface RegisterRequest {
  username: string;
  password: string;
}

export interface RegisterResponse {
  username: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export interface MeResponse {
  username: string;
}

export interface HighscoreEntry {
  rank: number;
  username: string;
  round: number;
}

// RFC 7807 problem details (Spring ProblemDetail); `type` is a URN like
// "urn:amiss:invalid-credentials".
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

// One stop of the 13-stop board ring; `id` is the domain.board.Location enum
// name (e.g. "LOW_COST_HOUSING").
export interface LocationDto {
  id: string;
  name: string;
  ringIndex: number;
  row: number;
  col: number;
}

export interface TravelDto {
  minutesPerStep: number;
  enterBuildingMinutes: number;
}

export interface BoardDto {
  stops: LocationDto[];
  travel: TravelDto;
}

export interface JobDto {
  name: string;
  hourlyWage: number | null;
  location: string;
}

export interface StatsDto {
  education: number;
  educationProgress: number;
  happiness: number;
  workExperience: number;
}

export interface GoalDto {
  current: number;
  target: number;
}

export interface GoalsDto {
  cash: GoalDto;
  happiness: GoalDto;
  workExperience: GoalDto;
  education: GoalDto;
}

export interface PlayerStateDto {
  username: string;
  round: number;
  timeMinutes: number;
  timeDisplay: string;
  weekOver: boolean;
  cash: number;
  bank: number;
  debt: number;
  rentDue: boolean;
  foodWeeks: number;
  clothing: number;
  job: JobDto | null;
  stats: StatsDto;
  goals: GoalsDto;
  location: LocationDto;
}

export interface MoveRequest {
  target: string;
}

export interface MoveResponse {
  target: string;
  steps: number;
  minutesCharged: number;
  state: PlayerStateDto;
}

export interface EndWeekResponse {
  round: number;
  fed: boolean;
  rentDue: boolean;
  debtCharged: boolean;
  state: PlayerStateDto;
}

export interface BankTransactionResponse {
  operation: string;
  amount: number;
  state: PlayerStateDto;
}

export interface RentPaymentResponse {
  amountPaid: number;
  minutesCharged: number;
  state: PlayerStateDto;
}
