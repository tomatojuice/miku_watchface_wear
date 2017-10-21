package tomatojuice.sakura.ne.jp.mywatchfaces;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.graphics.Palette;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;


public class VocaloWatchFaces extends CanvasWatchFaceService {

    /* Update rate in milliseconds for interactive mode. We update once a second to advance the second hand. */
    private static long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /* Handler message id for updating the time periodically in interactive mode. */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<VocaloWatchFaces.Engine> mWeakReference;

        public EngineHandler(VocaloWatchFaces.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            VocaloWatchFaces.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final float HOUR_STROKE_WIDTH = 5f;
        private static final float MINUTE_STROKE_WIDTH = 3f;
        private static final float SECOND_TICK_STROKE_WIDTH = 2f;

        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 4f;

        private static final int SHADOW_RADIUS = 2;
        private final Rect mPeekCardBounds = new Rect();
        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;

        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;
        private float mCenterX;
        private float mCenterY;
        private float mSecondHandLength;
        private float sMinuteHandLength;
        private float sHourHandLength;
        private float sScale;
        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private int mWatchHandColor;
        private int mWatchHandHighlightColor;
        private int mWatchHandShadowColor;
        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mSecondPaint;
        private Paint mTickAndCirclePaint;
        private Paint mBackgroundPaint;
        private Bitmap mBackgroundBitmap;
        private Bitmap mGrayBackgroundBitmap;
        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private boolean isRound;
        private Config mConfig;

        /**
         * 画面がRectかRoundかの判定メソッドだが、onCreate、onSurfaceViewの後に呼ばれるので、
         * ** ここで通常のバックグラウンドとAmbientモード時の画像だけ再描画
         **/
        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            isRound = insets.isRound();
            Log.i("onApplyWindowInsets", "onApplyWindowInsetsが呼ばれました");
            setmBackgroundBitmap();
            initGrayBackgroundBitmap();

        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(VocaloWatchFaces.this)
//                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
//                    .setAmbientPeekMode(WatchFaceStyle.AMBIENT_PEEK_MODE_HIDDEN) // Ambientモード時に通知カードを非表示で、プライバシーの配慮と電力節約！
//                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
//                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            mConfig = new Config(VocaloWatchFaces.this, null);
            mConfig.connect();

            creatMiku(); // ここで秒針の色などを設定している

            mCalendar = Calendar.getInstance();
        }  // onCreate

        /* Create the Miku Watch Face */
        private void creatMiku() {
            Log.i("creatMiku", "creatMikuが呼ばれました");
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);
            if (isRound) {
                if (mConfig.isAmbient()) {
                    mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.miku_analog_circle_ambient);
                } else {
                    mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.miku_analog_circle);
                }
            } else {
                if (mConfig.isAmbient()) {
                    mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.miku_analog_rect_ambient);
                } else {
                    mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.miku_analog_rect);
                }
            }

            /* Set defaults for colors
             * 色を直接指定する場合に利用 */
//           mWatchHandColor = Color.WHITE;
//           mWatchHandHighlightColor = Color.RED;
//           mWatchHandShadowColor = Color.BLACK;

            /* Extract colors from background image to improve watchface style.
             * Paletteは自動で色を決めてくれるライブラリなので、指定した色が反映されない場合がある */
            Palette.from(mBackgroundBitmap).generate(new Palette.PaletteAsyncListener() {

                // ここで針の色を指定
                @Override
                public void onGenerated(Palette palette) {

                    if (palette != null) {
                        Log.i("onGenerated", "onGeneratedの「!= null」が呼ばれました");
                        mWatchHandHighlightColor = palette.getVibrantColor(Color.RED);
//                       mWatchHandHighlightColor = Color.RED; // 指定した色が反映されない場合、Paletteは使わず直接指定する
                        mWatchHandColor = palette.getLightVibrantColor(Color.WHITE);
                        mWatchHandShadowColor = palette.getDarkMutedColor(Color.BLACK);
                        updateWatchHandStyle();
                    } else {
                        Log.i("onGenerated", "onGeneratedの「null」が呼ばれました");
                    }
                } // onGenerated
            });

            // 時針
            mHourPaint = new Paint();
            mHourPaint.setColor(mWatchHandColor);
            mHourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            // 分針
            mMinutePaint = new Paint();
            mMinutePaint.setColor(mWatchHandColor);
            mMinutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);
            mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            // 秒針
            mSecondPaint = new Paint();
            mSecondPaint.setColor(mWatchHandHighlightColor);
            mSecondPaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.ROUND);
            mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

