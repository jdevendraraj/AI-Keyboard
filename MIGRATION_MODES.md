# Migration Notes for Custom Prompt Modes Feature

## Overview

The Custom Prompt Modes feature is a new addition to the AI Keyboard app. This document outlines migration considerations and compatibility notes.

## Migration Requirements

### No Migration Needed
- **Fresh Install**: New users will have access to all features immediately
- **Existing Users**: No data migration required - existing formatting settings remain unchanged
- **Backward Compatibility**: All existing functionality continues to work as before

## Feature Introduction

### What's New
- **Custom Prompt Modes**: Users can now create, edit, and manage custom prompt templates
- **Built-in Modes**: Three pre-configured modes are available:
  - **Default**: Uses backend's default formatting prompt
  - **Casual Summary**: Formats text in a casual, friendly tone
  - **Technical Notes**: Formats text as technical notes with clear structure
- **Mode Selection**: Users can select which mode to use for transcription formatting
- **Template Preview**: Users can preview how their templates will look with sample text

### Storage Changes
- **New Storage**: Custom modes are stored in `EncryptedSharedPreferences` under key `formatting_modes_secure_prefs`
- **Selection Storage**: Selected mode ID stored under key `selected_mode_id`
- **Encryption**: All mode data is encrypted using AES256_GCM encryption
- **No Server Storage**: Custom prompts are never stored on the server

## Backward Compatibility

### Existing Settings
- **Formatting Backend Settings**: All existing settings remain unchanged
- **API Keys**: Existing API keys continue to work
- **Voice Settings**: Language and voice source settings unchanged
- **Enable/Disable**: Formatting enable/disable setting unchanged

### Default Behavior
- **No Mode Selected**: App behaves exactly as before (uses backend default prompt)
- **Default Mode Selected**: Same behavior as no mode selected
- **Custom Mode Selected**: Uses custom prompt template for formatting

## API Changes

### Backend API Updates
- **New Fields**: `promptTemplate` and `modeTitle` added to request bodies
- **Optional Fields**: Both fields are optional for backward compatibility
- **Auto-append**: Missing `{{transcript}}` placeholder is auto-appended
- **Validation**: Template length limited to 4000 characters (configurable)

### Request Examples
```json
// Existing request (still works)
{
  "transcript": "hello world",
  "requestId": "req-123"
}

// New request with custom prompt
{
  "transcript": "hello world",
  "requestId": "req-123",
  "modeTitle": "Casual Summary",
  "promptTemplate": "Summarize in a casual tone:\n\n{{transcript}}"
}
```

## Security Considerations

### Client-Side Security
- **Encrypted Storage**: All custom modes encrypted with AES256_GCM
- **No Network Transmission**: Modes only sent during transcription requests
- **Privacy Notice**: Users warned about LLM forwarding on first use

### Server-Side Security
- **No Persistence**: Custom prompts never stored on server
- **Safe Logging**: Only truncated previews (200 chars) + hash logged
- **Validation**: Strict length limits and input validation
- **Warning**: Server logs security warning on startup

## User Experience Changes

### New UI Elements
- **Prompt Modes Section**: Added to FormattingBackendScreen
- **Modes Management**: New screen for creating/editing modes
- **Mode Editor**: New screen for editing individual modes
- **Selection Display**: Shows currently selected mode

### Navigation Changes
- **New Routes**: Added routes for modes management and editing
- **Deep Links**: Support for direct navigation to mode screens
- **Backward Navigation**: All existing navigation preserved

## Testing Considerations

### Regression Testing
- **Existing Features**: All existing functionality must continue to work
- **Settings Migration**: Verify existing settings are preserved
- **API Compatibility**: Ensure old clients still work with new backend
- **Performance**: Verify no performance degradation

### New Feature Testing
- **Mode Creation**: Test creating, editing, deleting modes
- **Template Processing**: Test placeholder substitution and auto-append
- **Selection Persistence**: Test mode selection across app restarts
- **Error Handling**: Test validation and error scenarios

## Deployment Notes

### Backend Deployment
- **Environment Variables**: Add `MAX_PROMPT_TEMPLATE_CHARS` (default: 4000)
- **Security Warning**: Ensure startup warning is visible in logs
- **API Documentation**: Update API docs with new fields
- **Testing**: Run comprehensive test suite

### Frontend Deployment
- **Feature Flag**: Consider feature flag for gradual rollout
- **User Education**: Provide in-app guidance for new feature
- **Privacy Notice**: Ensure privacy notice is prominent
- **Fallback**: Ensure graceful fallback if backend doesn't support new fields

## Troubleshooting

### Common Issues
- **Mode Not Saving**: Check EncryptedSharedPreferences permissions
- **Template Not Working**: Verify `{{transcript}}` placeholder is present
- **Backend Errors**: Check template length and special characters
- **Selection Lost**: Verify storage permissions and app state

### Debug Information
- **Logs**: Check client logs for mode selection and template processing
- **Storage**: Verify modes are stored correctly in EncryptedSharedPreferences
- **Network**: Check request/response logs for custom prompt fields
- **Backend**: Verify server logs show custom prompt processing

## Future Considerations

### Potential Enhancements
- **Mode Sharing**: Allow users to share modes with others
- **Mode Categories**: Organize modes into categories
- **Template Variables**: Support for additional template variables
- **Mode Analytics**: Track usage patterns for mode optimization

### API Evolution
- **Versioning**: Consider API versioning for future changes
- **Deprecation**: Plan for deprecating old API versions
- **Extensions**: Design for additional template features
- **Performance**: Monitor and optimize template processing performance
