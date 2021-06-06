package com.schlaikjer.music.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.protobuf.InvalidProtocolBufferException;
import com.schlaikjer.msgs.TrackOuterClass;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class Packet {

    private static final String TAG = Packet.class.getSimpleName();

    // General packet format:
    // u32 nonce
    // u32 cmd
    // u32 data len
    // u8[data len] data

    public static final int HEADER_SIZE = 3 * 4;

    public int nonce;
    public int opcode;
    public byte[] data;


    public Packet(int nonce, int opcode, byte[] data) {
        this.nonce = nonce;
        this.opcode = opcode;
        this.data = data;
    }

    public static Packet deserialize(ByteArrayOutputStream is) throws IOException {
        // Are there even enough bytes for a header
        Log.d(TAG, "Attempt to deserialize is with " + is.size() + " bytes");
        if (is.size() < HEADER_SIZE) {
            return null;
        }

        // Get the backing data of the stream
        byte[] data = is.toByteArray();
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int nonce = buffer.getInt(0);
        int opcode = buffer.getInt(4);
        int data_len = buffer.getInt(8);
        Log.d(TAG, "Packet header: nonce: " + nonce + " opcode: " + opcode + " data len: " + data_len);

        // Is there enough data in this stream to populate the packet?
        if (data.length < data_len + HEADER_SIZE) {
            return null;
        }

        // If there is enough data, consume
        Packet p = new Packet(nonce, opcode, new byte[data_len]);
        System.arraycopy(data, 12, p.data,0, data_len);
        Log.d(TAG, "Decoded packet with len " + data_len);

        // Carve the read data off the front of the input stream
        is.reset();
        is.write(data, data_len + HEADER_SIZE, data.length - data_len - HEADER_SIZE);
        Log.d(TAG, "Input stream size now " + is.size());

        return p;
    }

    public void serialize(OutputStream os) throws IOException {
        // Allocate a byte buffer to pack the fixed size header
        ByteBuffer bb = ByteBuffer.allocate(4 * 3).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(this.nonce);
        bb.putInt(this.opcode);
        bb.putInt(this.data.length);
        os.write(bb.array());

        // Write the variable-size data
        os.write(data);
    }
}

class NetworkOpcode {
    // Trigger update of remote database
    // No data arguments
    // Zero-len response comes after update is complete
    static final int UPDATE_REMOTE_DB = 0;

    // Fetch serialized database information
    // No data arguments
    // Response is protobuf-serialized db info
    static final int FETCH_DB = 1;

    // Fetch track with specified checksum
    // Data argument is checksum (20 bytes)
    // Response is raw track data (variable size)
    static final int FETCH_TRACK = 2;

    // Fetch image with specified checksum
    // Data argument is checksum (20 bytes)
    // Response is raw image data (variable size)
    static final int FETCH_IMAGE = 3;
}

public class NetworkService extends Service {

    private static final String TAG = NetworkService.class.getSimpleName();

    // static final String SERVER_HOST = "rhye.org";
    static final String SERVER_HOST = "192.168.0.41";
    static final int SERVER_PORT = 5959;

    public final int NO_NONCE = 0xFFFFFFFF;
    private static int _nonce_counter = 0;

    private int nextNonce() {
        while (++_nonce_counter == NO_NONCE) ;
        return _nonce_counter;
    }

    public class NetworkServiceBinder extends Binder {
        public NetworkService getService() {
            return NetworkService.this;
        }
    }

    public interface NetworkTxnCallback {
        public void onTxnComplete(String data);

        public void onAbort();
    }

    private final NetworkServiceBinder _binder = new NetworkServiceBinder();

    private Socket _server_socket = null;

    private Map<Integer, NetworkTxnCallback> _callbacks = new HashMap<>();

    private Thread _network_thread;
    private AtomicBoolean _network_thread_enable = new AtomicBoolean(true);

    private final LinkedList<Packet> _packet_tx_queue = new LinkedList<>();

    ByteArrayOutputStream _incoming_data_slab = new ByteArrayOutputStream();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return _binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Create network IO thread
        _network_thread_enable.set(true);
        _network_thread = new Thread(this::networkLoop);
        _network_thread.start();
    }

    @Override
    public void onDestroy() {
        // Terminate network IO thread
        _network_thread_enable.set(false);
        try {
            _network_thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Proxy super
        super.onDestroy();
    }

    void reset_connection() {
        // Disconnect socket
        _server_socket = null;


        // Abort any pending callbacks
        synchronized (_packet_tx_queue) {
            for (NetworkTxnCallback callback : _callbacks.values()) {
                callback.onAbort();
            }
            _callbacks.clear();
        }
    }

    void handle_rx_packet(Packet packet) {
        if (packet.opcode == NetworkOpcode.FETCH_DB) {
            try {
                TrackOuterClass.MusicDatabase db =TrackOuterClass.MusicDatabase.parseFrom(packet.data);
                List<TrackOuterClass.Track> trackList = db.getTracksList();
                for (TrackOuterClass.Track track : trackList) {
                    Log.d(TAG, track.getRawPath());
                }
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }

    void networkLoop() {
        Log.d(TAG, "Starting network loop");
        while (_network_thread_enable.get()) {

            // Try and keep socket up
            if (!ensureConnected()) {
                Log.d(TAG, "Socket not connected, sleeping");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }

            // If socket is live, check for incoming data
            boolean did_rx_new_data = false;
            try {
                byte[] buffer = new byte[1024];
                while (_server_socket.getInputStream().available() > 0) {
                    int read =
                            _server_socket.getInputStream().read(buffer, 0, buffer.length);
                    _incoming_data_slab.write(buffer, 0, read);
                    did_rx_new_data = true;
                }
            } catch (IOException e) {
                e.printStackTrace();
                reset_connection();
                continue;
            }

            try {
                // Attempt to deserialize packets out of the buffered incoming data
                while (did_rx_new_data && _incoming_data_slab.size() > 12) {
                    // If there are at least enough bytes for a header in the rx buffer, test to see if we can resolve an incoming packet
                    Packet p = Packet.deserialize(_incoming_data_slab);
                    if (p != null) {
                        handle_rx_packet(p);
                    } else {
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                reset_connection();
                continue;
            }

            // If there are incoming data to be sent, handle them
            try {
                synchronized (_packet_tx_queue) {
                    while (!_packet_tx_queue.isEmpty()) {
                        Packet p = _packet_tx_queue.pop();
                        Log.d(TAG, "Sending packet with nonce " + p.nonce);
                        p.serialize(_server_socket.getOutputStream());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                reset_connection();
                continue;
            }

            // Hideous
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        Log.d(TAG, "Network loop terminating");
    }

    boolean ensureConnected() {
        // Is the socket initialized?
        if (_server_socket == null) {
            try {
                _server_socket = new Socket(SERVER_HOST, SERVER_PORT);
                _server_socket.setKeepAlive(true);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        return _server_socket.isConnected();
    }

    public boolean reloadDatabase(NetworkTxnCallback cb) {
        // Wrap packet
        Packet packet = new Packet(nextNonce(), NetworkOpcode.FETCH_DB, new byte[64]);

        // Lock tx queue
        synchronized (_packet_tx_queue) {
            // Queue packet to be sent
            _packet_tx_queue.add(packet);

            // Put the callback handler in the map
            _callbacks.put(packet.nonce, cb);
        }

        return true;
    }


}
