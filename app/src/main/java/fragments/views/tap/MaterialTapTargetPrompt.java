/*
 * Copyright (C) 2016 Samuel Wall
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fragments.views.tap;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DimenRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.StringRes;
import mt.karimi.ronevis.R;

/**
 * A Material Design tap target onboarding implementation.
 * <p>
 * <div class="special reference">
 * <h3>Onboarding</h3>
 * <p>For more information about onboarding and tap targets, read the
 * <a href="https://www.google.com/design/spec/growth-communications/onboarding.html">Onboarding</a>
 * Material Design guidelines.</p>
 * </div>
 */
public class MaterialTapTargetPrompt {
    final float mStatusBarHeight;
    final ViewTreeObserver.OnGlobalLayoutListener mGlobalLayoutListener;
    Activity mActivity;
    PromptView mView;
    View mTargetView;
    float mBaseLeft, mBaseTop;
    float mBaseFocalRadius, mBaseBackgroundRadius;
    float mFocalRadius10Percent;
    float mRevealedAmount;
    String mPrimaryText, mSecondaryText;
    float mMaxTextWidth;
    float mTextPadding;
    boolean mTextPositionRight, mTextPositionAbove;
    float mFocalToTextPadding;
    int mPrimaryTextColourAlpha, mSecondaryTextColourAlpha;
    ValueAnimator mAnimationCurrent, mAnimationFocalRipple;
    Interpolator mAnimationInterpolator;
    float mFocalRippleProgress;
    int mBaseFocalRippleAlpha;
    TextPaint mPaintPrimaryText, mPaintSecondaryText;
    OnHidePromptListener mOnHidePromptListener;
    boolean mDismissing;
    ViewGroup mParentView;
    boolean mParentViewIsDecor;
    ViewGroup mClipToView;
    boolean mAutoDismiss, mAutoFinish;

