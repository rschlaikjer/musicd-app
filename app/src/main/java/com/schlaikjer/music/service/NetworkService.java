package com.schlaikjer.music.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.schlaikjer.music.model.NetworkOpcode;
import com.schlaikjer.music.model.Packet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
        public void onTxnComplete(Packet p);

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
                        synchronized (_packet_tx_queue) {
                            NetworkTxnCallback callback = _callbacks.get(p.nonce);
                            _callbacks.remove(p.nonce);
                            if (callback != null) {
                                callback.onTxnComplete(p);
                            }
                        }
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

    public void reloadDatabase(NetworkTxnCallback cb) {
        // Wrap packet
        Packet packet = new Packet(nextNonce(), NetworkOpcode.FETCH_DB, new byte[0]);

        // Lock tx queue
        synchronized (_packet_tx_queue) {
            // Queue packet to be sent
            _packet_tx_queue.add(packet);

            // Put the callback handler in the map
            _callbacks.put(packet.nonce, cb);
        }
    }

    public void fetchTrack(byte[] checksum, NetworkTxnCallback cb) {
        Packet packet = new Packet(nextNonce(), NetworkOpcode.FETCH_TRACK, checksum);

        // Lock tx queue
        synchronized (_packet_tx_queue) {
            // Queue packet to be sent
            _packet_tx_queue.add(packet);

            // Put the callback handler in the map
            _callbacks.put(packet.nonce, cb);
        }
    }

    public void fetchImage(byte[] checksum, NetworkTxnCallback cb) {
        Packet packet = new Packet(nextNonce(), NetworkOpcode.FETCH_IMAGE, checksum);

        // Lock tx queue
        synchronized (_packet_tx_queue) {
            // Queue packet to be sent
            _packet_tx_queue.add(packet);

            // Put the callback handler in the map
            _callbacks.put(packet.nonce, cb);
        }
    }

}