//           mTickAndCirclePaint = new Paint();
//           mTickAndCirclePaint.setColor(mWatchHandColor);
//           mTickAndCirclePaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
//           mTickAndCirclePaint.setAntiAlias(true);
//           mTickAndCirclePaint.setStyle(Paint.Style.STROKE);
//           mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

        } // createMiku

        /* set the BackgroundBitmap */
        private void setmBackgroundBitmap() {
//            if(isRound){
//                mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.miku_analog_circle_ambient);
//            }else{
//                mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.miku_analog_rect_ambient);
//            }

            if (isRound) {
                if (mConfig.isAmbient()) {
                    mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.miku_analog_circle_ambient);
                } else {
                    mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.miku_analog_circle);
                }
            } else {
                if (mConfig.isAmbient()) {
                    mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.miku_analog_rect_ambient);
                } else {
                    mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.miku_analog_rect);
                }
            }

            /* Scale loaded background image (more efficient) if surface dimensions change. */
            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * sScale),
                    (int) (mBackgroundBitmap.getHeight() * sScale), true);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            mConfig.disconnect();
            mConfig = null;
            Log.i("onDestroy", "onDestroyが呼ばれました");
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            Log.i("onPropertiesChanged", "onPropertiesChangedが呼ばれました");
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            Log.i("onTimeTick", "onTimeTickが呼ばれました");
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
//            mAmbient = inAmbientMode;

            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    // 必要な処理
                    Log.i("onAmbientModeChanged", "mLowBitAmbientが呼ばれました");
                }
                if (mAmbient) {
                    mConfig.connect();
                    Log.i("onAmbientModeChanged", "mAmbientが呼ばれました");
                } else {
                    mConfig.disconnect();
                    Log.i("onAmbientModeChanged", "mAmbientのelseが呼ばれました");
                }
                invalidate();
            }

            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle() {
            Log.i("updateWatchHandStyle", "updateWatchHandStyleが呼ばれました");
            if (mAmbient) { // When in Ambient mode
                mHourPaint.setColor(Color.WHITE);
                mMinutePaint.setColor(Color.WHITE);
                mSecondPaint.setColor(Color.WHITE);
//                mTickAndCirclePaint.setColor(Color.WHITE);

                mHourPaint.setAntiAlias(false);
                mMinutePaint.setAntiAlias(false);
                mSecondPaint.setAntiAlias(false);
//                mTickAndCirclePaint.setAntiAlias(false);

                mHourPaint.clearShadowLayer();
                mMinutePaint.clearShadowLayer();
                mSecondPaint.clearShadowLayer();
//                mTickAndCirclePaint.clearShadowLayer();

            } else { // When it is not in Ambient mode
                mHourPaint.setColor(mWatchHandColor);
                mMinutePaint.setColor(mWatchHandColor);
                mSecondPaint.setColor(mWatchHandHighlightColor);
//                mTickAndCirclePaint.setColor(mWatchHandColor);

                mHourPaint.setAntiAlias(true);
                mMinutePaint.setAntiAlias(true);
                mSecondPaint.setAntiAlias(true);
//                mTickAndCirclePaint.setAntiAlias(true);

                mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
//                mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {

            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);
            Log.i("onInterruption～", "onInterruptionFilterChangedが呼ばれました");
            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            Log.i("onSurfaceChanged", "onSurfaceChangedが呼ばれました");
            /* Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion. */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            /* Calculate lengths of different hands based on watch screen size. */
            mSecondHandLength = (float) (mCenterX * 0.875);
            sMinuteHandLength = (float) (mCenterX * 0.75);
            sHourHandLength = (float) (mCenterX * 0.5);


            /* Scale loaded background image (more efficient) if surface dimensions change. */
            sScale = ((float) width) / (float) mBackgroundBitmap.getWidth();

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * sScale),
                    (int) (mBackgroundBitmap.getHeight() * sScale), true);

            /* Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don't want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it. */
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap();
            }
        }

        private void initGrayBackgroundBitmap() {
            Log.i("initGrayBackground", "initGrayBackgroundBitmapが呼ばれました");
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                    mBackgroundBitmap.getWidth(),
                    mBackgroundBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mGrayBackgroundBitmap);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, grayPaint);
        }

        /* Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture. */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = VocaloWatchFaces.this.getResources();

            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.

