"""JWT auth over the shared bcrypt-hashed users table.

Django's default auth model doesn't fit the shared schema, so this handles it
directly: verify bcrypt passwords, mint HS256 JWTs, and authenticate requests
from the Bearer token.
"""
import datetime

import bcrypt
import jwt
from django.conf import settings
from rest_framework import authentication, exceptions

from .models import User


def hash_password(raw: str) -> str:
    return bcrypt.hashpw(raw.encode(), bcrypt.gensalt(rounds=10)).decode()


def verify_password(raw: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(raw.encode(), hashed.encode())
    except ValueError:
        return False


def make_token(user: User) -> str:
    now = datetime.datetime.now(tz=datetime.timezone.utc)
    payload = {
        "sub": str(user.id),          # PyJWT requires the subject claim to be a string
        "role": user.role,
        "iat": now,
        "exp": now + datetime.timedelta(minutes=settings.JWT_EXPIRY_MINUTES),
    }
    return jwt.encode(payload, settings.JWT_SECRET, algorithm=settings.JWT_ALGORITHM)


class JWTAuthentication(authentication.BaseAuthentication):
    """Reads `Authorization: Bearer <token>` and loads the user."""

    keyword = "Bearer"

    def authenticate(self, request):
        header = authentication.get_authorization_header(request).decode()
        if not header:
            return None
        parts = header.split()
        if len(parts) != 2 or parts[0] != self.keyword:
            return None
        token = parts[1]
        try:
            payload = jwt.decode(
                token, settings.JWT_SECRET, algorithms=[settings.JWT_ALGORITHM]
            )
        except jwt.ExpiredSignatureError:
            raise exceptions.AuthenticationFailed("Token expired")
        except jwt.InvalidTokenError:
            raise exceptions.AuthenticationFailed("Invalid token")

        try:
            user = User.objects.get(pk=int(payload["sub"]))
        except User.DoesNotExist:
            raise exceptions.AuthenticationFailed("User not found")
        return (user, token)

    def authenticate_header(self, request):
        return self.keyword
