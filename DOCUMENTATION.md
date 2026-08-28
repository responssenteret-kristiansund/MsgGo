# MsgGo Project Documentation

MsgGo is a modern Android application designed for bulk SMS sending from local spreadsheet files (Excel and ODS). It prioritizes privacy by processing all data locally on the device with zero network access.

---

## 1. Application Architecture

The app follows a standard Android architectural pattern using **Activities** for navigation, **Fragments** for UI modules, and **Services** for long-running background tasks.

- **UI Layer**: Material 3 Design components.
- **Data Layer**: Singleton-based state management (`DataModel`) and `SharedPreferences` for persistence (`SettingManager`).
- **Service Layer**: Foreground service (`MessageService`) to ensure reliable SMS sending even when the app is in the background.

---

## 2. Core Components

### 2.1 Activities & Fragments

#### `MainActivity`
The entry point of the app. It hosts a `ViewPager2` that switches between the `HomeFrag` and `SettingFrag`.
- **Functions**: Handles permissions, privacy policy acceptance, and deep links (sharing files to MsgGo).

#### `HomeFrag`
The main dashboard where users interact with the core features.
- **Functions**: File selection, status display, and launching the sending process.

#### `SettingFrag`
Allows users to configure app behavior.
- **Functions**: Configure send delays, toggle dark mode, change language, and manage cache.

#### `SendingActivity`
The "Live" sending screen that displays real-time progress.
- **Functions**: Manages the sending loop, handles pause/resume logic, and updates progress bars.

#### `ChooserActivity`
A utility activity used to select which specific rows from an imported table should be included in the sending task.

#### `EditActivity`
The template editor.
- **Functions**: Dynamic variable substitution (e.g., `{Name}`) and real-time message preview.

---

### 2.2 Data Management

#### `DataModel`
A singleton class that holds the currently loaded spreadsheet data in memory.
- **Functions**: `load(path)` parses the file, `deduplicate()` removes duplicate phone numbers.

#### `SettingManager`
Handles all persistent user preferences using `SharedPreferences`.
- **Key Settings**: `send_delay`, `language`, `dark_mode`, `finish_delay`.

#### `HistoryManager`
Tracks previously imported files, their templates, and selected columns to provide a "smart" resume experience.

---

### 2.3 Services

#### `MessageService`
A foreground service that manages the actual SMS transmission.
- **Why Foreground?**: To prevent the Android OS from killing the process during a long-running batch send.
- **Callbacks**: Communicates back to the `SendingActivity` when a message is successfully submitted or confirmed by the carrier.

#### `SMSBroadcastReceiver`
Listens for system intents from the Android Telephony stack to confirm if a message was successfully delivered or if it failed.

---

### 2.4 Utilities

#### `SpreadsheetReader` & `Parsers`
- `SpreadsheetReader`: A factory that detects file extensions and routes them to the correct parser.
- `PoiSpreadsheetParser`: Uses the Apache POI library for `.xlsx` and `.xls` files.
- `OdsSpreadsheetParser`: Specifically for OpenDocument spreadsheets.

#### `SensitiveWordUtil DEPRECATED`
Uses the `sensitive-word` library to scan message content for restricted terms before sending, helping to avoid carrier blocks.

#### `LocaleUtils`
Manages on-the-fly language switching (English, Chinese, Norwegian) without requiring a full device reboot.

---

## 3. How the App Works: Workflow

1.  **Import**: The user selects an Excel/ODS file. The `SpreadsheetReader` converts the rows into a `List<HashMap>`.
2.  **Configuration**: The user selects which column contains the phone numbers and writes a template. Variables like `{ColumnName}` are automatically replaced with data from the row.
3.  **Validation**: The app checks for duplicates and scans for sensitive words.
4.  **Sending**: 
    - `SendingActivity` binds to `MessageService`.
    - The service creates a status bar notification.
    - Messages are sent one-by-one with a user-defined delay to avoid being flagged as spam by the carrier.
5.  **Confirmation**: The `SMSBroadcastReceiver` catches the result for every individual part of the SMS (handling multi-part messages) and updates the UI.
6.  **Completion**: Once all messages are "Confirmed" (or the timeout is reached), the app returns the user to the Dashboard.

---

## 4. Privacy & Security
- **No Internet Permission**: The app cannot send data to any external server.
- **Local Cache**: Files are copied to the app's internal private storage for processing and are cleared upon user request.
