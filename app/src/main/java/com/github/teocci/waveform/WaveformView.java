package com.github.teocci.waveform;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.Rect;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import com.github.teocci.audiotrackwaveform.R;
import com.github.teocci.waveform.utils.AudioUtils;
import com.github.teocci.waveform.utils.SamplingUtils;
import com.github.teocci.waveform.utils.TextUtils;

import java.util.LinkedList;

/**
 * Created by teocci.
 *
 * @author teocci@yandex.com on 2017/Apr/05
 */
public class WaveformView extends View
{
    public static final int MODE_RECORDING = 1;
    public static final int MODE_PLAYBACK = 2;

    private static final int HISTORY_SIZE = 6;

    private TextPaint textPaint;
    private Paint strokePaint, fillPaint, markerPaint;

    // Used in draw
    private int brightness;
    private Rect drawRect;

    private int width, height;
    private float xStep, centerY;
    private int mode, audioLength, markerPosition, sampleRate, channels;
    private short[] sampleRateList;
    private LinkedList<float[]> historicalData;
    private Picture cachedWaveform;
    private Bitmap cachedWaveformBitmap;
    private int colorDelta = 255 / (HISTORY_SIZE + 1);
    private boolean showTextAxis = true;

    public WaveformView(Context context)
    {
        super(context);
        init(context, null, 0);
    }

    public WaveformView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public WaveformView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    private void init(Context context, AttributeSet attrs, int defStyle)
    {
        // Load attributes
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.WaveformView, defStyle, 0);

        mode = a.getInt(R.styleable.WaveformView_mode, MODE_PLAYBACK);

        float strokeThickness = a.getFloat(R.styleable.WaveformView_waveformStrokeThickness, 1f);
        int mStrokeColor = a.getColor(R.styleable.WaveformView_waveformColor,
                ContextCompat.getColor(context, R.color.default_waveform));
        int mFillColor = a.getColor(R.styleable.WaveformView_waveformFillColor,
                ContextCompat.getColor(context, R.color.default_waveformFill));
        int mMarkerColor = a.getColor(R.styleable.WaveformView_playbackIndicatorColor,
                ContextCompat.getColor(context, R.color.default_playback_indicator));
        int mTextColor = a.getColor(R.styleable.WaveformView_timecodeColor,
                ContextCompat.getColor(context, R.color.default_timecode));

        a.recycle();

        textPaint = new TextPaint();
        textPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(mTextColor);
        textPaint.setTextSize(TextUtils.getFontSize(getContext(),
                android.R.attr.textAppearanceSmall));

        strokePaint = new Paint();
        strokePaint.setColor(mStrokeColor);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(strokeThickness);
        strokePaint.setAntiAlias(true);

        fillPaint = new Paint();
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAntiAlias(true);
        fillPaint.setColor(mFillColor);

