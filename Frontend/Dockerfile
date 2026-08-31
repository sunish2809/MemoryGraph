FROM node:24-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.29-alpine AS runtime
# Official image runs envsubst on /etc/nginx/templates/*.template at start.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /build/dist /usr/share/nginx/html

# Only these names are substituted so nginx's $host / $scheme stay intact.
ENV NGINX_ENVSUBST_FILTER=PORT|BACKEND_HOST|BACKEND_PORT|DNS_RESOLVER
ENV PORT=80
ENV BACKEND_HOST=backend
ENV BACKEND_PORT=8080
ENV DNS_RESOLVER=127.0.0.11

EXPOSE 80
