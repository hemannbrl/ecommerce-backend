package com.example.shop;

/** Business exceptions mapped to HTTP status codes by ApiExceptionHandler. */

class NotFoundException extends RuntimeException {
    NotFoundException(String message) { super(message); }
}

class ForbiddenException extends RuntimeException {
    ForbiddenException(String message) { super(message); }
}

class ConflictException extends RuntimeException {
    ConflictException(String message) { super(message); }
}

class UnauthorizedException extends RuntimeException {
    UnauthorizedException(String message) { super(message); }
}

class BadRequestException extends RuntimeException {
    BadRequestException(String message) { super(message); }
}
