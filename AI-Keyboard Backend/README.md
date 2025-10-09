# Transcript Formatter Backend

A minimal but robust Node.js backend service that receives transcribed text from a mobile keyboard client and returns a GPT-formatted version of that text.

## Features

- **Text Formatting**: Uses OpenAI GPT to format transcribed text with proper punctuation and casing
- **Voice Transcription**: Google Chirp cloud transcription for high-accuracy speech-to-text
- **Dual Mode Support**: Choose between device STT or cloud Chirp transcription
- **Audio Processing**: Handles WAV, OGG, M4A, and other common audio formats
- **Authentication**: API key-based authentication for secure access
- **Rate Limiting**: Configurable rate limiting to prevent abuse
- **Idempotency**: Simple in-memory cache for request deduplication
- **Health Checks**: Built-in health monitoring endpoint
- **Docker Support**: Ready-to-run Docker container
- **Comprehensive Testing**: Full test suite with Jest

## Quick Start

### Prerequisites

- Node.js 18+ (LTS recommended)
- npm or yarn
- OpenAI API key
- Google Cloud Speech-to-Text API credentials (for Chirp transcription)

### Local Development

1. **Clone and install dependencies:**
   ```bash
   git clone <repository-url>
   cd transcript-formatter
   npm install
   ```

2. **Set up Google Cloud credentials:**
   ```bash
   # Option 1: Set environment variable pointing to service account JSON
   export GOOGLE_APPLICATION_CREDENTIALS="/path/to/ai-keyboard-474217-dbd014a006c2.json"
   
   # Option 2: Set JSON content directly (for containers)
   export GOOGLE_SERVICE_ACCOUNT_JSON='{"type":"service_account",...}'
   ```

3. **Set up environment variables:**
   ```bash
   cp .env.example .env
   # Edit .env with your actual values
   ```

4. **Start development server:**
   ```bash
   npm run dev
   ```

The server will start on `http://localhost:3000`

### Docker

1. **Build the image:**
   ```bash
   docker build -t transcript-formatter .
   ```

2. **Run the container:**
   ```bash
   docker run -p 3000:3000 --env-file .env transcript-formatter
   ```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `3000` | Server port |
| `OPENAI_API_KEY` | - | OpenAI API key (required) |
| `CLIENT_API_KEYS` | - | Comma-separated list of valid API keys (required) |
| `LLM_PROVIDER` | `openai` | LLM provider (`openai` or `mock`) |
| `LLM_MODEL` | `gpt-3.5-turbo` | OpenAI model to use |
| `LLM_TEMPERATURE` | `0.1` | LLM temperature (0-1) |
| `LLM_MAX_TOKENS` | `2000` | Maximum tokens for LLM response |
| `MAX_PROMPT_TEMPLATE_CHARS` | `4000` | Maximum characters for custom prompt templates |
| `CORS_ALLOWED_ORIGINS` | `*` | Comma-separated list of allowed CORS origins |
| `RATE_LIMIT_WINDOW_MS` | `60000` | Rate limit window in milliseconds |
| `RATE_LIMIT_MAX` | `60` | Maximum requests per window |
| `NODE_ENV` | `development` | Environment mode |

## API Endpoints

### POST /api/format

Format transcribed text using GPT.

**Headers:**
- `Content-Type: application/json`
- `x-client-key: <your-api-key>`

**Request Body:**
```json
{
  "transcript": "hello world this is a test",
  "requestId": "optional-unique-id",
  "promptTemplate": "optional-custom-prompt-template",
  "modeTitle": "optional-mode-title"
}
```

**Response:**
```json
{
  "formattedText": "Hello world, this is a test.",
  "requestId": "optional-unique-id",
  "modeTitle": "optional-mode-title",
  "usage": {
    "promptTokens": 25,
    "completionTokens": 8,
    "totalTokens": 33
  }
}
```

