# Tekisuto

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="100" height="100" alt="Tekisuto app icon">

Tekisuto ("text" in Japanese) is an OCR-powered dictionary lookup tool for language learners. It lets you easily scan text from any app, look up words in your dictionaries, and export them to AnkiDroid for flashcard study.

## Features

- **OCR Text Extraction**: Capture text from any app using the accessibility service
- **Dictionary Lookup**: Support for Yomitan/Yomichan format dictionaries
- **Text Area Selection**: Precisely crop the area containing text you want to scan
- **Multi-language Support**: Works with Latin, Chinese, Japanese, Korean, Devanagari and other scripts
- **AnkiDroid Integration**: Export dictionary matches directly to AnkiDroid as flashcards
- **Privacy-focused**: All processing happens on-device with no cloud services required

## How It Works

1. Activate the floating OCR button through the accessibility service
2. Capture a screenshot and crop the text area
3. The app performs OCR on the selected area
4. Extracted text is matched against your imported dictionaries
5. Export results to AnkiDroid or save for later reference

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