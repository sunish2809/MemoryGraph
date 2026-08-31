FROM python:3.11-slim-bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libgl1 \
        libglib2.0-0 \
        g++ \
        build-essential \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

ENV INSIGHTFACE_HOME=/models
RUN mkdir -p /models

EXPOSE 8090

# HOST=:: on Railway so the private IPv6 network can reach this process.
ENV HOST=0.0.0.0
ENV PORT=8090
CMD ["sh", "-c", "uvicorn app.main:app --host ${HOST} --port ${PORT}"]
