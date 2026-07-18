from rest_framework.permissions import BasePermission


class IsAuthenticated(BasePermission):
    def has_permission(self, request, view):
        return getattr(request.user, "is_authenticated", False)


class IsAdmin(BasePermission):
    def has_permission(self, request, view):
        return getattr(request.user, "is_admin", False)
