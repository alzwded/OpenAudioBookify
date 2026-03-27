OpenAudioBookify
================

OpenAudioBookify is an open source app which allows you to select one or more plaintext, markdown, HTML or EPUB documents and generate an M4A audio file of the machine spoken ebook, which you can then load into your favourite podcast player.

OpenAudioBookify uses one of the system Text-to-Speech engines available on the device with whatever voices are available to that engine. The app likewise uses the system's audio encoding capabilities to produce the final audiobook. And it relies on the Storage Access Framework for input and output.

Usage
-----

To use it, tweak your TTS (voice, language, rate, pitch) and output bitrate settings (the default should be good enough), select a book, select a destination folder, and hit Start Processing, which will process your book in the background. You can return to the app, or check the notification, for status, or to cancel the conversion.

Eventually, you get a notification that the queue has been processed, and you can find your new audiobook in the selected output folder.

All on your phone, without having to switch to a PC :-)

License
-------

This project is licensed under a permissive [BSD License](./LICENSE).

This project lives at https://github.com/alzwded/AudioBookify