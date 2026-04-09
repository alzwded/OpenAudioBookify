This project started a looong time ago -- but on PC :-)

For someone who works 8h+ per day sitting at a PC, using my eye balls to read in the evening was something that was becoming less and less appearling as time marched on. I remember back then, Google Translate (or was it Bing Translate?) were using off-the-shelf TTS for "speak translation", so they had a tiny OSS notice on the page saying something to the tune of "powered by eSpeak" and I thought -- hang on -- I could probably use that for my own nefarious purposes.

So a batch file and a csh script were born -- take a giant txt file, run it through espeak, and boom, audiobook.

There was some manual preprocessing by way of various tools (ultimately settling on Calibre) to convert the whatever books to plaintext to pipe into eSpeak. And there was some automated post processing to turn the audio into something that isn't 2GB large (ffmpeg, then avconv, then ffmpeg again).

Eventually I ended up using my phone more and more, and, because of TalkBack or ebook reader builtin TTS, I thought I could do away with this eSpeak processing. Well, until I started getting annoyed TTS would stop when my screen turns off, and at some point the option to "keep screen on forever until I turn it off" option becamse the "10 minutes and screen is turned off" option (I think it might have something to do with Sreen Lock and biometrics, IDK).

So I came back to my Calibre -> eSpeak -> ffmpeg ways. But I wanted to turn this into an Android app. So I started by changing eSpeak into a C library, compile with NDK, figure out how to run a long running background service on Android, and ultimately gave up because it was too much of a faff to move from "working POC in an emulator" to something I would actually rely on.

Time marches on, and I thought "hang on, why even bother with eSpeak or ffmpeg, can't Android already do TTS and media encoding already?" And that ressurected the project, no with 0 dependencies other than the base OS platform. Well, the OS platform itself has dependencies on _a_ TTS engine and _a_ media encoder, but that's nothing I ship.

I enjoy using my own app. It's more conveninent to tell my phone "process this ebook and store it in my podcast catcher's audiobooks directory, I'll listen to it later" than to mess around with my PC, transfering files around, remembering to check up on it when it finishes, copying over the mp3, etc.

I hope others find a use for this app. Or maybe it'll give them some idea to do something with eSpeak by browsing my other repos. Or is it speech-dispatcherd these days on Linux? I know Windows has a DotNet thing these days that's quite similar to Android's, I have a Vim script/key binding to "read aloud". In any case, TTS is fun. Especially when, after a few decades, you can keep up with a tiny tinny robot talking at 300 words per minute -- though I am still perplexed by low sight people who manage to push the word rate even higher than that.

Perhaps all this project will do is remind people that TTS exists and that some people rely on accessibility technologies to get about their day. Or perhaps it will remind people books do exist. Who knows. I'll be happy is this project's mere existence made a change, however minimal, in someone else's life.
