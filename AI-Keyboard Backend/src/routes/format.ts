/**
 * Format endpoint for transcript processing
 */

import { Router, Request, Response } from 'express';
import { createLLMAdapter, withRetry } from '../lib/llmAdapter';
import { idempotencyCache } from '../lib/idempotency';
import { logger } from '../lib/logger';
import { AuthenticatedRequest } from '../lib/auth';
import crypto from 'crypto';

const router = Router();

interface FormatRequest {
  transcript: string;
  requestId?: string;
  promptTemplate?: string;
  modeTitle?: string;
}

interface FormatResponse {
  formattedText: string;
  requestId?: string;
  modeTitle?: string;
  usage?: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
  };
}

/**
 * Validate format request body
 */
function validateFormatRequest(body: any): { isValid: boolean; error?: string } {
  if (!body || typeof body !== 'object') {
    return { isValid: false, error: 'Request body must be a JSON object' };
  }

  if (!body.transcript || typeof body.transcript !== 'string') {
    return { isValid: false, error: 'transcript field is required and must be a string' };
  }

  if (body.transcript.trim().length === 0) {
    return { isValid: false, error: 'transcript cannot be empty' };
  }

  if (body.transcript.length > 8000) {
    return { isValid: false, error: 'transcript cannot exceed 8000 characters' };
  }

  if (body.requestId && typeof body.requestId !== 'string') {
    return { isValid: false, error: 'requestId must be a string if provided' };
  }

  if (body.promptTemplate && typeof body.promptTemplate !== 'string') {
    return { isValid: false, error: 'promptTemplate must be a string if provided' };
  }

  if (body.promptTemplate && body.promptTemplate.length > parseInt(process.env.MAX_PROMPT_TEMPLATE_CHARS || '4000')) {
    return { isValid: false, error: `promptTemplate cannot exceed ${process.env.MAX_PROMPT_TEMPLATE_CHARS || '4000'} characters` };
  }

  if (body.modeTitle && typeof body.modeTitle !== 'string') {
    return { isValid: false, error: 'modeTitle must be a string if provided' };
  }

  return { isValid: true };
}

/**
 * POST /format - Format transcript using LLM
 */
router.post('/format', async (req: AuthenticatedRequest, res: Response) => {
  const startTime = Date.now();
  const requestId = req.body.requestId;

  try {
    // Validate request
    const validation = validateFormatRequest(req.body);
    if (!validation.isValid) {
      logger.warn('Invalid format request', {
        requestId,
        error: validation.error,
        ip: req.ip
      });
      return res.status(400).json({
        error: 'Bad Request',
        message: validation.error
      });
    }

    const { transcript, promptTemplate, modeTitle } = req.body as FormatRequest;

    // Log transcript preview (safely)
    logger.logTranscriptPreview(transcript, requestId);

    // Log custom prompt info (safely)
    if (promptTemplate) {
      const promptPreview = promptTemplate.substring(0, 200);
      const promptHash = crypto.createHash('sha256').update(promptTemplate).digest('hex').substring(0, 8);
      logger.info('Custom prompt template provided', {
        requestId,
        modeTitle,
        promptPreview,
        promptHash,
        promptLength: promptTemplate.length
      });
    }

    // Check idempotency cache
    if (requestId) {
      const cachedResponse = idempotencyCache.get(requestId);
      if (cachedResponse) {
        const duration = Date.now() - startTime;
        logger.info('Format request completed (cached)', {
          requestId,
          duration,
          transcriptLength: transcript.length,
          modeTitle
        });
        return res.json(cachedResponse);
      }
    }

    // Create LLM adapter
    const llmAdapter = createLLMAdapter();

    // Format text with retry logic
    let llmResponse;
    if (promptTemplate) {
      // Process custom prompt template
      let processedTemplate = promptTemplate;
      
      // Auto-append transcript placeholder if missing
      if (!processedTemplate.includes('{{transcript}}')) {
        processedTemplate = `${processedTemplate}\n\nTranscript: {{transcript}}`;
        logger.info('Auto-appended transcript placeholder to custom prompt', {
          requestId,
          modeTitle
        });
      }
      
      llmResponse = await withRetry(() => llmAdapter.formatTextWithCustomPrompt(transcript, processedTemplate));
    } else {
      // Use default formatting
      llmResponse = await withRetry(() => llmAdapter.formatText(transcript));
    }

    const response: FormatResponse = {
      formattedText: llmResponse.text,
      requestId,
      modeTitle,
      usage: llmResponse.usage
    };

    // Cache response if requestId provided
    if (requestId) {
      idempotencyCache.set(requestId, response);
    }

    const duration = Date.now() - startTime;
    logger.info('Format request completed', {
      requestId,
      duration,
      transcriptLength: transcript.length,
      formattedLength: llmResponse.text.length,
      modeTitle,
      usage: llmResponse.usage
    });

    return res.json(response);

  } catch (error) {
    const duration = Date.now() - startTime;
    const errorMessage = error instanceof Error ? error.message : 'Unknown error';
    
    logger.error('Format request failed', {
      requestId,
      duration,
      error: errorMessage,
      ip: req.ip
    });

    // Handle specific error types
    if (errorMessage.includes('API key')) {
      return res.status(500).json({
        error: 'Configuration Error',
        message: 'LLM service configuration error'
      });
    }

    if (errorMessage.includes('rate limit') || errorMessage.includes('quota')) {
      return res.status(429).json({
        error: 'Rate Limited',
        message: 'LLM service rate limit exceeded'
      });
    }

    return res.status(500).json({
      error: 'Internal Server Error',
      message: 'Failed to format transcript'
    });
  }
});

export default router;
