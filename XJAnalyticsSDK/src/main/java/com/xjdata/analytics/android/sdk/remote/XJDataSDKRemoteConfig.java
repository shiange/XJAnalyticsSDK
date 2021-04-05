/*
 * Created by yuejianzhong on 2020/11/04.
 * Copyright 2015－2021 Sensors Data Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xjdata.analytics.android.sdk.remote;

import com.xjdata.analytics.android.sdk.XJAnalyticsAutoTrackEventType;
import com.xjdata.analytics.android.sdk.XJLog;

import org.json.JSONArray;
import org.json.JSONObject;

public class XJDataSDKRemoteConfig {
    static final int REMOTE_EVENT_TYPE_NO_USE = -1;
    /**
     * 在线控制版本，老的版本号
     */
    private String oldVersion;
    /**
     * 是否关闭 debug 模式
     */
    private boolean disableDebugMode;
    /**
     * 是否关闭 AutoTrack
     */
    private int autoTrackMode;
    /**
     * 是否关闭 SDK
     */
    private boolean disableSDK;

    /**
     * RSA 公钥
     */
    private String publicKey;

    /**
     * 公钥版本名称
     */
    private int pkv;

    /**
     * 禁用事件名列表
     */
    private JSONArray eventBlacklist;

    /**
     * 在线控制版本
     */
    private String newVersion;

    /**
     * 是否立即生效，0 表示下次生效，1 表示本次生效
     */
    private int effectMode;

    private int mAutoTrackEventType;

    public XJDataSDKRemoteConfig() {
        this.disableDebugMode = false;
        this.disableSDK = false;
        this.autoTrackMode = REMOTE_EVENT_TYPE_NO_USE;
    }

    String getOldVersion() {
        return oldVersion;
    }

    public void setOldVersion(String oldVersion) {
        this.oldVersion = oldVersion;
    }

    boolean isDisableDebugMode() {
        return disableDebugMode;
    }

    public void setDisableDebugMode(boolean disableDebugMode) {
        this.disableDebugMode = disableDebugMode;
    }

    boolean isDisableSDK() {
        return disableSDK;
    }

    public void setDisableSDK(boolean disableSDK) {
        this.disableSDK = disableSDK;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public int getPkv() {
        return pkv;
    }

    public void setPkv(int pkv) {
        this.pkv = pkv;
    }

    int getAutoTrackMode() {
        return autoTrackMode;
    }

    public void setAutoTrackMode(int autoTrackMode) {
        this.autoTrackMode = autoTrackMode;

        if (this.autoTrackMode == REMOTE_EVENT_TYPE_NO_USE || this.autoTrackMode == 0) {
            mAutoTrackEventType = 0;
            return;
        }

        if ((this.autoTrackMode & XJAnalyticsAutoTrackEventType.APP_START) == XJAnalyticsAutoTrackEventType.APP_START) {
            this.mAutoTrackEventType |= XJAnalyticsAutoTrackEventType.APP_START;
        }

        if ((this.autoTrackMode & XJAnalyticsAutoTrackEventType.APP_END) == XJAnalyticsAutoTrackEventType.APP_END) {
            this.mAutoTrackEventType |= XJAnalyticsAutoTrackEventType.APP_END;
        }

        if ((this.autoTrackMode & XJAnalyticsAutoTrackEventType.APP_CLICK) == XJAnalyticsAutoTrackEventType.APP_CLICK) {
            this.mAutoTrackEventType |= XJAnalyticsAutoTrackEventType.APP_CLICK;
        }

        if ((this.autoTrackMode & XJAnalyticsAutoTrackEventType.APP_VIEW_SCREEN) == XJAnalyticsAutoTrackEventType.APP_VIEW_SCREEN) {
            this.mAutoTrackEventType |= XJAnalyticsAutoTrackEventType.APP_VIEW_SCREEN;
        }
    }

    int getAutoTrackEventType() {
        return mAutoTrackEventType;
    }

    boolean isAutoTrackEventTypeIgnored(int eventType) {
        if (autoTrackMode == REMOTE_EVENT_TYPE_NO_USE) {
            return false;
        }

        if (autoTrackMode == 0) {
            return true;
        }

        return (mAutoTrackEventType | eventType) != mAutoTrackEventType;
    }

    JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("v", oldVersion);
            JSONObject configObject = new JSONObject();
            configObject.put("disableDebugMode", disableDebugMode);
            configObject.put("autoTrackMode", autoTrackMode);
            configObject.put("disableSDK", disableSDK);
            configObject.put("event_blacklist", eventBlacklist);
            configObject.put("nv", newVersion);
            configObject.put("effect_mode", effectMode);
            jsonObject.put("configs", configObject);
        } catch (Exception e) {
            XJLog.printStackTrace(e);
        }
        return jsonObject;
    }

    @Override
    public String toString() {
        return "{ v=" + oldVersion + ", disableDebugMode=" + disableDebugMode + ", disableSDK=" + disableSDK + ", autoTrackMode=" + autoTrackMode +
                ", event_blacklist=" + eventBlacklist + ", nv=" + newVersion + ", effect_mode=" + effectMode + "}";
    }

    public JSONArray getEventBlacklist() {
        return eventBlacklist;
    }

    public void setEventBlacklist(JSONArray eventArray) {
        this.eventBlacklist = eventArray;
    }

    public String getNewVersion() {
        return newVersion;
    }

    public void setNewVersion(String newVersion) {
        this.newVersion = newVersion;
    }

    public void setEffectMode(int effectMode) {
        this.effectMode = effectMode;
    }

    public int getEffectMode() {
        return effectMode;
    }
}