//                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
//                            .show();
//                    mConfig.setIsSmooth(!mConfig.isSmooth());
                    mConfig.setIsAmbient(!mConfig.isAmbient());
                    setmBackgroundBitmap();
                    initGrayBackgroundBitmap();

                    /* 変更されたデザインと合う指針の色を自動で決める
                    * Paletteは自動で色を決めてくれるライブラリなので、指定した色が反映されない場合がある */
                    Palette.from(mBackgroundBitmap).generate(new Palette.PaletteAsyncListener() {

                        // ここで針の色を自動で指定
                        @Override
                        public void onGenerated(Palette palette) {

                            if (palette != null) {
                                Log.i("onGenerated", "onGeneratedの「!= null」が呼ばれました");
                                mWatchHandHighlightColor = palette.getVibrantColor(Color.RED);
                                mWatchHandColor = palette.getLightVibrantColor(Color.WHITE);
                                mWatchHandShadowColor = palette.getDarkMutedColor(Color.BLACK);
                                updateWatchHandStyle();
                            } else {
                                Log.i("onGenerated", "onGeneratedの「null」が呼ばれました");
                            }
                        } // onGenerated
                    });

                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0, 0, mBackgroundPaint);
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
            }

            /* Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo. */
            float innerTickRadius = mCenterX - 10;
            float outerTickRadius = mCenterX;
            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
//                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
//                        mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint);

            }

            /* These calculations reflect the rotation in degrees per unit of time, e.g.,360 / 60 = 6 and 360 / 12 = 30. */
            final float seconds = (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);

            /* booleanで秒針をスムーズもしくは1秒で描画するかどうか決める */
            if (mConfig.isSmooth()) {
                INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1) / 30;
            } else {
                INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
            }

            final float secondsRotation = seconds * 6f;
            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;
            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            /* Save the canvas state before we can begin to rotate it. */
            canvas.save();

            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sHourHandLength,
                    mHourPaint);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sMinuteHandLength,
                    mMinutePaint);

            /* Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute. */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawLine(
                        mCenterX,
                        mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                        mCenterX,
                        mCenterY - mSecondHandLength,
                        mSecondPaint);

            }
//            canvas.drawCircle(
//                    mCenterX,
//                    mCenterY,
//                    CENTER_GAP_AND_CIRCLE_RADIUS,
//                    mTickAndCirclePaint);

            /* Restore the canvas' original orientation. */
            canvas.restore();

            /* Draw rectangle behind peek card in ambient mode to improve readability. */
            if (mAmbient) {
                canvas.drawRect(mPeekCardBounds, mBackgroundPaint);
            }
        }

        /* 非表示になった時に設定の確認を停止、再表示された時に設定の確認を再開 */
        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            Log.i("onVisibilityChanged", "onVisibilityChangedが呼ばれました");

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                mConfig.connect();
                invalidate();
            } else {
                unregisterReceiver();
                mConfig.disconnect();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

//        @Override
//        public void onPeekCardPositionUpdate(Rect rect) {
//            super.onPeekCardPositionUpdate(rect);
//            mPeekCardBounds.set(rect);
//        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            VocaloWatchFaces.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            VocaloWatchFaces.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /* Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face. */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /* Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should only run in active mode. */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }

        /* Handle updating the time periodically in interactive mode. */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
