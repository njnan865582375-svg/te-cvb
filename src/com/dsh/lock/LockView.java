package com.dsh.lock;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;

public final class LockView extends View {

    public interface Listener {
        void onStartTest();

        void onStartLock(int mode);

        void onOpenSettings();

        void onUnlockRequested();
    }

    public static final int LOCK_MODE_ACCESSIBILITY = 0;
    public static final int LOCK_MODE_SCREEN_PIN = 1;

    private static final int MODE_HOME = 0;
    private static final int MODE_PICKER = 1;
    private static final int MODE_LOCK = 2;
    private static final int MODE_SUCCESS = 3;

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8E8E93;
    private static final int DIM = 0xFF3A3A3C;
    private static final int KEY_BG = 0xFF1D1D1F;
    private static final int KEY_PRESSED = 0xFF353538;

    private final Listener listener;
    private final float density;
    private final float scaledDensity;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint homeNamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pickerTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pickerSubtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardDescPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardStatusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint homeIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lockIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pickerIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF homeIconRect = new RectF();
    private final RectF lockIconRect = new RectF();
    private final RectF pickerIconRect = new RectF();
    private final RectF buttonRect = new RectF();
    private final RectF sheetRect = new RectF();
    private final RectF backRect = new RectF();
    private final RectF settingsRect = new RectF();
    private final RectF[] optionRects = new RectF[2];
    private final RectF[] keyRects = new RectF[12];
    private final float[] keyCx = new float[12];
    private final float[] keyCy = new float[12];
    private final Path backspacePath = new Path();
    private final Path checkPath = new Path();

    private Bitmap mark;
    private RadialGradient homeGlow;
    private RadialGradient lockGlow;
    private RadialGradient pickerGlow;

    private int mode = MODE_HOME;
    private int selectedMode = LOCK_MODE_ACCESSIBILITY;
    private boolean accessibilityEnabled;
    private float pickerAnim = 1f;
    private float successAnim;
    private float shakeX;
    private boolean buttonDown;
    private boolean backDown;
    private int pressedOption = -1;
    private boolean error;
    private boolean busy;
    private long errorUntil;
    private int pressedKey = -1;
    private String input = "";

    private int insetTop;
    private int insetBottom;
    private float sheetTop;
    private float keySize;
    private float homeNameY;
    private float lockTitleY;
    private float lockSubtitleY;
    private float dotsY;
    private float pickerTitleY;
    private float pickerSubtitleY;
    private float pickerNoteY;

    private int passwordLength = 6;
    private boolean settingsDown;
    private SoundPool soundPool;
    private int pressSoundId;
    private boolean pressSoundLoaded;

    private ValueAnimator pickerAnimator;
    private ValueAnimator shakeAnimator;
    private ValueAnimator successAnimator;

    private final Runnable verifyRunnable = new Runnable() {
        @Override
        public void run() {
            verifyCode();
        }
    };

    public LockView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        this.density = getResources().getDisplayMetrics().density;
        this.scaledDensity = getResources().getDisplayMetrics().scaledDensity;

        setBackgroundColor(BLACK);
        setFocusable(true);
        setClickable(true);

        Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
        Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        textPaint.setTypeface(regular);
        textPaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTypeface(medium);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        subtitlePaint.setTypeface(regular);
        subtitlePaint.setTextAlign(Paint.Align.CENTER);
        homeNamePaint.setTypeface(regular);
        homeNamePaint.setTextAlign(Paint.Align.CENTER);
        homeNamePaint.setLetterSpacing(0.04f);
        buttonTextPaint.setTypeface(medium);
        buttonTextPaint.setTextAlign(Paint.Align.CENTER);
        buttonTextPaint.setLetterSpacing(0.02f);
        pickerTitlePaint.setTypeface(medium);
        pickerTitlePaint.setTextAlign(Paint.Align.CENTER);
        pickerSubtitlePaint.setTypeface(regular);
        pickerSubtitlePaint.setTextAlign(Paint.Align.CENTER);
        cardTitlePaint.setTypeface(medium);
        cardTitlePaint.setTextAlign(Paint.Align.LEFT);
        cardDescPaint.setTypeface(regular);
        cardDescPaint.setTextAlign(Paint.Align.LEFT);
        cardStatusPaint.setTypeface(regular);
        cardStatusPaint.setTextAlign(Paint.Align.LEFT);
        notePaint.setTypeface(regular);
        notePaint.setTextAlign(Paint.Align.CENTER);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        for (int i = 0; i < keyRects.length; i++) {
            keyRects[i] = new RectF();
        }
        for (int i = 0; i < optionRects.length; i++) {
            optionRects[i] = new RectF();
        }

