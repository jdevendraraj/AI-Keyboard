import request from 'supertest';
import { createServer } from '../src/server';
import { chirpAdapter } from '../src/lib/chirpAdapter';
import path from 'path';
import fs from 'fs/promises';

// Mock the chirpAdapter
jest.mock('../src/lib/chirpAdapter', () => ({
  chirpAdapter: {
    isAvailable: jest.fn(),
    transcribeAudio: jest.fn(),
  },
}));

const mockChirpAdapter = chirpAdapter as jest.Mocked<typeof chirpAdapter>;

describe('POST /api/transcribe-chirp', () => {
  let app: any;
  let testAudioFile: string;

  beforeAll(async () => {
    // Set up test environment variables
    process.env.CLIENT_API_KEYS = 'test-key,dev-key-123';
    process.env.OPENAI_API_KEY = 'test-openai-key';
    process.env.LLM_PROVIDER = 'mock';
    
    app = createServer();
    
    // Create a test audio file (empty WAV file)
    testAudioFile = path.join(__dirname, 'test-audio.wav');
    const wavHeader = Buffer.from([
      0x52, 0x49, 0x46, 0x46, // "RIFF"
      0x24, 0x00, 0x00, 0x00, // File size
      0x57, 0x41, 0x56, 0x45, // "WAVE"
      0x66, 0x6D, 0x74, 0x20, // "fmt "
      0x10, 0x00, 0x00, 0x00, // fmt chunk size
      0x01, 0x00, // Audio format (PCM)
      0x01, 0x00, // Number of channels
      0x44, 0xAC, 0x00, 0x00, // Sample rate (44100)
      0x88, 0x58, 0x01, 0x00, // Byte rate
      0x02, 0x00, // Block align
      0x10, 0x00, // Bits per sample
      0x64, 0x61, 0x74, 0x61, // "data"
      0x00, 0x00, 0x00, 0x00, // Data size
    ]);
    await fs.writeFile(testAudioFile, wavHeader);
  });

  afterAll(async () => {
    // Clean up test file
    try {
      await fs.unlink(testAudioFile);
    } catch (error) {
      // File might not exist
    }
    
    // Reset environment variables
    delete process.env.CLIENT_API_KEYS;
    delete process.env.OPENAI_API_KEY;
    delete process.env.LLM_PROVIDER;
  });

  beforeEach(() => {
    jest.clearAllMocks();
    // Mock successful Chirp availability
    mockChirpAdapter.isAvailable.mockResolvedValue(true);
  });

  it('should transcribe audio successfully', async () => {
    const mockTranscription = {
      transcript: 'Hello world, this is a test.',
      rawResponse: { results: [{ alternatives: [{ transcript: 'Hello world, this is a test.' }] }] }
    };

    mockChirpAdapter.transcribeAudio.mockResolvedValue(mockTranscription);

    const response = await request(app)
      .post('/api/transcribe-chirp')
      .set('x-client-key', 'test-key')
      .attach('file', testAudioFile)
      .field('requestId', 'test-request-123');

    expect(response.status).toBe(200);
    expect(response.body).toHaveProperty('formattedText');
    expect(response.body).toHaveProperty('requestId', 'test-request-123');
    expect(response.body).toHaveProperty('rawTranscription', 'Hello world, this is a test.');
    expect(mockChirpAdapter.transcribeAudio).toHaveBeenCalledWith(
      expect.any(String), // Accept any file path since multer creates temp files
      'auto'
    );
  });

  it('should reject request without API key', async () => {
    const response = await request(app)
      .post('/api/transcribe-chirp')
      .attach('file', testAudioFile);

    expect(response.status).toBe(401);
    expect(response.body).toHaveProperty('error', 'Invalid API key');
  });

  it('should reject request without audio file', async () => {
    const response = await request(app)
      .post('/api/transcribe-chirp')
      .set('x-client-key', 'test-key');

    expect(response.status).toBe(400);
    expect(response.body).toHaveProperty('error', 'No audio file provided');
  });

  it('should handle Chirp unavailability', async () => {
    mockChirpAdapter.isAvailable.mockResolvedValue(false);

    const response = await request(app)
      .post('/api/transcribe-chirp')
      .set('x-client-key', 'test-key')
      .attach('file', testAudioFile);

    expect(response.status).toBe(502);
    expect(response.body).toHaveProperty('error', 'Transcription service unavailable');
  });

  it('should handle empty transcription result', async () => {
    mockChirpAdapter.transcribeAudio.mockResolvedValue({
      transcript: '',
      rawResponse: { results: [] }
    });

    const response = await request(app)
      .post('/api/transcribe-chirp')
      .set('x-client-key', 'test-key')
      .attach('file', testAudioFile);

    expect(response.status).toBe(400);
    expect(response.body).toHaveProperty('error', 'No speech detected in audio');
  });

  it('should handle transcription errors', async () => {
    mockChirpAdapter.transcribeAudio.mockRejectedValue(new Error('Transcription failed'));

    const response = await request(app)
      .post('/api/transcribe-chirp')
      .set('x-client-key', 'test-key')
      .attach('file', testAudioFile);

    expect(response.status).toBe(500);
    expect(response.body).toHaveProperty('error', 'Transcription failed');
  });

  it('should accept language parameter', async () => {
    const mockTranscription = {
      transcript: 'Hola mundo',
      rawResponse: { results: [{ alternatives: [{ transcript: 'Hola mundo' }] }] }
    };

    mockChirpAdapter.transcribeAudio.mockResolvedValue(mockTranscription);

    const response = await request(app)
      .post('/api/transcribe-chirp?language=es-ES')
      .set('x-client-key', 'test-key')
      .attach('file', testAudioFile);

    expect(response.status).toBe(200);
    expect(mockChirpAdapter.transcribeAudio).toHaveBeenCalledWith(
      expect.any(String), // Accept any file path since multer creates temp files
      'es-ES'
    );
  });
});
