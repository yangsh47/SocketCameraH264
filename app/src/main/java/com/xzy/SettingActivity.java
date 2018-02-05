package com.xzy;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * 设置
 * @author 肖泽云
 *
 */
public class SettingActivity extends PreferenceActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.setting);
    }
}