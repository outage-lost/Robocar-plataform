FROM node:20-alpine

WORKDIR /app

COPY backend/package*.json ./backend/
RUN cd backend && npm ci --omit=dev

COPY backend/src ./backend/src
COPY frontend ./frontend

ENV NODE_ENV=production
ENV PORT=3100

EXPOSE 3100

CMD ["node", "backend/src/server.js"]
