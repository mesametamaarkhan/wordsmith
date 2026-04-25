FROM --platform=$BUILDPLATFORM golang:1.22-alpine AS builder
WORKDIR /src
ARG TARGETARCH

COPY dispatcher.go ./

RUN GOOS=linux GOARCH=$TARGETARCH go build -ldflags="-s -w" -o dispatcher ./dispatcher.go

FROM alpine:3.20
WORKDIR /app

COPY --from=builder /src/dispatcher ./dispatcher
COPY static ./static

ENV WEB_PORT=8080 \
    WORDS_HOST=api \
    WORDS_PORT=8080

EXPOSE 8080
CMD ["./dispatcher"]
