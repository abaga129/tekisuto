# Tekisuto

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="100" height="100" alt="Tekisuto app icon">

Tekisuto ("text" in Japanese) is an OCR-powered dictionary lookup tool for language learners. It lets you easily scan text from any app, look up words in your dictionaries, and export them to AnkiDroid for flashcard study.

## OCR Backends

Tekisuto offers multiple OCR (Optical Character Recognition) options to suit your needs:

- **ML Kit OCR**: Fast, on-device recognition powered by Google's ML Kit
- **Cloud OCR**: Higher accuracy recognition using cloud-based services (requires API key)
- **Google Lens**: Leverages Google's powerful visual search technology for text recognition
- **Tesseract OCR**: Open-source OCR engine with excellent multilingual support

For detailed information about the Tesseract OCR backend, see [Tesseract OCR Backend](TESSERACT_OCR_BACKEND.md).

## Features

- **OCR Text Extraction**: Capture text from any app that doesn't block screenshots
- **Dictionary Lookup**: Support for Yomitan/Yomichan format dictionaries
- **Multiple Profiles**: Configure different settings for various languages or learning setups
- **App Whitelist**: Automatically activate Tekisuto when opening specific apps
- **AnkiDroid Integration**: Export dictionary matches directly to AnkiDroid as flashcards
- **Smart Search Results**: Exact matches are prioritized at the top of search results
- **Visual Dictionary Management**: Dictionary entries show which words have been exported to Anki
- **Text Area Selection**: Precisely crop the area containing text you want to scan
- **Multiple OCR Backends**: Choose between ML Kit, Cloud OCR, Google Lens, and Tesseract OCR
- **Anki Deck Import**: Import words from Anki packages (.apkg files) to track already studied vocabulary
- **Privacy-focused**: All processing happens on-device with no cloud services required

## How It Works

1. Activate the floating OCR button through the accessibility service
2. Capture a screenshot and crop the text area
3. The app performs OCR on the selected area
4. Extracted text is matched against your imported dictionaries
5. Export results to AnkiDroid or save for later reference

For detailed instructions, see the [Tekisuto User Guide](wiki.md).

## Profile Management

Create multiple profiles for different language learning contexts:
- Configure separate dictionary sets for each language
- Set custom OCR parameters optimized for different scripts
- Maintain separate AnkiDroid export configurations
- Quickly switch between configurations with a single tap

## App Whitelist

Make language learning seamless by automatically activating Tekisuto in your preferred apps:
- Automatic OCR button display when opening whitelisted apps
- No need to manually enable the service for each session
- Perfect for e-readers, browsers, and learning apps

## AnkiDroid Integration

Streamline your flashcard creation workflow:
- Export dictionary entries directly to AnkiDroid
- Customize field mappings for your note types
- Import existing Anki decks to track words you've already studied
- Visual indicators show which words are already in your decks

## Requirements

- Android 9 (API 28) or newer
- AnkiDroid app (optional, for flashcard integration)

## Building from Source

```bash
# Clone the repository
git clone https://github.com/abaga129/Tekisuto.git

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.