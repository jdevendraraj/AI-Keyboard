# Voice Formatting POC

This adds an in-keyboard voice input using `SpeechRecognizer` and a small network client to send transcripts to a formatting backend.

Setup:
- Open Floris Settings → Formatting Backend.
- Set Backend Base URL (e.g., `http://10.0.2.2:3000`).
- Set Client API Key.
- Enable "Formatting for Voice Typing" and tap "Test Settings".

Run with local backend (emulator):
```bash
adb reverse tcp:3000 tcp:3000
```

Expected request:
```http
POST /api/format
Content-Type: application/json
x-client-key: <your key>

{ "transcript": "hello world this is a test", "requestId": "req-1" }
```

Expected response:
```json
{ "formattedText": "Hello world, this is a test.", "requestId": "req-1" }
```

Notes:
- On network failure or 6s timeout, raw transcript is inserted and a short toast is shown.
- For auth error (401), a toast indicates invalid key.

