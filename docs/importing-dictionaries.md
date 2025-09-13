# Importing Dictionaries

Dictionaries are essential for Tekisuto to provide Japanese word definitions and translations. The app supports multiple dictionary formats and sources.

## Supported Dictionary Formats

- **Yomichan format** - Popular browser extension format

## Quick Setup (Recommended)

### 1. Download JMdict
JMdict is the most comprehensive free Japanese dictionary:

1. Visit [JMdict Downloads](https://www.edrdg.org/wiki/index.php/JMDict-EDICT_Dictionary_Project)
2. Download the latest **JMdict_e.gz** file
3. Extract the `.gz` file to get the dictionary file

### 2. Import to Tekisuto

**Method 1: Direct File Import**
1. Open Tekisuto app
2. Open hamburger menu → "Manage Dictionaries"
3. Tap "Import Dictionary"
4. Navigate to your downloaded dictionary file
5. Select the file and wait for import to complete

**Method 2: Place in Dictionaries Folder**
1. Connect your device to computer
2. Navigate to `Android/data/com.abaga129.tekisuto/files/dictionaries/`
3. Copy your dictionary file to this folder
4. Open Tekisuto → "Manage Dictionaries" → "Refresh"

## Alternative Dictionary Sources

### Yomichan Dictionaries
1. Visit [Yomichan dictionaries](https://github.com/FooSoft/yomichan)
2. Download desired dictionary (e.g., JMdict, JMnedict, KANJIDIC)
3. Import the `.zip` file through Tekisuto

## Managing Your Dictionaries

### View Imported Dictionaries
1. Open hamburger menu → "Manage Dictionaries"
2. See list of all imported dictionaries
3. Check dictionary status (Active/Inactive)

### Enable/Disable Dictionaries
- Toggle dictionaries on/off to control which ones are used for lookups
- Disabled dictionaries remain imported but won't show results
- Useful when you have multiple dictionaries and want to prioritize certain ones

### Dictionary Priority
When multiple dictionaries are enabled:
- Results are shown in order of dictionary import
- More comprehensive dictionaries (like JMdict) should be imported first
- Specialized dictionaries can supplement main dictionaries

### Remove Dictionaries
1. In "Manage Dictionaries" screen
2. Press the Trashcan icon for the dictionary you would like to delete.
4. Confirm deletion

## Troubleshooting Dictionary Import

### Import Failed
- **File format not recognized**: Ensure your dictionary is in a supported format
- **File corrupted**: Re-download the dictionary file
- **Insufficient storage**: Free up device storage space

### Dictionary Not Showing Results
- **Check if enabled**: Make sure dictionary is toggled ON in manage dictionaries
- **Restart app**: Close and reopen Tekisuto after importing
- **Test with known words**: Try common words like "水" (water) or "食べる" (to eat)

### Import Taking Too Long
- Large dictionaries (like full JMdict) can take several minutes to import
- Don't interrupt the process or close the app
- Ensure device doesn't go to sleep during import

## Dictionary Recommendations

- **JMdict** - Essential comprehensive dictionary
- **KANJIDIC** - Kanji information and readings
- **[Seth's Spanish Dictionary](https://github.com/seth-js/yomichan-es/releases/download/v1.1/Spanish.Dictionary.zip)** - General purpose spanish dictionary.

## Testing Your Dictionaries

After importing:
1. Use the [double-tap screenshot](double-tap-screenshot.md) feature on Japanese text
2. Try [OCR camera](long-press-ocr.md) with printed Japanese text
3. Test with various word types:
   - Common nouns: 本 (book), 水 (water)
   - Verbs: 食べる (to eat), 行く (to go)
   - Adjectives: 美しい (beautiful), 大きい (big)

## Next Steps

Once your dictionaries are imported:
1. [Configure OCR settings](settings-overview.md) for better text recognition
2. [Set up AnkiDroid integration](ankidroid-integration.md) to export vocabulary
3. [Learn the double-tap feature](double-tap-screenshot.md) for quick lookups

---
*Need more dictionaries? Check the [community resources](README.md) for additional sources.*