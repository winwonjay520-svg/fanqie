package com.lumifocus.app;

import android.media.*;
import java.util.Random;

/** Original procedural soundscapes. Bounded PCM buffers, no downloads or audio assets. */
final class AmbientEngine implements AutoCloseable {
    static final int RATE=22050;
    private volatile boolean alive,muted;
    private volatile int[] levels={0,0,0};
    private AudioTrack track;
    private Thread worker;
    private final Random random=new Random(520);
    private double low,wind,phase,chirp,chirpLeft,chirpFrequency=1800;
    void levels(int[] next){levels=next.clone();}
    void muted(boolean value){muted=value;}
    boolean isPlaying(){return alive&&track!=null&&track.getPlayState()==AudioTrack.PLAYSTATE_PLAYING;}
    // Package visible for checking that each mixer channel produces actual finite PCM.
    short[] samples(int frames) {
        short[] pcm=new short[frames];int[] mix=levels;
        double rain=mix[0]/100.0,forest=mix[1]/100.0,cafe=mix[2]/100.0,normalizer=Math.max(1,rain+forest+cafe);
        for(int i=0;i<frames;i++){
            double white=random.nextDouble()*2-1;low+=.035*(white-low);wind+=.002*(white-wind);
            phase+=1.0/RATE;
            double rainSound=white*.22+low*1.2;
            if(forest>0&&chirpLeft<=0&&random.nextDouble()<.000035){chirpLeft=.23+random.nextDouble()*.25;chirpFrequency=1700+random.nextDouble()*1700;chirp=0;}
            double bird=0;if(chirpLeft>0){chirp+=1.0/RATE;chirpLeft-=1.0/RATE;bird=Math.sin(2*Math.PI*(chirpFrequency*chirp+140*chirp*chirp))*Math.min(1,chirp*40)*Math.min(1,chirpLeft*40)*.11;}
            double forestSound=wind*3+low*.2+bird;
            double cafeSound=cafe>0?low*.8+wind*.9+(Math.sin(phase*2*Math.PI*173)+Math.sin(phase*2*Math.PI*229))*.025*(.5+.5*Math.sin(phase*2*Math.PI*.37)):0;
            double value=muted?0:(rain*rainSound+forest*forestSound+cafe*cafeSound)/normalizer;
            pcm[i]=(short)(Math.max(-.85,Math.min(.85,value))*32767);
        }
        return pcm;
    }
    void start() {
        if(alive)return;
        int buffer=Math.max(8192,AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT));
        track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(buffer).setTransferMode(AudioTrack.MODE_STREAM).build();
        if(track.getState()!=AudioTrack.STATE_INITIALIZED){track.release();track=null;throw new IllegalStateException("音频输出不可用");}
        alive=true;track.play();
        worker=new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);while(alive){short[] pcm=samples(1024);int offset=0;while(alive&&offset<pcm.length){int written=track.write(pcm,offset,pcm.length-offset,AudioTrack.WRITE_BLOCKING);if(written<=0){alive=false;break;}offset+=written;}}},"LumiAmbient");worker.start();
    }
    @Override public void close() {
        alive=false;
        if(track!=null){try{track.pause();track.flush();}catch(IllegalStateException ignored){}}
        if(worker!=null){try{worker.join(1500);}catch(InterruptedException e){Thread.currentThread().interrupt();}worker=null;}
        if(track!=null){track.release();track=null;}
    }
}
