# SoundCrowd

[![android](https://github.com/soundcrowd/soundcrowd/actions/workflows/android.yml/badge.svg)](https://github.com/soundcrowd/soundcrowd/actions/workflows/android.yml)
[![GitHub release](https://img.shields.io/github/release/soundcrowd/soundcrowd.svg)](https://github.com/soundcrowd/soundcrowd/releases)
[![GitHub](https://img.shields.io/github/license/soundcrowd/soundcrowd.svg)](LICENSE)

SoundCrowd is a free, open-source and lightweight music player for Android in modern material design, specialized for playing long music tracks (DJ mixes, live sets, audio books).

It features the generation of waveforms that visualize your music tracks during playback and can be used for precise seeking through gestures.

You can create cue points at your favorite positions in your music tracks. With these markers in the waveform, you can remember them and easily jump back to them.

The app contains build-in plugin modules to support various online streaming services, namely SoundCloud, Spotify, Tidal, and YouTube.

Want to know the track id of a specific part in a mix? SoundCrowd comes with build-in audio tagging support by using [SongRec](https://github.com/marin-m/SongRec), an open-source Shazam client implementation, and records the internal audio so you don't need to play the music loud!

## Download

[<img src="https://f-droid.org/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">](https://soundcrowd.github.io/fdroid/repo)

or download the apk directly from the [GitHub releases](https://github.com/soundcrowd/soundcrowd/releases) page.

## Online Streaming Services

The following plugins for online streaming services are currently available:

| Service    | Download                                                                                                                                                                    |
|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SoundCloud | [![GitHub release](https://img.shields.io/github/release/soundcrowd/soundcrowd-plugin-soundcloud.svg)](https://github.com/soundcrowd/soundcrowd-plugin-soundcloud/releases) |
| YouTube    | [![GitHub release](https://img.shields.io/github/release/soundcrowd/soundcrowd-plugin-youtube.svg)](https://github.com/soundcrowd/soundcrowd-plugin-youtube/releases)       |
| Spotify    | [![GitHub release](https://img.shields.io/github/release/soundcrowd/soundcrowd-plugin-spotify.svg)](https://github.com/soundcrowd/soundcrowd-plugin-spotify/releases)       |
| Beatport   | [![GitHub release](https://img.shields.io/github/release/soundcrowd/soundcrowd-plugin-beatport.svg)](https://github.com/soundcrowd/soundcrowd-plugin-beatport/releases)     |
| Tidal      | [![GitHub release](https://img.shields.io/github/release/soundcrowd/soundcrowd-plugin-tidal.svg)](https://github.com/soundcrowd/soundcrowd-plugin-tidal/releases)           |

## Screenshots

<img src="https://soundcrowd.github.io/images/screenshot-browser.png" width="300"/> <img src="https://soundcrowd.github.io/images/screenshot-player.png" width="300"/>

## Building

    $ git clone --recursive https://github.com/soundcrowd/soundcrowd
    $ cd soundcrowd
    $ ./gradlew assembleDebug

Install via ADB:

    $ adb install app/build/outputs/apk/debug/app-debug.apk

## License

SoundCrowd and its modules are licensed under GPLv3.

## Dependencies

  - [Glide](https://github.com/bumptech/glide) - BSD, part MIT and Apache 2.0
  - [AlphabetIndex-Fast-Scroll-RecyclerView](https://github.com/myinnos/AlphabetIndex-Fast-Scroll-RecyclerView) - Apache 2.0
  - [AppIntro](https://github.com/AppIntro/AppIntro) - Apache 2.0
