/**
 * Tests for custom prompt functionality
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

describe('Custom Prompts', () => {
  let app: express.Application;

  beforeAll(() => {
    // Set up test environment
    process.env.CLIENT_API_KEYS = 'test-key-123';
    process.env.LLM_PROVIDER = 'mock';
    process.env.NODE_ENV = 'test';
    process.env.MAX_PROMPT_TEMPLATE_CHARS = '4000';
    
    app = createServer();
  });

  afterAll(() => {
    jest.clearAllMocks();
  });

  describe('POST /api/format with custom prompts', () => {
    const validHeaders = {
      'x-client-key': 'test-key-123',
      'Content-Type': 'application/json'
    };

    it('should format transcript with custom prompt containing {{transcript}} placeholder', async () => {
      const requestData = {
        transcript: 'hello world this is a test',
        requestId: 'test-custom-prompt-1',
        modeTitle: 'Casual Summary',
        promptTemplate: 'Summarize the following in a casual tone:\n\n{{transcript}}'
      };

      const response = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send(requestData)
        .expect(200);

      expect(response.body).toHaveProperty('formattedText');
      expect(response.body).toHaveProperty('requestId', 'test-custom-prompt-1');
      expect(response.body).toHaveProperty('modeTitle', 'Casual Summary');
      expect(response.body).toHaveProperty('usage');
      expect(typeof response.body.formattedText).toBe('string');
      expect(response.body.formattedText.length).toBeGreaterThan(0);
    });

    it('should auto-append transcript placeholder when missing', async () => {
      const requestData = {
        transcript: 'hello world this is a test',
        requestId: 'test-auto-append-1',
        modeTitle: 'Translation Mode',
        promptTemplate: 'Translate the following to Spanish:'
      };

      const response = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send(requestData)
        .expect(200);

      expect(response.body).toHaveProperty('formattedText');
      expect(response.body).toHaveProperty('modeTitle', 'Translation Mode');
      expect(response.body.formattedText.length).toBeGreaterThan(0);
    });

    it('should reject prompt template that is too long', async () => {
      const longTemplate = 'a'.repeat(4001); // Exceeds MAX_PROMPT_TEMPLATE_CHARS
      const requestData = {
        transcript: 'hello world',
        requestId: 'test-long-prompt-1',
        promptTemplate: longTemplate
      };

      const response = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send(requestData)
        .expect(400);

      expect(response.body).toHaveProperty('error', 'Bad Request');
      expect(response.body.message).toContain('promptTemplate cannot exceed 4000 characters');
    });

    it('should work without custom prompt (backward compatibility)', async () => {
      const requestData = {
        transcript: 'hello world this is a test',
        requestId: 'test-default-prompt-1'
      };

      const response = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send(requestData)
        .expect(200);

      expect(response.body).toHaveProperty('formattedText');
      expect(response.body).toHaveProperty('requestId', 'test-default-prompt-1');
      expect(response.body).not.toHaveProperty('modeTitle');
      expect(response.body.formattedText.length).toBeGreaterThan(0);
    });

    it('should validate promptTemplate type', async () => {
      const requestData = {
        transcript: 'hello world',
        requestId: 'test-invalid-type-1',
        promptTemplate: 123 // Invalid type
      };

      const response = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send(requestData)
        .expect(400);

      expect(response.body).toHaveProperty('error', 'Bad Request');
      expect(response.body.message).toBe('promptTemplate must be a string if provided');
    });

    it('should validate modeTitle type', async () => {
      const requestData = {
        transcript: 'hello world',
        requestId: 'test-invalid-mode-title-1',
        modeTitle: 123 // Invalid type
      };

      const response = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send(requestData)
        .expect(400);

      expect(response.body).toHaveProperty('error', 'Bad Request');
      expect(response.body.message).toBe('modeTitle must be a string if provided');
    });

    it('should handle empty promptTemplate', async () => {
      const requestData = {
        transcript: 'hello world',
        requestId: 'test-empty-prompt-1',
        promptTemplate: ''
      };

      const response = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send(requestData)
        .expect(200);

      expect(response.body).toHaveProperty('formattedText');
      expect(response.body.formattedText.length).toBeGreaterThan(0);
    });

    it('should handle promptTemplate with multiple {{transcript}} placeholders', async () => {
      const requestData = {
        transcript: 'hello world',
        requestId: 'test-multiple-placeholders-1',
        modeTitle: 'Multiple Placeholders Test',
        promptTemplate: 'First: {{transcript}}\nSecond: {{transcript}}\nThird: {{transcript}}'
      };

      const response = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send(requestData)
        .expect(200);

      expect(response.body).toHaveProperty('formattedText');
      expect(response.body).toHaveProperty('modeTitle', 'Multiple Placeholders Test');
      expect(response.body.formattedText.length).toBeGreaterThan(0);
    });

    it('should handle special characters in transcript', async () => {
      const requestData = {
        transcript: 'Hello "world" with\nnewlines\tand\ttabs',
        requestId: 'test-special-chars-1',
        modeTitle: 'Special Chars Test',
        promptTemplate: 'Format this text: {{transcript}}'
      };

      const response = await request(app)
        .post('/api/format')
        .set(validHeaders)
        .send(requestData)
        .expect(200);

      expect(response.body).toHaveProperty('formattedText');
      expect(response.body).toHaveProperty('modeTitle', 'Special Chars Test');
      expect(response.body.formattedText.length).toBeGreaterThan(0);
    });
  });

  describe('POST /api/transcribe-chirp with custom prompts', () => {
    const validHeaders = {
      'x-client-key': 'test-key-123'
    };

    // Note: These tests would require mocking the Chirp adapter and file upload
    // For now, we'll test the validation logic that happens before Chirp processing

    it('should validate promptTemplate length in multipart form', async () => {
      const longTemplate = 'a'.repeat(4001);
      
      const response = await request(app)
        .post('/api/transcribe-chirp')
        .set(validHeaders)
        .field('promptTemplate', longTemplate)
        .field('requestId', 'test-chirp-long-prompt-1')
        .attach('file', Buffer.from('fake audio data'), 'test.wav')
        .expect(400);

      expect(response.body).toHaveProperty('error', 'Bad Request');
      expect(response.body.message).toContain('promptTemplate cannot exceed 4000 characters');
    });
  });

  describe('MockLLMAdapter custom prompt functionality', () => {
    const mockAdapter = new MockLLMAdapter();

    it('should replace {{transcript}} placeholder correctly', async () => {
      const transcript = 'hello world test';
      const promptTemplate = 'Format this: {{transcript}}';
      
      const result = await mockAdapter.formatTextWithCustomPrompt(transcript, promptTemplate);
      
      expect(result).toHaveProperty('text');
      expect(result).toHaveProperty('usage');
      expect(result.text.length).toBeGreaterThan(0);
      expect(result.usage?.promptTokens).toBeGreaterThan(0);
    });

    it('should handle multiple {{transcript}} placeholders', async () => {
      const transcript = 'test';
      const promptTemplate = 'First: {{transcript}}, Second: {{transcript}}';
      
      const result = await mockAdapter.formatTextWithCustomPrompt(transcript, promptTemplate);
      
      expect(result).toHaveProperty('text');
      expect(result.text.length).toBeGreaterThan(0);
    });

    it('should handle prompt without {{transcript}} placeholder', async () => {
      const transcript = 'hello world';
      const promptTemplate = 'Just format this text';
      
      const result = await mockAdapter.formatTextWithCustomPrompt(transcript, promptTemplate);
      
      expect(result).toHaveProperty('text');
      expect(result.text.length).toBeGreaterThan(0);
    });
  });
});
