/*
 * ============================================================================
 * Copyright (C) 2017 Kaltura Inc.
 * 
 * Licensed under the AGPLv3 license, unless a different license for a
 * particular library is specified in the applicable library path.
 * 
 * You may obtain a copy of the License at
 * https://www.gnu.org/licenses/agpl-3.0.html
 * ============================================================================
 */

package com.kaltura.playkit.ads;

/**
 * Created by Noam Tamim @ Kaltura on 14/12/2016.
 */
public interface AdController {
    void skipAd();
    void openLearnMore();
    void openCompanionAdLearnMore();
    void screenOrientationChanged(boolean isFullScreen);
    void volumeKeySilent(boolean isMute);
    long getAdCurrentPosition();
    long getAdDuration();
}
