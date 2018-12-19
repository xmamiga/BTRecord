package com.xmamiga.btrecord;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import com.xxhander.BaseHandlerOperate;


public class RemoteControlReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            android.util.Log.e("xmamiga", "[EVENT]");
            if (KeyEvent.KEYCODE_MEDIA_PLAY == event.getKeyCode()) {
                // Handle key press.

                android.util.Log.e("xmamiga", "[Event-->] KeyEvent.KEYCODE_MEDIA_PLAY");

                BaseHandlerOperate.getBaseHandlerOperate().putMessageKey(MainActivity.class,
                        0, null);
            }
        }
    }
}
