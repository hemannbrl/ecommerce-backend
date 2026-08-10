"""Security-header middleware (this API keeps its middleware stack minimal, so it
sets the headers itself rather than pulling in Django's clickjacking middleware)."""


class SecurityHeadersMiddleware:
    _PERMISSIONS_POLICY = (
        "geolocation=(), microphone=(), camera=(), payment=(), usb=(), "
        "magnetometer=(), gyroscope=(), accelerometer=(), interest-cohort=()"
    )

    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        response = self.get_response(request)
        response.setdefault("X-Content-Type-Options", "nosniff")
        response.setdefault("X-Frame-Options", "DENY")
        response.setdefault("Referrer-Policy", "strict-origin-when-cross-origin")
        response.setdefault("Permissions-Policy", self._PERMISSIONS_POLICY)
        return response
