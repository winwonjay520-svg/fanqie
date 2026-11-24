package com.lumifocus.app;

import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.widget.*;

/** Test-APK-only IME: LDPlayer's host-input IME does not create a keyboard window.
 * This small real Android IME lets device tests exercise IME window insets. */
public final class QaImeService extends InputMethodService {
    @Override public boolean onEvaluateInputViewShown(){super.onEvaluateInputViewShown();return true;}
    @Override public boolean onEvaluateFullscreenMode(){return false;}
    @Override public View onCreateInputView(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackgroundColor(Color.rgb(230,232,235));int unit=(int)getResources().getDisplayMetrics().density;panel.setPadding(16*unit,16*unit,16*unit,16*unit);
        panel.setMinimumHeight(280*unit);TextView title=new TextView(this);title.setText("Android IME · tablet window check");title.setTextSize(18);title.setTextColor(Color.DKGRAY);panel.addView(title);
        for(String row:new String[]{"qwertyuiop","asdfghjkl","zxcvbnm"}){
            LinearLayout keys=new LinearLayout(this);for(char c:row.toCharArray()){Button key=new Button(this);key.setText(String.valueOf(c));key.setOnClickListener(v->{if(getCurrentInputConnection()!=null)getCurrentInputConnection().commitText(String.valueOf(c),1);});keys.addView(key,new LinearLayout.LayoutParams(0,58*unit,1));}panel.addView(keys);
        }
        Button done=new Button(this);done.setText("Done");done.setOnClickListener(v->requestHideSelf(0));panel.addView(done,new LinearLayout.LayoutParams(-1,52*unit));return panel;
    }
}
