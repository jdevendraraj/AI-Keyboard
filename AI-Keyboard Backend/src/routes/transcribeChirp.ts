import { Request, Response } from 'express';
import multer from 'multer';
import { v4 as uuidv4 } from 'uuid';
import path from 'path';
import fs from 'fs/promises';
import { chirpAdapter } from '../lib/chirpAdapter';
import { createLLMAdapter } from '../lib/llmAdapter';
import { logger } from '../lib/logger';
import { authenticateClient } from '../lib/auth';

// Configure multer for file uploads
const upload = multer({
  dest: '/tmp/audio-uploads/', // Use system temp directory
  limits: {
    fileSize: 10 * 1024 * 1024, // 10MB limit
  },
  fileFilter: (req, file, cb) => {
    // Accept common audio formats
    const allowedMimes = [
      'audio/wav',
      'audio/wave',
      'audio/x-wav',
      'audio/ogg',
      'audio/opus',
      'audio/m4a',
      'audio/mp4',
      'audio/mpeg'
    ];
    
    if (allowedMimes.includes(file.mimetype)) {
      cb(null, true);
    } else {
      cb(new Error(`Unsupported audio format: ${file.mimetype}`));
    }
  }
});

export const transcribeChirpHandler = async (req: Request, res: Response) => {
  const startTime = Date.now();
  let tempAudioFile: string | null = null;
  
  // Get request ID early so it's available for all error responses
  const requestId = req.body.requestId || uuidv4();
  
  // Get formatting preference from request body (default to true for backward compatibility)
  // Convert string to boolean since multer sends form data as strings
  const enableFormatting = req.body.enableFormatting !== 'false';
  
  logger.info('Chirp transcription request received', {
    requestId,
    enableFormatting,
    rawEnableFormatting: req.body.enableFormatting
  });
  
  try {
    // Validate file upload first
    if (!req.file) {
      return res.status(400).json({
        error: 'No audio file provided',
        code: 'MISSING_AUDIO_FILE',
        requestId
      });
    }

    // Check if Chirp is available
    const isChirpAvailable = await chirpAdapter.isAvailable();
    if (!isChirpAvailable) {
      logger.error('Chirp adapter not available');
      return res.status(502).json({
        error: 'Transcription service unavailable',
        code: 'CHIRP_UNAVAILABLE',
        requestId
      });
    }

    tempAudioFile = req.file.path;
    logger.info('Processing audio file for Chirp transcription', {
      requestId,
      filename: req.file.originalname,
      size: req.file.size,
      mimetype: req.file.mimetype
    });

    // Get language from query parameter or use auto
    const languageCode = req.query.language as string || 'auto';
    
    // Transcribe with Chirp
    const transcriptionResult = await chirpAdapter.transcribeAudio(tempAudioFile, languageCode);
    
    if (!transcriptionResult.transcript) {
      logger.warn('Empty transcription result', { requestId });
      return res.status(400).json({
        error: 'No speech detected in audio',
        code: 'NO_SPEECH_DETECTED',
        requestId
      });
    }

    logger.info('Chirp transcription completed', {
      requestId,
      transcriptLength: transcriptionResult.transcript.length,
      processingTime: Date.now() - startTime,
      enableFormatting
    });

    let formattedText: string;
    let usage: any = undefined;

    if (enableFormatting) {
      // Format the transcript using existing LLM adapter
      const llmAdapter = createLLMAdapter();
      const formattingResult = await llmAdapter.formatText(transcriptionResult.transcript);
      formattedText = formattingResult.text;
      usage = formattingResult.usage;
    } else {
      // Use raw transcription without formatting
      formattedText = transcriptionResult.transcript;
    }
    
    const response = {
      formattedText,
      requestId,
      rawTranscription: transcriptionResult.transcript,
      usage,
      processingTime: Date.now() - startTime
    };

    logger.info('Chirp transcription completed', {
      requestId,
      totalProcessingTime: response.processingTime,
      formattedTextLength: response.formattedText.length,
      formattingApplied: enableFormatting
    });

    return res.json(response);

  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    const errorStack = error instanceof Error ? error.stack : undefined;
    
    logger.error('Chirp transcription failed', {
      error: errorMessage,
      stack: errorStack,
      requestId
    });

    // Return appropriate error based on error type
    if (errorMessage.includes('auth') || errorMessage.includes('permission')) {
      return res.status(502).json({
        error: 'Transcription auth error',
        code: 'CHIRP_AUTH_ERROR',
        requestId
      });
    }

    if (errorMessage.includes('timeout')) {
      return res.status(504).json({
        error: 'Transcription timeout',
        code: 'CHIRP_TIMEOUT',
        requestId
      });
    }

    return res.status(500).json({
      error: 'Transcription failed',
      code: 'CHIRP_ERROR',
      requestId
    });

  } finally {
    // Clean up temporary audio file
    if (tempAudioFile) {
      try {
        await fs.unlink(tempAudioFile);
        logger.debug('Cleaned up temporary audio file', { tempAudioFile });
      } catch (cleanupError) {
        const cleanupErrorMessage = cleanupError instanceof Error ? cleanupError.message : String(cleanupError);
        logger.warn('Failed to clean up temporary audio file', { 
          tempAudioFile, 
          error: cleanupErrorMessage 
        });
      }
    }
  }
};

// Export multer middleware for use in route setup
export { upload };
