# Debug Build Configuration

This directory contains configuration files specific to debug builds of Tekisuto.

## Features

1. **Different App ID**: Debug builds use a different application ID (`com.abaga129.tekisuto.debug`) which allows installation alongside the release version.

2. **Distinct App Name**: Debug builds are labeled as "Tekisuto Debug" in the app drawer.

3. **Debug Icon**: Debug builds use a red icon with a white cross to distinguish them from release builds.

4. **Separate Database**: Debug builds use a separate database file (`tekisuto_dictionary_debug.db`) to avoid conflicts with the release version.

## Usage

To install both debug and release versions simultaneously:

1. Build and install the debug version:
   ```bash
   ./gradlew installDebug
   ```

2. The release version can be installed from Google Play or with:
   ```bash
   ./gradlew installRelease
   ```

This allows you to test changes without affecting your production data.