# Known Issues

## Rotation and resume can still desync zoom, contrast, and exposure

Status: partially mitigated in `v1.0.3`, not fully fixed.

On some devices, especially foldables or phones with more complex camera stacks, rotating the phone or sending the app to the background and reopening it can still cause `zoom`, `contrast`, or `exposure` to jump back to a previous value.

This issue has been present throughout the project, but it became more visible once manual zoom, edge sliders, and more aggressive state restoration were added to the UI.

### What appears to be happening

The main cause seems to be a race between:

- Compose/UI state restoration
- CameraX controller rebinding after orientation or lifecycle changes
- Device camera state being reported again once the preview pipeline is recreated

The result is that the UI may remember a value correctly, but CameraX or the device camera pipeline can overwrite part of that state a moment later.

### What was done in `v1.0.3`

- Removed the custom `configChanges` handling so Android can follow the standard recreation path
- Kept `rememberSaveable` as the main source of truth for rotation/state restoration
- Reduced preference-based restoration so disk state is mainly used for cold starts
- Reapplied camera state on lifecycle restart in a more controlled way

This improves the behavior noticeably, but it does not completely eliminate the issue on every device.

### Current workaround

- After rotating or reopening the app, quickly verify `zoom`, `contrast`, and `exposure`
- If one of them looks wrong, touching its control once forces the intended value back into the pipeline
- For the most predictable capture flow, avoid rotating the device while framing a critical shot

### Planned long-term fix

The next robust approach would be to move camera-control state into a dedicated `ViewModel` or state holder with an explicit CameraX rebind state machine, rather than relying on recomposition plus lifecycle callbacks alone.
