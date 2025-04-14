# Tekisuto User Guide

Tekisuto ("text" in Japanese) is an OCR-powered dictionary lookup tool designed for language learners. This guide will walk you through setting up and using the app effectively.

## Table of Contents

- [Setup](#setup)
  - [Requirements](#requirements)
  - [Enabling the Accessibility Service](#enabling-the-accessibility-service)
  - [Importing Dictionaries](#importing-dictionaries)
  - [AnkiDroid Integration](#ankidroid-integration)
    - [Importing from Anki Packages](#importing-from-anki-packages)
- [Basic Usage](#basic-usage)
  - [Capturing Text](#capturing-text)
  - [Text Selection](#text-selection)
  - [Word Lookup](#word-lookup)
  - [Searching Dictionaries](#searching-dictionaries)
- [Advanced Features](#advanced-features)
  - [Managing Dictionaries](#managing-dictionaries)
  - [Exporting to AnkiDroid](#exporting-to-ankidroid)
  - [Saving and Sharing OCR Results](#saving-and-sharing-ocr-results)
- [Troubleshooting](#troubleshooting)
  - [OCR Quality Issues](#ocr-quality-issues)
  - [Dictionary Lookup Problems](#dictionary-lookup-problems)
    - [Finding Specific Words](#finding-specific-words)
  - [AnkiDroid Connection Issues](#ankidroid-connection-issues)
    - [Anki Package Import Issues](#anki-package-import-issues)
- [Privacy and Data](#privacy-and-data)

## Setup

### Requirements

- Android 9 (API 28) or newer
- AnkiDroid app (optional, for flashcard integration)
- Yomitan/Yomichan format dictionaries (`.zip` files)

### Enabling the Accessibility Service

Tekisuto uses Android's accessibility service to capture text from any app:

1. Open the app and tap on the notification to go to accessibility settings
2. Find "Tekisuto" in the services list
3. Toggle the switch to enable the service
4. Accept the permissions prompt

The accessibility service allows Tekisuto to display an OCR button overlay and capture screenshots for text extraction.

### Importing Dictionaries

Tekisuto uses Yomitan/Yomichan format dictionaries (`.zip` files):

1. Go to "Dictionary Manager" from the app's main menu
2. Tap the "+" floating action button
3. Select a dictionary file (`.zip`) from your device storage
4. The app will process and import the dictionary

Dictionary priorities can be managed in the Dictionary Manager screen.

### AnkiDroid Integration

To set up AnkiDroid integration for creating flashcards:

1. Install AnkiDroid from the Play Store
2. In Tekisuto, go to "Settings" → "AnkiDroid Configuration"
3. Tap "Configure AnkiDroid" to grant permissions
4. Select a deck and note type for your flashcards
5. Configure field mappings for your cards

#### Importing from Anki Packages

You can also import words from existing Anki decks:

1. In the AnkiDroid Configuration screen, tap "Import from Anki Package"
2. Select an .apkg file from your device storage
3. Choose which field contains the vocabulary terms
4. The app will import these words to mark them as already exported
5. Supports both Anki 2.0 and Anki 2.1 package formats

## Basic Usage

### Capturing Text

To capture and process text from any app:

1. With the accessibility service enabled, you'll see an OCR button overlay
2. Navigate to content you want to capture in any app
3. Tap the OCR button
4. A screenshot will be taken

### Text Selection

After capturing a screenshot:

1. Drag to select the area containing text you want to analyze
2. Confirm your selection
3. The app will process the image and extract text

### Word Lookup

Once text is extracted:

1. The OCR result screen will display the extracted text
2. Tap on any word in the text to look it up in your dictionaries
3. Dictionary matches will appear in the lower section

### Searching Dictionaries

You can also search dictionaries directly:

1. Enter a word in the search field at the bottom of the OCR result screen
2. Tap the search button
3. Dictionary results will appear below

The search results are intelligently ordered:
- Exact matches for your search term appear at the top of the results
- Words that start with your search term appear next
- Matches in readings or definitions appear afterward
- Results are further ranked by dictionary priority

## Advanced Features

### Managing Dictionaries

Organize your dictionaries for optimal lookup:

1. Go to "Dictionary Manager" from the main menu
2. Reorder dictionaries using the up/down arrows (higher dictionaries are searched first)
3. Remove dictionaries by tapping the delete button
4. Import new dictionaries with the "+" button

When browsing dictionaries, entries are color-coded for your convenience:
- Words that have been exported to AnkiDroid are highlighted with a light blue background
- These entries also show a green checkmark on the export button
- This visual system helps you avoid creating duplicate flashcards

### Exporting to AnkiDroid

Create flashcards from your dictionary lookups:

1. Look up a word in the OCR result screen
2. Tap the AnkiDroid icon on a dictionary match
3. The entry will be exported to your configured AnkiDroid deck
4. Fields will be populated according to your configuration

### Saving and Sharing OCR Results

You can save or share extracted text:

1. On the OCR result screen, tap "Copy" to copy all text to clipboard
2. Tap "Save" to save the OCR text to a file
3. Saved files can be accessed later from the app's storage directory

## Troubleshooting

### OCR Quality Issues

If OCR results are poor:

- Ensure the text is clearly visible and well-lit
- Try to avoid capturing text at extreme angles
- For small text, zoom in before capturing
- Try adjusting the crop area to include only the text you want to recognize

### Dictionary Lookup Problems

If dictionary lookup is not working correctly:

- Check that you've imported compatible dictionaries
- Verify dictionaries are working by using the direct search function
- Try reordering dictionaries in Dictionary Manager
- For structured content dictionaries, ensure they are properly formatted

#### Finding Specific Words

If you're having trouble finding a specific word:
- Use the exact term in your search query
- The search prioritizes exact matches at the top of results
- If looking for a common word that might appear in many definitions, try typing the complete word instead of just a few letters

### AnkiDroid Connection Issues

If AnkiDroid integration is not working:

- Make sure AnkiDroid is installed and you've granted permissions
- Reconfigure the AnkiDroid settings in Tekisuto
- Check that your selected deck and note type exist in AnkiDroid
- Verify your field mappings are correct

#### Anki Package Import Issues

If you have problems importing from .apkg files:
- For Anki 2.1 packages, make sure you're using the latest version of Tekisuto
- If you see an error about "legacy export", try re-exporting from Anki with the "Legacy format (Anki 2.0)" option enabled
- Check that the .apkg file contains the vocabulary you expect
- Verify that the field names you select actually contain the terms you want to track

## Privacy and Data

Tekisuto respects your privacy:

- All text processing happens on your device
- No data is sent to remote servers
- Dictionary data remains on your device

