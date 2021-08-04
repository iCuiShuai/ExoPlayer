package com.mxplay.interactivemedia.internal.api;

import androidx.annotation.Nullable;

import com.mxplay.interactivemedia.api.FriendlyObstruction;
import java.util.List;

public interface FriendlyObstructionProvider {
    @Nullable List<FriendlyObstruction> getFriendlyObstructions();
}
