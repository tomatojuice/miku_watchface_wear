package tomatojuice.sakura.ne.jp.mywatchfaces;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;

public class Config implements DataApi.DataListener,GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    // PATHとKYEはそれぞれ用意しないとダメ、競合してしまう。
    private static final String PATH_SMOOTH = "/config_smooth";
    private static final String PATH_AMBIENT = "/config_ambient";
    private static final String KEY_SMOOTH_MOVE = "SMOOTH_MOVE";
    private static final String KEY_AMBIENT = "AMBIENT_MODE";
    private GoogleApiClient mGoogleApiClient;
    private boolean mIsSmooth;
    private boolean mIsAmbient;

    public Config(WeakReference<OnConfigChangedListener> mConfigChangedListenerWeakReference) {
        this.mConfigChangedListenerWeakReference = mConfigChangedListenerWeakReference;
    }

    /* コンストラクタ */
    public Config(Context context, OnConfigChangedListener reference) {
        if (reference == null) {
            mConfigChangedListenerWeakReference = null;
            } else {
            mConfigChangedListenerWeakReference = new WeakReference<>(reference);
            }
        mGoogleApiClient = new GoogleApiClient.Builder(context)
        .addConnectionCallbacks(this)
        .addOnConnectionFailedListener(this)
        .addApi(Wearable.API)
        .build();
        }

    public boolean isSmooth() {
        return mIsSmooth;
        }

    public boolean isAmbient(){
        return mIsAmbient;
    }

    public void setIsSmooth(boolean isSmooth) {
        mIsSmooth = isSmooth;
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(PATH_SMOOTH);
        DataMap dataMap = putDataMapRequest.getDataMap();
        dataMap.putBoolean(KEY_SMOOTH_MOVE, mIsSmooth);
        Wearable.DataApi.putDataItem(mGoogleApiClient, putDataMapRequest.asPutDataRequest());
        }

    public void setIsAmbient(boolean isAmbient) {
        mIsAmbient = isAmbient;
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(PATH_AMBIENT);
        DataMap dataMap = putDataMapRequest.getDataMap();
        dataMap.putBoolean(KEY_AMBIENT, mIsAmbient);
        Wearable.DataApi.putDataItem(mGoogleApiClient, putDataMapRequest.asPutDataRequest());
    }

    /* onConnectedでは設定画面のSMOOTHを反映。Ambientは不要 */
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Wearable.DataApi.getDataItems(mGoogleApiClient)
                .setResultCallback(new ResultCallback<DataItemBuffer>() {
                    @Override
                    public void onResult(@NonNull DataItemBuffer dataItems) {
                        for (DataItem dataItem : dataItems) {

                            if (dataItem.getUri().getPath().equals(PATH_SMOOTH)) {
                                Log.i("onConnected","onConnectedが呼ばれました");
                                DataMap dataMap = DataMap.fromByteArray(dataItem.getData());

                                mIsSmooth = dataMap.getBoolean(KEY_SMOOTH_MOVE, true);

                                if (mConfigChangedListenerWeakReference != null) {
                                    OnConfigChangedListener listener = mConfigChangedListenerWeakReference.get();
                                    if (listener != null) {
                                        listener.onConfigChanged(Config.this);
                                        } // if
                                    } // if
                                } // if

                            } // for
                        dataItems.release();
                    }
                });

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    /* ここでもDataが変わった時の処理を行うが、SMOOTHとAMBIENTどちらが変更されても呼び出されるので、PATH_SMOOTHのみ反映させていている
    * なぜならConfig画面はSMOOTHしか設定しないようにしているし、AMBIENTモードの反映はTapイベントで行っているので、ここでは行わない。 */
    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        for (DataEvent event : dataEventBuffer) {

            if (event.getType() == DataEvent.TYPE_CHANGED) {
                DataItem item = event.getDataItem();
                if (item.getUri().getPath().equals(PATH_SMOOTH)) {
                    Log.i("onDataChanged","onDataChangedが呼ばれました");
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();

                    mIsSmooth = dataMap.getBoolean(KEY_SMOOTH_MOVE);

                    if (mConfigChangedListenerWeakReference != null) {
                        OnConfigChangedListener listener = mConfigChangedListenerWeakReference.get();
                        if (listener != null) {
                            listener.onConfigChanged(Config.this);
                            } // if
                        } // if
                    } // if
                } // if
            } // for

    }

    private final WeakReference<OnConfigChangedListener> mConfigChangedListenerWeakReference;
    public interface OnConfigChangedListener {
        void onConfigChanged(Config config);
        }

    /* 外部からデータの同期を開始するメソッド */
    public void connect() {
        mGoogleApiClient.connect();
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        }

    /* 外部からデータの同期を終了するメソッド */
    public void disconnect() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
            }
        }
}
