# Deployment Instructions

This guide explains how to deploy the AI Task List application to your personal Android device.

## Prerequisites

1.  **Android Device**: An Android phone or tablet.
2.  **USB Cable**: To connect your device to your computer.
3.  **Developer Options**: You must enable Developer Options on your device.
    *   Go to **Settings > About Phone**.
    *   Tap **Build Number** 7 times until you see "You are now a developer".
4.  **USB Debugging**:
    *   Go to **Settings > System > Developer Options**.
    *   Enable **USB Debugging**.
5.  **Wireless Debugging (Optional)**:
    *   If you prefer wireless, go to **Developer Options** and enable **Wireless Debugging**.
    *   *Note: This requires Android 11 or higher.*

## Locating ADB (Android Debug Bridge)

Since `adb` might not be in your system path, you'll need to find it to run pairing commands.
Based on your project configuration, it should be here:
`D:\Users\ajeng\AppData\Local\AndroidSDK\platform-tools\adb.exe`

*If that folder doesn't exist, check `C:\Users\%USERNAME%\AppData\Local\Android\Sdk\platform-tools\`*

## Option 1: Install via Command Line (USB)

1.  Connect your Android device to your computer via USB.
2.  Accept the "Allow USB debugging" prompt on your phone if it appears.
3.  Open a terminal in the project root directory.
4.  Run the following command:

    **Windows (PowerShell/CMD):**
    ```powershell
    .\gradlew.bat installDebug
    ```

    **Mac/Linux:**
    ```bash
    ./gradlew installDebug
    ```

5.  The app should install and appear on your device's home screen or app drawer.

## Option 2: Manual APK Installation

If you prefer to copy the file manually:

1.  Build the APK by running:
    ```powershell
    .\gradlew.bat assembleDebug
    ```
2.  Locate the generated APK file at:
    `app\build\outputs\apk\debug\app-debug.apk`
3.  Transfer this file to your phone (via USB, Google Drive, etc.).
4.  Open the file on your phone to install it. You may need to allow installation from unknown sources.

## Option 3: Wireless Debugging (Android 11+)

If you don't have a USB cable handy:

1.  **Enable Wireless Debugging** on your phone (Developer Options).
2.  Tap on **Wireless Debugging** (the text itself, not the toggle) to see more options.
3.  Tap **Pair device with pairing code**. You will see an IP address, Port, and Code.
4.  **Don't double-click adb.exe!** Instead, run this command right here in your terminal:

    ```powershell
    & "D:\Users\ajeng\AppData\Local\AndroidSDK\platform-tools\adb.exe" pair 192.168.x.x:12345
    ```
    *(Replace `192.168.x.x:12345` with the IP and Port shown on your phone)*

5.  Enter the pairing code when prompted.
6.  Once paired, look at the main **Wireless Debugging** screen for the "IP address & Port" (this port might be different from the pairing port).
7.  Connect:
    ```powershell
    & "D:\Users\ajeng\AppData\Local\AndroidSDK\platform-tools\adb.exe" connect 192.168.x.x:55555
    ```
8.  Now you can install the app:
    ```powershell
    .\gradlew.bat installDebug
    ```

## What's Next? (Post-Installation)

Once the command finishes with `BUILD SUCCESSFUL`:

1.  **Unlock your phone**.
2.  Look for an app named **"AI Task List"** (or similar) in your app drawer or on your home screen.
3.  **Tap to open it**.
### Build Failed with "25.0.1" or similar error
This usually means you are using a Java version that is too new for the Android Gradle Plugin (e.g., Java 25).

**Yes, you can have both Java 17 and Java 25 installed at the same time.**

**Solution (Project-Specific Override):**
This is the best method because it keeps Java 25 as your system default but tells this specific project to use Java 17.

1.  Install **Java 17** (JDK 17) alongside your existing Java version.
2.  Find the installation path (e.g., `C:\Program Files\Java\jdk-17`).
3.  Create a `gradle.properties` file in the project root (if it doesn't exist) and add this line:
    ```properties
    org.gradle.java.home=C:\\Program Files\\Java\\jdk-17
    ```
    *(Note: Use double backslashes `\\` in the path)*
