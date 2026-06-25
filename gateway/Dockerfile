# Build stage
FROM golang:1.20-alpine AS builder

WORKDIR /app

COPY go.mod ./
COPY main.go ./
RUN go mod tidy

RUN CGO_ENABLED=0 GOOS=linux go build -o /gateway

# Run stage
FROM alpine:latest

WORKDIR /

COPY --from=builder /gateway /gateway

EXPOSE 8080

ENTRYPOINT ["/gateway"]
