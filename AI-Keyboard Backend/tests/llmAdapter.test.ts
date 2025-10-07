/**
 * Tests for LLM adapter functionality
 */

import { MockLLMAdapter, withRetry } from '../src/lib/llmAdapter';

describe('LLM Adapter', () => {
  describe('MockLLMAdapter', () => {
    let adapter: MockLLMAdapter;

    beforeEach(() => {
      adapter = new MockLLMAdapter();
    });

    it('should format text with basic punctuation', async () => {
      const transcript = 'hello world this is a test';
      const result = await adapter.formatText(transcript);

      expect(result).toHaveProperty('text');
      expect(result).toHaveProperty('usage');
      expect(typeof result.text).toBe('string');
      expect(result.text.length).toBeGreaterThanOrEqual(transcript.length);
      expect(result.usage).toHaveProperty('promptTokens');
      expect(result.usage).toHaveProperty('completionTokens');
      expect(result.usage).toHaveProperty('totalTokens');
    });

    it('should handle empty transcript', async () => {
      const transcript = '';
      const result = await adapter.formatText(transcript);

      expect(result.text).toBe('.');
      expect(result.usage).toBeDefined();
    });

    it('should handle transcript with existing punctuation', async () => {
      const transcript = 'Hello world! This is a test.';
      const result = await adapter.formatText(transcript);

      expect(result.text).toContain('Hello');
      expect(result.text).toContain('world');
      expect(result.text).toContain('test');
    });

    it('should handle long transcript', async () => {
      const transcript = 'a '.repeat(1000);
      const result = await adapter.formatText(transcript);

      expect(result.text).toBeDefined();
      expect(result.usage).toBeDefined();
    });
  });

  describe('withRetry', () => {
    it('should succeed on first attempt', async () => {
      const operation = jest.fn().mockResolvedValue('success');
      
      const result = await withRetry(operation);
      
      expect(result).toBe('success');
      expect(operation).toHaveBeenCalledTimes(1);
    });

    it('should retry on failure and eventually succeed', async () => {
      const operation = jest.fn()
        .mockRejectedValueOnce(new Error('First failure'))
        .mockRejectedValueOnce(new Error('Second failure'))
        .mockResolvedValue('success');
      
      const result = await withRetry(operation, 2, 10);
      
      expect(result).toBe('success');
      expect(operation).toHaveBeenCalledTimes(3);
    });

    it('should fail after max retries', async () => {
      const operation = jest.fn().mockRejectedValue(new Error('Persistent failure'));
      
      await expect(withRetry(operation, 2, 10)).rejects.toThrow('Persistent failure');
      expect(operation).toHaveBeenCalledTimes(3);
    });

    it('should use default retry parameters', async () => {
      const operation = jest.fn().mockRejectedValue(new Error('Failure'));
      
      await expect(withRetry(operation)).rejects.toThrow('Failure');
      expect(operation).toHaveBeenCalledTimes(3); // 1 initial + 2 retries
    });
  });
});
