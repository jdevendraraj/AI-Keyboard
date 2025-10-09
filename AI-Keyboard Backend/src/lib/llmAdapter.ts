/**
 * LLM Adapter abstraction for different providers
 */

import OpenAI from 'openai';
import { logger } from './logger';

export interface LLMResponse {
  text: string;
  usage?: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
  };
}

export interface LLMAdapter {
  formatText(transcript: string): Promise<LLMResponse>;
  formatTextWithCustomPrompt(transcript: string, promptTemplate: string): Promise<LLMResponse>;
}

export class OpenAIAdapter implements LLMAdapter {
  private client: OpenAI;
  private model: string;
  private temperature: number;
  private maxTokens: number;

  constructor() {
    const apiKey = process.env.OPENAI_API_KEY;
    if (!apiKey) {
      throw new Error('OPENAI_API_KEY environment variable is required');
    }

    this.client = new OpenAI({ apiKey });
    this.model = process.env.LLM_MODEL || 'gpt-3.5-turbo';
    this.temperature = parseFloat(process.env.LLM_TEMPERATURE || '0.1');
    this.maxTokens = parseInt(process.env.LLM_MAX_TOKENS || '2000');

    logger.info('OpenAI adapter initialized', {
      model: this.model,
      temperature: this.temperature,
      maxTokens: this.maxTokens
    });
  }

  async formatText(transcript: string): Promise<LLMResponse> {
    const prompt = `You are a text formatter. Format the text below by adding punctuation, fixing casing, and keeping the same language. Do not translate or rephrase the content or add new ideas. If the text has obvious spelling or word-joining mistakes (e.g., 'threeapples' or 'appls'), correct them minimally to be human-readable, but DO NOT change the meaning. 

IMPORTANT: Return ONLY the formatted text. Do not add any prefixes, labels, or explanatory text like "Transcript:", "Formatted text:", or anything similar. Just return the clean formatted text.

Text to format: ${transcript}`;

    const startTime = Date.now();
    
    try {
      const response = await this.client.chat.completions.create({
        model: this.model,
        messages: [
          {
            role: 'user',
            content: prompt
          }
        ],
        temperature: this.temperature,
        max_tokens: this.maxTokens
      });

      const duration = Date.now() - startTime;
      const choice = response.choices[0];

      if (!choice?.message?.content) {
        throw new Error('No content in LLM response');
      }

      logger.info('LLM request completed', {
        duration,
        model: this.model,
        usage: response.usage
      });

      return {
        text: choice.message.content,
        usage: response.usage ? {
          promptTokens: response.usage.prompt_tokens,
          completionTokens: response.usage.completion_tokens,
          totalTokens: response.usage.total_tokens
        } : undefined
      };
    } catch (error) {
      const duration = Date.now() - startTime;
      logger.error('LLM request failed', {
        duration,
        model: this.model,
        error: error instanceof Error ? error.message : 'Unknown error'
      });
      throw error;
    }
  }

  async formatTextWithCustomPrompt(transcript: string, promptTemplate: string): Promise<LLMResponse> {
    // Replace {{transcript}} placeholder with actual transcript
    // Escape any special characters that might break JSON
    const escapedTranscript = transcript
      .replace(/\\/g, '\\\\')  // Escape backslashes
      .replace(/"/g, '\\"')    // Escape quotes
      .replace(/\n/g, '\\n')   // Escape newlines
      .replace(/\r/g, '\\r')   // Escape carriage returns
      .replace(/\t/g, '\\t');  // Escape tabs
    
    const prompt = promptTemplate.replace(/\{\{transcript\}\}/g, escapedTranscript);

    const startTime = Date.now();
    
    try {
      const response = await this.client.chat.completions.create({
        model: this.model,
        messages: [
          {
            role: 'user',
            content: prompt
          }
        ],
        temperature: this.temperature,
        max_tokens: this.maxTokens
      });

      const duration = Date.now() - startTime;
      const choice = response.choices[0];

      if (!choice?.message?.content) {
        throw new Error('No content in LLM response');
      }

      logger.info('LLM custom prompt request completed', {
        duration,
        model: this.model,
        usage: response.usage,
        promptLength: prompt.length
      });

      return {
        text: choice.message.content,
        usage: response.usage ? {
          promptTokens: response.usage.prompt_tokens,
          completionTokens: response.usage.completion_tokens,
          totalTokens: response.usage.total_tokens
        } : undefined
      };
    } catch (error) {
      const duration = Date.now() - startTime;
      logger.error('LLM custom prompt request failed', {
        duration,
        model: this.model,
        error: error instanceof Error ? error.message : 'Unknown error',
        promptLength: prompt.length
      });
      throw error;
    }
  }
}

/**
 * Mock adapter for testing
 */
export class MockLLMAdapter implements LLMAdapter {
  async formatText(transcript: string): Promise<LLMResponse> {
    // Simple mock that adds basic punctuation
    const formatted = transcript
      .replace(/\s+/g, ' ')
      .trim()
      .replace(/([.!?])\s*([a-z])/g, '$1 $2')
      .replace(/^([a-z])/, (match) => match.toUpperCase())
      .replace(/([.!?])\s*$/, '$1')
      .replace(/([.!?])\s*$/, '$1') || transcript + '.';

    return {
      text: formatted,
      usage: {
        promptTokens: transcript.length / 4,
        completionTokens: formatted.length / 4,
        totalTokens: (transcript.length + formatted.length) / 4
      }
    };
  }

  async formatTextWithCustomPrompt(transcript: string, promptTemplate: string): Promise<LLMResponse> {
    // Mock implementation that replaces {{transcript}} and adds basic formatting
    const processedPrompt = promptTemplate.replace(/\{\{transcript\}\}/g, transcript);
    
    // Simple mock that adds basic punctuation to the transcript
    const formatted = transcript
      .replace(/\s+/g, ' ')
      .trim()
      .replace(/([.!?])\s*([a-z])/g, '$1 $2')
      .replace(/^([a-z])/, (match) => match.toUpperCase())
      .replace(/([.!?])\s*$/, '$1')
      .replace(/([.!?])\s*$/, '$1') || transcript + '.';

    return {
      text: formatted,
      usage: {
        promptTokens: processedPrompt.length / 4,
        completionTokens: formatted.length / 4,
        totalTokens: (processedPrompt.length + formatted.length) / 4
      }
    };
  }
}

/**
 * Factory function to create LLM adapter based on environment
 */
export function createLLMAdapter(): LLMAdapter {
  const provider = process.env.LLM_PROVIDER || 'openai';
  
  switch (provider.toLowerCase()) {
    case 'openai':
      return new OpenAIAdapter();
    case 'mock':
      return new MockLLMAdapter();
    default:
      throw new Error(`Unsupported LLM provider: ${provider}`);
  }
}

/**
 * Retry wrapper for LLM requests with exponential backoff
 */
export async function withRetry<T>(
  operation: () => Promise<T>,
  maxRetries: number = 2,
  baseDelay: number = 1000
): Promise<T> {
  let lastError: Error;

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      return await operation();
    } catch (error) {
      lastError = error instanceof Error ? error : new Error('Unknown error');
      
      if (attempt === maxRetries) {
        logger.error('LLM request failed after all retries', {
          attempts: attempt + 1,
          error: lastError.message
        });
        throw lastError;
      }

      const delay = baseDelay * Math.pow(2, attempt);
      logger.warn('LLM request failed, retrying', {
        attempt: attempt + 1,
        maxRetries,
        delay,
        error: lastError.message
      });
      
      await new Promise(resolve => setTimeout(resolve, delay));
    }
  }

  throw lastError!;
}
