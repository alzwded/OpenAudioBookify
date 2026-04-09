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
