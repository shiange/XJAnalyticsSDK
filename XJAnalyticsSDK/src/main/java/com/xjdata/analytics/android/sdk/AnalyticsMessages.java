/*
 * Created by wangzhuozhou on 2015/08/01.
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

package com.xjdata.analytics.android.sdk;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.widget.Toast;

import com.xjdata.analytics.android.sdk.data.DbAdapter;
import com.xjdata.analytics.android.sdk.data.DbParams;
import com.xjdata.analytics.android.sdk.data.XJJSONDataUtils;
import com.xjdata.analytics.android.sdk.exceptions.ConnectErrorException;
import com.xjdata.analytics.android.sdk.exceptions.DebugModeException;
import com.xjdata.analytics.android.sdk.exceptions.InvalidDataException;
import com.xjdata.analytics.android.sdk.exceptions.ResponseErrorException;
import com.xjdata.analytics.android.sdk.network.HttpUtils;
import com.xjdata.analytics.android.sdk.util.Base64Coder;
import com.xjdata.analytics.android.sdk.util.JSONUtils;
import com.xjdata.analytics.android.sdk.util.NetworkUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;

import static com.xjdata.analytics.android.sdk.AbstractXJDataAPI.API_BURY_WXATTRIBUTE;
import static com.xjdata.analytics.android.sdk.AbstractXJDataAPI.API_BURY_WXEVENT;
import static com.xjdata.analytics.android.sdk.AbstractXJDataAPI.API_BURY_WXSHARE;
import static com.xjdata.analytics.android.sdk.AbstractXJDataAPI.API_BURY_WXUSER;
import static com.xjdata.analytics.android.sdk.AbstractXJDataAPI.INIT_SUCCESS;
import static com.xjdata.analytics.android.sdk.AbstractXJDataAPI.TIME_LONG_EVENT;
import static com.xjdata.analytics.android.sdk.data.InterfaceTypes.API_BURY_WXATTRIBUTE_TYPE;
import static com.xjdata.analytics.android.sdk.data.InterfaceTypes.API_BURY_WXEVENT_TYPE;
import static com.xjdata.analytics.android.sdk.data.InterfaceTypes.API_BURY_WXSHARE_TYPE;
import static com.xjdata.analytics.android.sdk.data.InterfaceTypes.API_BURY_WXUSER_TYPE;
import static com.xjdata.analytics.android.sdk.util.Base64Coder.CHARSET_UTF8;


/**
 * Manage communication of events with the internal database and the SensorsData servers.
 * This class straddles the thread boundary between user threads and
 * a logical SensorsData thread.
 */
class AnalyticsMessages {
    private static final String TAG = "XJ.AnalyticsMessages";
    private static final int FLUSH_QUEUE = 3;
    private static final int DELETE_ALL = 4;
    private static final Map<Context, AnalyticsMessages> S_INSTANCES = new HashMap<>();
    private final Worker mWorker;
    private final Context mContext;
    private final DbAdapter mDbAdapter;
    private XJDataAPI mSensorsDataAPI;

    /**
     * 不要直接调用，通过 getInstance 方法获取实例
     */
    private AnalyticsMessages(final Context context, XJDataAPI sensorsDataAPI) {
        mContext = context;
        mDbAdapter = DbAdapter.getInstance();
        mWorker = new Worker();
        mSensorsDataAPI = sensorsDataAPI;
    }

    /**
     * 获取 AnalyticsMessages 对象
     *
     * @param messageContext Context
     */
    public static AnalyticsMessages getInstance(final Context messageContext, final XJDataAPI sensorsDataAPI) {
        synchronized (S_INSTANCES) {
            final Context appContext = messageContext.getApplicationContext();
            final AnalyticsMessages ret;
            if (!S_INSTANCES.containsKey(appContext)) {
                ret = new AnalyticsMessages(appContext, sensorsDataAPI);
                S_INSTANCES.put(appContext, ret);
            } else {
                ret = S_INSTANCES.get(appContext);
            }
            return ret;
        }
    }

