FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000

# Gunicorn serves the WSGI app in production.
# gthread workers + threads so a slow request doesn't tie up a whole worker;
# override the worker count with WEB_CONCURRENCY on bigger hosts.
CMD ["sh", "-c", "gunicorn config.wsgi:application --bind 0.0.0.0:8000 --workers ${WEB_CONCURRENCY:-3} --worker-class gthread --threads 4 --worker-tmp-dir /dev/shm --timeout 60"]
