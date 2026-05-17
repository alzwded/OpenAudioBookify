10.0
====

Rollback workaround from version 9.0.

You can now export books without specifying an output directory. In this case,
the app uses the MediaStore to route output to your /Audiobooks directory.

This should be more reliable across Android versions, including those which
lost their Files/DocumentsUI app (or never had it in the first place).

9.0
===

- Add fallbacks for the directory picker in case the Files/DocumentUI activity is indisposed

8.0
===

- Added Romanian language translation, more translations welcome

7.0
===

- You can now share epubs/text files into the app, as it is now registered
  for the `SEND` and `SEND_MULTIPLE` intents
- Book URIs are now passed through ClipData, ensuring the app doesn't randomly
  lose read permissions on the selected books partway through processing
- Log tags now share a common prefix

6.0
===

- Acquire wakelock while background service is active
- Don't overwrite files in the output directory
- Deal with SAF better
- Defensive around output dir actually being selected

5.0
===

- Remove all temporary cache files when done; fixes resource leak
- Fix potential race condition in AudiobookService
- Don't increment chunkIndex twice
- Report failed books in final notification

4.0
===

Accessibility improvements for screen readers.

3.0
===

- Turn encoder bitrate text entry field into a slider with more human friendly names
- Cope with TTS or Encoder errors
- Cope with no TTS engines being installed
- Add remove button to each entry in the list of selected books

2.0
===

Fix theme to actually pick up on accent colour.

1.0
===

Initial release