        markerPaint = new Paint();
        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeWidth(0);
        markerPaint.setAntiAlias(true);
        markerPaint.setColor(mMarkerColor);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh)
    {
        super.onSizeChanged(w, h, oldw, oldh);

        width = getMeasuredWidth();
        height = getMeasuredHeight();
        xStep = width / (audioLength * 1.0f);
        centerY = height / 2f;
        drawRect = new Rect(0, 0, width, height);

        if (historicalData != null) {
            historicalData.clear();
        }
        if (mode == MODE_PLAYBACK) {
            createPlaybackWaveform();
        }
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);

        LinkedList<float[]> temp = historicalData;
        if (mode == MODE_RECORDING && temp != null) {
            brightness = colorDelta;
            for (float[] p : temp) {
                strokePaint.setAlpha(brightness);
                canvas.drawLines(p, strokePaint);
                brightness += colorDelta;
            }
        } else if (mode == MODE_PLAYBACK) {
            if (cachedWaveform != null) {
                canvas.drawPicture(cachedWaveform);
            } else if (cachedWaveformBitmap != null) {
                canvas.drawBitmap(cachedWaveformBitmap, null, drawRect, null);
            }
            if (markerPosition > -1 && markerPosition < audioLength)
                canvas.drawLine(xStep * markerPosition, 0, xStep * markerPosition, height, markerPaint);
        }
    }

    public int getMode()
    {
        return mode;
    }

    public void setMode(int mMode)
    {
        mMode = mMode;
    }

    public short[] getSamples()
    {
        return sampleRateList;
    }

    public void setSamples(short[] samples)
    {
        sampleRateList = samples;
        calculateAudioLength();
        onSamplesChanged();
    }

    public int getMarkerPosition()
    {
        return markerPosition;
    }

    public void setMarkerPosition(int markerPosition)
    {
        this.markerPosition = markerPosition;
        postInvalidate();
    }

    public int getAudioLength()
    {
        return audioLength;
    }

    public int getSampleRate()
    {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate)
    {
        this.sampleRate = sampleRate;
        calculateAudioLength();
    }

    public int getChannels()
    {
        return channels;
    }

    public void setChannels(int channels)
    {
        this.channels = channels;
        calculateAudioLength();
    }

    public boolean showTextAxis()
    {
        return showTextAxis;
    }

    public void setShowTextAxis(boolean showTextAxis)
    {
        this.showTextAxis = showTextAxis;
    }

    private void calculateAudioLength()
    {
        if (sampleRateList == null || sampleRate == 0 || channels == 0)
            return;

        audioLength = AudioUtils.calculateAudioLength(sampleRateList.length, sampleRate, channels);
    }

    private void onSamplesChanged()
    {
        if (mode == MODE_RECORDING) {
            if (historicalData == null)
                historicalData = new LinkedList<>();
            LinkedList<float[]> temp = new LinkedList<>(historicalData);

            // For efficiency, we are reusing the array of points.
            float[] waveformPoints;
            if (temp.size() == HISTORY_SIZE) {
                waveformPoints = temp.removeFirst();
            } else {
                waveformPoints = new float[width * 4];
            }

            drawRecordingWaveform(sampleRateList, waveformPoints);
            temp.addLast(waveformPoints);
            historicalData = temp;
            postInvalidate();
        } else if (mode == MODE_PLAYBACK) {
            markerPosition = -1;
            xStep = width / (audioLength * 1.0f);
            createPlaybackWaveform();
        }
    }

    void drawRecordingWaveform(short[] buffer, float[] waveformPoints)
    {
        float lastX = -1;
        float lastY = -1;
        int pointIndex = 0;
        float max = Short.MAX_VALUE;

        // For efficiency, we don't draw all of the samples in the buffer, but only the ones
        // that align with pixel boundaries.
        for (int x = 0; x < width; x++) {
            int index = (int) (((x * 1.0f) / width) * buffer.length);
            short sample = buffer[index];
            float y = centerY - ((sample / max) * centerY);

            if (lastX != -1) {
                waveformPoints[pointIndex++] = lastX;
                waveformPoints[pointIndex++] = lastY;
                waveformPoints[pointIndex++] = x;
                waveformPoints[pointIndex++] = y;
            }

            lastX = x;
            lastY = y;
        }
    }

    Path drawPlaybackWaveform(int width, int height, short[] buffer)
    {
        Path waveformPath = new Path();
        float centerY = height / 2f;
        float max = Short.MAX_VALUE;

        short[][] extremes = SamplingUtils.getExtremes(buffer, width);


        waveformPath.moveTo(0, centerY);

        // draw maximums
        for (int x = 0; x < width; x++) {
            short sample = extremes[x][0];
            float y = centerY - ((sample / max) * centerY);
            waveformPath.lineTo(x, y);
        }

        // draw minimums
        for (int x = width - 1; x >= 0; x--) {
            short sample = extremes[x][1];
            float y = centerY - ((sample / max) * centerY);
            waveformPath.lineTo(x, y);
        }

        waveformPath.close();

        return waveformPath;
    }

    private void createPlaybackWaveform()
    {
        if (width <= 0 || height <= 0 || sampleRateList == null)
            return;

        Canvas cacheCanvas;
        if (Build.VERSION.SDK_INT >= 23 && isHardwareAccelerated()) {
            cachedWaveform = new Picture();
            cacheCanvas = cachedWaveform.beginRecording(width, height);
        } else {
            cachedWaveformBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            cacheCanvas = new Canvas(cachedWaveformBitmap);
        }

        Path mWaveform = drawPlaybackWaveform(width, height, sampleRateList);
        cacheCanvas.drawPath(mWaveform, fillPaint);
        cacheCanvas.drawPath(mWaveform, strokePaint);
        drawAxis(cacheCanvas, width);

        if (cachedWaveform != null)
            cachedWaveform.endRecording();
    }

    private void drawAxis(Canvas canvas, int width)
    {
        if (!showTextAxis) return;
        int seconds = audioLength / 1000;
        float xStep = width / (audioLength / 1000f);
        float textHeight = textPaint.getTextSize();
        float textWidth = textPaint.measureText("10.00");
        int secondStep = (int) (textWidth * seconds * 2) / width;
        secondStep = Math.max(secondStep, 1);
        for (float i = 0; i <= seconds; i += secondStep) {
            canvas.drawText(String.format("%.2f", i), i * xStep, textHeight, textPaint);
        }
    }
}
