# Settings Overview

Tekisuto provides several customizable settings to enhance your Japanese reading experience. This guide documents the actual settings available in the app.

## Accessing Settings

Access settings through the hamburger menu (☰) → "Settings" in the main screen.

## Profile Settings

### Current Profile
- **Purpose**: Shows which profile is currently active
- **Default**: "Default" profile
- **Note**: Display-only setting showing current profile name

### Manage Profiles
- **Purpose**: Create, edit, and switch between different setting profiles
- **Function**: Opens Profile Manager Activity
- **Use Case**: Allows you to save different configurations for different use cases

### Save Settings to Profile
- **Purpose**: Saves your current settings configuration to the active profile
- **Function**: Preserves current settings for later use or sharing

## OCR Settings

### OCR Service
- **Purpose**: Choose the text recognition engine
- **Options**: Multiple OCR services available (configured in arrays)
- **Default**: MLKit
- **Function**: Determines which engine processes screenshots for text recognition

### OCR Language
- **Purpose**: Set the primary language for text recognition
- **Function**: Improves OCR accuracy by telling the engine what language to expect
- **Default**: Configured in string resources
- **Note**: Optimizes recognition for specific language characteristics

### Translate OCR Text
- **Purpose**: Enable automatic translation of recognized text
- **Type**: Switch (On/Off)
- **Default**: Enabled (true)
- **Function**: When enabled, recognized text is automatically translated

### Translate Target Language
- **Purpose**: Choose the language to translate text into
- **Dependency**: Only available when "Translate OCR Text" is enabled
- **Default**: English (en)
- **Options**: Multiple languages available (configured in arrays)

## Audio Settings

### Enable Audio
- **Purpose**: Turn audio features on or off
- **Type**: Switch (On/Off)
- **Default**: Enabled (true)
- **Function**: Controls all audio-related functionality in the app

### Azure Speech Key
- **Purpose**: API key for Azure Speech services
- **Type**: Text input
- **Dependency**: Only available when "Enable Audio" is turned on
- **Function**: Required for text-to-speech functionality
- **Security**: Input is protected (not displayed in summary)

### Azure Speech Region
- **Purpose**: Azure service region for speech processing
- **Type**: Text input
- **Default**: eastus
- **Dependency**: Only available when "Enable Audio" is turned on
- **Function**: Determines which Azure datacenter to use

### Select Voice
- **Purpose**: Choose specific voice for text-to-speech
- **Type**: Selection interface
- **Dependency**: Only available when "Enable Audio" is turned on
- **Function**: Opens voice selection dialog

## Pitch Accent Export Settings

### Export Format
- **Purpose**: Choose how pitch accent data is formatted for AnkiDroid export
- **Options**: Multiple format options available (HTML_FORMATTED is default)
- **Default**: HTML_FORMATTED
- **Function**: Controls the structure and appearance of exported pitch accent data

### Include Pattern Type
- **Purpose**: Add Japanese pattern type names to exports
- **Type**: Switch (On/Off)
- **Default**: Enabled (true)
- **Examples**: 平板型 (flat type), 頭高型 (head-high type), etc.
- **Function**: Provides additional linguistic information in exports

### Include Visual Graph
- **Purpose**: Export with SVG pitch accent graph
- **Type**: Switch (On/Off)
- **Default**: Enabled (true)
- **Function**: Adds visual representation of pitch patterns to AnkiDroid cards

### Include Reading
- **Purpose**: Show reading (hiragana/katakana) with pitch accent
- **Type**: Switch (On/Off)
- **Default**: Enabled (true)
- **Function**: Provides pronunciation guide alongside pitch accent information

### Graph Style
- **Purpose**: Choose visual style for pitch accent graphs
- **Dependency**: Only available when "Include Visual Graph" is enabled
- **Default**: DOTS_AND_LINES
- **Options**: Multiple visual styles available
- **Function**: Controls appearance of pitch accent visualizations

## App Whitelist Settings

### Enable App Whitelist
- **Purpose**: Restrict floating button to only appear for selected apps
- **Type**: Switch (On/Off)
- **Default**: Disabled (false)
- **Function**: When enabled, floating button only shows for whitelisted apps

### Manage App Whitelist
- **Purpose**: Select which apps should show the floating button
- **Dependency**: Only available when "Enable App Whitelist" is turned on
- **Function**: Opens app selection interface for whitelist management

## Setting Dependencies

Several settings are dependent on others being enabled:

- **Translate Target Language** requires **Translate OCR Text** to be enabled
- **Azure Speech Key** requires **Enable Audio** to be enabled
- **Azure Speech Region** requires **Enable Audio** to be enabled
- **Select Voice** requires **Enable Audio** to be enabled
- **Graph Style** requires **Include Visual Graph** to be enabled
- **Manage App Whitelist** requires **Enable App Whitelist** to be enabled

## Default Values Summary

- OCR Service: MLKit
- Translate OCR Text: Enabled
- Translate Target Language: English
- Enable Audio: Enabled
- Azure Speech Region: eastus
- Export Format: HTML_FORMATTED
- Include Pattern Type: Enabled
- Include Visual Graph: Enabled
- Include Reading: Enabled
- Graph Style: DOTS_AND_LINES
- Enable App Whitelist: Disabled

## Profile System

The app includes a comprehensive profile system that allows you to:
- Save different setting configurations
- Switch between profiles for different use cases
- Share settings between devices or users
- Maintain separate configurations for different learning styles

---
*For feature-specific guides, see: [Double-tap screenshot](double-tap-screenshot.md), [AnkiDroid integration](ankidroid-integration.md)*