package qu.astro.vrshellpatcher.wm;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * Full-screen HUD overlay; renders a small card near the anchor (coords passed in).
 * Taps outside or BACK dismisses. Single-overlay policy handled by caller via decor tag.
 */
final class HudDropdownOverlay extends FrameLayout {

    private LinearLayout card;
    private ListView list;
    private AdapterView.OnItemClickListener listener;
    private Runnable onDismiss;

    HudDropdownOverlay(Context context) {
        super(context);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setClickable(true); // ensure we get touch events
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundColor(Color.TRANSPARENT); // allow HUD behind to remain visible
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        // BACK to dismiss
        setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                dismiss();
                return true;
            }
            return false;
        });
    }

    void show(ViewGroup decor,
              ListAdapter adapter,
              AdapterView.OnItemClickListener l,
              int preferredWidthPx,
              int x, int y, int gravity) {

        this.listener = l;

        // Card container
        card = new LinearLayout(getContext());
        LayoutParams cardLp = new LayoutParams(
                preferredWidthPx > 0 ? preferredWidthPx : dp(280),
                LayoutParams.WRAP_CONTENT
        );
        cardLp.leftMargin = Math.max(0, x);
        cardLp.topMargin  = Math.max(0, y);
        card.setLayoutParams(cardLp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setBackground(makeCardBg());
        card.setElevation(dp(8));
        card.setClickable(true); // so outside-tap logic works

        list = new ListView(getContext());
        list.setAdapter(adapter);
        list.setDividerHeight(dp(1));
        list.setVerticalScrollBarEnabled(true);
        list.setOnItemClickListener((parent, view, position, id) -> {
            try {
                if (listener != null) listener.onItemClick(parent, view, position, id);
            } finally {
                dismiss();
            }
        });
        // Cap height: 3..8 rows (approx)
        int maxHeight = Math.min(dp(320), dp(48) * Math.max(3, Math.min(8, adapter.getCount())));
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
        card.addView(list, listLp);

        addView(card);
        decor.addView(this);

        // Outside tap: dismiss (don’t forward to underlying UI)
        setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                int[] loc = new int[2];
                card.getLocationOnScreen(loc);
                float sx = ev.getRawX();
                float sy = ev.getRawY();
                int left = loc[0], top = loc[1];
                int right = left + card.getWidth();
                int bottom = top + card.getHeight();
                if (sx < left || sx > right || sy < top || sy > bottom) {
                    dismiss();
                    return true;
                }
            }
            return false;
        });

        // Focus ourselves for BACK handling
        requestFocus();
    }

    void setOnDismiss(Runnable r) { this.onDismiss = r; }

    void dismiss() {
        try {
            ViewGroup p = (ViewGroup) getParent();
            if (p != null) p.removeView(this);
        } catch (Throwable ignored) {}
        if (onDismiss != null) {
            try { onDismiss.run(); } catch (Throwable ignored) {}
        }
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Intercept so underlying HUD doesn’t handle touches while dropdown is open
        return true;
    }

    private GradientDrawable makeCardBg() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xFF1F1F1F);
        gd.setCornerRadius(dp(12));
        gd.setStroke(dp(1), 0x33FFFFFF);
        return gd;
    }

    private int dp(int v) {
        float d = (getResources() != null) ? getResources().getDisplayMetrics().density : 1f;
        return (int) (v * d + 0.5f);
    }
}
