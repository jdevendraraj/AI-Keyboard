# Testing Checklist for Chirp Integration

## Prerequisites Setup

### 1. Backend Setup
- [ ] Install dependencies: `npm install`
- [ ] Set up Google Cloud credentials:
  - [ ] Place `ai-keyboard-474217-dbd014a006c2.json` in backend root directory
  - [ ] Verify `GOOGLE_APPLICATION_CREDENTIALS=./ai-keyboard-474217-dbd014a006c2.json` in `.env`
- [ ] Set up OpenAI API key in `.env`
- [ ] Start backend: `npm run dev`

### 2. Frontend Setup (Android Emulator)
- [ ] Build and install FlorisBoard APK
- [ ] Enable microphone permission
- [ ] Configure backend settings:
  - [ ] Set Backend Base URL to `http://10.0.2.2:3000` (emulator) or `http://localhost:3000` (device)
  - [ ] Set Client API Key to `dev-key-123`
  - [ ] Select "Chirp (cloud STT)" as Voice to Text Source

## Test Cases

### 1. Device STT Mode (Existing)
- [ ] Open voice input panel
- [ ] Tap microphone button
- [ ] Speak: "Hello world this is a test"
- [ ] Tap microphone button to stop
- [ ] Verify: Text appears formatted as "Hello world, this is a test."

### 2. Chirp Cloud STT Mode (New)
- [ ] In settings, select "Chirp (cloud STT)" as voice source
- [ ] Open voice input panel
- [ ] Tap microphone button
- [ ] Verify: Shows "Recording for Chirp…" toast
- [ ] Speak: "Hello world this is a test"
- [ ] Tap microphone button to stop
- [ ] Verify: Shows "Transcribing…" toast
- [ ] Verify: Text appears formatted as "Hello world, this is a test."

### 3. Backend Health Check
- [ ] Test health endpoint: `curl http://localhost:3000/health`
- [ ] Expected: `{"status":"ok"}`

### 4. Chirp API Test
- [ ] Test Chirp endpoint with cURL:
  ```bash
  curl -X POST http://localhost:3000/api/transcribe-chirp \
    -H "x-client-key: dev-key-123" \
    -F "file=@test-audio.wav" \
    -F "requestId=test-123"
  ```
- [ ] Expected: JSON response with `formattedText`

### 5. Error Handling
- [ ] Test with invalid API key
- [ ] Test with no audio file
- [ ] Test with invalid audio format
- [ ] Test with backend offline (fallback behavior)

### 6. Language Selection
- [ ] Test with different language settings
- [ ] Verify language parameter is passed to Chirp

## Performance Tests

### 1. Response Times
- [ ] Device STT: Should be < 2 seconds
- [ ] Chirp STT: Should be < 10 seconds
- [ ] Timeout handling: 10 second limit

### 2. Audio Quality
- [ ] Test with clear speech
- [ ] Test with background noise
- [ ] Test with different accents
- [ ] Test with different languages

## Security Tests

### 1. Authentication
- [ ] Invalid API key returns 401
- [ ] Missing API key returns 401
- [ ] Valid API key works

### 2. File Upload
- [ ] Large files (>10MB) rejected
- [ ] Invalid file types rejected
- [ ] Audio files processed correctly

## Integration Tests

### 1. End-to-End Flow
- [ ] Voice input → Audio recording → Chirp transcription → Formatting → Text insertion
- [ ] Verify complete pipeline works
- [ ] Check logs for any errors

### 2. Fallback Behavior
- [ ] Backend offline → Device STT fallback
- [ ] Chirp fails → Error message shown
- [ ] Network timeout → Appropriate error handling

## Manual Testing Commands

### Test Audio File Creation
```bash
# Create a test WAV file (requires ffmpeg)
ffmpeg -f lavfi -i "sine=frequency=1000:duration=3" -ar 16000 -ac 1 test-audio.wav
```

### Test cURL Commands
```bash
# Health check
curl http://localhost:3000/health

# Format text
curl -X POST http://localhost:3000/api/format \
  -H "Content-Type: application/json" \
  -H "x-client-key: dev-key-123" \
  -d '{"transcript": "hello world test", "requestId": "test-123"}'

# Transcribe audio
curl -X POST http://localhost:3000/api/transcribe-chirp \
  -H "x-client-key: dev-key-123" \
  -F "file=@test-audio.wav" \
  -F "requestId=test-123"
```

## Expected Results

### Success Cases
- ✅ Voice input works in both modes
- ✅ Chirp provides higher accuracy than device STT
- ✅ Formatting works consistently
- ✅ Error handling is graceful
- ✅ Performance is acceptable

### Known Limitations
- ⚠️ Chirp requires internet connection
- ⚠️ Chirp has higher latency than device STT
- ⚠️ Audio files are temporarily stored on server
- ⚠️ Google Cloud Speech API has usage costs

## Troubleshooting

### Common Issues
1. **"Chirp auth failed"** → Check Google Cloud credentials
2. **"No audio recorded"** → Check microphone permissions
3. **"Transcription timeout"** → Check network connection
4. **"Invalid API key"** → Check backend configuration

### Debug Steps
1. Check backend logs for errors
2. Verify Google Cloud credentials are valid
3. Test with cURL commands
4. Check Android logs for voice input issues
5. Verify network connectivity
