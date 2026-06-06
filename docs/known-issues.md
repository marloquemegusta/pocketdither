# Known issues

PocketDither is usable on real Android devices, but some behavior still depends on camera hardware, CameraX implementation details, and lifecycle edge cases.

## Rotation and app resume can desync some controls

On some devices, rotating the phone or resuming the app from the background can still cause `zoom`, `contrast`, or `exposure` to momentarily drift from the last visible UI state.

This has been partially mitigated, but not fully eliminated.

Why it happens:

- CameraX may rebind the preview and camera control state after orientation or lifecycle changes
- Compose state and device camera state do not always reattach in exactly the same order
- lens switching and per-device camera stacks introduce additional timing variability

Current workaround:

- verify zoom and exposure after a rotation or resume event
- touching the control again usually reapplies the intended value cleanly

## Camera behavior is device-dependent

Lens switching, available zoom range, exposure compensation support, and flash behavior vary between Android devices. PocketDither uses the real camera capabilities exposed by CameraX, so the UI can behave slightly differently depending on the phone.

## Preview and saved capture are close, but not identical on every path

The app is designed to keep preview and capture visually aligned, but exact parity is still affected by:

- crop and scaling differences
- device camera output behavior
- internal processing resolution
- camera state changes during capture

## Release artifacts may not always include a signed APK

The repository supports release builds and GitHub Releases, but signed release APKs depend on local or CI signing configuration. If a release contains both debug and unsigned artifacts, the debug build is the directly installable fallback unless a signed APK is attached explicitly.

## Older test builds may install as a separate app

During repository cleanup, PocketDither moved to the neutral package id `com.marlo.pocketdither`. If you installed an older local/test APK before that cleanup, Android may treat the current build as a different app. Uninstalling older test builds before installing the latest APK avoids duplicate launcher entries.
