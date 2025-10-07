/**
 * Tests for idempotency cache functionality
 */

import { idempotencyCache } from '../src/lib/idempotency';

describe('Idempotency Cache', () => {
  beforeEach(() => {
    // Clear cache before each test
    (idempotencyCache as any).cache.clear();
  });

  it('should store and retrieve values', () => {
    const requestId = 'test-123';
    const response = { formattedText: 'Hello world!', requestId };

    idempotencyCache.set(requestId, response);
    const retrieved = idempotencyCache.get(requestId);

    expect(retrieved).toEqual(response);
  });

  it('should return null for non-existent key', () => {
    const retrieved = idempotencyCache.get('non-existent');
    expect(retrieved).toBeNull();
  });

  it('should return null for expired key', () => {
    const requestId = 'test-123';
    const response = { formattedText: 'Hello world!', requestId };

    // Manually set an expired entry
    const cache = (idempotencyCache as any).cache;
    cache.set(requestId, {
      response,
      timestamp: Date.now() - 6 * 60 * 1000, // 6 minutes ago
      expiresAt: Date.now() - 1 * 60 * 1000  // expired 1 minute ago
    });

    const retrieved = idempotencyCache.get(requestId);
    expect(retrieved).toBeNull();
  });

  it('should return cache statistics', () => {
    const stats = idempotencyCache.getStats();
    
    expect(stats).toHaveProperty('size');
    expect(stats).toHaveProperty('ttlMs');
    expect(typeof stats.size).toBe('number');
    expect(typeof stats.ttlMs).toBe('number');
    expect(stats.ttlMs).toBe(5 * 60 * 1000); // 5 minutes
  });

  it('should handle multiple entries', () => {
    const request1 = { formattedText: 'First response', requestId: 'req-1' };
    const request2 = { formattedText: 'Second response', requestId: 'req-2' };

    idempotencyCache.set('req-1', request1);
    idempotencyCache.set('req-2', request2);

    expect(idempotencyCache.get('req-1')).toEqual(request1);
    expect(idempotencyCache.get('req-2')).toEqual(request2);
    expect(idempotencyCache.getStats().size).toBe(2);
  });

  it('should overwrite existing entries', () => {
    const requestId = 'test-123';
    const response1 = { formattedText: 'First response', requestId };
    const response2 = { formattedText: 'Second response', requestId };

    idempotencyCache.set(requestId, response1);
    idempotencyCache.set(requestId, response2);

    const retrieved = idempotencyCache.get(requestId);
    expect(retrieved).toEqual(response2);
    expect(idempotencyCache.getStats().size).toBe(1);
  });
});
