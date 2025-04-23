# OCR Result Module Refactoring

## Overview

This module contains components related to the OCR result functionality that was previously contained in a single monolithic activity file. The code has been refactored according to the Single Responsibility Principle to improve maintainability and testability.

## Components

### 1. OCRResultActivity.kt

The main activity that coordinates all the components and handles the UI lifecycle. This is a much slimmer version of the original file, with most of the logic delegated to specialized manager classes.

Key responsibilities:
- Activity lifecycle management
- UI initialization and event handling
- Coordination of manager classes

### 2. OCRTextProcessor.kt

Handles all text processing operations including:
- Setting up clickable text in the flow layout
- Detecting and processing vertical Japanese and Chinese text
- Highlighting matched dictionary words

### 3. AudioManager.kt

Manages audio generation and playback:
- Generating audio for terms via Azure TTS
- Playing audio files
- Caching audio for reuse
- Language detection for audio generation

### 4. AnkiExportManager.kt

Handles exporting dictionary entries to AnkiDroid:
- Checking AnkiDroid availability and configuration
- Preparing audio files for export
- Exporting notes to AnkiDroid with definitions, context, and media

### 5. DictionarySearchManager.kt

Manages dictionary lookup and search functionality:
- Initializing and configuring the dictionary adapter
- Handling search queries
- Processing search results
- Managing text input and lowercase conversion

## Advantages of the Refactoring

1. **Improved Maintainability**: Each component has a clear, single responsibility, making it easier to understand and maintain.

2. **Enhanced Testability**: Smaller, focused classes are easier to test in isolation.

3. **Better Code Organization**: Related functionality is grouped together logically.

4. **Reduced Complexity**: The main activity is now much simpler and focuses on coordination rather than implementation details.

5. **Easier Extension**: New features can be added by extending or modifying specific components without affecting the whole system.

## Usage

The OCRResultActivity initializes all the manager classes and coordinates their interaction. Each manager class handles its specific responsibility and communicates with the others through well-defined interfaces.