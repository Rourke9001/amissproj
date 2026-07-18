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

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

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
  location: string | null;
}

export interface GoalDto {
  current: number;
  target: number;
  met: boolean;
}
export interface GoalsDto {
  wealth: GoalDto;
  happiness: GoalDto;
  education: GoalDto;
  career: GoalDto;
}

export interface CurrentCourseDto {
  id: number;
  name: string;
  studiesDone: number;
}

export interface SaveStateDto {
  id: number;
  label: string;
  round: number;
  timeMinutes: number;
  timeDisplay: string;
  weekOver: boolean;
  cash: number;
  bank: number;
  debt: number;
  rentDue: boolean;
  foodWeeks: number;
  ateFastFoodLastTurn: boolean;
  clothingCasualWeeks: number;
  clothingDressWeeks: number;
  clothingBusinessWeeks: number;
  relaxation: number;
  job: JobDto;
  location: LocationDto;
  degreesEarned: string[];
  currentCourse: CurrentCourseDto | null;
  goals: GoalsDto;
  won: boolean;
}

export interface SaveSummaryDto {
  id: number;
  label: string;
  round: number;
  cash: number;
  won: boolean;
  updatedAt: string;
}
export interface GoalTargets {
  wealth: number;
  happiness: number;
  education: number;
  career: number;
}
export interface CreateSaveRequest {
  label: string;
  goals?: GoalTargets;
  random?: boolean;
}

export interface MoveRequest {
  target: string;
}
export interface MoveResponse {
  target: string;
  steps: number;
  minutesCharged: number;
  state: SaveStateDto;
}

export interface EconomyEventDto {
  event: 'NONE' | 'BOOM' | 'CRASH';
  severity: 'MINOR' | 'MODERATE' | 'MAJOR' | null;
  fired: boolean;
  wageCutTo: number | null;
  bankWiped: boolean;
  happinessLost: number;
}

export interface DoctorVisitDto {
  triggered: boolean;
  hoursLost: number;
  happinessLost: number;
  cashLost: number;
}

export interface EndWeekResponse {
  round: number;
  fed: boolean;
  rentDue: boolean;
  debtCharged: boolean;
  won: boolean;
  economy: EconomyEventDto;
  doctorVisit: DoctorVisitDto;
  state: SaveStateDto;
}

export interface BankTransactionResponse {
  operation: string;
  amount: number;
  state: SaveStateDto;
}
export interface RentPaymentResponse {
  amountPaid: number;
  minutesCharged: number;
  state: SaveStateDto;
}

export interface JobListingDto {
  id: number;
  name: string;
  location: string;
  wage: number;
}
export interface ApplyResponse {
  hired: boolean;
  reasons: string[];
  minutesCharged: number;
  job: string;
  wage: number | null;
  state: SaveStateDto;
}
export interface WorkResponse {
  status: 'OK' | 'FIRED';
  warning: boolean;
  job: string;
  pay: number;
  netPaid: number;
  garnished: number;
  minutesCharged: number;
  state: SaveStateDto;
}

export interface CourseDto {
  id: number;
  name: string;
  status: 'EARNED' | 'AVAILABLE' | 'LOCKED';
  prereqName: string | null;
  enrolled: boolean;
  studiesDone: number;
}
export interface EnrollResponse {
  feePaid: number;
  state: SaveStateDto;
}
export interface StudyResponse {
  studiesDone: number;
  studiesRemaining: number;
  degreeCompleted: string | null;
  minutesCharged: number;
  state: SaveStateDto;
}

export interface MenuItemDto {
  id: string;
  name: string;
  price: number;
}

export interface FoodPackDto {
  id: string;
  name: string;
  price: number;
  weeks: number;
}

export interface FoodCatalogDto {
  menu: MenuItemDto[];
  packs: FoodPackDto[];
}

export interface EatResponse {
  item: string;
  price: number;
  ate: boolean;
  reason: string | null;
  minutesCharged: number;
  state: SaveStateDto;
}

export interface GroceriesResponse {
  pack: string;
  price: number;
  weeksAdded: number;
  foodWeeks: number;
  state: SaveStateDto;
}

export interface ClothingItemDto {
  id: string;
  name: string;
  price: number;
  level: number;
  weeks: number;
}

export interface ClothesResponse {
  item: string;
  price: number;
  state: SaveStateDto;
}

export interface ApplianceDto {
  id: string;
  price: number;
  store: string;
  owned: boolean;
}

export interface ApplianceResponse {
  item: string;
  price: number;
  state: SaveStateDto;
}

export interface RelaxResponse {
  minutesCharged: number;
  relaxation: number;
  state: SaveStateDto;
}