**Validation:**
- `transcript`: Required string, max 8000 characters
- `requestId`: Optional string for idempotency
- `promptTemplate`: Optional string, max 4000 characters (configurable via `MAX_PROMPT_TEMPLATE_CHARS`)
- `modeTitle`: Optional string for identifying the prompt mode

**Custom Prompt Templates:**
- If `promptTemplate` is provided, it will be used instead of the default formatting prompt
- The template should contain `{{transcript}}` where the transcribed text should be inserted
- If `{{transcript}}` is missing, it will be auto-appended as `\n\nTranscript: {{transcript}}`
- Custom prompts are forwarded to the LLM provider and not stored by the server

### POST /api/transcribe-chirp

Transcribe audio using Google Chirp and format the result.

**Headers:**
- `x-client-key: <your-api-key>`
- `Accept: application/json`

**Request Body (multipart/form-data):**
- `file`: Audio file (WAV, OGG, M4A, etc.)
- `requestId`: Optional unique identifier
- `promptTemplate`: Optional custom prompt template (max 4000 chars)
- `modeTitle`: Optional mode title for identification
- `enableFormatting`: Optional boolean (default: true)

**Query Parameters:**
- `language`: Optional language code (e.g., `en-US`, `es-ES`, `auto`)

**Response:**
```json
{
  "formattedText": "Hello world, this is a test.",
  "requestId": "optional-unique-id",
  "modeTitle": "optional-mode-title",
  "rawTranscription": "hello world this is a test",
  "usage": {
    "promptTokens": 25,
    "completionTokens": 8,
    "totalTokens": 33
  },
  "processingTime": 1234
}
```

**Validation:**
- `file`: Required audio file, max 10MB
- `requestId`: Optional string for idempotency
- `language`: Optional language code for transcription
- `promptTemplate`: Optional string, max 4000 characters
- `modeTitle`: Optional string for identifying the prompt mode

### GET /health

Health check endpoint.

**Response:**
```json
{
  "status": "ok"
}
```

## Client Examples

### cURL

**Format text:**
```bash
curl -X POST http://localhost:3000/api/format \
  -H "Content-Type: application/json" \
  -H "x-client-key: your-api-key" \
  -d '{
    "transcript": "hello world this is a test",
    "requestId": "unique-request-id"
  }'
```

**Format text with custom prompt:**
```bash
curl -X POST http://localhost:3000/api/format \
  -H "Content-Type: application/json" \
  -H "x-client-key: your-api-key" \
  -d '{
    "transcript": "hello world this is a test",
    "requestId": "unique-request-id",
    "modeTitle": "Casual Summary",
    "promptTemplate": "Summarize the following in a casual tone:\n\n{{transcript}}"
  }'
```

**Transcribe and format audio:**
```bash
curl -X POST http://localhost:3000/api/transcribe-chirp \
  -H "x-client-key: your-api-key" \
  -F "file=@/path/to/recorded.wav" \
  -F "requestId=req-123" \
  -G -d "language=en-US"
```

**Transcribe and format audio with custom prompt:**
```bash
curl -X POST http://localhost:3000/api/transcribe-chirp \
  -H "x-client-key: your-api-key" \
  -F "file=@/path/to/recorded.wav" \
  -F "requestId=req-123" \
  -F "modeTitle=Technical Notes" \
  -F "promptTemplate=Format the following as technical notes with clear structure:\n\n{{transcript}}" \
  -G -d "language=en-US"
```

### Android (Kotlin)

```kotlin
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class TranscriptFormatterClient {
    private val client = OkHttpClient()
    private val baseUrl = "http://your-server:3000"
    private val apiKey = "your-api-key"
    
    suspend fun formatTranscript(transcript: String, requestId: String? = null): String {
        val json = JSONObject().apply {
            put("transcript", transcript)
            requestId?.let { put("requestId", it) }
        }
        
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("$baseUrl/api/format")
            .post(requestBody)
            .addHeader("x-client-key", apiKey)
            .build()
            
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Request failed: ${response.code}")
            }
            
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            val responseJson = JSONObject(responseBody)
            return responseJson.getString("formattedText")
        }
    }
}
```