    private static byte[] slurp(final InputStream inputStream)
            throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[8192];

        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }

    void enqueueEventMessage(final String type,  JSONObject eventJson) {
        try {

            synchronized (mDbAdapter) {
                final Message m = Message.obtain();
                m.what = FLUSH_QUEUE;
                String event = eventJson.getString("event");
                JSONObject object = XJJSONDataUtils.convertJsonOb(eventJson);
                if (event.equals(API_BURY_WXATTRIBUTE)){
                    object.put("type",API_BURY_WXATTRIBUTE_TYPE);//初始化接口
                    m.obj=object;
                    mWorker.runMessage(m);
                    return;
                }else  if (event.equals(API_BURY_WXSHARE)){//分享者信息接口
                    object.put("type",API_BURY_WXSHARE_TYPE);
                    m.obj=object;
                    mWorker.runMessage(m);
                    return;
                }else if (event.equals(API_BURY_WXUSER)){//上报用户信息接口
                    object.put("type",API_BURY_WXUSER_TYPE);
                    m.obj=object;
                    mWorker.runMessage(m);
                    return;
                }
                JSONObject properties = eventJson.getJSONObject("properties");
                 if (properties.has("$eventId")) {
                   if (properties.getString("$eventId").equals(TIME_LONG_EVENT)) {
                       object.put("type",API_BURY_WXEVENT_TYPE);
                       sendData(object);
                       return;
                       }
                      }
                int ret = mDbAdapter.addJSON(eventJson);
                if (ret < 0) {
                    String error = "Failed to enqueue the event: " + eventJson;
                    if (mSensorsDataAPI.isDebugMode()) {
                        throw new DebugModeException(error);
                    } else {
                        XJLog.i(TAG, error);
                    }
                }



                if (mSensorsDataAPI.isDebugMode() || ret ==
                        DbParams.DB_OUT_OF_MEMORY_ERROR) {
                    mWorker.runMessage(m);
                } else {
                    // track_signup 立即发送
                    if (type.equals("track_signup") || ret > mSensorsDataAPI
                            .getFlushBulkSize()) {
                        mWorker.runMessage(m);
                    } else {
                        final int interval = mSensorsDataAPI.getFlushInterval();
                        mWorker.runMessageOnce(m, interval);
                    }
                }
            }
        } catch (Exception e) {
            XJLog.i(TAG, "enqueueEventMessage error:" + e);
        }
    }

    void flush() {
        final Message m = Message.obtain();
        m.what = FLUSH_QUEUE;

        mWorker.runMessage(m);
    }

    void flush(long timeDelayMills) {
        final Message m = Message.obtain();
        m.what = FLUSH_QUEUE;

        mWorker.runMessageOnce(m, timeDelayMills);
    }

    void deleteAll() {
        final Message m = Message.obtain();
        m.what = DELETE_ALL;

        mWorker.runMessage(m);
    }

    private void sendData(JSONObject json) {
        try {
            if (!mSensorsDataAPI.isNetworkRequestEnable()) {
                XJLog.i(TAG, "NetworkRequest 已关闭，不发送数据！");
                return;
            }
            if (json!=null){//立即上报
                if (json.getInt("type")==API_BURY_WXATTRIBUTE_TYPE){//初始化
                    sendHttpRequest(mSensorsDataAPI.getServerUrl()+ HttpUtils.getUrl(API_BURY_WXATTRIBUTE_TYPE),
                            "不加密", json.toString(), false,true);
                }else if (json.getInt("type")==API_BURY_WXSHARE_TYPE||
                        json.getInt("type")==API_BURY_WXUSER_TYPE ||
                        json.getInt("type")==API_BURY_WXEVENT_TYPE ){
                    sendHttpRequest(mSensorsDataAPI.getServerUrl()+ HttpUtils.getUrl(json.getInt("type")),
                            "不加密", json.toString(), false,false);
                }
                return;
            }else {
                if (!INIT_SUCCESS){
                    return;
                }
            }
            if (TextUtils.isEmpty(mSensorsDataAPI.getServerUrl())) {
                XJLog.i(TAG, "Server url is null or empty.");
                return;
            }

            //无网络
            if (!NetworkUtils.isNetworkAvailable(mContext)) {
                return;
            }

            //不符合同步数据的网络策略
            String networkType = NetworkUtils.networkType(mContext);
            if (!NetworkUtils.isShouldFlush(networkType, mSensorsDataAPI.getFlushNetworkPolicy())) {
                XJLog.i(TAG, String.format("您当前网络为 %s，无法发送数据，请确认您的网络发送策略！", networkType));
                return;
            }

            // 如果开启多进程上报
            if (mSensorsDataAPI.isMultiProcessFlushData()) {
                // 已经有进程在上报
                if (DbAdapter.getInstance().isSubProcessFlushing()) {
                    return;
                }
                DbAdapter.getInstance().commitSubProcessFlushState(true);
            } else if (!XJDataAPI.mIsMainProcess) {//不是主进程
                return;
            }
        } catch (Exception e) {
            XJLog.printStackTrace(e);
            return;
        }
        int count = 100;
        Toast toast = null;
        while (count > 0) {
            boolean deleteEvents = true;
            String[] eventsData;
            synchronized (mDbAdapter) {
//                if (mSensorsDataAPI.isDebugMode()) {
//                    /* debug 模式下服务器只允许接收 1 条数据 */
//                    eventsData = mDbAdapter.generateDataString(DbParams.TABLE_EVENTS, 1);
//                } else {
                    eventsData = mDbAdapter.generateDataString(DbParams.TABLE_EVENTS, 50);
//                }
            }

            if (eventsData == null) {
                DbAdapter.getInstance().commitSubProcessFlushState(false);
                return;
            }

            final String lastId = eventsData[0];
            final String rawMessage = eventsData[1];
            final String gzip = eventsData[2];
            String errorMessage = null;

            try {


                if (!TextUtils.isEmpty(rawMessage)) {
                    try {
                        JSONArray array=new JSONArray(rawMessage);
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject object = array.getJSONObject(i);
                            String event = object.getString("event");
//                            if (API_BURY_WXATTRIBUTE.equals(event)){
//                                sendHttpRequest(mSensorsDataAPI.getServerUrl()+ HttpUtils.getUrl(API_BURY_WXATTRIBUTE_TYPE),
//                                        gzip, object.toString(), false,true);
//                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//                                    array.remove(i);
//                                }
//                                break;
//                            }
                            if (event.equals("$AppViewScreen")){
                                object.put("type",API_BURY_WXEVENT_TYPE);
                                object= XJJSONDataUtils.convertJsonOb(object);
                            }
                            if (object.has("properties")){
                                JSONObject properties = object.getJSONObject("properties");
                                if (properties.has("$type")){
                                    if (properties.getInt("$type")==API_BURY_WXEVENT_TYPE){//自定义事件
                                        object.put("type",API_BURY_WXEVENT_TYPE);
                                        object= XJJSONDataUtils.convertJsonOb(object);
                                    }
                                }
                                if (properties.has("$eventId")) {
                                    if (properties.getString("$eventId").equals(TIME_LONG_EVENT)) {
                                        object.put("type",API_BURY_WXEVENT_TYPE);
                                        object= XJJSONDataUtils.convertJsonOb(object);
                                    }
                                }
                            }
                            if (object.has("type")){
                                if (object.has("event")){
                                    object.remove("event");
                                }
                                sendHttpRequest(mSensorsDataAPI.getServerUrl()+ HttpUtils.getUrl(object.getInt("type")),//单个上传
                                        gzip, object.toString(), false,false);
                            }

                        }
//                        sendHttpRequest(mSensorsDataAPI.getServerUrl()+ HttpUtils.getWxattributeUrl(),  //上传数组
//                                gzip, rawMessage, false,false);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }
            } catch (ConnectErrorException e) {
                deleteEvents = false;
                errorMessage = "Connection error: " + e.getMessage();
            }
//            catch (InvalidDataException e) {
//                errorMessage = "Invalid data: " + e.getMessage();
//            }
            catch (ResponseErrorException e) {
                deleteEvents = isDeleteEventsByCode(e.getHttpCode());
                errorMessage = "ResponseErrorException: " + e.getMessage();
            } catch (Exception e) {
                deleteEvents = false;
                errorMessage = "Exception: " + e.getMessage();
            } finally {
                boolean isDebugMode = mSensorsDataAPI.isDebugMode();
                if (!TextUtils.isEmpty(errorMessage)) {
                    if (isDebugMode || XJLog.isLogEnabled()) {
                        XJLog.i(TAG, errorMessage);
                        if (isDebugMode && XJDataAPI.SHOW_DEBUG_INFO_VIEW) {
                            try {
                                /*
                                 * 问题：https://www.jianshu.com/p/1445e330114b
                                 * 目前没有比较好的解决方案，暂时规避，只对开启 debug 模式下有影响
                                 */
                                if (Build.VERSION.SDK_INT != 25) {
                                    if (toast != null) {
                                        toast.cancel();
                                    }
                                    toast = Toast.makeText(mContext, errorMessage, Toast.LENGTH_SHORT);
                                    toast.show();
                                }
                            } catch (Exception e) {
                                XJLog.printStackTrace(e);
                            }
                        }
                    }
                }
                if (deleteEvents || isDebugMode) {
                    count = mDbAdapter.cleanupEvents(lastId);
                    XJLog.i(TAG, String.format(Locale.CHINA, "Events flushed. [left = %d]", count));
                } else {
                    count = 0;
                }

            }
        }
        if (mSensorsDataAPI.isMultiProcessFlushData()) {
            DbAdapter.getInstance().commitSubProcessFlushState(false);
        }
    }

    private void sendHttpRequest(String path,String gzip, String rawMessage, boolean isRedirects,boolean isInit) throws ConnectErrorException, ResponseErrorException {


        HttpURLConnection connection = null;
        InputStream in = null;
        OutputStream out = null;
        BufferedOutputStream bout = null;
        try {
            final URL url = new URL(path);
            connection = (HttpURLConnection) url.openConnection();
            if (connection == null) {
                XJLog.i(TAG, String.format("can not connect %s, it shouldn't happen", url.toString()), null);
                return;
            }
            XJConfigOptions configOptions = XJDataAPI.getConfigOptions();
            if (configOptions != null && configOptions.mSSLSocketFactory != null
                    && connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(configOptions.mSSLSocketFactory);
            }
            connection.setInstanceFollowRedirects(false);
            if (mSensorsDataAPI.getDebugMode() == XJDataAPI.DebugMode.DEBUG_ONLY) {
                connection.addRequestProperty("Dry-Run", "true");
            }

            connection.setRequestProperty("Cookie", mSensorsDataAPI.getCookie(false));
            connection.setRequestProperty("Content-Type", "application/json");

            Uri.Builder builder = new Uri.Builder();
            String data = rawMessage;
            if (DbParams.GZIP_DATA_EVENT.equals(gzip)) {
                try {
                    data = encodeData(rawMessage);
                } catch (InvalidDataException e) {
                    e.printStackTrace();
                }
            }
            //先校验crc
            if (!TextUtils.isEmpty(data)) {
                builder.appendQueryParameter("crc", String.valueOf(data.hashCode()));
            }
            builder.appendQueryParameter("gzip", gzip);
            builder.appendQueryParameter("data_list", data);
//            String query = builder.build().getEncodedQuery();
            String query=rawMessage;
            if (TextUtils.isEmpty(query)) {
                return;
            }

            connection.setFixedLengthStreamingMode(query.getBytes(CHARSET_UTF8).length);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            out = connection.getOutputStream();
            bout = new BufferedOutputStream(out);
            bout.write(query.getBytes(CHARSET_UTF8));
            bout.flush();

            int responseCode = connection.getResponseCode();
            XJLog.i(TAG, "responseCode: " + responseCode);
            if (!isRedirects && NetworkUtils.needRedirects(responseCode)) {
                String location = NetworkUtils.getLocation(connection, path);
                if (!TextUtils.isEmpty(location)) {
                    closeStream(bout, out, null, connection);
                    sendHttpRequest(location, gzip, rawMessage, true,isInit);
                    return;
                }
            }
            try {
                in = connection.getInputStream();
            } catch (FileNotFoundException e) {
                in = connection.getErrorStream();
            }
            byte[] responseBody = slurp(in);
            in.close();
            in = null;

            String response = new String(responseBody, CHARSET_UTF8);
            if (isInit){
                try {
                    JSONObject jsonObject=new JSONObject(response);
                    if (jsonObject.getInt("code")==1){
                        INIT_SUCCESS=true;
                        XJDataAPI.sharedInstance().apiBuryWxevent(TIME_LONG_EVENT);//时长(初始化后每五秒上报一次)
                        flush(1000);
                    }
                }catch (Exception e){
                    XJLog.i(TAG, "send data message err:"+e.toString() );
                }
            }

            if (XJLog.isLogEnabled()) {
                String jsonMessage = JSONUtils.formatJson(rawMessage);
                // 状态码 200 - 300 间都认为正确
                if (responseCode >= HttpURLConnection.HTTP_OK &&
                        responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                    XJLog.i(TAG, "valid message: \n" + jsonMessage);
                    XJLog.i(TAG, "send data message: \n" + response+"\n"+path);
                } else {
                    XJLog.i(TAG, "invalid message: \n" + jsonMessage);
                    XJLog.i(TAG, String.format(Locale.CHINA, "ret_code: %d", responseCode));
                    XJLog.i(TAG, String.format(Locale.CHINA, "ret_content: %s", response));
                }
            }
            if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                // 校验错误
                throw new ResponseErrorException(String.format("flush failure with response '%s', the response code is '%d'",
                        response, responseCode), responseCode);
            }
        } catch (IOException e) {
            throw new ConnectErrorException(e);
        } finally {
            closeStream(bout, out, in, connection);
        }
    }

    /**
     * 在服务器正常返回状态码的情况下，目前只有 (>= 500 && < 600) || 404 || 403 才不删数据
     *
     * @param httpCode 状态码
     * @return true: 删除数据，false: 不删数据
     */
    private boolean isDeleteEventsByCode(int httpCode) {
        boolean shouldDelete = true;
        if (httpCode == HttpURLConnection.HTTP_NOT_FOUND ||
                httpCode == HttpURLConnection.HTTP_FORBIDDEN ||
                (httpCode >= HttpURLConnection.HTTP_INTERNAL_ERROR && httpCode < 600)) {
            shouldDelete = false;
        }
        return shouldDelete;
    }

    private void closeStream(BufferedOutputStream bout, OutputStream out, InputStream in, HttpURLConnection connection) {
        if (null != bout) {
            try {
                bout.close();
            } catch (Exception e) {
                XJLog.i(TAG, e.getMessage());
            }
        }

        if (null != out) {
            try {
                out.close();
            } catch (Exception e) {
                XJLog.i(TAG, e.getMessage());
            }
        }

        if (null != in) {
            try {
                in.close();
            } catch (Exception e) {
                XJLog.i(TAG, e.getMessage());
            }
        }

        if (null != connection) {
            try {
                connection.disconnect();
            } catch (Exception e) {
                XJLog.i(TAG, e.getMessage());
            }
        }
    }

    private String encodeData(final String rawMessage) throws InvalidDataException {
        GZIPOutputStream gos = null;
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream(rawMessage.getBytes(CHARSET_UTF8).length);
            gos = new GZIPOutputStream(os);
            gos.write(rawMessage.getBytes(CHARSET_UTF8));
            gos.close();
            byte[] compressed = os.toByteArray();
            os.close();
            return new String(Base64Coder.encode(compressed));
        } catch (IOException exception) {
            // 格式错误，直接将数据删除
            throw new InvalidDataException(exception);
        } finally {
            if (gos != null) {
                try {
                    gos.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    // Worker will manage the (at most single) IO thread associated with
    // this AnalyticsMessages instance.
    // XXX: Worker class is unnecessary, should be just a subclass of HandlerThread
    private class Worker {

        private final Object mHandlerLock = new Object();
        private Handler mHandler;

        Worker() {
            final HandlerThread thread =
                    new HandlerThread("com.sensorsdata.analytics.android.sdk.AnalyticsMessages.Worker",
                            Thread.MIN_PRIORITY);
            thread.start();
            mHandler = new AnalyticsMessageHandler(thread.getLooper());
        }

        void runMessage(Message msg) {
            synchronized (mHandlerLock) {
                // We died under suspicious circumstances. Don't try to send any more events.
                if (mHandler == null) {
                    XJLog.i(TAG, "Dead worker dropping a message: " + msg.what);
                } else {
                    mHandler.sendMessage(msg);
                }
            }
        }

        void runMessageOnce(Message msg, long delay) {
            synchronized (mHandlerLock) {
                // We died under suspicious circumstances. Don't try to send any more events.
                if (mHandler == null) {
                    XJLog.i(TAG, "Dead worker dropping a message: " + msg.what);
                } else {
                    if (!mHandler.hasMessages(msg.what)) {
                        mHandler.sendMessageDelayed(msg, delay);
                    }
                }
            }
        }

        private class AnalyticsMessageHandler extends Handler {

            AnalyticsMessageHandler(Looper looper) {
                super(looper);
            }

            @Override
            public void handleMessage(Message msg) {
                try {
                    if (msg.what == FLUSH_QUEUE) {
                        if (msg.obj!=null){
                            sendData((JSONObject) msg.obj);
                        }else {
                            sendData(null);
                        }

                    } else if (msg.what == DELETE_ALL) {
                        try {
                            mDbAdapter.deleteAllEvents();
                        } catch (Exception e) {
                            XJLog.printStackTrace(e);
                        }
                    } else {
                        XJLog.i(TAG, "Unexpected message received by SensorsData worker: " + msg);
                    }
                } catch (final RuntimeException e) {
                    XJLog.i(TAG, "Worker threw an unhandled exception", e);
                }
            }
        }
    }
}