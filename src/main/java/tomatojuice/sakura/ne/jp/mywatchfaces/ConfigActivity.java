package tomatojuice.sakura.ne.jp.mywatchfaces;

import android.app.Activity;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;

public class ConfigActivity extends Activity{

    private Config mConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        final CheckBox smooth = (CheckBox)findViewById(R.id.smooth);

        /* 初期化と値の取得 */
        mConfig = new Config(this, new Config.OnConfigChangedListener() {
            @Override
            public void onConfigChanged(Config config) {
                smooth.setChecked(config.isSmooth());

            }
        });

        /* Check時の動作 */
        smooth.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mConfig.setIsSmooth(isChecked);
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        /* 同期開始 */
        mConfig.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        /* 同期終了 */
        mConfig.disconnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

}
