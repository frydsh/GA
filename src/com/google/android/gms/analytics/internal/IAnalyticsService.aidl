package com.google.android.gms.analytics.internal;

import com.google.android.gms.analytics.internal.Command;

interface IAnalyticsService {
    void sendHit(in Map wireParams, in long hitTimeInMilliseconds, in String path, in List<Command> commands);
    void clearHits();
}