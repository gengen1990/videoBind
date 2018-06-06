package com.hanzi.videobinddemo;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.hanzi.videobinddemo.media.MediaBind;
import com.hanzi.videobinddemo.media.Variable.MediaBean;
import com.hanzi.videobinddemo.media.Variable.MediaBindInfo;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private Button button;

    private MediaBind mediaBind;
    private static String PATH = Environment.getExternalStorageDirectory().getAbsolutePath();
//    private String finalinputFilePath1 = PATH + "/1/ice.mp4";
    private String finalinputFilePath2 = PATH + "/1/hei.mp4";
    private String bgmPath = PATH + "/1/Christmas_Story.aac";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        TextView tv = (TextView) findViewById(R.id.sample_text);
        button = findViewById(R.id.merger);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCompose();
            }
        });
    }

    private void startCompose() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mediaBind = new MediaBind(MediaBind.BOTH_PROCESS);
                MediaBindInfo mediaBindInfo = new MediaBindInfo();

                List<MediaBean> mediaBeans = new ArrayList<>();
//                mediaBeans.add(new MediaBean(finalinputFilePath1, 0));
                mediaBeans.add(new MediaBean(finalinputFilePath2, 0));

                mediaBindInfo.setMediaBeans(mediaBeans);

//                mediaBindInfo.setBgm(new MediaBean(bgmPath, 0));

                mediaBind.open(mediaBindInfo);
                mediaBind.start();
            }
        }).start();
    }

}
