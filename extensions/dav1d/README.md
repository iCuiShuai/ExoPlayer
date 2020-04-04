# ExoPlayer DAV1D extension #

The DAV1D extension provides `LibDAV1DVideoRenderer`, which uses libdav1d native
library to decode AV1 videos.

## License note ##

Please note that whilst the code in this repository is licensed under
[Apache 2.0][], using this extension also requires building and including one or
more external libraries as described below. These are licensed separately.

[Apache 2.0]: https://github.com/google/ExoPlayer/blob/release-v2/LICENSE

## Build instructions (Linux, macOS) ##

To use this extension you need to clone the ExoPlayer repository and depend on
its modules locally. Instructions for doing this can be found in ExoPlayer's
[top level README][].


```

* [Install CMake][].

Having followed these steps, gradle will build the extension automatically when
run on the command line or via Android Studio, using [CMake][] and [Ninja][]
to configure and build libdav1d and the extension's [JNI wrapper library][].

[top level README]: https://github.com/google/ExoPlayer/blob/release-v2/README.md
[Install CMake]: https://developer.android.com/studio/projects/install-ndk
[CMake]: https://cmake.org/
[Ninja]: https://ninja-build.org
[JNI wrapper library]: https://github.com/google/ExoPlayer/blob/release-v2/extensions/dav1d/src/main/jni/dav1d_jni.cc

## Build instructions (Windows) ##

We do not provide support for building this extension on Windows, however it
should be possible to follow the Linux instructions in [Windows PowerShell][].

[Windows PowerShell]: https://docs.microsoft.com/en-us/powershell/scripting/getting-started/getting-started-with-windows-powershell

## Using the extension ##

Once you've followed the instructions above to check out, build and depend on
the extension, the next step is to tell ExoPlayer to use `Libdav1dVideoRenderer`.
How you do this depends on which player API you're using:

* If you're passing a `DefaultRenderersFactory` to `SimpleExoPlayer.Builder`,
  you can enable using the extension by setting the `extensionRendererMode`
  parameter of the `DefaultRenderersFactory` constructor to
  `EXTENSION_RENDERER_MODE_ON`. This will use `Libdav1dVideoRenderer` for
  playback if `MediaCodecVideoRenderer` doesn't support decoding the input AV1
  stream. Pass `EXTENSION_RENDERER_MODE_PREFER` to give `Libdav1dVideoRenderer`
  priority over `MediaCodecVideoRenderer`.
* If you've subclassed `DefaultRenderersFactory`, add a `Libvgav1VideoRenderer`
  to the output list in `buildVideoRenderers`. ExoPlayer will use the first
  `Renderer` in the list that supports the input media format.
* If you've implemented your own `RenderersFactory`, return a
  `Libdav1dVideoRenderer` instance from `createRenderers`. ExoPlayer will use the
  first `Renderer` in the returned array that supports the input media format.
* If you're using `ExoPlayer.Builder`, pass a `Libdav1dVideoRenderer` in the
  array of `Renderer`s. ExoPlayer will use the first `Renderer` in the list that
  supports the input media format.

Note: These instructions assume you're using `DefaultTrackSelector`. If you have
a custom track selector the choice of `Renderer` is up to your implementation.
You need to make sure you are passing a `Libdav1dVideoRenderer` to the player and
then you need to implement your own logic to use the renderer for a given track.

## Using the extension in the demo application ##

To try out playback using the extension in the [demo application][], see
[enabling extension decoders][].

[demo application]: https://exoplayer.dev/demo-application.html
[enabling extension decoders]: https://exoplayer.dev/demo-application.html#enabling-extension-decoders

## Rendering options ##

There are two possibilities for rendering the output `Libdav1dVideoRenderer`
gets from the libdav1d decoder:

* GL rendering using GL shader for color space conversion
  * If you are using `SimpleExoPlayer` with `PlayerView`, enable this option by
    setting `surface_type` of `PlayerView` to be
    `video_decoder_gl_surface_view`.
  * Otherwise, enable this option by sending `Libdav1dVideoRenderer` a message
    of type `C.MSG_SET_VIDEO_DECODER_OUTPUT_BUFFER_RENDERER` with an instance of
    `VideoDecoderOutputBufferRenderer` as its object.

* Native rendering using `ANativeWindow`
  * If you are using `SimpleExoPlayer` with `PlayerView`, this option is enabled
    by default.
  * Otherwise, enable this option by sending `Libdav1dVideoRenderer` a message of
    type `C.MSG_SET_SURFACE` with an instance of `SurfaceView` as its object.

Note: Although the default option uses `ANativeWindow`, based on our testing the
GL rendering mode has better performance, so should be preferred

## Links ##

* [Javadoc][]: Classes matching `com.google.android.exoplayer2.ext.dav1d.*`
  belong to this module.

[Javadoc]: https://exoplayer.dev/doc/reference/index.html
