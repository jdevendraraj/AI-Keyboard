/**
 * Express server setup with middleware
 */

import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';
import rateLimit from 'express-rate-limit';
import dotenv from 'dotenv';
import { logger } from './lib/logger.js';
import { authenticateClient } from './lib/auth.js';
import formatRouter from './routes/format.js';
import { transcribeChirpHandler, upload } from './routes/transcribeChirp.js';

// Load environment variables
dotenv.config();

// Log security warning about custom prompts
logger.warn('Custom prompts are forwarded to LLM provider and not stored by this server', {
  warning: 'Custom prompt templates sent by clients are forwarded to the configured LLM API and subject to third-party processing. Do not include secrets in prompts.'
});

export function createServer(): express.Application {
  const app = express();

  // Trust proxy for accurate IP detection (needed for rate limiting behind proxies)
  // Only trust first proxy in development, disable in production for security
  app.set('trust proxy', process.env.NODE_ENV === 'development' ? 1 : false);

  // Security middleware
  app.use(helmet({
    contentSecurityPolicy: {
      directives: {
        defaultSrc: ["'self'"],
        styleSrc: ["'self'", "'unsafe-inline'"],
        scriptSrc: ["'self'"],
        imgSrc: ["'self'", "data:", "https:"],
      },
    },
  }));

  // CORS configuration
  const corsOrigins = process.env.CORS_ALLOWED_ORIGINS?.split(',').map(origin => origin.trim()) || ['*'];
  app.use(cors({
    origin: corsOrigins,
    credentials: true,
    methods: ['GET', 'POST', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'x-client-key']
  }));

  // Rate limiting
  const rateLimitWindowMs = parseInt(process.env.RATE_LIMIT_WINDOW_MS || '60000');
  const rateLimitMax = parseInt(process.env.RATE_LIMIT_MAX || '60');
  
  const limiter = rateLimit({
    windowMs: rateLimitWindowMs,
    max: rateLimitMax,
    message: {
      error: 'Too Many Requests',
      message: 'Rate limit exceeded. Please try again later.'
    },
    standardHeaders: true,
    legacyHeaders: false,
    // Use a more specific key generator to avoid trust proxy issues
    keyGenerator: (req) => {
      // Use the real IP if available, otherwise fall back to connection remote address
      return req.ip || req.connection.remoteAddress || 'unknown';
    },
    handler: (req, res) => {
      logger.warn('Rate limit exceeded', {
        ip: req.ip,
        userAgent: req.get('User-Agent')
      });
      res.status(429).json({
        error: 'Too Many Requests',
        message: 'Rate limit exceeded. Please try again later.'
      });
    }
  });

  app.use(limiter);

  // Logging middleware
  const morganFormat = process.env.NODE_ENV === 'production' 
    ? 'combined' 
    : 'dev';
  
  app.use(morgan(morganFormat, {
    stream: {
      write: (message: string) => {
        logger.info('HTTP Request', { message: message.trim() });
      }
    }
  }));

  // Body parsing
  app.use(express.json({ limit: '10mb' }));
  app.use(express.urlencoded({ extended: true, limit: '10mb' }));

  // Health check endpoint (no auth required)
  app.get('/health', (req, res) => {
    res.json({ status: 'ok' });
  });

  // API routes with authentication
  app.use('/api', authenticateClient, formatRouter);
  
  // Chirp transcription endpoint (with file upload)
  app.post('/api/transcribe-chirp', authenticateClient, upload.single('file'), transcribeChirpHandler);

  // 404 handler
  app.use('*', (req, res) => {
    res.status(404).json({
      error: 'Not Found',
      message: 'Endpoint not found'
    });
  });

  // Global error handler
  app.use((error: Error, req: express.Request, res: express.Response, next: express.NextFunction) => {
    logger.error('Unhandled error', {
      error: error.message,
      stack: error.stack,
      ip: req.ip,
      url: req.url,
      method: req.method
    });

    res.status(500).json({
      error: 'Internal Server Error',
      message: 'An unexpected error occurred'
    });
  });

  return app;
}
