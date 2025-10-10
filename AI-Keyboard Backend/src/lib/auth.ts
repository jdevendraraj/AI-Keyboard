/**
 * Authentication middleware for API key validation
 */

import { Request, Response, NextFunction } from 'express';
import { logger } from './logger.js';

export interface AuthenticatedRequest extends Request {
  clientKey?: string;
}

/**
 * Middleware to validate client API key
 */
export function authenticateClient(req: AuthenticatedRequest, res: Response, next: NextFunction): void {
  const clientKey = req.headers['x-client-key'] as string;
  
  if (!clientKey) {
    logger.warn('Missing client API key', {
      ip: req.ip,
      userAgent: req.get('User-Agent')
    });
    res.status(401).json({
      error: 'Unauthorized',
      message: 'Missing x-client-key header'
    });
    return;
  }

  const validKeys = process.env.CLIENT_API_KEYS?.split(',').map(key => key.trim()) || [];
  
  if (!validKeys.includes(clientKey)) {
    logger.warn('Invalid client API key', {
      ip: req.ip,
      userAgent: req.get('User-Agent'),
      providedKey: clientKey.substring(0, 8) + '...' // Log partial key for debugging
    });
    res.status(401).json({
      error: 'Unauthorized',
      message: 'Invalid API key'
    });
    return;
  }

  req.clientKey = clientKey;
  next();
}
