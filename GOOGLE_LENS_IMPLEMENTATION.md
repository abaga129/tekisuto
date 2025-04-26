# Google Lens Backend Implementation

## Overview

This implementation adds a Google Lens OCR backend to the Tekisuto application. The Google Lens backend provides high-quality OCR capabilities without requiring an API key, by directly interfacing with Google's public Lens service.

## Implementation Details

The implementation is based on a Python approach that uses a workaround to access Google Lens OCR capabilities. The key components are:

1. A direct connection to Google Lens upload endpoint (`https://lens.google.com/v3/upload`)
2. A multi-stage process that:
   - Uploads an image to Google Lens
   - Follows a redirect to get session parameters
   - Retrieves OCR metadata using the session parameters
   - Extracts text content from the returned JSON

The implementation is contained within `GoogleLensOcrService.kt` and integrates with the existing OCR service framework.

## Changes Made

1. **Updated GoogleLensOcrService.kt**:
   - Implemented a robust image upload mechanism
   - Added proper header handling for Google Lens requests
   - Created JSON parsing code that follows the nested structure of Google Lens responses
   - Added fallback parsing logic for robustness
   - Enhanced image preprocessing algorithm that:
     - Ensures images are under 3,000,000 pixels total (width × height)
     - Maintains aspect ratio while resizing
     - Uses high-quality scaling for better text recognition
     - Optimizes PNG compression for Google Lens processing

2. **Added UI Integration**:
   - Added "Google Lens" to the OCR service options in `strings.xml`
   - Added the service value mapping (`glens`) to service values array

3. **Updated Service Selection Logic**:
   - Modified `OcrHelper.kt` to properly handle the Google Lens service type

## Usage Instructions

To use the Google Lens OCR backend:

1. Open Tekisuto and go to the Settings screen
2. In the OCR Settings section, tap on "OCR Service"
3. Select "Google Lens" from the dropdown menu
4. The app will now use Google Lens for all OCR operations

## Technical Notes

- The implementation doesn't require an API key
- It uses standard Android libraries and doesn't add any additional dependencies
- It includes fallback mechanisms for robustness
- It has proper error handling and user feedback
- It implements all required methods from the OcrService interface
- The image preprocessing algorithm is optimized for OCR text recognition

## Limitations

- Relies on the public Google Lens service, which may change without notice
- May have limitations on request frequency (recommended to implement rate limiting in production)
- Not officially supported by Google

## Testing

The Google Lens backend can be tested by:

1. Selecting "Google Lens" as the OCR service in Settings
2. Using the floating button to capture screenshots of text
3. Verifying that the text is correctly recognized

Performance may vary based on:
- Text language
- Image quality and resolution
- Internet connection
