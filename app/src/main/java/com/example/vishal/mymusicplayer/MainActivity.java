package com.example.vishal.mymusicplayer;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity{

    public MediaPlayerService player;
    boolean serviceBound=false;
    ServiceConnection serviceConnection;
    Button playpauseButton, nextButton, previousButton;
    SeekBar seekBar;
    int togglePlayPause = 0;
    int pos = 0, totalSong = 0;

    ArrayList<Audio> audioList;     //available List of AudioFiles
    RecyclerView audioListAdapter;
    CustomAdapter customAdapter;
    RecyclerView.LayoutManager layoutManager;
    private String PREFRENCENAME="AKASHSASH";
    SharedPreferences sharedPreferences;

    public static final String Broadcast_PLAY_NEW_AUDIO="com.example.vishal.audioplayer.PlayNewAudio";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        playpauseButton = (Button) findViewById(R.id.playpauseButton);
        nextButton = (Button) findViewById(R.id.nextButton);
        previousButton = (Button) findViewById(R.id.previousButton);
        seekBar = (SeekBar) findViewById(R.id.seekbar);
        audioListAdapter= (RecyclerView) findViewById(R.id.AudioListAdapter);
            //Binding this Client to the AudioPlayer Service
        serviceConnection=new ServiceConnection() {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                //MediaPlayerService.LocalBinder localBinder=(MediaPlayerService.LocalBinder) iBinder;
                //player=localBinder.getService();
                player = new MediaPlayerService(getApplicationContext(), MainActivity.this);
                serviceBound=true;
                Toast.makeText(MainActivity.this,"Service Bounded",Toast.LENGTH_LONG).show();
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                serviceBound=false;
            }
        };




        loadAudio();
        pos = 0;

        //play  first audio in audiolist

        //playAudio("https://upload.wikimedia.org/wikipedia/commons/6/6c/Grieg_Lyric_Pieces_Kobold.ogg");
        playAudio(0);

        customAdapter.setClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pos = audioListAdapter.indexOfChild(view);
                Toast.makeText(MainActivity.this, "this is " + pos, Toast.LENGTH_SHORT).show();
                playAudio(pos);
            }
        });


        previousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, "Previous Button pressed", Toast.LENGTH_SHORT).show();
                player.skipToPrevious();
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, "Next Button pressed", Toast.LENGTH_SHORT).show();
                player.skipToNext();
            }
        });

        playpauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (togglePlayPause == 0 && !player.mediaPlayer.isPlaying()) {  //already not playing and pressed the button
                    playpauseButton.setBackground(getResources().getDrawable(R.drawable.ic_pause_circle_outline_black_24dp));
                    player.playmedia();
                    togglePlayPause = 1;
                } else if (togglePlayPause == 0 && player.mediaPlayer.isPlaying()) {  //Started with already playing and pressed the button
                    playpauseButton.setBackground(getResources().getDrawable(R.drawable.ic_pause_circle_outline_black_24dp));
                    player.resumemedia();
                    togglePlayPause = 1;
                } else if (togglePlayPause == 1) {  //Playing and pressed the button
                    player.pauseMedia();
                    playpauseButton.setBackground(getResources().getDrawable(R.drawable.ic_play_circle_outline_black_24dp));
                    togglePlayPause = 0;
                }

            }
        });

        //startAudio(0);
        Log.d("message","Audio started");
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("ServiceState",serviceBound);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceBound=savedInstanceState.getBoolean("ServiceState");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(serviceBound){
            unbindService(serviceConnection);
            //player is active
            player.stopSelf();
        }

    }

    /*public void playAudio(String media){
        //check is service active
        if(!serviceBound){
            Intent playIntent=new Intent(MainActivity.this,MediaPlayerService.class);
            playIntent.putExtra("media",media);
            startService(playIntent);
            bindService(playIntent,serviceConnection, Context.BIND_AUTO_CREATE);
        }
        else{
            //Service is active
            //Send media with BroadcastReceiver
        }*/


    public void playAudio(int audioIndex){
        //check is service active
        if(!serviceBound){
            StorageUtil storage=new StorageUtil(getApplicationContext());
            storage.storeAudio(audioList);
            storage.storeAudioIndex(audioIndex);
            Intent playIntent=new Intent(MainActivity.this,MediaPlayerService.class);
            startService(playIntent);
            bindService(playIntent,serviceConnection, Context.BIND_AUTO_CREATE);
        }
        else{
            //stor the new audioIndex to sharedPreferences
            StorageUtil storage=new StorageUtil(getApplicationContext());
            storage.storeAudioIndex(audioIndex);
            // service is Active
            //send a broadcast to the service -> PLAY_NEW_AUDIO
            Intent broadcastIntent =new Intent(Broadcast_PLAY_NEW_AUDIO);
            sendBroadcast(broadcastIntent);
        }

    }



    public void startAudio(int audioIndex){
        //check is service active
        if(!serviceBound){
            //store Serializable audiolist to SharedPreference
           StorageUtil storage=new StorageUtil(getApplicationContext());
           storage.storeAudio(audioList);
           storage.storeAudioIndex(audioIndex);

           Intent playerIntent=new Intent(this,MediaPlayerService.class);
           startService(playerIntent);
           bindService(playerIntent,serviceConnection,Context.BIND_AUTO_CREATE);
        }
        else{
                //store new AudioIndex to ShredPreference
            StorageUtil storage=new StorageUtil(getApplicationContext());
            storage.storeAudioIndex(audioIndex);
            //service is active
            //send broadcast  to the service -> PLAY_NEW_AUDIO
            Intent broadcastIntent= new Intent(Broadcast_PLAY_NEW_AUDIO);
            sendBroadcast(broadcastIntent);
        }

    }

    private void loadAudio(){
        ContentResolver contentResolver=getContentResolver();
        Uri uri= MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection=MediaStore.Audio.Media.IS_MUSIC+"!= 0";
        String sortOrder=MediaStore.Audio.Media.TITLE+" ASC";
        Cursor cursor=contentResolver.query(uri,null,selection,null,sortOrder);
    if(cursor!=null&cursor.getCount()>0){
        audioList=new ArrayList<>();
        cursor.moveToFirst();
        do{
            String data=cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
            String title=cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
            String album=cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
            String artist=cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
            //save to AudioList
            audioList.add(new Audio(data,title,album,artist));
        }while (cursor.moveToNext());
    }
    cursor.close();


        totalSong = audioList.size() - 1;
        customAdapter = new CustomAdapter(MainActivity.this, audioList, serviceConnection);
        layoutManager= new LinearLayoutManager(getApplicationContext());
        audioListAdapter.setLayoutManager(layoutManager);
        audioListAdapter.setItemAnimator(new DefaultItemAnimator());
        audioListAdapter.setAdapter(customAdapter);

    }

}
