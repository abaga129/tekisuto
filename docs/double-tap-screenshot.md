# Double-Tap Screenshot Feature

The double-tap screenshot feature allows you to quickly capture and extract text from any screen on your device using OCR (Optical Character Recognition) technology.

## How It Works

1. **Double-tap the floating button** to initiate the screenshot process
2. **Screenshot is automatically taken** of the current screen content
3. **OCR processing runs** on the captured image to extract text
4. **OCR Result screen opens** displaying both the screenshot and extracted text
5. **Select words** from the extracted text to perform dictionary lookups
6. **Optionally export** dictionary entries to create Anki flashcards

## Prerequisites

Before using this feature:
- [Accessibility Service must be enabled](enabling-accessibility-service.md)
- [Floating button must be visible and active](floating-button-setup.md)
- [At least one dictionary must be imported](importing-dictionaries.md) (for word lookups)
- [Anki integration configured](ankidroid-integration.md) (optional, for card export)

## Step-by-Step Usage

### 1. Activate the Screenshot
- Navigate to any screen with text you want to extract
- **Double-tap the floating button** quickly
- The floating button should respond to the double-tap gesture
- Wait for the screenshot to be captured (usually instant)

### 2. OCR Processing
After the screenshot is taken:
- **OCR processing begins automatically** on the captured image
- Text extraction runs in the background (typically 1-3 seconds)
- The app analyzes the image for readable text content

### 3. OCR Result Screen
Once processing is complete:
- **OCR Result screen opens** automatically
- **Screenshot is displayed** at the top of the screen
- **Extracted text appears below** the screenshot image
- Text is presented in selectable format for further interaction

### 4. Word Selection and Dictionary Lookup
From the OCR Result screen:
- **Tap and select words** from the extracted text
- **Dictionary lookup is performed** automatically for selected words
- **Definition and readings appear** in a dictionary entry view
- Browse through multiple meanings and usage examples

### 5. Export to Anki (Optional)
If Anki integration is configured:
- **Tap the export icon** on the dictionary entry
- **Anki card is created** with the word, definition, and context
- Card is added to your configured Anki deck
- **Previously exported words will be marked** with a blue background and checkmark next to the word.

## Optimal Usage Tips

### Screenshot Quality
- **High contrast text** (dark on light backgrounds) works best
- **Standard fonts** are more accurately recognized than stylized text

### Text Recognition
- **Larger text sizes** generally produce better OCR results
- **Horizontal text** is typically more accurate than vertical orientation
- **Clean backgrounds** improve text boundary detection
- **Avoid overlapping elements** that might confuse the OCR engine

### Best Content Types
**Excellent for**:
- Manga, Webtoons, and Comics
- Web pages with clear article text
- Social media posts and comments
- E-book reader content
- Menu screens and UI text
- Email and messaging content

**Challenging for**:
- Handwritten text
- Heavily stylized fonts
- Text with special effects or shadows
- Very small font sizes (under 10pt)
- Low contrast or faded text

## Troubleshooting

### Double-Tap Not Registering
**Floating Button Issues**:
- Ensure floating button is visible on screen
- Check that accessibility service is running
- Try tapping more rapidly (within 300ms between taps)
- Restart the app if floating button becomes unresponsive

**Permission Problems**:
- Verify screenshot permissions are granted
- Check "Display over other apps" permission
- Ensure accessibility service has full permissions

### Poor OCR Results
**Improve Text Recognition**:
- Increase text size before capturing (zoom in)
- Try different OCR engine settings if available
- Ensure the target text is in focus and clear

**Language Detection**:
- Verify OCR is configured for the correct language
- Check if language data files are properly installed
- Test with simple, clear text to validate OCR functionality

### OCR Result Screen Not Opening
**Processing Issues**:
- Wait longer for OCR processing to complete
- Check device performance and available memory
- Restart app if processing appears stuck
- Verify OCR engine installation and configuration

### Dictionary Lookup Problems
**Word Selection Issues**:
- Ensure dictionaries are properly imported and enabled
- Try selecting different parts of words or phrases
- Check dictionary coverage for specific language or domain

### Anki Export Not Working
**Integration Problems**:
- Confirm AnkiDroid is installed and properly configured
- Check that Anki integration permissions are granted
- Verify the target deck exists and is accessible
- Test export with simple, known words first

---

*Need help with setup? Check the [floating button configuration guide](floating-button-setup.md) or [accessibility service setup](enabling-accessibility-service.md).*