OpenAudioBookify
================

OpenAudioBookify is an open source Android app which allows you to select one or more plaintext, markdown, HTML or EPUB documents and generate an M4A audio file of the machine spoken ebook, which you can then load into your favourite podcast player.

OpenAudioBookify uses one of the system Text-to-Speech engines available on the device with whatever voices are available to that engine. The app likewise uses the system's audio encoding capabilities to produce the final audiobook. And it relies on the Storage Access Framework for input and output.

OpenAudioBookify itself does not depend on any cloud, online, or otherwise any form of subscription service -- it does all it does locally.

Usage
-----

You probably first want to hit the gear wheel in the top right and tweak your TTS voice (engine, voice, rate, pitch). The TTS engines shown as available are whatever is installed on your system, and accessible to regular apps.

You can also tweak the compressed output audio bitrate from the default 48kbps (which is about the lower limit before voices start to sound like underwater snakes).

![settings screen](./screenshots/Settings--readme.jpg)

At this point, you can select some books, select an output folder, and click "Start Processing".

![main activity, idle state](./screenshots/Main--readme.jpg)

You can return to the app periodically to see what it's doing, or stop it.

![main activity, rendering state](./screenshots/Main-Processing--readme.jpg)

You will get a notification when it's done.

Note that the very first time you click "Start Processing" you'll get a prompt to allow notifications. I encourage you to permit notifications, as it allows the rendering to happen in the background while you go do something else. Modern Android OSes are very eager to silently shut down apps which aren't in the foreground, and speaking through an entire book can take many, many minutes. If you find it stopping without ever finishing, you might want to check your Android settings for "Battery Optimization" and set OpenAudioBookify to "Unrestricted".

License
-------

This project is licensed under a permissive [BSD License](./LICENSE).

This project lives at https://github.com/alzwded/OpenAudioBookify
