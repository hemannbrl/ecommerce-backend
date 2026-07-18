"""Consistent error responses: every error comes back as {"detail": "..."}."""
from rest_framework.views import exception_handler as drf_handler


class ConflictError(Exception):
    """Business-rule conflict (e.g. insufficient stock) -> HTTP 409."""


def exception_handler(exc, context):
    if isinstance(exc, ConflictError):
        from rest_framework.response import Response
        return Response({"detail": str(exc)}, status=409)

    response = drf_handler(exc, context)
    if response is not None and isinstance(response.data, dict) and "detail" not in response.data:
        # flatten DRF's field-error dict into a single message
        first = next(iter(response.data.values()))
        msg = first[0] if isinstance(first, list) and first else str(first)
        response.data = {"detail": msg}
    return response
