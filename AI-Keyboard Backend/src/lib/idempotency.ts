/**
 * Simple in-memory idempotency cache for POC
 * In production, this should be replaced with Redis or similar persistent store
 */

import { logger } from './logger';

interface CacheEntry {
  response: any;
  timestamp: number;
  expiresAt: number;
}

class IdempotencyCache {
  private cache = new Map<string, CacheEntry>();
  private readonly TTL_MS = 5 * 60 * 1000; // 5 minutes

  constructor() {
    // Clean up expired entries every minute
    setInterval(() => {
      this.cleanup();
    }, 60 * 1000);
  }

  /**
   * Get cached response for requestId
   */
  get(requestId: string): any | null {
    const entry = this.cache.get(requestId);
    
    if (!entry) {
      return null;
    }

    if (Date.now() > entry.expiresAt) {
      this.cache.delete(requestId);
      logger.debug('Idempotency cache entry expired', { requestId });
      return null;
    }

    logger.info('Idempotency cache hit', { requestId });
    return entry.response;
  }

  /**
   * Store response for requestId
   */
  set(requestId: string, response: any): void {
    const now = Date.now();
    const entry: CacheEntry = {
      response,
      timestamp: now,
      expiresAt: now + this.TTL_MS
    };

    this.cache.set(requestId, entry);
    logger.info('Idempotency cache entry stored', { 
      requestId,
      cacheSize: this.cache.size 
    });
  }

  /**
   * Remove expired entries
   */
  private cleanup(): void {
    const now = Date.now();
    let removedCount = 0;

    for (const [key, entry] of this.cache.entries()) {
      if (now > entry.expiresAt) {
        this.cache.delete(key);
        removedCount++;
      }
    }

    if (removedCount > 0) {
      logger.debug('Idempotency cache cleanup', { 
        removedCount,
        remainingSize: this.cache.size 
      });
    }
  }

  /**
   * Get cache statistics
   */
  getStats(): { size: number; ttlMs: number } {
    return {
      size: this.cache.size,
      ttlMs: this.TTL_MS
    };
  }
}

export const idempotencyCache = new IdempotencyCache();