        mark = BitmapFactory.decodeResource(getResources(), R.drawable.app_mark);
        initPressSound(context);

        setOnApplyWindowInsetsListener(new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                readInsets(insets);
                updateLayout();
                return insets;
            }
        });
        requestApplyInsets();
    }

    private void initPressSound(Context context) {
        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(2)
                    .setAudioAttributes(attributes)
                    .build();
            soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool pool, int sampleId, int status) {
                    pressSoundLoaded = status == 0;
                }
            });
            pressSoundId = soundPool.load(context, R.raw.press, 1);
        } catch (Exception ignored) {
            soundPool = null;
        }
    }

    private void playPressFeedback() {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (soundPool != null && pressSoundLoaded) {
            soundPool.play(pressSoundId, 0.85f, 0.85f, 1, 0, 1f);
        }
    }

    private void readInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            Insets stable = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
            insetTop = Math.max(bars.top, stable.top);
            insetBottom = Math.max(bars.bottom, stable.bottom);
        } else {
            insetTop = Math.max(insets.getSystemWindowInsetTop(), insets.getStableInsetTop());
            insetBottom = Math.max(insets.getSystemWindowInsetBottom(), insets.getStableInsetBottom());
        }
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * scaledDensity;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateLayout();
    }

    private void updateLayout() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float cx = w / 2f;

        float homeIconSize = dp(112);
        float homeCy = h * 0.50f;
        homeIconRect.set(
                cx - homeIconSize / 2f,
                homeCy - homeIconSize / 2f,
                cx + homeIconSize / 2f,
                homeCy + homeIconSize / 2f);

        homeNameY = homeIconRect.bottom + dp(26);
        float bottomSafe = Math.max(insetBottom, dp(16));
        float buttonHeight = dp(48);
        float buttonBottom = h - bottomSafe - dp(16);
        buttonRect.set(dp(20), buttonBottom - buttonHeight, w - dp(20), buttonBottom);

        float settingsTop = Math.max(insetTop, dp(10)) + dp(10);
        settingsRect.set(dp(12), settingsTop, dp(60), settingsTop + dp(46));

        float lockIconSize = dp(80);
        float lockTop = insetTop + dp(50);
        lockIconRect.set(
                cx - lockIconSize / 2f,
                lockTop,
                cx + lockIconSize / 2f,
                lockTop + lockIconSize);
        lockTitleY = lockIconRect.bottom + dp(36);
        lockSubtitleY = lockTitleY + dp(26);

        sheetTop = Math.max(h * 0.50f, h - dp(390));
        dotsY = Math.min(lockSubtitleY + dp(62), sheetTop - dp(46));
        sheetRect.set(dp(10), sheetTop, w - dp(10), h + dp(60));

        float padTop = dp(36);
        float bottomPad = bottomSafe + dp(12);
        float available = h - sheetTop - padTop - bottomPad;
        float gapY = dp(9);
        keySize = Math.min(dp(66), (available - 3 * gapY) / 4f);
        if (keySize < dp(44)) {
            keySize = Math.max(dp(40), (available - 3 * gapY) / 4f);
        }
        float totalHeight = 4 * keySize + 3 * gapY;
        float gridTop = sheetTop + padTop + Math.max(0, (available - totalHeight) / 2f);
        float spacingX = keySize + dp(20);

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                float kcx = cx + (col - 1) * spacingX;
                float kcy = gridTop + keySize / 2f + row * (keySize + gapY);
                keyCx[index] = kcx;
                keyCy[index] = kcy;
                keyRects[index].set(
                        kcx - keySize / 2f,
                        kcy - keySize / 2f,
                        kcx + keySize / 2f,
                        kcy + keySize / 2f);
            }
        }

        float edge = Math.max(insetTop, dp(16));
        backRect.set(dp(10), edge + dp(8), dp(56), edge + dp(56));
        float pickerIconSize = dp(64);
        pickerIconRect.set(
                cx - pickerIconSize / 2f,
                edge + dp(30),
                cx + pickerIconSize / 2f,
                edge + dp(30) + pickerIconSize);
        pickerTitleY = pickerIconRect.bottom + dp(38);
        pickerSubtitleY = pickerTitleY + dp(28);
        float cardsTop = pickerSubtitleY + dp(36);
        float cardHeight = dp(104);
        float cardGap = dp(14);
        optionRects[0].set(dp(20), cardsTop, w - dp(20), cardsTop + cardHeight);
        optionRects[1].set(
                dp(20),
                cardsTop + cardHeight + cardGap,
                w - dp(20),
                cardsTop + cardHeight + cardGap + cardHeight);
        pickerNoteY = optionRects[1].bottom + dp(34);

        if (mark != null) {
            BitmapShader homeShader = new BitmapShader(mark, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix homeMatrix = new Matrix();
            homeMatrix.setScale(
                    homeIconRect.width() / mark.getWidth(),
                    homeIconRect.height() / mark.getHeight());
            homeMatrix.postTranslate(homeIconRect.left, homeIconRect.top);
            homeShader.setLocalMatrix(homeMatrix);
            homeIconPaint.setShader(homeShader);

            BitmapShader lockShader = new BitmapShader(mark, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix lockMatrix = new Matrix();
            lockMatrix.setScale(
                    lockIconRect.width() / mark.getWidth(),
                    lockIconRect.height() / mark.getHeight());
            lockMatrix.postTranslate(lockIconRect.left, lockIconRect.top);
            lockShader.setLocalMatrix(lockMatrix);
            lockIconPaint.setShader(lockShader);

            BitmapShader pickerShader = new BitmapShader(mark, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix pickerMatrix = new Matrix();
            pickerMatrix.setScale(
                    pickerIconRect.width() / mark.getWidth(),
                    pickerIconRect.height() / mark.getHeight());
            pickerMatrix.postTranslate(pickerIconRect.left, pickerIconRect.top);
            pickerShader.setLocalMatrix(pickerMatrix);
            pickerIconPaint.setShader(pickerShader);
        }

        homeGlow = new RadialGradient(
                cx,
                homeCy,
                dp(225),
                new int[]{0x14FFFFFF, 0x07FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP);
        lockGlow = new RadialGradient(
                lockIconRect.centerX(),
                lockIconRect.centerY(),
                dp(165),
                new int[]{0x12FFFFFF, 0x05FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP);
        pickerGlow = new RadialGradient(
                pickerIconRect.centerX(),
                pickerIconRect.centerY(),
                dp(150),
                new int[]{0x12FFFFFF, 0x05FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP);

        titlePaint.setTextSize(sp(27));
        subtitlePaint.setTextSize(sp(13.5f));
        homeNamePaint.setTextSize(sp(15));
        passwordLength = LockPrefs.getPasswordLength(getContext());
        buttonTextPaint.setTextSize(sp(16));
        pickerTitlePaint.setTextSize(sp(24));
        pickerSubtitlePaint.setTextSize(sp(13));
        cardTitlePaint.setTextSize(sp(16));
        cardDescPaint.setTextSize(sp(12));
        cardStatusPaint.setTextSize(sp(11.5f));
        notePaint.setTextSize(sp(12));
        invalidate();
    }

    public void showHome() {
        mode = MODE_HOME;
        buttonDown = false;
        backDown = false;
        settingsDown = false;
        pressedOption = -1;
        invalidate();
    }

    public void showModePicker() {
        mode = MODE_PICKER;
        buttonDown = false;
        backDown = false;
        pressedOption = -1;
        if (pickerAnimator != null) {
            pickerAnimator.cancel();
        }
        pickerAnim = 0f;
        pickerAnimator = ValueAnimator.ofFloat(0f, 1f);
        pickerAnimator.setDuration(260);
        pickerAnimator.setInterpolator(new DecelerateInterpolator());
        pickerAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                pickerAnim = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        pickerAnimator.start();
        invalidate();
    }

    public void setAccessibilityEnabled(boolean enabled) {
        accessibilityEnabled = enabled;
        if (mode == MODE_PICKER) {
            invalidate();
        }
    }

    public void showLockedImmediately() {
        mode = MODE_LOCK;
        input = "";
        error = false;
        busy = false;
        pressedKey = -1;
        passwordLength = LockPrefs.getPasswordLength(getContext());
        invalidate();
    }

    public void enterLock() {
        mode = MODE_LOCK;
        input = "";
        error = false;
        busy = false;
        pressedKey = -1;
        passwordLength = LockPrefs.getPasswordLength(getContext());
        invalidate();
    }


    public boolean handleBack() {
        if (mode == MODE_PICKER) {
            showHome();
            return true;
        }
        return mode == MODE_LOCK || mode == MODE_SUCCESS;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        if (mode == MODE_HOME) {
            return handleHomeTouch(event, x, y);
        }
        if (mode == MODE_PICKER) {
            return handlePickerTouch(event, x, y);
        }
        if (mode == MODE_LOCK) {
            return handleLockTouch(event, x, y);
        }
        return true;
    }

    private boolean handleHomeTouch(MotionEvent event, float x, float y) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (settingsRect.contains(x, y)) {
                    settingsDown = true;
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    invalidate();
                } else if (buttonRect.contains(x, y)) {
                    buttonDown = true;
                    playPressFeedback();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (buttonDown && !buttonRect.contains(x, y)) {
                    buttonDown = false;
                    invalidate();
                }
                if (settingsDown && !settingsRect.contains(x, y)) {
                    settingsDown = false;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                boolean fire = buttonDown && buttonRect.contains(x, y);
                boolean openSettings = settingsDown && settingsRect.contains(x, y);
                buttonDown = false;
                settingsDown = false;
                invalidate();
                if (openSettings) {
                    listener.onOpenSettings();
                } else if (fire) {
                    listener.onStartTest();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                buttonDown = false;
                settingsDown = false;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private boolean handlePickerTouch(MotionEvent event, float x, float y) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (backRect.contains(x, y)) {
                    backDown = true;
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                } else if (buttonRect.contains(x, y)) {
                    buttonDown = true;
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                } else {
                    pressedOption = hitOption(x, y);
                    if (pressedOption >= 0) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    }
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (backDown && !backRect.contains(x, y)) {
                    backDown = false;
                    invalidate();
                }
                if (buttonDown && !buttonRect.contains(x, y)) {
                    buttonDown = false;
                    invalidate();
                }
                if (pressedOption >= 0 && hitOption(x, y) != pressedOption) {
                    pressedOption = -1;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                boolean back = backDown && backRect.contains(x, y);
                boolean start = buttonDown && buttonRect.contains(x, y);
                int option = pressedOption;
                boolean choose = option >= 0 && hitOption(x, y) == option;
                backDown = false;
                buttonDown = false;
                pressedOption = -1;
                invalidate();
                if (back) {
                    showHome();
                } else if (choose) {
                    selectedMode = option;
                    invalidate();
                } else if (start) {
                    listener.onStartLock(selectedMode);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                backDown = false;
                buttonDown = false;
                pressedOption = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private int hitOption(float x, float y) {
        for (int i = 0; i < optionRects.length; i++) {
            if (optionRects[i].contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private boolean handleLockTouch(MotionEvent event, float x, float y) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressedKey = hitKey(x, y);
                if (pressedKey == 9) {
                    pressedKey = -1;
                }
                if (pressedKey >= 0) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressedKey >= 0 && !keyRects[pressedKey].contains(x, y)) {
                    pressedKey = -1;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                int upKey = hitKey(x, y);
                int previous = pressedKey;
                pressedKey = -1;
                invalidate();
                if (previous >= 0 && upKey == previous) {
                    handleKey(upKey);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressedKey = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private int hitKey(float x, float y) {
        for (int i = 0; i < keyRects.length; i++) {
            if (keyRects[i].contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private void handleKey(int index) {
        if (busy || mode != MODE_LOCK) {
            return;
        }
        if (index == 11) {
            if (input.length() > 0) {
                input = input.substring(0, input.length() - 1);
                error = false;
                errorUntil = 0;
                invalidate();
            }
            return;
        }
        if (index == 9 || input.length() >= passwordLength) {
            return;
        }
        int digit = index == 10 ? 0 : index + 1;
        input = input + digit;
        error = false;
        errorUntil = 0;
        invalidate();
        if (input.length() == passwordLength) {
            busy = true;
            postDelayed(verifyRunnable, 150);
        }
    }

    private void verifyCode() {
        if (mode != MODE_LOCK) {
            return;
        }
        if (!LockPrefs.checkPassword(getContext(), input)) {
            error = true;
            errorUntil = SystemClock.uptimeMillis() + 1600;
            busy = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            startShake();
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    input = "";
                    busy = false;
                    invalidate();
                }
            }, 430);
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (error && SystemClock.uptimeMillis() >= errorUntil) {
                        error = false;
                        invalidate();
                    }
                }
            }, 1680);
            invalidate();
            return;
        }

        error = false;
        busy = true;
        mode = MODE_SUCCESS;
        successAnim = 0f;
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (successAnimator != null) {
            successAnimator.cancel();
        }
        successAnimator = ValueAnimator.ofFloat(0f, 1f);
        successAnimator.setDuration(650);
        successAnimator.setInterpolator(new DecelerateInterpolator());
        successAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                successAnim = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        successAnimator.start();
        postDelayed(new Runnable() {
            @Override
            public void run() {
                listener.onUnlockRequested();
            }
        }, 1050);
        invalidate();
    }

    private void startShake() {
        if (shakeAnimator != null) {
            shakeAnimator.cancel();
        }
        shakeAnimator = ValueAnimator.ofFloat(0f, 1f);
        shakeAnimator.setDuration(430);
        shakeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float t = (float) animation.getAnimatedValue();
                shakeX = (float) Math.sin(t * Math.PI * 6f) * (1f - t) * dp(8);
                invalidate();
            }
        });
        shakeAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(BLACK);

        if (mode == MODE_HOME) {
            drawHome(canvas, 1f);
        } else if (mode == MODE_PICKER) {
            drawPicker(canvas, pickerAnim);
        } else if (mode == MODE_LOCK) {
            drawLock(canvas, 1f);
        } else if (mode == MODE_SUCCESS) {
            drawLock(canvas, 1f);
            drawSuccess(canvas, successAnim);
        }
    }

    private void drawHome(Canvas canvas, float alpha) {
        if (alpha <= 0.001f) {
            return;
        }
        int save = saveAlpha(canvas, alpha);
        glowPaint.setShader(homeGlow);
        canvas.drawCircle(homeIconRect.centerX(), homeIconRect.centerY(), dp(225), glowPaint);
        glowPaint.setShader(null);
        drawMark(canvas, homeIconRect, dp(26), false);
        homeNamePaint.setColor(MUTED);
        drawCenteredText(canvas, "TE cvb", homeIconRect.centerX(), homeNameY, homeNamePaint);
        fillPaint.setColor(settingsDown ? MUTED : 0xCCFFFFFF);
        float dotsY = settingsRect.centerY();
        float dotsX = settingsRect.centerX();
        float gap = dp(6.5f);
        for (int i = -1; i <= 1; i++) {
            canvas.drawCircle(dotsX + i * gap, dotsY, dp(2.4f), fillPaint);
        }
        drawPrimaryButton(canvas, "开始测试");
        restoreAlpha(canvas, save);
    }

    private void drawPicker(Canvas canvas, float alpha) {
        if (alpha <= 0.001f) {
            return;
        }
        int save = saveAlpha(canvas, alpha);

        strokePaint.setColor(WHITE);
        strokePaint.setStrokeWidth(dp(2));
        float bx = backRect.centerX();
        float by = backRect.centerY();
        canvas.drawLine(bx + dp(3), by - dp(7), bx - dp(4), by, strokePaint);
        canvas.drawLine(bx - dp(4), by, bx + dp(3), by + dp(7), strokePaint);

        glowPaint.setShader(pickerGlow);
        canvas.drawCircle(pickerIconRect.centerX(), pickerIconRect.centerY(), dp(150), glowPaint);
        glowPaint.setShader(null);
        drawMark(canvas, pickerIconRect, dp(16), false);

        pickerTitlePaint.setColor(WHITE);
        drawCenteredText(canvas, "选择锁机模式", getWidth() / 2f, pickerTitleY, pickerTitlePaint);
        pickerSubtitlePaint.setColor(MUTED);
        drawCenteredText(canvas, "选一种方式，然后开始锁机", getWidth() / 2f, pickerSubtitleY, pickerSubtitlePaint);

        drawModeCard(canvas, 0);
        drawModeCard(canvas, 1);

        if (pickerNoteY < buttonRect.top - dp(20)) {
            notePaint.setColor(MUTED);
            String note;
            if (selectedMode == LOCK_MODE_ACCESSIBILITY) {
                note = accessibilityEnabled
                        ? "无障碍已开启，可阻止 Home 手势"
                        : "开始时会跳转系统无障碍设置";
            } else {
                note = "免权限模式可能被上滑手势退出";
            }
            drawCenteredText(canvas, note, getWidth() / 2f, pickerNoteY, notePaint);
        }

        drawPrimaryButton(canvas, "开始锁机");
        restoreAlpha(canvas, save);
    }

    private void drawModeCard(Canvas canvas, int index) {
        RectF rect = optionRects[index];
        boolean selected = selectedMode == index;
        boolean accessibility = index == LOCK_MODE_ACCESSIBILITY;

        fillPaint.setColor(selected ? 0xFF141416 : 0xFF0B0B0C);
        canvas.drawRoundRect(rect, dp(24), dp(24), fillPaint);
        strokePaint.setColor(selected ? WHITE : 0x1FFFFFFF);
        strokePaint.setStrokeWidth(selected ? dp(1.5f) : dp(1));
        canvas.drawRoundRect(rect, dp(24), dp(24), strokePaint);

        float left = rect.left + dp(20);
        float titleY = rect.top + dp(33);
        float descY = rect.top + dp(60);
        float statusY = rect.top + dp(84);

        cardTitlePaint.setColor(WHITE);
        canvas.drawText(accessibility ? "无障碍锁机" : "屏幕固定", left, titleY, cardTitlePaint);

        String desc = accessibility
                ? "推荐 · 阻止 Home / 最近任务退出"
                : "免权限 · 可能被上滑手势退出";
        cardDescPaint.setColor(MUTED);
        canvas.drawText(desc, left, descY, cardDescPaint);

        if (accessibility) {
            String status = accessibilityEnabled ? "无障碍权限：已开启" : "无障碍权限：需要开启";
            fillPaint.setColor(accessibilityEnabled ? WHITE : DIM);
            canvas.drawCircle(left + dp(4), statusY - dp(3.5f), dp(3), fillPaint);
            cardStatusPaint.setColor(accessibilityEnabled ? WHITE : MUTED);
            canvas.drawText(status, left + dp(12), statusY, cardStatusPaint);
        } else {
            cardStatusPaint.setColor(MUTED);
            canvas.drawText("备用模式 · 无需额外权限", left, statusY, cardStatusPaint);
        }

        float rx = rect.right - dp(28);
        float ry = rect.top + dp(33);
        if (selected) {
            fillPaint.setColor(WHITE);
            canvas.drawCircle(rx, ry, dp(10), fillPaint);
            strokePaint.setColor(BLACK);
            strokePaint.setStrokeWidth(dp(1.8f));
            canvas.drawLine(rx - dp(4), ry, rx - dp(1), ry + dp(3), strokePaint);
            canvas.drawLine(rx - dp(1), ry + dp(3), rx + dp(5), ry - dp(4), strokePaint);
        } else {
            strokePaint.setColor(DIM);
            strokePaint.setStrokeWidth(dp(1.4f));
            canvas.drawCircle(rx, ry, dp(9), strokePaint);
        }
    }

    private void drawPrimaryButton(Canvas canvas, String label) {
        canvas.save();
        if (buttonDown) {
            canvas.scale(0.99f, 0.99f, buttonRect.centerX(), buttonRect.centerY());
        }
        fillPaint.setColor(buttonDown ? 0xFFEDEDED : WHITE);
        canvas.drawRoundRect(buttonRect, buttonRect.height() / 2f,
                buttonRect.height() / 2f, fillPaint);
        buttonTextPaint.setColor(BLACK);
        drawCenteredText(canvas, label, buttonRect.centerX(), buttonRect.centerY(), buttonTextPaint);
        canvas.restore();
    }

    private void drawLock(Canvas canvas, float alpha) {
        if (alpha <= 0.001f) {
            return;
        }
        int save = saveAlpha(canvas, alpha);

        glowPaint.setShader(lockGlow);
        canvas.drawCircle(lockIconRect.centerX(), lockIconRect.centerY(), dp(165), glowPaint);
        glowPaint.setShader(null);
        drawMark(canvas, lockIconRect, dp(20), true);

        titlePaint.setColor(WHITE);
        drawCenteredText(canvas, "已锁定", getWidth() / 2f, lockTitleY, titlePaint);

        boolean showError = error && SystemClock.uptimeMillis() < errorUntil;
        subtitlePaint.setColor(showError ? WHITE : MUTED);
        drawCenteredText(canvas,
                showError ? "解锁码错误，请重试" : "输入解锁码退出",
                getWidth() / 2f,
                lockSubtitleY,
                subtitlePaint);

        canvas.save();
        canvas.translate(shakeX, 0f);
        drawDots(canvas);
        canvas.restore();

        fillPaint.setColor(0xFF111113);
        canvas.drawRoundRect(sheetRect, dp(30), dp(30), fillPaint);
        strokePaint.setColor(0x14FFFFFF);
        strokePaint.setStrokeWidth(dp(1));
        canvas.drawRoundRect(sheetRect, dp(30), dp(30), strokePaint);

        for (int i = 0; i < 12; i++) {
            if (i == 9) {
                continue;
            }
            boolean pressed = i == pressedKey && pressedKey >= 0;
            float radius = keySize / 2f;
            if (pressed) {
                radius *= 0.96f;
            }
            fillPaint.setColor(pressed ? KEY_PRESSED : KEY_BG);
            canvas.drawCircle(keyCx[i], keyCy[i], radius, fillPaint);
            if (i == 11) {
                drawBackspace(canvas, keyCx[i], keyCy[i]);
            } else {
                int digit = i == 10 ? 0 : i + 1;
                textPaint.setColor(WHITE);
                textPaint.setTextSize(keySize * 0.40f);
                drawCenteredText(canvas, String.valueOf(digit), keyCx[i], keyCy[i], textPaint);
            }
        }

        restoreAlpha(canvas, save);
    }

    private void drawDots(Canvas canvas) {
        int count = passwordLength;
        float spacing = count > 6 ? dp(15.5f) : (count <= 4 ? dp(22) : dp(19));
        float startX = getWidth() / 2f - spacing * (count - 1) / 2f;
        float radius = dp(4.2f);
        for (int i = 0; i < count; i++) {
            float x = startX + spacing * i;
            if (i < input.length()) {
                fillPaint.setColor(WHITE);
                canvas.drawCircle(x, dotsY, radius, fillPaint);
            } else {
                strokePaint.setColor(DIM);
                strokePaint.setStrokeWidth(dp(1.3f));
                canvas.drawCircle(x, dotsY, radius, strokePaint);
            }
        }
    }

    private void drawBackspace(Canvas canvas, float cx, float cy) {
        float s = keySize * 0.19f;
        backspacePath.reset();
        backspacePath.moveTo(cx - s * 1.25f, cy);
        backspacePath.lineTo(cx - s * 0.55f, cy - s * 0.88f);
        backspacePath.lineTo(cx + s * 0.92f, cy - s * 0.88f);
        backspacePath.quadTo(cx + s * 1.32f, cy - s * 0.88f, cx + s * 1.32f, cy - s * 0.42f);
        backspacePath.lineTo(cx + s * 1.32f, cy + s * 0.42f);
        backspacePath.quadTo(cx + s * 1.32f, cy + s * 0.88f, cx + s * 0.92f, cy + s * 0.88f);
        backspacePath.lineTo(cx - s * 0.55f, cy + s * 0.88f);
        backspacePath.close();

        strokePaint.setColor(WHITE);
        strokePaint.setStrokeWidth(dp(2));
        canvas.drawPath(backspacePath, strokePaint);

        float x = s * 0.18f;
        float y = s * 0.30f;
        canvas.drawLine(cx - x, cy - y, cx + x, cy + y, strokePaint);
        canvas.drawLine(cx + x, cy - y, cx - x, cy + y, strokePaint);
    }

    private void drawMark(Canvas canvas, RectF rect, float radius, boolean badge) {
        if (mark != null) {
            Paint paint = homeIconPaint;
            if (rect == lockIconRect) {
                paint = lockIconPaint;
            } else if (rect == pickerIconRect) {
                paint = pickerIconPaint;
            }
            canvas.drawRoundRect(rect, radius, radius, paint);
        } else {
            fillPaint.setColor(0xFF0A0A0A);
            canvas.drawRoundRect(rect, radius, radius, fillPaint);
            textPaint.setColor(WHITE);
            textPaint.setTextSize(rect.height() * 0.42f);
            drawCenteredText(canvas, "TE", rect.centerX(), rect.centerY(), textPaint);
        }

        strokePaint.setColor(0x1FFFFFFF);
        strokePaint.setStrokeWidth(dp(1));
        RectF inner = new RectF(rect);
        inner.inset(dp(0.5f), dp(0.5f));
        canvas.drawRoundRect(inner, radius, radius, strokePaint);

        if (badge) {
            float br = dp(13);
            float bx = rect.right - dp(7);
            float by = rect.bottom - dp(7);
            fillPaint.setColor(BLACK);
            canvas.drawCircle(bx, by, br + dp(3), fillPaint);
            fillPaint.setColor(WHITE);
            canvas.drawCircle(bx, by, br, fillPaint);

            fillPaint.setColor(BLACK);
            RectF body = new RectF(bx - dp(5.5f), by - dp(0.5f), bx + dp(5.5f), by + dp(7));
            canvas.drawRoundRect(body, dp(2), dp(2), fillPaint);
            strokePaint.setColor(BLACK);
            strokePaint.setStrokeWidth(dp(1.7f));
            RectF shackle = new RectF(bx - dp(3.7f), by - dp(7.2f), bx + dp(3.7f), by + dp(2));
            canvas.drawArc(shackle, 180, 180, false, strokePaint);
        }
    }

    private void drawSuccess(Canvas canvas, float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        int overlayAlpha = (int) (255 * Math.min(1f, p * 1.45f));
        fillPaint.setColor(BLACK);
        fillPaint.setAlpha(overlayAlpha);
        canvas.drawRect(0, 0, getWidth(), getHeight(), fillPaint);
        fillPaint.setAlpha(255);

        float reveal = Math.max(0f, (p - 0.08f) / 0.92f);
        float scale = 0.82f + 0.18f * reveal;
        float cx = getWidth() / 2f;
        float cy = getHeight() * 0.45f;

        fillPaint.setColor(WHITE);
        fillPaint.setAlpha((int) (255 * reveal));
        canvas.drawCircle(cx, cy, dp(38) * scale, fillPaint);
        fillPaint.setAlpha(255);

        checkPath.reset();
        checkPath.moveTo(cx - dp(14), cy + dp(1));
        checkPath.lineTo(cx - dp(3.5f), cy + dp(11));
        checkPath.lineTo(cx + dp(16), cy - dp(10));
        strokePaint.setColor(BLACK);
        strokePaint.setStrokeWidth(dp(4.2f));
        strokePaint.setAlpha((int) (255 * reveal));
        canvas.drawPath(checkPath, strokePaint);
        strokePaint.setAlpha(255);

        titlePaint.setColor(WHITE);
        titlePaint.setTextSize(sp(21));
        titlePaint.setAlpha((int) (255 * reveal));
        drawCenteredText(canvas, "已解锁", cx, cy + dp(76), titlePaint);
        titlePaint.setAlpha(255);

        subtitlePaint.setColor(MUTED);
        subtitlePaint.setAlpha((int) (255 * reveal));
        drawCenteredText(canvas, "正在退出锁机模式", cx, cy + dp(102), subtitlePaint);
        subtitlePaint.setAlpha(255);
    }

    private int saveAlpha(Canvas canvas, float alpha) {
        if (alpha >= 0.999f) {
            return 0;
        }
        return canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (255 * alpha));
    }

    private void restoreAlpha(Canvas canvas, int save) {
        if (save != 0) {
            canvas.restoreToCount(save);
        }
    }

    private void drawCenteredText(Canvas canvas, String text, float cx, float cy, Paint paint) {
        float baseline = cy - (paint.ascent() + paint.descent()) / 2f;
        canvas.drawText(text, cx, baseline, paint);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK
                && (mode == MODE_LOCK || mode == MODE_SUCCESS)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK
                && (mode == MODE_LOCK || mode == MODE_SUCCESS)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (pickerAnimator != null) {
            pickerAnimator.cancel();
        }
        if (shakeAnimator != null) {
            shakeAnimator.cancel();
        }
        if (successAnimator != null) {
            successAnimator.cancel();
        }
        removeCallbacks(verifyRunnable);
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        super.onDetachedFromWindow();
    }
}
