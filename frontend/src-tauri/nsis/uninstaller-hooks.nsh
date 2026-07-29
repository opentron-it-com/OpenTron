!macro NSIS_HOOK_PREUNINSTALL
  MessageBox MB_ICONQUESTION|MB_YESNO "Do you want to delete all local OpenTron data (models cache, logs, settings, and sidecar files)?" IDNO skip_data_cleanup

  ; Primary local data path used by the desktop app runtime.
  RMDir /r "$LOCALAPPDATA\OpenTron"

  ; Embedded H2 database path used by backend profile.
  RMDir /r "$PROFILE\.opentron"

  ; Defensive cleanup for legacy and framework-specific paths.
  RMDir /r "$LOCALAPPDATA\com.opentron.desktop"
  RMDir /r "$APPDATA\com.opentron.desktop"

skip_data_cleanup:
!macroend
