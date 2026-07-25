FROM node:22-alpine AS build
WORKDIR /build

# `npm ci` against the lockfile only, so this layer is cached until dependencies
# actually change.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# `npm run build` typechecks first, so a type error fails the image build.
RUN npm run build

FROM nginx:1.27-alpine AS runtime

COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# Probes 127.0.0.1, not localhost: inside the container `localhost` resolves to
# ::1 first, and a connection there is refused unless nginx also binds IPv6.
HEALTHCHECK --interval=15s --timeout=3s --retries=3 \
    CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
