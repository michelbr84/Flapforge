package io.github.michelbr84.flapforge.android;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/**
 * P0 spike stub: proves the skeleton, the SDK and the AGP pipeline. The real host (P2) replaces
 * this with the GameHost wiring; keep this file trivial until then.
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView hello = new TextView(this);
        hello.setText("Flapforge Android P0 skeleton");
        hello.setGravity(Gravity.CENTER);
        setContentView(hello);
    }
}
