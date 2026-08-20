# Add Norwegian Language Support

This plan covers adding Norwegian language support (Bokmål) to the MsgGo app by creating a new `strings.xml` resource file in the `values-nb` configuration.

## Proposed Changes

### [app](file:///C:/Users/05vagkri/Utvikling/MsgGo/app)

#### [NEW] [strings.xml](file:///C:/Users/05vagkri/Utvikling/MsgGo/app/src/main/res/values-nb/strings.xml)
- Create a new directory `app/src/main/res/values-nb/`.
- Add a new `strings.xml` file with Norwegian Bokmål translations for all existing string resources.

## Verification Plan

### Automated Tests
- N/A (Localization verification is primarily visual).

### Manual Verification
1. Change the device/emulator language to Norwegian (Norsk bokmål).
2. Open the MsgGo app.
3. Verify that all UI elements (Home, Settings, Dialogs, etc.) are correctly displayed in Norwegian.
