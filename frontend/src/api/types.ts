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
