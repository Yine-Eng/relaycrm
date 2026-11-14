/**
 * Every error the API returns, as one type. The backend answers errors with
 * application/problem+json (RFC 9457); its extra members are kept as fields:
 *
 *   400  errors:  {"lastName": "must not be blank"}
 *   409  field, from, to, allowed:  a stage move that is not allowed, and where the record may go
 *   412  the record changed since it was read (If-Match)
 *   428  If-Match was missing
 *
 * A request that never reached the server is status 0.
 */
export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly detail: string | undefined
  readonly instance: string | undefined
  /** 400: the fields that were rejected, and why. */
  readonly errors: Readonly<Record<string, string>> | undefined
  /** 409 on a stage or status move: the field, where the record is, where the caller tried to take it. */
  readonly field: string | undefined
  readonly from: string | undefined
  readonly to: string | undefined
  /** 409 on a stage or status move: where the record may go from here (empty when it is final). */
  readonly allowed: readonly string[] | undefined

  constructor(problem: {
    status: number
    title: string
    detail?: string | undefined
    instance?: string | undefined
    errors?: Record<string, string> | undefined
    field?: string | undefined
    from?: string | undefined
    to?: string | undefined
    allowed?: string[] | undefined
  }) {
    super(problem.detail ?? problem.title)
    this.name = 'ApiError'
    this.status = problem.status
    this.title = problem.title
    this.detail = problem.detail
    this.instance = problem.instance
    this.errors = problem.errors
    this.field = problem.field
    this.from = problem.from
    this.to = problem.to
    this.allowed = problem.allowed
  }

  /**
   * Builds the error from a problem+json body. Members of the wrong type are
   * dropped rather than trusted; the HTTP status always wins over the body's.
   */
  static fromProblem(status: number, body: unknown, fallbackTitle = 'Request failed'): ApiError {
    const problem = isObject(body) ? body : {}
    return new ApiError({
      status,
      title: stringOr(problem.title) ?? fallbackTitle,
      detail: stringOr(problem.detail),
      instance: stringOr(problem.instance),
      errors: stringRecord(problem.errors),
      field: stringOr(problem.field),
      from: stringOr(problem.from),
      to: stringOr(problem.to),
      allowed: stringArray(problem.allowed),
    })
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function stringOr(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined
}

function stringArray(value: unknown): string[] | undefined {
  return Array.isArray(value) && value.every((item) => typeof item === 'string') ? value : undefined
}

function stringRecord(value: unknown): Record<string, string> | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const entries = Object.entries(value).filter(
    (entry): entry is [string, string] => typeof entry[1] === 'string',
  )
  return Object.fromEntries(entries)
}
