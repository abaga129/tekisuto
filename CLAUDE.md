# Tekisuto Android App Guidelines

## Build/Test Commands
```bash
./gradlew assembleDebug              # Build debug APK
./gradlew installDebug               # Install on connected device
./gradlew test                       # Run unit tests
./gradlew test --tests "TestClass"   # Run a specific test class
./gradlew connectedAndroidTest       # Run instrumented tests
./gradlew lint                       # Run lint checks
./gradlew clean                      # Clean build files
```

## Code Style Guidelines
- **Imports**: Organize by package type (Android first, then Kotlin, then project-specific). No wildcard imports.
- **Formatting**: 4-space indentation. Braces on same line as declaration (Kotlin style).
- **Types**: Use Kotlin type inference where appropriate. Prefer safe call operators (?.) over non-null assertions (!).
- **Naming**: PascalCase for classes, camelCase for functions/variables, UPPER_SNAKE_CASE for constants.
- **Error Handling**: Catch exceptions, log with Android Log utility, display user-friendly messages via Toast.
- **Asynchronous**: Use coroutines for async operations with proper exception handling.
- **Documentation**: Use KDoc style comments for classes and public methods, focusing on "why" not "what".
- **Architecture**: Follow MVVM pattern with ViewModels, LiveData/StateFlow, and Repositories.