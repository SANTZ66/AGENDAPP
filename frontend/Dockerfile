# ---- Etapa 1: compilar la aplicacion ----
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install
COPY . .
RUN npm run build

# ---- Etapa 2: servirla con nginx ----
# La imagen final solo lleva los archivos estaticos ya compilados:
# no incluye Node ni las dependencias de desarrollo.
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
