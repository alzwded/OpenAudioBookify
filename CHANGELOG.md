15.0
====

Actually successfully read book when TalkBack is running. Note, this uses
a much smaller utterance chunk. Also note, it is very likely the app will
look up on Androids older than 10.

14.0
====

- The Main Activity now provides a percentage of book processed to get a better
  feel of how long it will take to process a book.
- Stop double re-encoding final merged m4a
- Output is now 44.1KHz, eliminating some audio artefacts (I don't why it defaulted to 12Khz)

13.0
====

- Fix permission issue for Androids < 10 introduced by the workaround
  introduced in version 10.0 which uses the MediaStore to output audiobooks to
  a default directory
- Add a LaunchedEffect to re-check if permissions were gained/lost, in case
  they were gained/lost since the app was started and since we last checked
  and the activity wasn't reaped by the system

12.0
====

- Apparently, `Environment.DIRECTORY_AUDIOBOOKS` didn't exist on Android 10.0, so use `Environment.DIRECTORY_PODCASTS` instead
  + possibly [here](https://android.googlesource.com/platform/packages/providers/MediaProvider/+/refs/tags/android-10.0.0_r47/src/com/android/providers/media/MediaProvider.java#1959)
- Should fix crash writing final merged output file on Android 10

11.0
====

- Fix accessibility content description of the little reset button next to "Set output directory" to now correctly say "Reset to default output directory"

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
