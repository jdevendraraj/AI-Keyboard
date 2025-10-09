# QA Checklist for Custom Prompt Modes Feature

## Backend Testing

### 1. Custom Prompt with Placeholder
- [ ] Create a mode with `{{transcript}}` placeholder
- [ ] Select the mode in the app
- [ ] Transcribe something using voice input
- [ ] Verify backend receives `promptTemplate` in request
- [ ] Verify formatted text returns correctly
- [ ] Check logs show custom prompt preview (first 200 chars) and hash

### 2. Custom Prompt without Placeholder (Auto-append)
- [ ] Create a mode without `{{transcript}}` placeholder
- [ ] Select the mode in the app
- [ ] Transcribe something using voice input
- [ ] Verify backend auto-appends `\n\nTranscript: {{transcript}}`
- [ ] Verify formatted text returns correctly
- [ ] Check logs show auto-append message

### 3. Long Prompt Rejection
- [ ] Create a mode with template > 4000 characters
- [ ] Try to transcribe with this mode
- [ ] Verify client-side validation rejects it
- [ ] Verify backend also rejects it with 400 error
- [ ] Check error message mentions character limit

### 4. Backward Compatibility
- [ ] Use app without selecting any custom mode
- [ ] Transcribe something
- [ ] Verify backend uses default prompt
- [ ] Verify no `promptTemplate` field in request
- [ ] Verify formatted text returns correctly

### 5. Server-side Security
- [ ] Check server logs for custom prompts
- [ ] Verify only first 200 chars + hash are logged
- [ ] Verify no full templates in persistent storage
- [ ] Verify security warning appears in startup logs

## Frontend Testing

### 6. Mode Management
- [ ] Create multiple custom modes
- [ ] Edit one mode (change title and template)
- [ ] Delete another mode
- [ ] Verify selection persists across app restarts
- [ ] Verify built-in modes cannot be edited/deleted

### 7. Mode Editor Validation
- [ ] Try to save mode with empty title (should fail)
- [ ] Try to save mode with empty template (should fail)
- [ ] Try to save mode with template > 4000 chars (should fail)
- [ ] Test preview button with sample transcript
- [ ] Verify privacy notice appears on first create/edit

### 8. Mode Selection
- [ ] Select different modes
- [ ] Verify selected mode shows in FormattingBackendScreen
- [ ] Verify "Default (backend prompt)" shows when no mode selected
- [ ] Test navigation between screens

### 9. Template Processing
- [ ] Create mode with `{{transcript}}` placeholder
- [ ] Test preview shows correct substitution
- [ ] Create mode without placeholder
- [ ] Test preview shows auto-appended placeholder
- [ ] Test multiple `{{transcript}}` placeholders

## End-to-End Testing

### 10. Full Flow Test
- [ ] Set up backend with valid API key
- [ ] Create custom mode: "Translate to Spanish: {{transcript}}"
- [ ] Select the mode
- [ ] Use voice input to say "Hello world"
- [ ] Verify backend receives custom prompt
- [ ] Verify response is formatted according to custom prompt
- [ ] Check logs show mode title

### 11. Chirp Integration
- [ ] Test custom prompts with Chirp transcription
- [ ] Verify both `/api/format` and `/api/transcribe-chirp` work
- [ ] Test with different voice sources (device vs Chirp)
- [ ] Verify mode title appears in response

### 12. Error Handling
- [ ] Test with invalid API key
- [ ] Test with network timeout
- [ ] Test with malformed prompt template
- [ ] Verify graceful fallback to raw transcript
- [ ] Check user-friendly error messages

## Security & Privacy Verification

### 13. Data Storage
- [ ] Verify modes stored in EncryptedSharedPreferences
- [ ] Verify no modes transmitted except during transcription
- [ ] Verify backend doesn't persist prompt templates
- [ ] Check that templates are only forwarded to LLM

### 14. Privacy Notices
- [ ] Verify privacy notice appears on first mode creation
- [ ] Verify notice warns about LLM forwarding
- [ ] Verify notice recommends not including secrets
- [ ] Test that notice only appears once

## Performance Testing

### 15. Large Templates
- [ ] Test with template near 4000 character limit
- [ ] Verify processing time is reasonable
- [ ] Test with multiple placeholders
- [ ] Verify memory usage is acceptable

### 16. Multiple Modes
- [ ] Create 10+ custom modes
- [ ] Verify app performance remains good
- [ ] Test switching between modes
- [ ] Verify storage doesn't grow excessively

## Edge Cases

### 17. Special Characters
- [ ] Test templates with quotes, newlines, tabs
- [ ] Test transcripts with special characters
- [ ] Verify proper JSON escaping
- [ ] Test Unicode characters in templates

### 18. Network Issues
- [ ] Test with poor network connection
- [ ] Test with backend unavailable
- [ ] Verify retry logic works
- [ ] Test cancellation of requests

## Regression Testing

### 19. Existing Functionality
- [ ] Verify existing formatting still works
- [ ] Verify voice input still works without custom modes
- [ ] Verify settings screens still function
- [ ] Verify no crashes in existing features

### 20. Migration
- [ ] Test fresh app install
- [ ] Test upgrade from previous version
- [ ] Verify no data loss
- [ ] Verify default modes are available
