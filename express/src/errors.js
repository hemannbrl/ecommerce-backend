// A single error type carrying an HTTP status, plus helpers to use it.

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export const notFound = (msg = "Not found") => new ApiError(404, msg);
export const forbidden = (msg = "Forbidden") => new ApiError(403, msg);
export const conflict = (msg) => new ApiError(409, msg);
export const unauthorized = (msg = "Unauthenticated") => new ApiError(401, msg);
export const badRequest = (msg) => new ApiError(400, msg);

// Wrap async route handlers so thrown errors reach the error middleware.
export const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

// Final error handler: every error becomes { "detail": "..." }.
export function errorHandler(err, req, res, next) {
  const status = err.status || 500;
  const detail = status === 500 ? "Server error" : err.message;
  if (status === 500) console.error(err);
  res.status(status).json({ detail });
}
