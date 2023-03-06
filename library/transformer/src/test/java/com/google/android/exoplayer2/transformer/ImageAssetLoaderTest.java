/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.robolectric.RobolectricUtil.runLooperUntil;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowSystemClock;

/** Unit tests for {@link ExoPlayerAssetLoader}. */
@RunWith(AndroidJUnit4.class)
public class ImageAssetLoaderTest {

  @Test
  public void imageAssetLoader_callsListenerCallbacksInRightOrder() throws Exception {
    HandlerThread assetLoaderThread = new HandlerThread("AssetLoaderThread");
    assetLoaderThread.start();
    Looper assetLoaderLooper = assetLoaderThread.getLooper();
    AtomicReference<Exception> exceptionRef = new AtomicReference<>();
    AtomicBoolean isOutputFormatSet = new AtomicBoolean();
    AssetLoader.Listener listener =
        new AssetLoader.Listener() {

          private volatile boolean isDurationSet;
          private volatile boolean isTrackCountSet;
          private volatile boolean isTrackAdded;

          @Override
          public void onDurationUs(long durationUs) {
            sleep();
            isDurationSet = true;
          }

          @Override
          public void onTrackCount(int trackCount) {
            // Sleep to increase the chances of the test failing.
            sleep();
            isTrackCountSet = true;
          }

          @Override
          public boolean onTrackAdded(
              Format inputFormat,
              @AssetLoader.SupportedOutputTypes int supportedOutputTypes,
              long streamStartPositionUs,
              long streamOffsetUs) {
            if (!isTrackCountSet) {
              exceptionRef.set(
                  new IllegalStateException("onTrackAdded() called before onTrackCount()"));
            }
            sleep();
            isTrackAdded = true;
            return false;
          }

          @Override
          public SampleConsumer onOutputFormat(Format format) {

            if (!isDurationSet) {
              exceptionRef.set(
                  new IllegalStateException("onTrackAdded() called before onDurationUs()"));
            } else if (!isTrackAdded) {
              exceptionRef.set(
                  new IllegalStateException("onOutputFormat() called before onTrackAdded()"));
            }
            isOutputFormatSet.set(true);
            return new FakeSampleConsumer();
          }

          @Override
          public void onError(ExportException e) {
            exceptionRef.set(e);
          }

          void sleep() {
            try {
              Thread.sleep(10);
            } catch (InterruptedException e) {
              exceptionRef.set(e);
            }
          }
        };
    AssetLoader assetLoader = getAssetLoader(assetLoaderLooper, listener);

    new Handler(assetLoaderLooper).post(assetLoader::start);
    runLooperUntil(
        Looper.myLooper(),
        () -> {
          ShadowSystemClock.advanceBy(Duration.ofMillis(10));
          return isOutputFormatSet.get() || exceptionRef.get() != null;
        });

    assertThat(exceptionRef.get()).isNull();
  }

  private static AssetLoader getAssetLoader(Looper looper, AssetLoader.Listener listener) {
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(
                MediaItem.fromUri("asset:///media/bitmap/input_images/media3test.png"))
            .setDurationUs(1_000_000)
            .setFrameRate(30)
            .build();
    return new ImageAssetLoader.Factory(ApplicationProvider.getApplicationContext())
        .createAssetLoader(editedMediaItem, looper, listener);
  }

  private static final class FakeSampleConsumer implements SampleConsumer {

    @Nullable
    @Override
    public DecoderInputBuffer getInputBuffer() {
      return null;
    }

    @Override
    public void queueInputBuffer() {}

    @Override
    public void queueInputBitmap(Bitmap inputBitmap, long durationUs, int frameRate) {}

    @Override
    public void signalEndOfVideoInput() {}
  }
}
