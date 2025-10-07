# Use Node.js LTS Alpine image
FROM node:20-alpine

# Set working directory
WORKDIR /app

# Copy package files
COPY package*.json ./

# Install all dependencies (including devDependencies for build)
RUN npm ci

# Copy source code
COPY . .

# Copy Google Cloud service account credentials
COPY ai-keyboard-474217-dbd014a006c2.json /app/

# Build the application
RUN npm run build

# Remove devDependencies after build to reduce image size
RUN npm prune --production

# Create non-root user
RUN addgroup -g 1001 -S nodejs
RUN adduser -S nodejs -u 1001

# Change ownership of the app directory and service account file
RUN chown -R nodejs:nodejs /app
RUN chmod 600 /app/ai-keyboard-474217-dbd014a006c2.json

# Switch to non-root user
USER nodejs

# Set environment variables
ENV NODE_ENV=production
ENV PORT=3000
ENV GOOGLE_APPLICATION_CREDENTIALS=/app/ai-keyboard-474217-dbd014a006c2.json

# OpenAI Configuration
ENV LLM_PROVIDER=openai
ENV LLM_MODEL=gpt-3.5-turbo
ENV LLM_TEMPERATURE=0.1
ENV LLM_MAX_TOKENS=2000

# CORS Configuration
ENV CORS_ALLOWED_ORIGINS=*

# Rate Limiting
ENV RATE_LIMIT_WINDOW_MS=60000
ENV RATE_LIMIT_MAX=60

# Logging
ENV LOG_LEVEL=info

# Expose port
EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD node -e "require('http').get('http://localhost:3000/health', (res) => { process.exit(res.statusCode === 200 ? 0 : 1) })"

# Start the application
CMD ["npm", "start"]
