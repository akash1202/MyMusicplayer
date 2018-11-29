package com.example.vishal.mymusicplayer;

import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Created by Vishal on 1/6/2018.
 */

public class CustomAdapter extends RecyclerView.Adapter<CustomAdapter.MyViewHolder> {
    public List<Audio> AudioList;
    public List<Audio> AudiolistFiltered;
    Context context;
    ServiceConnection serviceConnection;
    PopupMenu popupMenu;
    boolean serviceBound = false;
    public static final String Broadcast_PLAY_NEW_AUDIO="com.example.vishal.audioplayer.PlayNewAudio";
    private String PREFRENCENAME="AKASHSASH";
    SharedPreferences sharedPreferences;

    public class MyViewHolder extends RecyclerView.ViewHolder {
        CircleImageView profilecircleImageView;
        TextView stitleTextView;
        TextView salbumTextView;
        TextView sartistTextView;

       public MyViewHolder(View view){
          super(view);
          profilecircleImageView=(CircleImageView) view.findViewById(R.id.albumImage);
          stitleTextView=view.findViewById(R.id.songTitle);
          salbumTextView=view.findViewById(R.id.albumTitle);
          sartistTextView=view.findViewById(R.id.artistTitle);
       }
    }
    public CustomAdapter(Context context, List<Audio> AudioList,ServiceConnection serviceConnection){
        this.AudioList=AudioList;
        this.AudiolistFiltered=AudioList;
        this.context=context;
        this.serviceConnection=serviceConnection;
        sharedPreferences=context.getSharedPreferences(PREFRENCENAME, Context.MODE_PRIVATE);
    }
    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View tempView=LayoutInflater.from(parent.getContext())
                .inflate(R.layout.singleaudiolayout,parent,false);
        return new MyViewHolder(tempView);
    }

    @Override
    public void onBindViewHolder(final MyViewHolder holder, final int position) {
       try {
           String tempname = "";
           String tempalbum = "";
           String tempartist = "";
           String tempdata="";
           tempname = this.AudiolistFiltered.get(position).getTitle()+"";
           tempalbum = this.AudiolistFiltered.get(position).getAlbum() + "";
           tempartist = this.AudiolistFiltered.get(position).getArtist() + "";
           tempdata = this.AudiolistFiltered.get(position).getData() + "";

           //SimpleDateFormat formatter=new SimpleDateFormat("dd-MM-yyyy hh:mm a");
           //formatter.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
           //temptime=formatter.format(new Date(temptime));
            final String tname=tempname;
           final Audio TempAudio = this.AudiolistFiltered.get(position);
           final int TempPosition = position;
           holder.stitleTextView.setText(tempname);
           holder.salbumTextView.setText(tempalbum);
           holder.sartistTextView.setText(tempartist);
           holder.profilecircleImageView.setOnClickListener(new View.OnClickListener() {
               @Override
               public void onClick(View view) {
                   startAudio(position);
                    Toast.makeText(context,tname+" started", Toast.LENGTH_SHORT).show();
               }
           });


           /*holder.optionView.setOnClickListener(new View.OnClickListener() {
               @Override
               public void onClick(View view) {

                  Toast.makeText(context,"hello",Toast.LENGTH_SHORT).show();
                   popupMenu = new PopupMenu(context, view);
                   popupMenu.getMenu().add("View");
                   popupMenu.getMenu().add("Message");
                   popupMenu.getMenu().add("Remove");
                   popupMenu.show();
                   popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                       @Override
                       public boolean onMenuItemClick(MenuItem item) {

                           if (item.getTitle().equals("View")) {
                               Toast.makeText(context, "View clicked...", Toast.LENGTH_SHORT).show();
                           }
                           if (item.getTitle().equals("Message")) {
                               Toast.makeText(context, "Message clicked...", Toast.LENGTH_SHORT).show();
                           }
                           if (item.getTitle().equals("Solved?")) {
                               removeItem(TempPosition, TempAudio);
                           }
                           return false;
                       }
                   });

               }
           });*/
           Picasso.with(context)
                   .load(R.drawable.album)
                   .resize(70,70)
                   .centerInside()
                   .onlyScaleDown()
                   .placeholder(R.drawable.ic_launcher_foreground)
                   .error(R.drawable.ic_launcher_foreground)
                   .into(holder.profilecircleImageView);
       }catch (Exception e){
           Log.d("Error(CustomAdapter):",e.toString());
       }
    }

    @Override
    public int getItemCount() {   return this.AudiolistFiltered.size();  }
    public void updateContactList(List<Audio> AudioList){
        this.AudioList=AudioList;
        notifyItemRangeChanged(0,this.AudioList.size());
    }


    public void startAudio(int audioIndex){
        //check is service active
        if(!serviceBound){
            //store Serializable audiolist to SharedPreference
            StorageUtil storage=new StorageUtil(context.getApplicationContext());
            storage.storeAudio((ArrayList<Audio>)AudioList);
            storage.storeAudioIndex(audioIndex);

            Intent playerIntent=new Intent(context,MediaPlayerService.class);
            context.startService(playerIntent);
            context.bindService(playerIntent,serviceConnection,Context.BIND_AUTO_CREATE);
        }
        else{
            //store new AudioIndex to ShredPreference
            StorageUtil storage=new StorageUtil(context.getApplicationContext());
            storage.storeAudioIndex(audioIndex);
            //service is active
            //send broadcast  to the service -> PLAY_NEW_AUDIO
            Intent broadcastIntent= new Intent(Broadcast_PLAY_NEW_AUDIO);
            context.sendBroadcast(broadcastIntent);
        }

    }


    /*public void removeItem(int position,Audio TempAudio){
        AudioList.remove(position);
        SharedPreferences.Editor editor=sharedPreferences.edit();
        int totalAudios=sharedPreferences.getInt("Audios",0);
        if(totalAudios>1)
        editor.putInt("Audios",totalAudios-1).commit();
        notifyItemRemoved(position);
        //notifyItemRangeChanged(position,ContactList.size());
        notifyItemRangeChanged(position,AudioList.size());
       Toast.makeText(context,"Removed...",Toast.LENGTH_SHORT).show();
   }
*/


}



