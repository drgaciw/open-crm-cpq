/**
 * Mirrors: com.opencrm.common.dto.ApiResponse<T>
 * Backend wraps ALL responses in this envelope.
 * The apiResponseInterceptor unwraps it so services receive T directly.
 */
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  errorCode?: string;
  traceId?: string;
  data?: T;
  errors?: string[];
  pagination?: PageInfo;
  timestamp: string;
}

/**
 * Mirrors: com.opencrm.common.dto.ApiResponse.PageInfo
 * Nested pagination metadata within ApiResponse.
 */
export interface PageInfo {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

/**
 * Convenience alias used by services for signal-based pagination state.
 * Subset of PageInfo without hasNext/hasPrevious (computed in services).
 */
export interface PaginationMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Standard pagination + sorting request params sent to Spring Boot endpoints.
 */
export interface PageRequest {
  page?: number;
  size?: number;
  sort?: string;
}

/**
 * Spring-like page shape synthesized by apiResponseInterceptor when the backend
 * returns ApiResponse with `data: T[]` plus `pagination` (list, search, org contacts).
 *
 * CRM list/search endpoints all use List + PageInfo; after the interceptor runs,
 * services receive this shape and should read `.content` for the row array.
 */
export interface SpringPageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;       // current page (0-indexed)
  first?: boolean;
  last?: boolean;
  empty?: boolean;
}

/**
 * Custom error class for errors thrown by the apiResponseInterceptor.
 * Extends Error with additional fields from the backend ApiResponse.
 */
export class ApiError extends Error {
  errorCode?: string;
  traceId?: string;
  errors?: string[];
  timestamp?: string;

  constructor(message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * Base interface for entities with an ID field.
 * Used by BaseCrudService for type-safe operations.
 */
export interface Identifiable {
  id: string | number;
}
