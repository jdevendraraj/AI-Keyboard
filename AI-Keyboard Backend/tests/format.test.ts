/**
 * Tests for the format endpoint
 */

import request from 'supertest';
import express from 'express';
import { createServer } from '../src/server';
import { MockLLMAdapter } from '../src/lib/llmAdapter';

// Mock the LLM adapter
jest.mock('../src/lib/llmAdapter', () => ({
  ...jest.requireActual('../src/lib/llmAdapter'),
  createLLMAdapter: () => new MockLLMAdapter()
}));

describe('Format Endpoint', () => {
  let app: express.Application;

  beforeAll(() => {
    // Set up test environment
    process.env.CLIENT_API_KEYS = 'test-key-123';
    process.env.LLM_PROVIDER = 'mock';
    process.env.NODE_ENV = 'test';
    
    app = createServer();
  });

  afterAll(() => {
    jest.clearAllMocks();
  });

  describe('POST /api/format', () => {
    const validRequest = {
      transcript: 'hello world this is a test',
      requestId: 'test-request-123'
    };

    const validHeaders = {
      'x-client-key': 'test-key-123',
      'Content-Type': 'application/json'
    };

    it('should format transcript successfully', async () => {
      const response = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send(validRequest)
        .expect(200);

      expect(response.body).toHaveProperty('formattedText');
      expect(response.body).toHaveProperty('requestId', 'test-request-123');
      expect(response.body).toHaveProperty('usage');
      expect(typeof response.body.formattedText).toBe('string');
      expect(response.body.formattedText.length).toBeGreaterThan(0);
    });

    it('should return 401 for missing API key', async () => {
      const response = await request(app)
        .post('/api/format')
        .set({ 'Content-Type': 'application/json' })
        .send(validRequest)
        .expect(401);

      expect(response.body).toHaveProperty('error', 'Unauthorized');
      expect(response.body).toHaveProperty('message', 'Missing x-client-key header');
    });

    it('should return 401 for invalid API key', async () => {
      const response = await request(app)
        .post('/api/format')
        .set({
          'x-client-key': 'invalid-key',
          'Content-Type': 'application/json'
        })
        .send(validRequest)
        .expect(401);

      expect(response.body).toHaveProperty('error', 'Unauthorized');
      expect(response.body).toHaveProperty('message', 'Invalid API key');
    });

    it('should return 400 for missing transcript', async () => {
      const response = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send({ requestId: 'test-123' })
        .expect(400);

      expect(response.body).toHaveProperty('error', 'Bad Request');
      expect(response.body).toHaveProperty('message', 'transcript field is required and must be a string');
    });

    it('should return 400 for empty transcript', async () => {
      const response = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send({ transcript: '', requestId: 'test-123' })
        .expect(400);

      expect(response.body).toHaveProperty('error', 'Bad Request');
      expect(response.body).toHaveProperty('message', 'transcript field is required and must be a string');
    });

    it('should return 400 for transcript too long', async () => {
      const longTranscript = 'a'.repeat(8001);
      const response = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send({ transcript: longTranscript, requestId: 'test-123' })
        .expect(400);

      expect(response.body).toHaveProperty('error', 'Bad Request');
      expect(response.body).toHaveProperty('message', 'transcript cannot exceed 8000 characters');
    });

    it('should return 400 for invalid requestId type', async () => {
      const response = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send({ transcript: 'test', requestId: 123 })
        .expect(400);

      expect(response.body).toHaveProperty('error', 'Bad Request');
      expect(response.body).toHaveProperty('message', 'requestId must be a string if provided');
    });

    it('should work without requestId', async () => {
      const response = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send({ transcript: 'hello world' })
        .expect(200);

      expect(response.body).toHaveProperty('formattedText');
      expect(response.body).not.toHaveProperty('requestId');
    });

    it('should return 500 for non-JSON body', async () => {
      const response = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send('invalid json')
        .expect(500);

      expect(response.body).toHaveProperty('error', 'Internal Server Error');
    });

    it('should handle idempotency correctly', async () => {
      const requestData = {
        transcript: 'this is a test for idempotency',
        requestId: 'idempotency-test-123'
      };

      // First request
      const response1 = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send(requestData)
        .expect(200);

      // Second request with same requestId
      const response2 = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send(requestData)
        .expect(200);

      // Should return the same response
      expect(response1.body.formattedText).toBe(response2.body.formattedText);
      expect(response1.body.requestId).toBe(response2.body.requestId);
    });
  });

  describe('GET /health', () => {
    it('should return health status', async () => {
      const response = await request(app)
        .get('/health')
        .expect(200);

      expect(response.body).toEqual({ status: 'ok' });
    });
  });

  describe('404 handling', () => {
    it('should return 404 for unknown endpoints', async () => {
      const response = await request(app)
        .get('/unknown-endpoint')
        .expect(404);

      expect(response.body).toHaveProperty('error', 'Not Found');
      expect(response.body).toHaveProperty('message', 'Endpoint not found');
    });
  });
});
