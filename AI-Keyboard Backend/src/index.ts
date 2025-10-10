/**
 * Main application entry point
 */

import { config } from 'dotenv';
import path from 'path';
import { fileURLToPath } from 'url';
import { existsSync } from 'fs';

// Get the directory of the current module
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Load environment variables from .env file
const envPath = path.join(__dirname, '..', '.env');
console.log('Looking for .env file at:', envPath);
console.log('.env file exists:', existsSync(envPath));

const result = config({ path: envPath });

// Manually set environment variables from parsed result
if (result.parsed) {
  Object.assign(process.env, result.parsed);
}
import { createServer } from './server.js';
import { logger } from './lib/logger.js';


const PORT = parseInt(process.env.PORT || '3000');

async function startServer(): Promise<void> {
  try {
    // Validate required environment variables
    if (!process.env.OPENAI_API_KEY && process.env.LLM_PROVIDER !== 'mock') {
      logger.error('Missing required environment variable: OPENAI_API_KEY');
      process.exit(1);
    }

    if (!process.env.CLIENT_API_KEYS) {
      logger.error('Missing required environment variable: CLIENT_API_KEYS');
      process.exit(1);
    }

    // Create and start server
    const app = createServer();
    
    const server = app.listen(PORT, () => {
      logger.info('Server started', {
        port: PORT,
        nodeEnv: process.env.NODE_ENV || 'development',
        llmProvider: process.env.LLM_PROVIDER || 'openai',
        corsOrigins: process.env.CORS_ALLOWED_ORIGINS || '*'
      });
    });

    // Graceful shutdown
    const gracefulShutdown = (signal: string) => {
      logger.info(`Received ${signal}, shutting down gracefully`);
      
      server.close(() => {
        logger.info('Server closed');
        process.exit(0);
      });

      // Force close after 10 seconds
      setTimeout(() => {
        logger.error('Forced shutdown after timeout');
        process.exit(1);
      }, 10000);
    };

    process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
    process.on('SIGINT', () => gracefulShutdown('SIGINT'));

  } catch (error) {
    logger.error('Failed to start server', {
      error: error instanceof Error ? error.message : 'Unknown error'
    });
    process.exit(1);
  }
}

// Start the server
startServer();
