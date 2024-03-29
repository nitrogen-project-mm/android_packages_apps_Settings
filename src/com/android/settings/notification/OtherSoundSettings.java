/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.settings.notification;

import static com.android.settings.notification.SettingPref.TYPE_GLOBAL;
import static com.android.settings.notification.SettingPref.TYPE_SYSTEM;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.preference.ListPreference;
import android.provider.SearchIndexableResource;
import android.provider.Settings.Global;
import android.provider.Settings.System;
import android.telephony.TelephonyManager;

import com.android.internal.logging.MetricsLogger;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OtherSoundSettings extends SettingsPreferenceFragment implements Indexable, OnPreferenceChangeListener {
    private static final String TAG = "OtherSoundSettings";

    private static final int DEFAULT_ON = 1;

    private static final int EMERGENCY_TONE_SILENT = 0;
    private static final int EMERGENCY_TONE_ALERT = 1;
    private static final int EMERGENCY_TONE_VIBRATE = 2;
    private static final int DEFAULT_EMERGENCY_TONE = EMERGENCY_TONE_SILENT;

    private static final int DOCK_AUDIO_MEDIA_DISABLED = 0;
    private static final int DOCK_AUDIO_MEDIA_ENABLED = 1;
    private static final int DEFAULT_DOCK_AUDIO_MEDIA = DOCK_AUDIO_MEDIA_DISABLED;

    private static final String KEY_DIAL_PAD_TONES = "dial_pad_tones";
    private static final String KEY_SCREEN_LOCKING_SOUNDS = "screen_locking_sounds";
    private static final String KEY_CHARGING_SOUNDS = "charging_sounds";
    private static final String KEY_CHARGING_VIBRATION = "charging_vibration";
    private static final String KEY_DOCKING_SOUNDS = "docking_sounds";
    private static final String KEY_VOLUME_ADJUST_SOUNDS = "volume_adjust_sounds";
    private static final String KEY_TOUCH_SOUNDS = "touch_sounds";
    private static final String KEY_VIBRATE_ON_TOUCH = "vibrate_on_touch";
    private static final String KEY_DOCK_AUDIO_MEDIA = "dock_audio_media";
    private static final String KEY_EMERGENCY_TONE = "emergency_tone";
    private static final String KEY_CAMERA_SOUNDS = "camera_sounds";
    private static final String PROP_CAMERA_SOUND = "persist.sys.camera-sound";

    private SwitchPreference mCameraSounds;

    private static final SettingPref PREF_DIAL_PAD_TONES = new SettingPref(
            TYPE_SYSTEM, KEY_DIAL_PAD_TONES, System.DTMF_TONE_WHEN_DIALING, DEFAULT_ON) {
        @Override
        public boolean isApplicable(Context context) {
            return Utils.isVoiceCapable(context);
        }
    };

    private static final SettingPref PREF_SCREEN_LOCKING_SOUNDS = new SettingPref(
            TYPE_SYSTEM, KEY_SCREEN_LOCKING_SOUNDS, System.LOCKSCREEN_SOUNDS_ENABLED, DEFAULT_ON);

    private static final SettingPref PREF_CHARGING_SOUNDS = new SettingPref(
            TYPE_GLOBAL, KEY_CHARGING_SOUNDS, Global.CHARGING_SOUNDS_ENABLED, DEFAULT_ON);

    private static final SettingPref PREF_CHARGING_VIBRATION = new SettingPref(
            TYPE_GLOBAL, KEY_CHARGING_VIBRATION, Global.CHARGING_VIBRATION_ENABLED, DEFAULT_ON) {
        @Override
        public boolean isApplicable(Context context) {
            return hasHaptic(context);
        }
    };

    private static final SettingPref PREF_DOCKING_SOUNDS = new SettingPref(
            TYPE_GLOBAL, KEY_DOCKING_SOUNDS, Global.DOCK_SOUNDS_ENABLED, DEFAULT_ON) {
        @Override
        public boolean isApplicable(Context context) {
            return hasDockSettings(context);
        }
    };

    private static final SettingPref PREF_VOLUME_ADJUST_SOUNDS = new SettingPref(
            TYPE_SYSTEM, KEY_VOLUME_ADJUST_SOUNDS, System.VOLUME_ADJUST_SOUNDS_ENABLED,
            DEFAULT_ON) {
    };

    private static final SettingPref PREF_TOUCH_SOUNDS = new SettingPref(
            TYPE_SYSTEM, KEY_TOUCH_SOUNDS, System.SOUND_EFFECTS_ENABLED, DEFAULT_ON) {
        @Override
        protected boolean setSetting(Context context, int value) {
            final AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (value != 0) {
                am.loadSoundEffects();
            }
            return super.setSetting(context, value);
        }
    };

    private static final SettingPref PREF_VIBRATE_ON_TOUCH = new SettingPref(
            TYPE_SYSTEM, KEY_VIBRATE_ON_TOUCH, System.HAPTIC_FEEDBACK_ENABLED, DEFAULT_ON) {
        @Override
        public boolean isApplicable(Context context) {
            return hasHaptic(context);
        }
    };

    @Override
    public void onStop() {
        super.onStop();
        // Unload sound effects to free some memory
        boolean touchSoundsEnabled = android.provider.Settings.System.getInt(getContentResolver(),
                System.SOUND_EFFECTS_ENABLED, DEFAULT_ON) == 1 ? true : false;
        if (!touchSoundsEnabled) {
            AudioManager am = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
            am.unloadSoundEffects();
        }
    }

    private static final SettingPref PREF_DOCK_AUDIO_MEDIA = new SettingPref(
            TYPE_GLOBAL, KEY_DOCK_AUDIO_MEDIA, Global.DOCK_AUDIO_MEDIA_ENABLED,
            DEFAULT_DOCK_AUDIO_MEDIA, DOCK_AUDIO_MEDIA_DISABLED, DOCK_AUDIO_MEDIA_ENABLED) {
        @Override
        public boolean isApplicable(Context context) {
            return hasDockSettings(context);
        }

        @Override
        protected String getCaption(Resources res, int value) {
            switch(value) {
                case DOCK_AUDIO_MEDIA_DISABLED:
                    return res.getString(R.string.dock_audio_media_disabled);
                case DOCK_AUDIO_MEDIA_ENABLED:
                    return res.getString(R.string.dock_audio_media_enabled);
                default:
                    throw new IllegalArgumentException();
            }
        }
    };

    private static final SettingPref PREF_EMERGENCY_TONE = new SettingPref(
            TYPE_GLOBAL, KEY_EMERGENCY_TONE, Global.EMERGENCY_TONE, DEFAULT_EMERGENCY_TONE,
            EMERGENCY_TONE_ALERT, EMERGENCY_TONE_VIBRATE, EMERGENCY_TONE_SILENT) {
        @Override
        public boolean isApplicable(Context context) {
            final int activePhoneType = TelephonyManager.getDefault().getCurrentPhoneType();
            return activePhoneType == TelephonyManager.PHONE_TYPE_CDMA;
        }

        @Override
        protected String getCaption(Resources res, int value) {
            switch(value) {
                case EMERGENCY_TONE_SILENT:
                    return res.getString(R.string.emergency_tone_silent);
                case EMERGENCY_TONE_ALERT:
                    return res.getString(R.string.emergency_tone_alert);
                case EMERGENCY_TONE_VIBRATE:
                    return res.getString(R.string.emergency_tone_vibrate);
                default:
                    throw new IllegalArgumentException();
            }
        }
    };

    private static final SettingPref[] PREFS = {
        PREF_DIAL_PAD_TONES,
        PREF_SCREEN_LOCKING_SOUNDS,
        PREF_CHARGING_SOUNDS,
        PREF_CHARGING_VIBRATION,
        PREF_DOCKING_SOUNDS,
        PREF_VOLUME_ADJUST_SOUNDS,
        PREF_TOUCH_SOUNDS,
        PREF_VIBRATE_ON_TOUCH,
        PREF_DOCK_AUDIO_MEDIA,
        PREF_EMERGENCY_TONE,
    };

    private final SettingsObserver mSettingsObserver = new SettingsObserver();

    private Context mContext;

    @Override
    protected int getMetricsCategory() {
        return MetricsLogger.NOTIFICATION_OTHER_SOUND;
    }

    @Override
    protected int getHelpResource() {
        return R.string.help_uri_other_sounds;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.other_sound_settings);

        mContext = getActivity();

        for (SettingPref pref : PREFS) {
            pref.init(this);
        }

        mCameraSounds = (SwitchPreference) findPreference(KEY_CAMERA_SOUNDS);
        mCameraSounds.setChecked(SystemProperties.getBoolean(PROP_CAMERA_SOUND, true));
        mCameraSounds.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mSettingsObserver.register(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        mSettingsObserver.register(false);
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_CAMERA_SOUNDS.equals(key)) {
            SystemProperties.set(PROP_CAMERA_SOUND, (Boolean) objValue ? "1" : "0");
            return true;
        }
        return false;
    }

    private static boolean hasDockSettings(Context context) {
        return context.getResources().getBoolean(R.bool.has_dock_settings);
    }

    private static boolean hasHaptic(Context context) {
        final Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        return vibrator != null && vibrator.hasVibrator();
    }

    // === Callbacks ===

    private final class SettingsObserver extends ContentObserver {
        public SettingsObserver() {
            super(new Handler());
        }

        public void register(boolean register) {
            final ContentResolver cr = getContentResolver();
            if (register) {
                for (SettingPref pref : PREFS) {
                    cr.registerContentObserver(pref.getUri(), false, this);
                }
            } else {
                cr.unregisterContentObserver(this);
            }
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            for (SettingPref pref : PREFS) {
                if (pref.getUri().equals(uri)) {
                    pref.update(mContext);
                    return;
                }
            }
        }
    }

    // === Indexing ===

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {

        public List<SearchIndexableResource> getXmlResourcesToIndex(
                Context context, boolean enabled) {
            final SearchIndexableResource sir = new SearchIndexableResource(context);
            sir.xmlResId = R.xml.other_sound_settings;
            return Arrays.asList(sir);
        }

        public List<String> getNonIndexableKeys(Context context) {
            final ArrayList<String> rt = new ArrayList<String>();
            for (SettingPref pref : PREFS) {
                if (!pref.isApplicable(context)) {
                    rt.add(pref.getKey());
                }
            }
            return rt;
        }
    };
}
