package com.example.vishal.mymusicplayer;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;

public class MediaPlayerService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener,
        MediaPlayer.OnBufferingUpdateListener,
        AudioManager.OnAudioFocusChangeListener {

    private final IBinder iBinder = new LocalBinder();


    public MediaPlayer mediaPlayer;
    private String mediaFile;
    private int resumePosition;
    private AudioManager audioManager;

    SeekBar seekBar;
    TextView completedTime, remainTime;

    BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //pausing audio on becoming noisy
            pauseMedia();
            buildNotification(PlaybackStatus.PAUSED);
        }
    };
    //Handle incoming phone calls
    private boolean onGoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    //List of Available Audio Files
    private ArrayList<Audio> audioList;
    private int audioIndex = -1;
    private Audio activeAudio; //and Object of currently playing Audio


    public static final String ACTION_PLAY = "com.example.vishal.audioplayer.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.example.vishal.audioplayer.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "com.example.vishal.audioplayer.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.example.vishal.audioplayer.ACTION_NEXT";
    public static final String ACTION_STOP = "com.example.vishal.audioplayer.ACTION_STOP";

    //MediaSession
    private MediaSessionManager mediaSessionManager;
    private MediaSession mediaSession;
    private MediaController.TransportControls transportControls;

    //AudioPlayer notification ID
    private static final int NOTIFICATION_ID = 101;


    public MediaPlayerService(Context context, Activity activity) {
        seekBar = (SeekBar) activity.findViewById(R.id.seekbar);
        completedTime = (TextView) activity.findViewById(R.id.completedTime);
        remainTime = (TextView) activity.findViewById(R.id.remainTime);
    }

    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();

        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnInfoListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.reset();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_SYSTEM);
        //mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        try {
            //mediaPlayer.setDataSource(mediaFile);
            mediaPlayer.setDataSource(activeAudio.getData());
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
        mediaPlayer.prepareAsync();


    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    //The system calls this method when an activity, requests the service be started
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        try {
            // for media stream from url
            //An audio file is passed to the service through putExtra();
            //mediaFile=intent.getExtras().getString("media");


            //mediaPlayer=new MediaPlayer();

            //load Data from SharedPreference
            StorageUtil storage = new StorageUtil(getApplicationContext());
            audioList = storage.loadAudio();
            audioIndex = storage.loadAudioIndex();
            if (audioIndex != -1 && audioIndex < audioList.size()) {
                //index is in valid range
                activeAudio = audioList.get(audioIndex);
            } else {
                stopSelf();
            }

        } catch (NullPointerException e) {
            stopSelf();
        }
        //request  audio focus
        if (requestAudioFocus() == false) {
            //couldn't get audio focus
            stopSelf();
        }

        if (mediaSessionManager == null) {
            try {
                initMediaPlayer();
                initMediaSession();
            } catch (RemoteException e) {
                e.printStackTrace();
                stopSelf();
            }
            buildNotification(PlaybackStatus.PLAYING);
        }
        //Handle Intent action from MediaSession.TransportControls

        handleIncomingActions(intent);

        // for media stream from url
       /* if(mediaFile!=null&&mediaFile!="")
            initMediaPlayer();*/
        return super.onStartCommand(intent, flags, startId);
    }


    public void registerBecomingNoisyRegister() {
        //register after getting audio focus
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }


    private void callStateListener() {
        //get the telephony manager
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        //starting listening for Phonestate changes
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                //if at least one call exists or the phone is ringing
                //pause the MediaPlayer
                switch (state) {
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mediaPlayer != null) {
                            pauseMedia();
                            onGoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        //phone idle start playing
                        if (mediaPlayer != null) {
                            onGoingCall = false;
                            resumemedia();
                        }
                        break;
                }
                // Register the listener with the telephony manager
                // Listen for changes to the device call state.
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            }
        };
    }


    @Override
    public void onCreate() {

        super.onCreate();
        //performing One-time Setup Procedure

        //managing incoming phone calls during playback
        //pause Mediaplayer on incoming call
        //Resume on Hangup
        callStateListener();
        //Action Audio_Becoming_Noisy -- change in audio outputs -- Broadcaster receiver
        registerBecomingNoisyRegister();
        //Listen For new Audio to play  --BroadCastReceiver
        register_playNewAudio();
    }


    private BroadcastReceiver playnewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            audioIndex = new StorageUtil(getApplicationContext()).loadAudioIndex();
            if (audioIndex != -1 && audioIndex < audioList.size()) {
                //Index is in Valid Range
                activeAudio = audioList.get(audioIndex);
            } else {
                stopSelf();
            }

            //A PLAY_NEW_AUDIO action received
            //reset mediaPlayer to play new Audio
            stopMedia();
            mediaPlayer.reset();
            initMediaPlayer();
            updateMetaData();
            buildNotification(PlaybackStatus.PLAYING);
        }
    };

    private void register_playNewAudio() {
        //Register playNewMedia receiver
        IntentFilter filter = new IntentFilter(MainActivity.Broadcast_PLAY_NEW_AUDIO);
        registerReceiver(playnewAudio, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            stopMedia();
            mediaPlayer.release();
        }
        removeAudioFocus();
        // Disable phoneStateListener
        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
        removeNotification();
        //unregister broadcastReceivers
        unregisterReceiver(becomingNoisyReceiver);
        unregisterReceiver(playnewAudio);
        //clear cached playlist
        new StorageUtil(getApplicationContext()).clearCachedAudioPlaylist();
    }


    public boolean requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            //Focus Gained
            return true;
        }
        //couldn't gain focus
        return false;
    }

    private boolean removeAudioFocus() {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(this);
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mediaPlayer, int i) {
        //Invoked means updating buffering status of
        // a media resource is being streamed on network
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        //Invoked when playback of a media source has completed.
        stopMedia();
        //stop service
        stopSelf();
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        //Invoked when there has been an error during an asynchronous operation

        // i=what,i1=extra
        switch (i) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.d("Media Error:", "MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK" + i1);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.d("Media Error:", "MEDIA_ERROR_SERVER_DIED" + i1);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.d("Media Error:", "MEDIA_ERROR_UNKNOWN" + i1);
                break;
        }
        return false;
    }

    @Override
    public boolean onInfo(MediaPlayer mediaPlayer, int i, int i1) {
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        //Invoked when the media source is ready for playback.
        playmedia();
        seekBar.setProgress(0);
        seekBar.setMax(mediaPlayer.getDuration());
        completedTime.setText("00:00");
        remainTime.setText(mediaPlayer.getDuration() + "");
    }

    @Override
    public void onSeekComplete(MediaPlayer mediaPlayer) {

    }


    @Override
    public void onAudioFocusChange(int i) {
        switch (i) {
            case AudioManager.AUDIOFOCUS_GAIN:
                //resume playback
                if (mediaPlayer == null) initMediaPlayer();
                else if (!mediaPlayer.isPlaying()) mediaPlayer.start();
                mediaPlayer.setVolume(1.0f, 1.0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Lost focus for an unbounded amount of time: stop playback and release media player
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                if (mediaPlayer.isPlaying()) mediaPlayer.pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (mediaPlayer.isPlaying()) mediaPlayer.setVolume(1.0f, 1.0f);
                break;
        }
    }


    public void playmedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    public void stopMedia() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }

    public void pauseMedia() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            resumePosition = mediaPlayer.getCurrentPosition();
        }
    }

    public void resumemedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.seekTo(resumePosition);
            mediaPlayer.start();
        }
    }


    private void initMediaSession() throws RemoteException {
        if (mediaSessionManager != null) return;
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        //create a new MediaSession
        mediaSession = new MediaSession(getApplicationContext(), "AudioPlayer");
        //get MediaSessions transport controls
        transportControls = mediaSession.getController().getTransportControls();
        //set MediaSession -> ready to receive media commands
        mediaSession.setActive(true);
        //indicate that the MediaSession handles transport control commands
        // through its MediaSession.Callback.
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        //set mediaSession's Metadata
        updateMetaData();

        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                resumemedia();
                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onPause() {
                super.onPause();
                pauseMedia();
                buildNotification(PlaybackStatus.PAUSED);
            }


            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                skipToNext();
                updateMetaData();
                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                skipToPrevious();
                updateMetaData();
                buildNotification(PlaybackStatus.PLAYING);
            }


            @Override
            public void onStop() {
                super.onStop();
                removeNotification();
                //stop service
                stopSelf();
            }

            @Override
            public void onSeekTo(long pos) {
                super.onSeekTo(pos);
            }
        });
    }

    public void updateMetaData() {
        //replace with medias abumart
        Bitmap albumArt = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_background);
        //update the current metadata
        mediaSession.setMetadata(new MediaMetadata.Builder()
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, activeAudio.getArtist())
                .putString(MediaMetadata.METADATA_KEY_ALBUM, activeAudio.getAlbum())
                .putString(MediaMetadata.METADATA_KEY_TITLE, activeAudio.getTitle())
                .build());
    }

    public void skipToNext() {

        //get first if reach to end
        if (audioIndex == audioList.size() - 1) {
            audioIndex = 0;
            activeAudio = audioList.get(audioIndex);
        } else {
            // get next
            activeAudio = audioList.get(++audioIndex);
        }

        //update
        new StorageUtil(getApplicationContext()).storeAudioIndex(audioIndex);
        stopMedia();
        //reset mediaPlayer
        mediaPlayer.reset();
        initMediaPlayer();
    }

    public void skipToPrevious() {

        //get first if reach to first
        if (audioIndex == 0) {
            audioIndex = audioList.size() - 1;
            activeAudio = audioList.get(audioIndex);
        } else {
            // get previous
            activeAudio = audioList.get(++audioIndex);
        }

        //update
        new StorageUtil(getApplicationContext()).storeAudioIndex(audioIndex);
        stopMedia();
        //reset mediaPlayer
        mediaPlayer.reset();
        initMediaPlayer();
    }


    private void buildNotification(PlaybackStatus playbackStatus) {
        int notificationAction = android.R.drawable.ic_media_pause;

        PendingIntent play_pauseAction = null;
        //building a new notification according to current MediaPlayerStatus
        if (playbackStatus == PlaybackStatus.PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause;
            //creating Pause Action
            play_pauseAction = playbackAction(1);
        } else if (playbackStatus == PlaybackStatus.PAUSED) {
            notificationAction = android.R.drawable.ic_media_play;
            //creating a Play Action
            play_pauseAction = playbackAction(0);
        }

        Intent resultIntent = new Intent(this, MainActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntentWithParentStack(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), android.R.drawable.stat_sys_headset);

        //create a new Notification
        Notification.Builder notificationBuilder = new Notification.Builder(this)
                .setShowWhen(false)
                .setStyle(new Notification.MediaStyle()
                        //attach our media session token
                        .setMediaSession(mediaSession.getSessionToken())
                        //show our playback controls in compat notification view
                        .setShowActionsInCompactView(0, 1, 2))
                .setColor(getResources().getColor(R.color.colorPrimary))
                .setLargeIcon(largeIcon)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                //set notification content information
                .setContentText(activeAudio.getArtist())
                .setContentTitle(activeAudio.getTitle())
                .setContentInfo(activeAudio.getTitle())
                //add playback action
                .addAction(android.R.drawable.ic_media_previous, "previous", playbackAction(3))
                .addAction(notificationAction, "pause", play_pauseAction)
                .addAction(android.R.drawable.ic_media_next, "next", playbackAction(2))
                .setContentIntent(resultPendingIntent);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notificationBuilder.build());

    }

    private void removeNotification() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }


    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackAction = new Intent(this, MediaPlayerService.class);
        switch (actionNumber) {
            case 0:
                //Play
                playbackAction.setAction(ACTION_PLAY);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 1:
                //Pause
                playbackAction.setAction(ACTION_PAUSE);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 2:
                //Next Track
                playbackAction.setAction(ACTION_NEXT);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 3:
                //Previous Track
                playbackAction.setAction(ACTION_PREVIOUS);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            default:
                break;
        }
        return null;
    }

    private void handleIncomingActions(Intent playbackAction) {
        if (playbackAction == null || playbackAction.getAction() == null) return;

        String actionString = playbackAction.getAction();
        if (actionString.equalsIgnoreCase(ACTION_PLAY)) {
            transportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)) {
            transportControls.pause();
        } else if (actionString.equalsIgnoreCase(ACTION_NEXT)) {
            transportControls.skipToNext();
        } else if (actionString.equalsIgnoreCase(ACTION_PREVIOUS)) {
            transportControls.skipToPrevious();
        } else if (actionString.equalsIgnoreCase(ACTION_STOP)) {
            transportControls.stop();
        }

    }


    public class LocalBinder extends Binder {
        public MediaPlayerService getService() {
            return MediaPlayerService.this;
        }
    }
}
