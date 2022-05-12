package com.google.android.exoplayer2.util;

import android.content.Context;
import android.graphics.Point;

import com.google.android.exoplayer2.C;

public class TrackSelectorUtil {

    private static final float FRACTION_TO_CONSIDER_FULLSCREEN = 0.98f;

    public static int indexOf(int[] tracks, int indexInTrackGroup) {
        for (int i = 0; i < tracks.length; i++) {
            if (tracks[i] == indexInTrackGroup) {
                return i;
            }
        }
        return C.INDEX_UNSET;
    }

    public static boolean needFilter(Context context,int width, int height) {
        Point viewportSize = Util.getCurrentDisplayModeSize(context);
        return shouldViewportFilteredTrackIndices(width, height, viewportSize.x, viewportSize.y, true);
    }

    private static boolean shouldViewportFilteredTrackIndices(
            int width, int height, int viewportWidth, int viewportHeight, boolean orientationMayChange) {
        if (viewportWidth == Integer.MAX_VALUE || viewportHeight == Integer.MAX_VALUE) {
            // Viewport dimensions not set. Return the full set of indices.
            return false;
        }

        if (width > 0 && height > 0) {
            Point maxVideoSizeInViewport =
                    getMaxVideoSizeInViewport(
                            orientationMayChange, viewportWidth, viewportHeight, width, height);
            if (width >= (int) (maxVideoSizeInViewport.x * FRACTION_TO_CONSIDER_FULLSCREEN)
                    && height >= (int) (maxVideoSizeInViewport.y * FRACTION_TO_CONSIDER_FULLSCREEN)) {
                return true;
            }
        }

        return false;
    }


    /**
     * Given viewport dimensions and video dimensions, computes the maximum size of the video as it
     * will be rendered to fit inside of the viewport.
     */
    private static Point getMaxVideoSizeInViewport(
            boolean orientationMayChange,
            int viewportWidth,
            int viewportHeight,
            int videoWidth,
            int videoHeight) {
        if (orientationMayChange && (videoWidth > videoHeight) != (viewportWidth > viewportHeight)) {
            // Rotation is allowed, and the video will be larger in the rotated viewport.
            int tempViewportWidth = viewportWidth;
            viewportWidth = viewportHeight;
            viewportHeight = tempViewportWidth;
        }

        if (videoWidth * viewportHeight >= videoHeight * viewportWidth) {
            // Horizontal letter-boxing along top and bottom.
            return new Point(viewportWidth, Util.ceilDivide(viewportWidth * videoHeight, videoWidth));
        } else {
            // Vertical letter-boxing along edges.
            return new Point(Util.ceilDivide(viewportHeight * videoWidth, videoHeight), viewportHeight);
        }
    }

}
