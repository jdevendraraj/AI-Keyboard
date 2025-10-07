/**
 * Simple structured logging utility
 */

export interface LogEntry {
  timestamp: string;
  level: 'info' | 'warn' | 'error' | 'debug';
  message: string;
  requestId?: string;
  [key: string]: any;
}

class Logger {
  private isDevelopment = process.env.NODE_ENV === 'development';

  private formatLog(entry: LogEntry): string {
    if (this.isDevelopment) {
      return JSON.stringify(entry, null, 2);
    }
    return JSON.stringify(entry);
  }

  info(message: string, meta: Record<string, any> = {}): void {
    const entry: LogEntry = {
      timestamp: new Date().toISOString(),
      level: 'info',
      message,
      ...meta
    };
    console.log(this.formatLog(entry));
  }

  warn(message: string, meta: Record<string, any> = {}): void {
    const entry: LogEntry = {
      timestamp: new Date().toISOString(),
      level: 'warn',
      message,
      ...meta
    };
    console.warn(this.formatLog(entry));
  }

  error(message: string, meta: Record<string, any> = {}): void {
    const entry: LogEntry = {
      timestamp: new Date().toISOString(),
      level: 'error',
      message,
      ...meta
    };
    console.error(this.formatLog(entry));
  }

  debug(message: string, meta: Record<string, any> = {}): void {
    if (this.isDevelopment) {
      const entry: LogEntry = {
        timestamp: new Date().toISOString(),
        level: 'debug',
        message,
        ...meta
      };
      console.debug(this.formatLog(entry));
    }
  }

  // Helper to safely log transcript preview
  logTranscriptPreview(transcript: string, requestId?: string): void {
    const preview = transcript.length > 120 
      ? transcript.substring(0, 120) + '...' 
      : transcript;
    
    this.info('Transcript received', {
      requestId,
      transcriptLength: transcript.length,
      transcriptPreview: preview
    });
  }
}

export const logger = new Logger();
