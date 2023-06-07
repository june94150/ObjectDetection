package org.pytorch.demo.objectdetection;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

public class SettingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

        private CheckBoxPreference checkBox1;
        private CheckBoxPreference checkBox2;
        private CheckBoxPreference checkBox3;
        private SwitchPreference switch1;


        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.activity_setting, rootKey);

            checkBox1 = findPreference("checkbox1");
            switch1 = findPreference("switch1");

            // Set the initial value of the checkbox preference
            boolean isChecked = switch1.isChecked();
            checkBox1.setChecked(isChecked);

            // Set the listener for the switch preference
            switch1.setOnPreferenceChangeListener(this);
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference.getKey().equals("switch_preference")) {
                boolean isChecked = (boolean) newValue;
                if (isChecked) {
                    // Set the checkbox preference to false when the switch preference is true
                    checkBox1.setChecked(false);
                }
            }
            return true;
        }
    }
}