### JavaScript/TypeScript

```typescript
interface FormatRequest {
  transcript: string;
  requestId?: string;
}

interface FormatResponse {
  formattedText: string;
  requestId?: string;
  usage?: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
  };
}

async function formatTranscript(
  transcript: string, 
  requestId?: string
): Promise<FormatResponse> {
  const response = await fetch('http://localhost:3000/api/format', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-client-key': 'your-api-key'
    },
    body: JSON.stringify({ transcript, requestId })
  });

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }

  return response.json();
}
```

## Development

### Scripts

- `npm run dev` - Start development server with hot reload
- `npm run build` - Build TypeScript to JavaScript
- `npm start` - Start production server
- `npm test` - Run test suite
- `npm run test:watch` - Run tests in watch mode
- `npm run lint` - Run ESLint
- `npm run lint:fix` - Fix ESLint issues

### Project Structure

```
src/
├── index.ts              # Application entry point
├── server.ts             # Express server setup
├── routes/
│   └── format.ts         # Format endpoint
└── lib/
    ├── auth.ts           # Authentication middleware
    ├── idempotency.ts    # Idempotency cache
    ├── llmAdapter.ts     # LLM provider abstraction
    └── logger.ts         # Structured logging

tests/
├── format.test.ts        # Format endpoint tests
├── llmAdapter.test.ts    # LLM adapter tests
└── idempotency.test.ts   # Idempotency tests
```

## Testing

Run the test suite:

```bash
npm test
```

The tests include:
- Happy path formatting
- Input validation
- Authentication
- Idempotency
- Error handling
- LLM adapter functionality

## Security & Privacy

### Custom Prompt Templates
- **Client-side storage**: Custom prompt templates are stored only on the client device
- **No server persistence**: The server does not store or persist custom prompt templates
- **LLM forwarding**: Custom prompts are forwarded to the configured LLM provider (OpenAI, etc.)
- **Third-party processing**: Custom prompts are subject to the LLM provider's data processing policies
- **Logging**: Only truncated previews (first 200 chars) and hashes of prompts are logged for debugging
- **Recommendation**: Do not include secrets, personal information, or sensitive data in custom prompts

### Environment Variables
- Set `MAX_PROMPT_TEMPLATE_CHARS` to limit prompt template length (default: 4000)
- Configure `CLIENT_API_KEYS` with strong, unique keys for each client
- Use HTTPS in production to encrypt all communications

## Production Considerations

This is a POC implementation. For production deployment, consider:

### Security
- **Secrets Management**: Use AWS Secrets Manager, Azure Key Vault, or similar
- **API Key Rotation**: Implement key rotation and revocation
- **Device Attestation**: Add Play Integrity or similar device verification
- **HTTPS**: Always use HTTPS in production
- **Input Sanitization**: Additional validation for malicious inputs

### Scalability
- **Persistent Idempotency**: Replace in-memory cache with Redis or database
- **Rate Limiting**: Implement per-API-key rate limiting
- **Load Balancing**: Use multiple instances behind a load balancer
- **Monitoring**: Add APM tools (DataDog, New Relic, etc.)

### Reliability
- **Database**: Add persistent storage for audit logs
- **Retry Logic**: Implement circuit breakers and exponential backoff
- **Health Checks**: Add dependency health checks (OpenAI API status)
- **Graceful Shutdown**: Handle SIGTERM properly

### Observability
- **Structured Logging**: Use centralized logging (ELK stack, Splunk)
- **Metrics**: Add Prometheus metrics for monitoring
- **Tracing**: Implement distributed tracing
- **Alerting**: Set up alerts for errors and performance issues

## License

MIT License - see LICENSE file for details.
