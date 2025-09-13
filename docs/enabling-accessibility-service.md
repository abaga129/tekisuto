# Enabling Accessibility Service

The Accessibility Service is **required** for Tekisuto to function properly. It allows the app to capture screenshots and analyze text from your screen.

## Why It's Needed

Tekisuto uses Android's Accessibility Service API to:
- Take screenshots when you double-tap
- Access text content from other apps
- Provide the floating button overlay
- Analyze screen content for text

## Step-by-Step Setup

### 1. Open Tekisuto App
- Launch the Tekisuto app from your app drawer
- You should see the main screen with status indicators

### 2. Check Accessibility Status
- Look for the "Accessibility Service Status" indicator
- If it shows "❌ Disabled", you need to enable it
- If it shows "✅ Enabled", you're all set!

### 3. Enable the Service

**Method 1: From Tekisuto App**
1. Open the hamburger menu (three horizontal lines) in the top-left
2. Tap "Accessibility Settings"
3. This will take you directly to the Android accessibility settings

**Method 2: Manual Navigation**
1. Open your device's **Settings** app
2. Scroll down to **Accessibility** (may be under "System" on some devices)
3. Tap **Accessibility**
4. Find **Tekisuto** in the list of services
5. Tap on **Tekisuto**
6. Toggle the switch to **ON**
7. Confirm by tapping **OK** in the dialog

### 4. Grant Permissions
When enabling the service, Android will show a warning dialog:
- Read the permissions carefully
- Tap **Allow** or **OK** to grant access
- The service needs these permissions to capture and analyze screen content

### 5. Verify Setup
Return to the Tekisuto app and check that:
- "Accessibility Service Status" shows "✅ Enabled"
- The floating button appears on your screen (if enabled in settings)

## Troubleshooting

### Service Won't Enable
- **Restart the app** - Close Tekisuto completely and reopen it
- **Restart your device** - Some devices require a reboot after enabling accessibility services
- **Check for conflicts** - Other screen capture apps might interfere

### Permission Denied
- Make sure you tapped "Allow" in all permission dialogs
- Some devices have additional security settings that block accessibility services
- Check if "Install apps from unknown sources" is enabled (for sideloaded apps)

### Service Keeps Disabling
- Some device manufacturers (Samsung, Xiaomi, Huawei) have aggressive battery optimization
- Add Tekisuto to the battery optimization whitelist:
  1. Settings → Battery → Battery Optimization
  2. Find Tekisuto and set to "Don't optimize"

### Floating Button Not Appearing
- The floating button is controlled by a separate setting
- Open the hamburger menu → "Show Floating Button" to enable it
- Some devices block overlay permissions - check notification for permission request

## Security Note

The accessibility service can access screen content, which is why Android shows a warning. Tekisuto only uses this permission to:
- Capture screenshots when you double-tap
- Analyze visible text for dictionary lookups
- **No data is sent to external servers**
- All processing happens locally on your device except for Google Lens OCR which may use online processing

## Next Steps

Once accessibility is enabled:
1. [Import dictionaries](importing-dictionaries.md) for word lookups
2. [Configure OCR settings](settings-overview.md) for better text recognition
3. [Test the double-tap feature](double-tap-screenshot.md) on some Japanese text

---
*Having trouble? Check our [troubleshooting section](README.md) or report issues on GitHub.*