    MaterialTapTargetPrompt(final Activity activity) {
        mActivity = activity;
        mView = new PromptView(activity);
        mView.mOnPromptTouchedListener = new PromptView.OnPromptTouchedListener() {
            @Override
            public void onPromptTouched(MotionEvent event, boolean tappedTarget) {
                if (!mDismissing) {
                    MaterialTapTargetPrompt.this.onHidePrompt(event, tappedTarget);
                    if (tappedTarget) {
                        if (mAutoFinish) {
                            finish();
                        }
                    } else {
                        if (mAutoDismiss) {
                            dismiss();
                        }
                    }
                }
            }
        };
        Rect rect = new Rect();
        mActivity.getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
        mStatusBarHeight = rect.top;
        mGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                updateFocalCentrePosition();
            }
        };
    }

    /**
     * Returns {@link #mParentView}.
     * <p>
     * If the {@link #mParentView} is {@link null} it determines what view it should be.
     *
     * @return The view to add the prompt view to.
     */
    ViewGroup getParentView() {
        if (mParentView == null) {
            final ViewGroup decorView = (ViewGroup) mActivity.getWindow().getDecorView();
            final ViewGroup contentView = (ViewGroup) ((ViewGroup) decorView.findViewById(android.R.id.content)).getChildAt(0);
            // If the content view is a drawer layout then that is the parent so
            // that the prompt can be added behind the navigation drawer
            if (contentView.getClass().getName().equals("android.support.v4.widget.DrawerLayout")) {
                mParentView = contentView;
                mParentViewIsDecor = false;
            } else {
                mParentView = decorView;
                mParentViewIsDecor = true;
            }
            mView.mClipBounds = mParentViewIsDecor;
        }
        return mParentView;
    }

    /**
     * Displays the prompt.
     */
    public void show() {
        final ViewGroup parent = getParentView();
        // If the content view is a drawer layout then that is the parent so
        // that the prompt can be added behind the navigation drawer
        if (parent.getClass().getName().equals("android.support.v4.widget.DrawerLayout")) {
            parent.addView(mView, 1);
        } else {
            parent.addView(mView);
        }
        addGlobalLayoutListener();
        updateFocalCentrePosition();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            startRevealAnimation();
        } else {
            mView.mBackgroundRadius = mBaseBackgroundRadius;
            mView.mFocalRadius = mBaseFocalRadius;
            mView.mPaintFocal.setAlpha(255);
            mView.mPaintBackground.setAlpha(244);
            mPaintSecondaryText.setAlpha(mSecondaryTextColourAlpha);
            mPaintPrimaryText.setAlpha(mPrimaryTextColourAlpha);
        }
    }

    /**
     * Adds layout listener to view parent to capture layout changes.
     */
    void addGlobalLayoutListener() {
        final ViewTreeObserver viewTreeObserver = getParentView().getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(mGlobalLayoutListener);
        }
    }

    /**
     * Removes global layout listener added in {@link #addGlobalLayoutListener()}.
     */
    void removeGlobalLayoutListener() {
        final ViewTreeObserver viewTreeObserver = getParentView().getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                viewTreeObserver.removeOnGlobalLayoutListener(mGlobalLayoutListener);
            } else {
                //noinspection deprecation
                viewTreeObserver.removeGlobalOnLayoutListener(mGlobalLayoutListener);
            }
        }
    }

    /**
     * Removes the prompt from view, using a expand and fade animation.
     * <p>
     * This is treated as if the user has touched the target focal point.
     */
    public void finish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            if (mDismissing) {
                return;
            }
            mDismissing = true;
            if (mAnimationCurrent != null) {
                mAnimationCurrent.removeAllListeners();
                mAnimationCurrent.cancel();
                mAnimationCurrent = null;
            }
            mAnimationCurrent = ValueAnimator.ofFloat(1f, 0f);
            mAnimationCurrent.setDuration(225);
            mAnimationCurrent.setInterpolator(mAnimationInterpolator);
            mAnimationCurrent.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @TargetApi(Build.VERSION_CODES.HONEYCOMB)
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    final float value = (float) animation.getAnimatedValue();
                    mRevealedAmount = 1f + ((1f - value) / 4);
                    mView.mBackgroundRadius = mBaseBackgroundRadius * mRevealedAmount;
                    mView.mFocalRadius = mBaseFocalRadius * mRevealedAmount;
                    mView.mPaintFocal.setAlpha((int) (255 * value));
                    mView.mPaintBackground.setAlpha((int) (244 * value));
                    mPaintSecondaryText.setAlpha((int) (mSecondaryTextColourAlpha * value));
                    mPaintPrimaryText.setAlpha((int) (mPrimaryTextColourAlpha * value));
                    if (mView.mIconDrawable != null) {
                        mView.mIconDrawable.setAlpha(mView.mPaintBackground.getAlpha());
                    }
                    mView.invalidate();
                }
            });
            mAnimationCurrent.addListener(new AnimatorListener() {
                @TargetApi(Build.VERSION_CODES.HONEYCOMB)
                @Override
                public void onAnimationEnd(Animator animation) {
                    removeGlobalLayoutListener();
                    getParentView().removeView(mView);
                    mAnimationCurrent.removeAllListeners();
                    mAnimationCurrent = null;
                    mDismissing = false;
                    onHidePromptComplete();
                    mParentView = null;
                }

                @TargetApi(Build.VERSION_CODES.HONEYCOMB)
                @Override
                public void onAnimationCancel(Animator animation) {
                    removeGlobalLayoutListener();
                    getParentView().removeView(mView);
                    mAnimationCurrent.removeAllListeners();
                    mAnimationCurrent = null;
                    mDismissing = false;
                    onHidePromptComplete();
                    mParentView = null;
                }
            });
            mAnimationCurrent.start();
        } else {
            removeGlobalLayoutListener();
            getParentView().removeView(mView);
            onHidePromptComplete();
            mParentView = null;
        }
    }

    /**
     * Removes the prompt from view, using a contract and fade animation.
     * <p>
     * This is treated as if the user has touched outside the target focal point.
     */
    public void dismiss() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            if (mDismissing) {
                return;
            }
            mDismissing = true;
            if (mAnimationCurrent != null) {
                mAnimationCurrent.removeAllListeners();
                mAnimationCurrent.cancel();
                mAnimationCurrent = null;
            }
            mAnimationCurrent = ValueAnimator.ofFloat(1f, 0f);
            mAnimationCurrent.setDuration(225);
            mAnimationCurrent.setInterpolator(mAnimationInterpolator);
            mAnimationCurrent.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @TargetApi(Build.VERSION_CODES.HONEYCOMB)
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mRevealedAmount = (float) animation.getAnimatedValue();
                    mView.mBackgroundRadius = mBaseBackgroundRadius * mRevealedAmount;
                    mView.mFocalRadius = mBaseFocalRadius * mRevealedAmount;
                    mView.mPaintBackground.setAlpha((int) (244 * mRevealedAmount));
                    mPaintSecondaryText.setAlpha((int) (mSecondaryTextColourAlpha * mRevealedAmount));
                    mPaintPrimaryText.setAlpha((int) (mPrimaryTextColourAlpha * mRevealedAmount));
                    if (mView.mIconDrawable != null) {
                        mView.mIconDrawable.setAlpha(mView.mPaintBackground.getAlpha());
                    }
                    mView.invalidate();
                }
            });
            mAnimationCurrent.addListener(new AnimatorListener() {
                @TargetApi(Build.VERSION_CODES.HONEYCOMB)
                @Override
                public void onAnimationEnd(Animator animation) {
                    removeGlobalLayoutListener();
                    getParentView().removeView(mView);
                    mAnimationCurrent.removeAllListeners();
                    mAnimationCurrent = null;
                    mDismissing = false;
                    onHidePromptComplete();
                    mParentView = null;
                }

                @TargetApi(Build.VERSION_CODES.HONEYCOMB)
                @Override
                public void onAnimationCancel(Animator animation) {
                    removeGlobalLayoutListener();
                    getParentView().removeView(mView);
                    mAnimationCurrent.removeAllListeners();
                    mAnimationCurrent = null;
                    mDismissing = false;
                    onHidePromptComplete();
                    mParentView = null;
                }
            });
            mAnimationCurrent.start();
        } else {
            removeGlobalLayoutListener();
            getParentView().removeView(mView);
            onHidePromptComplete();
            mParentView = null;
        }
    }

    @TargetApi(11)
    void startRevealAnimation() {
        mPaintSecondaryText.setAlpha(0);
        mPaintPrimaryText.setAlpha(0);
        mView.mPaintBackground.setAlpha(0);
        mView.mPaintFocal.setAlpha(0);
        mView.mFocalRadius = 0;
        mView.mBackgroundRadius = 0;
        if (mView.mIconDrawable != null) {
            mView.mIconDrawable.setAlpha(0);
        }
        mRevealedAmount = 0f;
        mAnimationCurrent = ValueAnimator.ofFloat(0f, 1f);
        mAnimationCurrent.setInterpolator(mAnimationInterpolator);
        mAnimationCurrent.setDuration(225);
        mAnimationCurrent.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mRevealedAmount = (float) animation.getAnimatedValue();
                mView.mBackgroundRadius = mBaseBackgroundRadius * mRevealedAmount;
                mView.mFocalRadius = mBaseFocalRadius * mRevealedAmount;
                mView.mPaintFocal.setAlpha((int) (255 * mRevealedAmount));
                mView.mPaintBackground.setAlpha((int) (244 * mRevealedAmount));
                mPaintSecondaryText.setAlpha((int) (mSecondaryTextColourAlpha * mRevealedAmount));
                mPaintPrimaryText.setAlpha((int) (mPrimaryTextColourAlpha * mRevealedAmount));
                if (mView.mIconDrawable != null) {
                    mView.mIconDrawable.setAlpha(mView.mPaintBackground.getAlpha());
                }
                mView.invalidate();
            }
        });
        mAnimationCurrent.addListener(new AnimatorListener() {
            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public void onAnimationEnd(Animator animation) {
                animation.removeAllListeners();
                mAnimationCurrent = null;
                mRevealedAmount = 1;
                startIdleAnimations();
            }

            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public void onAnimationCancel(Animator animation) {
                animation.removeAllListeners();
                mRevealedAmount = 1;
                mAnimationCurrent = null;
            }
        });
        mAnimationCurrent.start();
    }

    @TargetApi(11)
    void startIdleAnimations() {
        if (mAnimationCurrent != null) {
            mAnimationCurrent.removeAllUpdateListeners();
            mAnimationCurrent.cancel();
            mAnimationCurrent = null;
        }
        mAnimationCurrent = ValueAnimator.ofFloat(0, mFocalRadius10Percent, 0);
        mAnimationCurrent.setInterpolator(mAnimationInterpolator);
        mAnimationCurrent.setDuration(1000);
        mAnimationCurrent.setStartDelay(225);
        mAnimationCurrent.setRepeatCount(ValueAnimator.INFINITE);
        mAnimationCurrent.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            boolean direction = true;

            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float newFocalFraction = (Float) animation.getAnimatedValue();
                boolean newDirection = direction;
                if (newFocalFraction < mFocalRippleProgress && direction) {
                    newDirection = false;
                } else if (newFocalFraction > mFocalRippleProgress && !direction) {
                    newDirection = true;
                }
                if (newDirection != direction && !newDirection) {
                    mAnimationFocalRipple.start();
                }
                direction = newDirection;
                mFocalRippleProgress = newFocalFraction;
                mView.mFocalRadius = mBaseFocalRadius + mFocalRippleProgress;
                mView.invalidate();
            }
        });
        mAnimationCurrent.start();
        if (mAnimationFocalRipple != null) {
            mAnimationFocalRipple.removeAllUpdateListeners();
            mAnimationFocalRipple.cancel();
            mAnimationFocalRipple = null;
        }
        final float baseRadius = mBaseFocalRadius + mFocalRadius10Percent;
        mAnimationFocalRipple = ValueAnimator.ofFloat(baseRadius, baseRadius + (mFocalRadius10Percent * 6));
        mAnimationFocalRipple.setInterpolator(mAnimationInterpolator);
        mAnimationFocalRipple.setDuration(500);
        mAnimationFocalRipple.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @TargetApi(Build.VERSION_CODES.HONEYCOMB)
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mView.mFocalRippleSize = (float) animation.getAnimatedValue();
                final float fraction;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
                    fraction = animation.getAnimatedFraction();
                } else {
                    fraction = (mFocalRadius10Percent * 6) / (mView.mFocalRippleSize - mBaseFocalRadius - mFocalRadius10Percent);
                }
                mView.mFocalRippleAlpha = (int) (mBaseFocalRippleAlpha * (1f - fraction));
            }
        });
    }

    void updateFocalCentrePosition() {
        updateClipBounds();
        if (mTargetView != null) {
            final int[] viewPosition = new int[2];
            mView.getLocationInWindow(viewPosition);
            final int[] targetPosition = new int[2];
            mTargetView.getLocationInWindow(targetPosition);
            mView.mCentreLeft = mBaseLeft + targetPosition[0] - viewPosition[0] + (mTargetView.getWidth() / 2);
            mView.mCentreTop = mBaseTop + targetPosition[1] - viewPosition[1] + (mTargetView.getHeight() / 2);
        } else {
            mView.mCentreLeft = mBaseLeft;
            mView.mCentreTop = mBaseTop;
        }
        mTextPositionAbove = mView.mCentreTop > mView.mClipBoundsTop + ((mView.mClipBoundsBottom - mView.mClipBoundsTop) / 2);
        mTextPositionRight = mView.mCentreLeft > mView.mClipBoundsLeft + ((mView.mClipBoundsRight - mView.mClipBoundsLeft) / 2);
        updateTextPositioning();
    }

    void updateTextPositioning() {
        final float primaryTextWidth = mPaintPrimaryText.measureText(mPrimaryText);
        final float secondaryTextWidth = mSecondaryText != null ? mPaintSecondaryText.measureText(mSecondaryText) : 0;
        final float textWidth;
        final float maxWidth = Math.max(80, (mView.mClipBounds ? mView.mClipBoundsRight - mView.mClipBoundsLeft : getParentView().getWidth()) - (mTextPadding * 2));
        final float textWidthCalculation = Math.min(mMaxTextWidth, Math.max(primaryTextWidth, secondaryTextWidth));
        if (textWidthCalculation > maxWidth) {
            mView.mTextLeft = (mView.mClipBounds ? mView.mClipBoundsLeft : 0) + mTextPadding;
            textWidth = maxWidth;
        } else {
            if (mTextPositionRight) {
                mView.mTextLeft = (mView.mClipBounds ? mView.mClipBoundsRight : getParentView().getRight()) - mTextPadding - textWidthCalculation;
            } else {
                mView.mTextLeft = mTextPadding + (mView.mClipBounds ? mView.mClipBoundsLeft : 0);
            }
            textWidth = textWidthCalculation;
        }
        mView.mPrimaryTextLayout = new StaticLayout(mPrimaryText, mPaintPrimaryText, (int) textWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        mView.mPrimaryTextTop = mView.mCentreTop;
        if (mTextPositionAbove) {
            mView.mPrimaryTextTop = mView.mPrimaryTextTop - mBaseFocalRadius - mFocalToTextPadding - mView.mPrimaryTextLayout.getHeight();
        } else {
            mView.mPrimaryTextTop += mBaseFocalRadius + mFocalToTextPadding;
        }
        if (mSecondaryText != null) {
            mView.mSecondaryTextLayout = new StaticLayout(mSecondaryText, mPaintSecondaryText, (int) textWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            if (mTextPositionAbove) {
                mView.mPrimaryTextTop = mView.mPrimaryTextTop - mView.mTextSeparation - mView.mSecondaryTextLayout.getHeight();
            }
            mView.mSecondaryTextOffsetTop = mView.mPrimaryTextLayout.getHeight() + mView.mTextSeparation;
        } else {
            mView.mSecondaryTextLayout = null;
        }
        updateBackgroundRadius();
        updateIconPosition();
    }

    void updateBackgroundRadius() {
        final float height;
        if (mTextPositionAbove) {
            height = mView.mCentreTop - mView.mPrimaryTextTop;
        } else {
            height = mView.mPrimaryTextTop + mView.mPrimaryTextLayout.getHeight() + (mView.mSecondaryTextLayout != null ? mView.mSecondaryTextLayout.getHeight() : 0) - mView.mCentreTop + mView.mTextSeparation;
        }
        final float length;
        if (mTextPositionRight) {
            length = mView.mCentreLeft - mView.mTextLeft + mTextPadding;
        } else {
            length = mView.mTextLeft + Math.max(mView.mPrimaryTextLayout.getWidth(), mView.mSecondaryTextLayout != null ? mView.mSecondaryTextLayout.getWidth() : 0)
                    + mTextPadding - mView.mCentreLeft;
        }
        //noinspection SuspiciousNameCombination
        mBaseBackgroundRadius = Double.valueOf(Math.sqrt(Math.pow(length, 2) + Math.pow(height, 2))).floatValue();
    }

    void updateIconPosition() {
        if (mView.mIconDrawable != null) {
            mView.mIconDrawableLeft = mView.mCentreLeft - (mView.mIconDrawable.getIntrinsicWidth() / 2);
            mView.mIconDrawableTop = mView.mCentreTop - (mView.mIconDrawable.getIntrinsicHeight() / 2);
        } else if (mView.mTargetRenderView != null) {
            final int[] viewPosition = new int[2];
            mView.getLocationInWindow(viewPosition);
            final int[] targetPosition = new int[2];
            mView.mTargetRenderView.getLocationInWindow(targetPosition);
            mView.mIconDrawableLeft = mBaseLeft + targetPosition[0] - viewPosition[0];
            mView.mIconDrawableTop = mBaseTop + targetPosition[1] - viewPosition[1];
        }
    }

    void updateClipBounds() {
        if (mClipToView != null) {
            mView.mClipBounds = true;
            //Reset the top to 0
            mView.mClipBoundsTop = 0;
            //Find the location of the clip view on the screen
            final Rect rect = new Rect();
            final Point offset = new Point();
            mClipToView.getGlobalVisibleRect(rect, offset);
            mView.mClipBoundsLeft = rect.left;
            mView.mClipBoundsTop = rect.top;
            mView.mClipBoundsRight = rect.right;
            mView.mClipBoundsBottom = rect.bottom;
            if (mParentViewIsDecor) {
                if (offset.y == 0) {
                    mView.mClipBoundsTop += mStatusBarHeight;
                }
            } else if (offset.y > 0) {
                mView.mClipBoundsTop -= offset.y;
            }
        } else if (mParentViewIsDecor) {
            mView.mClipBounds = true;
            //Stop the canvas drawing over the status bar
            mView.mClipBoundsTop = mStatusBarHeight;
            mView.mClipBoundsLeft = 0f;
            mView.mClipBoundsBottom = mActivity.getResources().getDisplayMetrics().heightPixels;
            mView.mClipBoundsRight = mActivity.getResources().getDisplayMetrics().widthPixels;
        } else {
            mView.mClipBounds = false;
        }
    }

    protected void onHidePrompt(final MotionEvent event, final boolean targetTapped) {
        if (mOnHidePromptListener != null) {
            mOnHidePromptListener.onHidePrompt(event, targetTapped);
        }
    }

    protected void onHidePromptComplete() {
        if (mOnHidePromptListener != null) {
            mOnHidePromptListener.onHidePromptComplete();
        }
    }

    /**
     * Interface definition for a callback to be invoked when a {@link MaterialTapTargetPrompt} is removed from view.
     */
    public interface OnHidePromptListener {
        /**
         * Called when the use touches the prompt view,
         * but before the prompt is removed from view.
         *
         * @param event        The touch event that triggered the dismiss or finish.
         * @param tappedTarget True if the prompt focal point was touched.
         */
        void onHidePrompt(final MotionEvent event, final boolean tappedTarget);

        /**
         * Called after the prompt has been removed from view.
         */
        void onHidePromptComplete();
    }

    /**
     * View used to render the tap target.
     */
    static class PromptView extends View {
        float mCentreLeft, mCentreTop;
        Paint mPaintBackground, mPaintFocal;
        float mFocalRadius, mBackgroundRadius;
        float mFocalRippleSize;
        int mFocalRippleAlpha;
        Drawable mIconDrawable;
        float mIconDrawableLeft;
        float mIconDrawableTop;
        float mTextLeft;
        float mPrimaryTextTop;
        float mSecondaryTextOffsetTop;
        Layout mPrimaryTextLayout;
        Layout mSecondaryTextLayout;
        boolean mDrawRipple = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
        OnPromptTouchedListener mOnPromptTouchedListener;
        boolean mCaptureTouchEventOnFocal;
        float mClipBoundsTop, mClipBoundsLeft, mClipBoundsBottom, mClipBoundsRight;
        View mTargetView, mTargetRenderView;
        float mTextSeparation;
        boolean mClipBounds;
        boolean mCaptureTouchEventOutsidePrompt;

        public PromptView(final Context context) {
            super(context);
        }

        @Override
        public void onDraw(final Canvas canvas) {
            if (mClipBounds) {
                canvas.clipRect(mClipBoundsLeft, mClipBoundsTop, mClipBoundsRight, mClipBoundsBottom);
            }
            //Draw the backgrounds
            canvas.drawCircle(mCentreLeft, mCentreTop, mBackgroundRadius, mPaintBackground);
            //Draw the ripple
            if (mDrawRipple) {
                final int oldAlpha = mPaintFocal.getAlpha();
                mPaintFocal.setAlpha(mFocalRippleAlpha);
                canvas.drawCircle(mCentreLeft, mCentreTop, mFocalRippleSize, mPaintFocal);
                mPaintFocal.setAlpha(oldAlpha);
            }
            //Draw the focal
            canvas.drawCircle(mCentreLeft, mCentreTop, mFocalRadius, mPaintFocal);
            //Draw the icon
            if (mIconDrawable != null) {
                canvas.translate(mIconDrawableLeft, mIconDrawableTop);
                mIconDrawable.draw(canvas);
                canvas.translate(-mIconDrawableLeft, -mIconDrawableTop);
            } else if (mTargetRenderView != null) {
                canvas.translate(mIconDrawableLeft, mIconDrawableTop);
                mTargetRenderView.draw(canvas);
                canvas.translate(-mIconDrawableLeft, -mIconDrawableTop);
            }
            //Draw the text
            canvas.translate(mTextLeft, mPrimaryTextTop);
            mPrimaryTextLayout.draw(canvas);
            if (mSecondaryTextLayout != null) {
                canvas.translate(0f, mSecondaryTextOffsetTop);
                mSecondaryTextLayout.draw(canvas);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final float x = event.getX();
            final float y = event.getY();
            //If the touch point is within the prompt background stop the event from passing through it
            boolean captureEvent = pointInCircle(x, y, mBackgroundRadius);
            //If the touch event was at least in the background and in the focal
            if (captureEvent && pointInCircle(x, y, mFocalRadius)) {
                //Override allowing the touch event to pass through the view with the user defined value
                captureEvent = mCaptureTouchEventOnFocal;
                onPromptTouched(event, true);
            } else {
                // If the prompt background was not touched
                if (!captureEvent) {
                    captureEvent = mCaptureTouchEventOutsidePrompt;
                }
                onPromptTouched(event, false);
            }
            return captureEvent;
        }

        /**
         * Determines if a point is in the centre of a circle with a radius from the point ({@link #mCentreLeft, {@link #mCentreTop}}.
         *
         * @param x      The x position in the view.
         * @param y      The y position in the view.
         * @param radius The radius of the circle.
         * @return True if the point (x, y) is in the circle.
         */
        boolean pointInCircle(final float x, final float y, final float radius) {
            return Math.pow(x - mCentreLeft, 2) + Math.pow(y - mCentreTop, 2) < Math.pow(radius, 2);
        }

        protected void onPromptTouched(final MotionEvent event, final boolean targetTapped) {
            if (mOnPromptTouchedListener != null) {
                mOnPromptTouchedListener.onPromptTouched(event, targetTapped);
            }
        }

        /**
         * Interface definition for a callback to be invoked when a {@link PromptView} is touched.
         */
        public interface OnPromptTouchedListener {
            /**
             * Called when a touch event occurs in the prompt view.
             *
             * @param event        The touch event that triggered the dismiss or finish.
             * @param tappedTarget True if the prompt focal point was touched.
             */
            void onPromptTouched(final MotionEvent event, final boolean tappedTarget);
        }
    }

    /**
     * A builder to create a {@link MaterialTapTargetPrompt} instance.
     */
    public static class Builder {
        /**
         * The containing activity.
         */
        private Activity mActivity;
        private boolean mTargetSet;
        /**
         * The view to place the prompt around.
         */
        private View mTargetView;
        /**
         * The left and top positioning for the focal centre point.
         */
        private float mCentreLeft, mCentreTop;
        /**
         * The text to display.
         */
        private String mPrimaryText, mSecondaryText;
        private int mPrimaryTextColour, mSecondaryTextColour, mBackgroundColour, mFocalColour;
        private float mFocalRadius;
        private float mPrimaryTextSize, mSecondaryTextSize;
        private float mMaxTextWidth;
        private float mTextPadding;
        private float mFocalToTextPadding;
        private Interpolator mAnimationInterpolator;
        private Drawable mIconDrawable;
        private OnHidePromptListener mOnHidePromptListener;
        private boolean mCaptureTouchEventOnFocal;
        private float mTextSeparation;
        private boolean mAutoDismiss, mAutoFinish;
        private boolean mCaptureTouchEventOutsidePrompt;
        private Typeface mPrimaryTextTypeface, mSecondaryTextTypeface;
        private int mPrimaryTextTypefaceStyle, mSecondaryTextTypefaceStyle;
        private ColorStateList mIconDrawableTintList = null;
        private PorterDuff.Mode mIconDrawableTintMode = null;
        private boolean mHasIconDrawableTint;
        private int mIconDrawableColourFilter;
        private View mTargetRenderView;

        /**
         * Creates a builder for a tap target prompt that uses the default
         * tap target prompt theme.
         *
         * @param activity the activity to show the prompt within.
         */
        public Builder(final Activity activity) {
            this(activity, 0);
        }

        /**
         * Creates a builder for a material tap target prompt that uses an explicit theme
         * resource.
         * <p>
         * The {@code themeResId} may be specified as {@code 0}
         * to use the parent {@code context}'s resolved value for
         * {@link R.attr#MaterialTapTargetPromptTheme}.
         *
         * @param activity   the activity to show the prompt within.
         * @param themeResId the resource ID of the theme against which to inflate
         *                   this dialog, or {@code 0} to use the parent
         *                   {@code context}'s default material tap target prompt theme
         */
        public Builder(final Activity activity, int themeResId) {
            mActivity = activity;
            //Attempt to load the theme from the activity theme
            if (themeResId == 0) {
                final TypedValue outValue = new TypedValue();
                activity.getTheme().resolveAttribute(R.attr.MaterialTapTargetPromptTheme, outValue, true);
                themeResId = outValue.resourceId;
            }
            final float density = activity.getResources().getDisplayMetrics().density;
            final TypedArray a = mActivity.obtainStyledAttributes(themeResId, R.styleable.PromptView);
            mPrimaryTextColour = a.getColor(R.styleable.PromptView_primaryTextColour, Color.WHITE);
            mSecondaryTextColour = a.getColor(R.styleable.PromptView_secondaryTextColour, Color.argb(179, 255, 255, 255));
            mPrimaryText = a.getString(R.styleable.PromptView_mprimaryText);
            mSecondaryText = a.getString(R.styleable.PromptView_msecondaryText);
            mBackgroundColour = a.getColor(R.styleable.PromptView_backgroundColour, Color.argb(244, 63, 81, 181));
            mFocalColour = a.getColor(R.styleable.PromptView_focalColour, Color.WHITE);
            mFocalRadius = a.getDimension(R.styleable.PromptView_focalRadius, density * 44);
            mPrimaryTextSize = a.getDimension(R.styleable.PromptView_mprimaryTextSize, 22 * density);
            mSecondaryTextSize = a.getDimension(R.styleable.PromptView_msecondaryTextSize, 18 * density);
            mMaxTextWidth = a.getDimension(R.styleable.PromptView_maxTextWidth, 400 * density);
            mTextPadding = a.getDimension(R.styleable.PromptView_textPadding, 40 * density);
            mFocalToTextPadding = a.getDimension(R.styleable.PromptView_focalToTextPadding, 20 * density);
            mTextSeparation = a.getDimension(R.styleable.PromptView_textSeparation, 16 * density);
            mAutoDismiss = a.getBoolean(R.styleable.PromptView_autoDismiss, true);
            mAutoFinish = a.getBoolean(R.styleable.PromptView_autoFinish, true);
            mCaptureTouchEventOutsidePrompt = a.getBoolean(R.styleable.PromptView_captureTouchEventOutsidePrompt, false);
            mCaptureTouchEventOnFocal = a.getBoolean(R.styleable.PromptView_captureTouchEventOnFocal, false);
            mPrimaryTextTypefaceStyle = a.getInt(R.styleable.PromptView_mprimaryTextStyle, 0);
            mSecondaryTextTypefaceStyle = a.getInt(R.styleable.PromptView_msecondaryTextStyle, 0);
            mPrimaryTextTypeface = setTypefaceFromAttrs(a.getString(R.styleable.PromptView_primaryTextFontFamily), a.getInt(R.styleable.PromptView_mprimaryTextTypeface, 0), mPrimaryTextTypefaceStyle);
            mSecondaryTextTypeface = setTypefaceFromAttrs(a.getString(R.styleable.PromptView_secondaryTextFontFamily), a.getInt(R.styleable.PromptView_msecondaryTextTypeface, 0), mSecondaryTextTypefaceStyle);
            mIconDrawableColourFilter = a.getColor(R.styleable.PromptView_iconColourFilter, mBackgroundColour);
            mIconDrawableTintList = a.getColorStateList(R.styleable.PromptView_iconTint);
            mIconDrawableTintMode = parseTintMode(a.getInt(R.styleable.PromptView_iconTintMode, -1), PorterDuff.Mode.MULTIPLY);
            mHasIconDrawableTint = true;
            final int targetId = a.getResourceId(R.styleable.PromptView_target, 0);
            a.recycle();
            if (targetId != 0) {
                mTargetView = mActivity.findViewById(targetId);
                if (mTargetView != null) {
                    mTargetSet = true;
                }
            }
        }

        /**
         * Set the view for the prompt to focus on.
         *
         * @param target The view that the prompt will highlight.
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setTarget(final View target) {
            mTargetView = target;
            mTargetSet = true;
            return this;
        }

        /**
         * Set the view for the prompt to focus on using the given resource id.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setTarget(@IdRes final int target) {
            mTargetView = mActivity.findViewById(target);
            mTargetSet = mTargetView != null;
            return this;
        }

        /**
         * Set the centre point as a screen position
         *
         * @param left Centre point from screen left
         * @param top  Centre point from screen top
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setTarget(final float left, final float top) {
            mTargetView = null;
            mCentreLeft = left;
            mCentreTop = top;
            mTargetSet = true;
            return this;
        }

        /**
         * Change the view that is rendered as the target.
         * By default the view from {@link #setTarget(View)} is rendered as the target.
         *
         * @param view The view to use to render the prompt target
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setTargetRenderView(final View view) {
            mTargetRenderView = view;
            return this;
        }

        /**
         * Has the target been set successfully?
         *
         * @return True if set successfully.
         */
        public boolean isTargetSet() {
            return mTargetSet;
        }

        /**
         * Set the primary text using the given resource id.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setPrimaryText(@StringRes final int resId) {
            mPrimaryText = mActivity.getString(resId);
            return this;
        }

        /**
         * Set the primary text to the given string
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setPrimaryText(final String text) {
            mPrimaryText = text;
            return this;
        }

        /**
         * Set the primary text font size using the given resource id.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setPrimaryTextSize(@DimenRes final int resId) {
            mPrimaryTextSize = mActivity.getResources().getDimension(resId);
            return this;
        }

        /**
         * Set the primary text font size.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setPrimaryTextSize(final float size) {
            mPrimaryTextSize = size;
            return this;
        }

        /**
         * Set the primary text colour.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setPrimaryTextColour(@ColorInt final int colour) {
            mPrimaryTextColour = colour;
            return this;
        }

        /**
         * Set the primary text colour using the given resource id.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setPrimaryTextColourFromRes(@ColorRes final int resId) {
            mPrimaryTextColour = getColour(resId);
            return this;
        }

        /**
         * Sets the typeface and style used to display the primary text.
         */
        public Builder setPrimaryTextTypeface(final Typeface typeface) {
            return setPrimaryTextTypeface(typeface, 0);
        }

        /**
         * Sets the typeface used to display the primary text.
         */
        public Builder setPrimaryTextTypeface(final Typeface typeface, final int style) {
            mPrimaryTextTypeface = typeface;
            mPrimaryTextTypefaceStyle = style;
            return this;
        }

        /**
         * Set the secondary text using the given resource id.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setSecondaryText(@StringRes final int resId) {
            mSecondaryText = mActivity.getString(resId);
            return this;
        }

        /**
         * Set the secondary text.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setSecondaryText(final String text) {
            mSecondaryText = text;
            return this;
        }

        /**
         * Set the secondary text font size using the give resource id.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setSecondaryTextSize(@DimenRes final int resId) {
            mSecondaryTextSize = mActivity.getResources().getDimension(resId);
            return this;
        }

        /**
         * Set the secondary text font size.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setSecondaryTextSize(final float size) {
            mSecondaryTextSize = size;
            return this;
        }

        /**
         * Set the secondary text colour.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setSecondaryTextColour(@ColorInt final int colour) {
            mSecondaryTextColour = colour;
            return this;
        }

        /**
         * Set the secondary text colour using the give resource id.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setSecondaryTextColourFromRes(@ColorRes final int resId) {
            mSecondaryTextColour = getColour(resId);
            return this;
        }

        /**
         * Sets the typeface used to display the secondary text.
         */
        public Builder setSecondaryTextTypeface(final Typeface typeface) {
            return setSecondaryTextTypeface(typeface, 0);
        }

        /**
         * Sets the typeface and style used to display the secondary text.
         */
        public Builder setSecondaryTextTypeface(final Typeface typeface, final int style) {
            mSecondaryTextTypeface = typeface;
            mSecondaryTextTypefaceStyle = style;
            return this;
        }

        /**
         * Set the text left and right padding.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setTextPadding(final float padding) {
            mTextPadding = padding;
            return this;
        }

        /**
         * Set the text left and right padding using the given resource id.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setTextPadding(@DimenRes final int resId) {
            mTextPadding = mActivity.getResources().getDimension(resId);
            return this;
        }

        /**
         * Set the distance between the primary and secondary text.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setTextSeparation(final float separation) {
            mTextSeparation = separation;
            return this;
        }

        /**
         * Set the distance between the primary and secondary text using the given
         * resource id.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setTextSeparation(@DimenRes final int resId) {
            mTextSeparation = mActivity.getResources().getDimension(resId);
            return this;
        }

        /**
         * Set the padding between the text and the focal point.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setFocalToTextPadding(final float padding) {
            mFocalToTextPadding = padding;
            return this;
        }

        /**
         * Set the padding between the text and the focal point using the given
         * resource id.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setFocalToTextPadding(@DimenRes final int resId) {
            mFocalToTextPadding = mActivity.getResources().getDimension(resId);
            return this;
        }

        /**
         * Set the interpolator to use in animations.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setAnimationInterpolator(final Interpolator interpolator) {
            mAnimationInterpolator = interpolator;
            return this;
        }

        /**
         * Set the icon to draw in the focal point using the given resource id.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setIcon(@DrawableRes final int resId) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mIconDrawable = mActivity.getDrawable(resId);
            } else {
                //noinspection deprecation
                mIconDrawable = mActivity.getResources().getDrawable(resId);
            }
            return this;
        }

        /**
         * Set the icon to draw in the focal point.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setIconDrawable(final Drawable drawable) {
            mIconDrawable = drawable;
            return this;
        }

        /**
         * Applies a tint to the icon drawable
         *
         * @param tint the tint to apply to the icon drawable, {@code null} will remove the tint.
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setIconDrawableTintList(@Nullable ColorStateList tint) {
            mIconDrawableTintList = tint;
            mHasIconDrawableTint = tint != null;
            return this;
        }

        /**
         * Sets the PorterDuff mode to use to apply the tint.
         *
         * @param tintMode the tint mode to use on the icon drawable, {@code null} will remove the tint.
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setIconDrawableTintMode(@Nullable PorterDuff.Mode tintMode) {
            mIconDrawableTintMode = tintMode;
            if (tintMode == null) {
                mIconDrawableTintList = null;
                mHasIconDrawableTint = false;
            }
            return this;
        }

        /**
         * Sets the colour to use to tint the icon drawable.
         *
         * @param colour The colour to use to tint the icon drawable, call
         *               {@link #setIconDrawableTintList(ColorStateList)} or
         *               {@link #setIconDrawableTintMode(PorterDuff.Mode)} with {@code null}
         *               to remove the tint.
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setIconDrawableColourFilter(@ColorInt final int colour) {
            mIconDrawableColourFilter = colour;
            mIconDrawableTintList = null;
            mHasIconDrawableTint = true;
            return this;
        }

        /**
         * Sets the colour (from a resource) to use to tint the icon drawable.
         *
         * @param id The resource id for the colour to use to tint the icon drawable,
         *           call {@link #setIconDrawableTintList(ColorStateList)} or
         *           {@link #setIconDrawableTintMode(PorterDuff.Mode)} with {@code null}
         *           to remove the tint.
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setIconDrawableColourFilterFromRes(@ColorRes final int id) {
            final int colour;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                colour = mActivity.getColor(id);
            } else {
                colour = mActivity.getResources().getColor(id);
            }
            return setIconDrawableColourFilter(colour);
        }

        /**
         * Set the listener to listen for when the prompt is touched.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setOnHidePromptListener(final OnHidePromptListener listener) {
            mOnHidePromptListener = listener;
            return this;
        }

        /**
         * Set if the prompt should stop touch events on the focal point from passing
         * to underlying views. Default is false.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setCaptureTouchEventOnFocal(final boolean captureTouchEvent) {
            mCaptureTouchEventOnFocal = captureTouchEvent;
            return this;
        }

        /**
         * Set the max width that the primary and secondary text can be.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setMaxTextWidth(final float width) {
            mMaxTextWidth = width;
            return this;
        }

        /**
         * Set the max width that the primary and secondary text can be using the given
         * resource id.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setMaxTextWidth(@DimenRes final int resId) {
            mMaxTextWidth = mActivity.getResources().getDimension(resId);
            return this;
        }

        /**
         * Set the background colour.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setBackgroundColour(@ColorInt final int colour) {
            mBackgroundColour = colour;
            return this;
        }

        /**
         * Set the background colour using the given resource id.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setBackgroundColourFromRes(@ColorRes final int resId) {
            mBackgroundColour = getColour(resId);
            return this;
        }

        /**
         * Set the focal point colour.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setFocalColour(@ColorInt final int colour) {
            mFocalColour = colour;
            return this;
        }

        /**
         * Set the focal point colour using the given resource id.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setFocalColourFromRes(@ColorRes final int resId) {
            mFocalColour = getColour(resId);
            return this;
        }

        /**
         * Set the focal point radius.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setFocalRadius(final float radius) {
            mFocalRadius = radius;
            return this;
        }

        /**
         * Set the focal point radius using the given resource id.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setFocalRadius(@DimenRes final int resId) {
            mFocalRadius = mActivity.getResources().getDimension(resId);
            return this;
        }

        /**
         * Set whether the prompt should dismiss itself when a touch event occurs outside the focal.
         * Default is true.
         *
         * @param autoDismiss True - prompt will dismiss when touched outside the focal, false - no action taken.
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setAutoDismiss(final boolean autoDismiss) {
            mAutoDismiss = autoDismiss;
            return this;
        }

        /**
         * Set whether the prompt should finish itself when a touch event occurs inside the focal.
         * Default is true.
         *
         * @param autoFinish True - prompt will finish when touched inside the focal, false - no action taken.
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setAutoFinish(final boolean autoFinish) {
            mAutoFinish = autoFinish;
            return this;
        }

        /**
         * Set if the prompt should stop touch events outside the prompt from passing
         * to underlying views. Default is false.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setCaptureTouchEventOutsidePrompt(final boolean captureTouchEventOutsidePrompt) {
            mCaptureTouchEventOutsidePrompt = captureTouchEventOutsidePrompt;
            return this;
        }

        /**
         * Creates an {@link MaterialTapTargetPrompt} with the arguments supplied to this
         * builder.
         * <p>
         * Calling this method does not display the prompt. If no additional
         * processing is needed, {@link #show()} may be called instead to both
         * create and display the prompt.
         * </p>
         * <p>
         * Will return {@link null} if a valid target has not been set or the primary text is {@link null}.
         * To check that a valid target has been set call {@link #isTargetSet()}.
         * </p>
         */
        public MaterialTapTargetPrompt create() {
            if (!mTargetSet || mPrimaryText == null) {
                return null;
            }
            final MaterialTapTargetPrompt mPrompt = new MaterialTapTargetPrompt(mActivity);
            if (mTargetView != null) {
                mPrompt.mTargetView = mTargetView;
                mPrompt.mView.mTargetView = mTargetView;
            } else {
                mPrompt.mBaseLeft = mCentreLeft;
                mPrompt.mBaseTop = mCentreTop;
            }
            mPrompt.mClipToView = (ViewGroup) ((ViewGroup) mActivity.findViewById(android.R.id.content)).getChildAt(0);
            mPrompt.mPrimaryText = mPrimaryText;
            mPrompt.mPrimaryTextColourAlpha = Color.alpha(mPrimaryTextColour);
            mPrompt.mSecondaryText = mSecondaryText;
            mPrompt.mSecondaryTextColourAlpha = Color.alpha(mSecondaryTextColour);
            mPrompt.mMaxTextWidth = mMaxTextWidth;
            mPrompt.mTextPadding = mTextPadding;
            mPrompt.mFocalToTextPadding = mFocalToTextPadding;
            mPrompt.mBaseFocalRippleAlpha = 150;
            mPrompt.mView.mTextSeparation = mTextSeparation;
            mPrompt.mOnHidePromptListener = mOnHidePromptListener;
            mPrompt.mView.mCaptureTouchEventOnFocal = mCaptureTouchEventOnFocal;
            if (mAnimationInterpolator != null) {
                mPrompt.mAnimationInterpolator = mAnimationInterpolator;
            } else {
                mPrompt.mAnimationInterpolator = new AccelerateDecelerateInterpolator();
            }
            mPrompt.mBaseFocalRadius = mFocalRadius;
            //Calculate 10% of the focal radius
            mPrompt.mFocalRadius10Percent = mFocalRadius / 100 * 10;
            if (mIconDrawable != null) {
                mIconDrawable.setBounds(0, 0, mIconDrawable.getIntrinsicWidth(), mIconDrawable.getIntrinsicHeight());
                if (mHasIconDrawableTint) {
                    if (mIconDrawableTintList != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mIconDrawable.setTintList(mIconDrawableTintList);
                        }
                    } else {
                        mIconDrawable.setColorFilter(mIconDrawableColourFilter, mIconDrawableTintMode);
                        mIconDrawable.setAlpha(Color.alpha(mIconDrawableColourFilter));
                    }
                }
            }
            mPrompt.mView.mIconDrawable = mIconDrawable;
            mPrompt.mView.mPaintFocal = new Paint();
            mPrompt.mView.mPaintFocal.setColor(mFocalColour);
            mPrompt.mView.mPaintFocal.setAlpha(Color.alpha(mFocalColour));
            mPrompt.mView.mPaintFocal.setAntiAlias(true);
            mPrompt.mView.mPaintBackground = new Paint();
            mPrompt.mView.mPaintBackground.setColor(mBackgroundColour);
            mPrompt.mView.mPaintBackground.setAlpha(Color.alpha(mBackgroundColour));
            mPrompt.mView.mPaintBackground.setAntiAlias(true);
            mPrompt.mPaintPrimaryText = new TextPaint();
            mPrompt.mPaintPrimaryText.setColor(mPrimaryTextColour);
            mPrompt.mPaintPrimaryText.setAlpha(Color.alpha(mPrimaryTextColour));
            mPrompt.mPaintPrimaryText.setAntiAlias(true);
            mPrompt.mPaintPrimaryText.setTextSize(mPrimaryTextSize);
            setTypeface(mPrompt.mPaintPrimaryText, mPrimaryTextTypeface, mPrimaryTextTypefaceStyle);
            mPrompt.mPaintSecondaryText = new TextPaint();
            mPrompt.mPaintSecondaryText.setColor(mSecondaryTextColour);
            mPrompt.mPaintSecondaryText.setAlpha(Color.alpha(mSecondaryTextColour));
            mPrompt.mPaintSecondaryText.setAntiAlias(true);
            mPrompt.mPaintSecondaryText.setTextSize(mSecondaryTextSize);
            setTypeface(mPrompt.mPaintSecondaryText, mSecondaryTextTypeface, mSecondaryTextTypefaceStyle);
            mPrompt.mAutoDismiss = mAutoDismiss;
            mPrompt.mAutoFinish = mAutoFinish;
            mPrompt.mView.mCaptureTouchEventOutsidePrompt = mCaptureTouchEventOutsidePrompt;
            if (mTargetRenderView == null) {
                mPrompt.mView.mTargetRenderView = mPrompt.mView.mTargetView;
            } else {
                mPrompt.mView.mTargetRenderView = mTargetRenderView;
            }
            return mPrompt;
        }

        /**
         * Creates an {@link MaterialTapTargetPrompt} with the arguments supplied to this
         * builder and immediately displays the prompt.
         * <p>
         * Calling this method is functionally identical to:
         * </p>
         * <pre>
         *     MaterialTapTargetPrompt prompt = builder.create();
         *     prompt.show();
         * </pre>
         * <p>
         * Will return {@link null} if a valid target has not been set or the primary text is {@link null}.
         * To check that a valid target has been set call {@link #isTargetSet()}.
         * </p>
         */
        public MaterialTapTargetPrompt show() {
            final MaterialTapTargetPrompt mPrompt = create();
            if (mPrompt != null) {
                mPrompt.show();
            }
            return mPrompt;
        }

        private int getColour(final int resId) {
            final int colour;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                colour = mActivity.getColor(resId);
            } else {
                //noinspection deprecation
                colour = mActivity.getResources().getColor(resId);
            }
            return colour;
        }

        /**
         * Based on setTypeface in android TextView, Copyright (C) 2006 The Android Open Source Project.
         * https://android.googlesource.com/platform/frameworks/base.git/+/master/core/java/android/widget/TextView.java
         */
        private void setTypeface(TextPaint textPaint, Typeface typeface, int style) {
            if (style > 0) {
                if (typeface == null) {
                    typeface = Typeface.defaultFromStyle(style);
                } else {
                    typeface = Typeface.create(typeface, style);
                }
                textPaint.setTypeface(typeface);
                int typefaceStyle = typeface != null ? typeface.getStyle() : 0;
                int need = style & ~typefaceStyle;
                textPaint.setFakeBoldText((need & Typeface.BOLD) != 0);
                textPaint.setTextSkewX((need & Typeface.ITALIC) != 0 ? -0.25f : 0);
            } else {
                textPaint.setTypeface(typeface);
            }
        }

        /**
         * Based on setTypefaceFromAttrs in android TextView, Copyright (C) 2006 The Android Open Source Project.
         * https://android.googlesource.com/platform/frameworks/base.git/+/master/core/java/android/widget/TextView.java
         */
        private Typeface setTypefaceFromAttrs(String familyName, int typefaceIndex, int styleIndex) {
            Typeface tf = null;
            if (familyName != null) {
                tf = Typeface.create(familyName, styleIndex);
                if (tf != null) {
                    return tf;
                }
            }
            switch (typefaceIndex) {
                case 1:
                    tf = Typeface.SANS_SERIF;
                    break;
                case 2:
                    tf = Typeface.SERIF;
                    break;
                case 3:
                    tf = Typeface.MONOSPACE;
                    break;
            }
            return tf;
        }

        /**
         * Based on parseTintMode in android appcompat v7 DrawableUtils, Copyright (C) 2014 The Android Open Source Project.
         * https://android.googlesource.com/platform/frameworks/support.git/+/master/v7/appcompat/src/android/support/v7/widget/DrawableUtils.java
         */
        private PorterDuff.Mode parseTintMode(int value, PorterDuff.Mode defaultMode) {
            switch (value) {
                case 3:
                    return PorterDuff.Mode.SRC_OVER;
                case 5:
                    return PorterDuff.Mode.SRC_IN;
                case 9:
                    return PorterDuff.Mode.SRC_ATOP;
                case 14:
                    return PorterDuff.Mode.MULTIPLY;
                case 15:
                    return PorterDuff.Mode.SCREEN;
                case 16:
                    return Build.VERSION.SDK_INT >= 11
                            ? PorterDuff.Mode.valueOf("ADD")
                            : defaultMode;
                default:
                    return defaultMode;
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    static class AnimatorListener implements Animator.AnimatorListener {
        @Override
        public void onAnimationStart(Animator animation) {
        }

        @Override
        public void onAnimationEnd(Animator animation) {
        }

        @Override
        public void onAnimationCancel(Animator animation) {
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
        }
    }
}
