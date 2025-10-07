import { SpeechClient } from '@google-cloud/speech';
import { logger } from './logger';

export interface ChirpTranscriptionResult {
  transcript: string;
  rawResponse: any;
}

export class ChirpAdapter {
  private speechClient: SpeechClient;

  constructor() {
    // Initialize Google Cloud Speech client
    // Credentials will be loaded from GOOGLE_APPLICATION_CREDENTIALS or GOOGLE_SERVICE_ACCOUNT_JSON
    this.speechClient = new SpeechClient();
  }

  async transcribeAudio(audioFile: string, languageCode?: string): Promise<ChirpTranscriptionResult> {
    try {
      logger.info('Starting Chirp transcription', { audioFile, languageCode });

      // Read audio file
      const fs = await import('fs/promises');
      const audioBytes = await fs.readFile(audioFile);

      // Configure the request
      const request: any = {
        audio: {
          content: audioBytes.toString('base64'),
        },
        config: {
          encoding: 'LINEAR16' as const,
          sampleRateHertz: 16000,
          model: 'default', // Use valid model name - 'chirp' doesn't exist in v1 API
          enableAutomaticPunctuation: true,
          maxAlternatives: 1,
          useEnhanced: true, // This is supported with 'default' model
        },
      };

      // Handle language detection
      if (languageCode && languageCode !== 'auto') {
        // Use specific language
        request.config.languageCode = languageCode;
      } else {
        // For auto-detection, use multiple language codes for better detection
        request.config.languageCode = 'en-US'; // Primary language
        request.config.alternativeLanguageCodes = [
          'es-ES', 'fr-FR', 'de-DE', 'it-IT', 'pt-BR', 'ru-RU', 'ja-JP', 'ko-KR',
          'zh-CN', 'zh-TW', 'ar-SA', 'hi-IN', 'tr-TR', 'pl-PL', 'nl-NL', 'sv-SE',
          'no-NO', 'da-DK', 'fi-FI', 'cs-CZ', 'hu-HU', 'ro-RO', 'bg-BG', 'hr-HR',
          'sk-SK', 'sl-SI', 'et-EE', 'lv-LV', 'lt-LT', 'uk-UA', 'el-GR', 'he-IL',
          'th-TH', 'vi-VN', 'id-ID', 'ms-MY', 'tl-PH', 'ca-ES', 'eu-ES', 'gl-ES'
        ];
      }

      logger.info('Sending request to Google Cloud Speech API', { 
        languageCode: request.config.languageCode,
        alternativeLanguageCodes: request.config.alternativeLanguageCodes?.length || 0,
        isAutoDetection: languageCode === 'auto' || !languageCode,
        model: request.config.model 
      });

      // Perform the transcription
      const [response] = await this.speechClient.recognize(request);
      
      if (!response.results || response.results.length === 0) {
        logger.warn('No transcription results received');
        return {
          transcript: '',
          rawResponse: response
        };
      }

      const transcript = response.results
        .map(result => result.alternatives?.[0]?.transcript || '')
        .join(' ')
        .trim();

      logger.info('Chirp transcription completed', { 
        transcriptLength: transcript.length,
        confidence: response.results[0]?.alternatives?.[0]?.confidence || 0
      });

      return {
        transcript,
        rawResponse: response
      };

    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      const errorStack = error instanceof Error ? error.stack : undefined;
      logger.error('Chirp transcription failed', { error: errorMessage, stack: errorStack });
      throw new Error(`Chirp transcription failed: ${errorMessage}`);
    }
  }

  async isAvailable(): Promise<boolean> {
    try {
      // Test if we can create a client (credentials are valid)
      await this.speechClient.getProjectId();
      return true;
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      logger.error('Chirp adapter not available', { error: errorMessage });
      return false;
    }
  }
}

// Export singleton instance
export const chirpAdapter = new ChirpAdapter();
